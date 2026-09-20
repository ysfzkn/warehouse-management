# Deployment & Subdomain Routing Guide

## Mimari Ozet

```
                        Internet
                           |
                    [Load Balancer / CDN]
                           |
                     [Nginx Reverse Proxy]
                      /              \
        admin.domain.com          domain.com
              |                       |
        [Admin Panel]           [Storefront]
         React SPA               React SPA
              \                     /
               \                   /
            [Spring Boot Backend]
                 Port 8080
                    |
              [PostgreSQL]
                 Port 5432
```

**Onemli:** Admin ve storefront AYNI React build'inden servis edilir.
URL routing (`/store/*` vs `/`) ile ayrilir. Tek build, tek deploy.
Subdomain routing Nginx seviyesinde API yonlendirme ile saglanir.

---

## Subdomain Yaklasimi Aciklamasi

### Ne Yapildi?

1. **Backend API namespace ayrildi:**
   - `/api/admin/*` → WMS (depo yonetim) endpoint'leri
   - `/api/store/*` → E-ticaret endpoint'leri
   - Tek Spring Boot uygulamasi, iki namespace

2. **Frontend tek app, iki layout:**
   - `/store/*` route'lari → StoreLayout (musteri arayuzu)
   - `/` root route'lari → AdminLayout (admin arayuzu)
   - Ayni React build, ayni Docker image

3. **Nginx subdomain routing:**
   - `admin.domain.com` → Admin API'ye yonlendirir (`/api/admin/*`)
   - `domain.com` → Store API'ye yonlendirir (`/api/store/*`)
   - Her iki subdomain ayni React build'i sunar
   - Frontend icindeki React Router hangi sayfalarin gosterilecegine karar verir

4. **CORS env-based:**
   - `CORS_ALLOWED_ORIGINS` ortam degiskeni ile production domain'ler tanimlanir
   - Varsayilan: `http://localhost:*,https://localhost:*`

---

## Production Deployment Adimlari

### Adim 1: Sunucu Hazırligi

```bash
# Ubuntu/Debian sunucuda
sudo apt update
sudo apt install docker.io docker-compose nginx certbot python3-certbot-nginx

# Docker'i baslat
sudo systemctl enable docker
sudo systemctl start docker
```

### Adim 2: DNS Ayarlari

Domain saglayicinizda su DNS kayitlarini ekleyin:

| Tip | Ad | Deger |
|-----|-----|-------|
| A | @ | SUNUCU_IP |
| A | www | SUNUCU_IP |
| A | admin | SUNUCU_IP |

Yayilma suresi: 5-30 dakika.

### Adim 3: Proje Kopyalama

```bash
# Sunucuya projeyi kopyala
git clone <repo-url> /opt/warehouse-management
cd /opt/warehouse-management

# .env dosyasi olustur
cp .env.example .env
nano .env
```

### Adim 4: .env Dosyasini Doldur

```bash
# Zorunlu degisiklikler
DATABASE_URL=jdbc:postgresql://db:5432/warehouse_db
DB_USERNAME=warehouse_user
DB_PASSWORD=GUCLU_BIR_SIFRE_BURAYA

APP_ADMIN_USERNAME=admin
APP_ADMIN_PASSWORD=GUCLU_BIR_SIFRE
ADMIN_SECURITY_CODE=GUCLU_BIR_KOD

JWT_SECRET=EN_AZ_64_KARAKTER_RASTGELE_STRING_BURAYA

# CORS — production domain'lerinizi ekleyin
CORS_ALLOWED_ORIGINS=https://admin.yourdomain.com,https://yourdomain.com,https://www.yourdomain.com

# Odeme (iyzico production)
PAYMENT_SANDBOX=false
IYZICO_API_KEY=GERCEK_API_KEY
IYZICO_SECRET_KEY=GERCEK_SECRET_KEY
IYZICO_BASE_URL=https://api.iyzipay.com
IYZICO_CALLBACK_URL=https://yourdomain.com/api/store/payment/callback
```

### Adim 5: Nginx Config

```bash
# Production nginx config'i kopyala
sudo cp nginx/prod.conf /etc/nginx/sites-available/warehouse

# Domain adini degistir
sudo sed -i 's/yourdomain.com/GERCEK_DOMAIN/g' /etc/nginx/sites-available/warehouse

# Aktif et
sudo ln -s /etc/nginx/sites-available/warehouse /etc/nginx/sites-enabled/
sudo rm /etc/nginx/sites-enabled/default

# Test et
sudo nginx -t

# Baslat
sudo systemctl restart nginx
```

### Adim 6: SSL Sertifikasi (Let's Encrypt)

```bash
# Certbot ile ucretsiz SSL
sudo certbot --nginx -d yourdomain.com -d www.yourdomain.com -d admin.yourdomain.com

# Otomatik yenileme kontrol
sudo certbot renew --dry-run
```

Certbot, nginx config'e SSL ayarlarini otomatik ekler.

### Adim 7: Docker Compose ile Deploy

```bash
cd /opt/warehouse-management

# .env'yi docker-compose'a yukle
set -a && source .env && set +a

# Build ve baslat
docker-compose up -d --build

# Logları izle
docker-compose logs -f backend
```

### Adim 8: Dogrulama

```bash
# Backend saglik kontrolu
curl https://yourdomain.com/api/info

# Admin panel
# Tarayicida: https://admin.yourdomain.com

# Storefront
# Tarayicida: https://yourdomain.com/store

# API testi
curl https://yourdomain.com/api/store/products
curl https://admin.yourdomain.com/api/admin/auth/login \
  -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"YOUR_PASSWORD"}'
```

---

## Subdomain Routing Detaylari

### Nasil Calisiyor?

```
[Tarayici] → admin.yourdomain.com/products
     ↓
[Nginx] server_name admin.yourdomain.com
     ↓ /api/* istekleri → rewrite → /api/admin/*
     ↓ Diger istekler → React SPA (index.html)
     ↓
[React Router] /products → AdminLayout → Products.js
     ↓
[Axios] GET /api/products → Nginx rewrite → /api/admin/products → Backend
```

```
[Tarayici] → yourdomain.com/store/urun/laptop-x1
     ↓
[Nginx] server_name yourdomain.com (default_server)
     ↓ /api/store/* istekleri → proxy → Backend
     ↓ Diger istekler → React SPA (index.html)
     ↓
[React Router] /store/urun/laptop-x1 → StoreLayout → ProductDetailPage.js
     ↓
[Axios] GET /api/store/products/laptop-x1 → Nginx proxy → Backend
```

### Neden Admin Frontend Degisikligi Gerekmedi?

Admin frontend hala `/api/stocks`, `/api/products` gibi eski path'leri kullaniyor. Nginx'teki **rewrite kuralı** bunlari otomatik olarak `/api/admin/stocks`, `/api/admin/products`'a donusturuyor:

```nginx
location ~ ^/api/(?!admin/|store/|info)(.*)$ {
    rewrite ^/api/(.*)$ /api/admin/$1 break;
    proxy_pass http://backend;
}
```

Bu sayede frontend kodu degistirmeden subdomain routing calisiyor.

---

## Railway / PaaS Deployment

Railway, Heroku veya benzer PaaS platformlarinda:

```bash
# railway.json zaten mevcut
# 1. Railway CLI ile deploy
railway login
railway link
railway up

# 2. Environment variables Railway dashboard'dan ayarla
# Diger env var'lari manuel ekle
```

**Not:** PaaS'ta subdomain routing icin platform-specific DNS ve routing ayarlari gerekir. Railway'de custom domain ekleme: Settings → Domains.

### Veritabani baglantisi

Railway'in Postgres servisi baglantiyi `postgresql://kullanici:parola@host:5432/db`
biciminde verir; JDBC surucusu bu semayi tanimaz ve "claims to not accept jdbcUrl"
diye reddeder. `DatabaseUrlNormalizer` acilista bu degeri `jdbc:postgresql://...`
bicimine cevirir ve URL'e gomulu kullanici/parolayi ayiklar, dolayisiyla
`DATABASE_URL` her iki bicimde de calisir. En saglami referans vermektir:

```
DATABASE_URL=${{Postgres.DATABASE_URL}}
```

Boyle kullanildiginda `DB_USERNAME` / `DB_PASSWORD` gerekmez; parola servis tarafinda
degistiginde kendiliginden guncellenir. 32 karakterlik bir parolayi ayri bir
degiskene elle kopyalamak, sessizce yanlis yapilabilen bir adimdir.

**Tuzak 1 — `SPRING_DATASOURCE_*` degiskenleri `DATABASE_URL`'i ezer.** Ortam
degiskenleri, jar'a paketlenmis `application-prod.properties`'ten ustundur ve
`SPRING_DATASOURCE_URL` gevsek eslemeyle dogrudan `spring.datasource.url`'e baglanir.
Boyle bir degisken varsa `DATABASE_URL`'e ne yazilirsa yazilsin okunmaz. Ayni sey
`SPRING_DATASOURCE_USERNAME` / `_PASSWORD` icin de gecerli. Bunlar silinmelidir.

**Tuzak 2 — degisken degisiklikleri redeploy ister.** Ortam degiskenleri konteyner
baslarken sabitlenir; panelden degistirip restart etmek yetmez.

**Tuzak 3 — servis silinince DNS kaydi aninda kaybolur.**
`<servis>.railway.internal` kaydi yalnizca calisan bir deployment varken yayinlanir.
Postgres servisi silinirse ayakta duran uygulama bir sonraki baglantida
`UnknownHostException` alir. Hata "Connection refused" degil "UnknownHostException"
ise sebep veritabaninin mesgul olmasi degil, servisin yok olmasidir.

### Dolu bir volume'e baglanan Postgres parolayi reddediyorsa

Postgres `POSTGRES_PASSWORD` degiskenini yalnizca **bos** bir veri dizininde ilk
acilista kullanir. Var olan bir volume yeni bir servise baglandiginda log
`Skipping initialization` der: panel yeni parolayi gosterir, kume ise kendi
kuruldugunda verilen eski parolayi bekler. Her baglanti `28P01 password
authentication failed` ile reddedilir ve panelden kopyalanan parola asla tutmaz.

Parolayi kumenin icinden sifirlamak gerekir. Postgres servisine gecici olarak bir
`RESET_SQL` degiskeni eklenir:

```
ALTER USER postgres WITH PASSWORD 'POSTGRES_PASSWORD_degeri';
```

ve Custom Start Command olarak:

```bash
/bin/bash -c 'docker-entrypoint.sh postgres & until pg_isready -h /var/run/postgresql -q; do sleep 2; done; printf "%s" "$RESET_SQL" | psql -h /var/run/postgresql -U postgres -d postgres; wait'
```

Logda `ALTER ROLE` gorulunce ikisi de silinip yeniden deploy edilir.

`-h /var/run/postgresql` **zorunludur**: bu serviste `PGHOST` tanimli oldugu icin
`psql` aksi halde konteynerin icinden bile TCP uzerinden baglanir ve parola isteyen
`host all all all scram-sha-256` kuralina duser. Unix soketi ise `pg_hba.conf`'taki
`local all all trust` satirina duser, parola sorulmaz.

### Veritabanina baglanma

`railway connect` yalnizca Railway'in kendi veritabani sablonlarini tanir; ozel imajli
bir serviste "No supported database found" der. Bunun yerine servisin
`DATABASE_PUBLIC_URL` degeri kullanilir:

```bash
docker run --rm -it postgres:17-alpine psql "postgresql://postgres:PAROLA@<proxy-host>:<port>/railway"
```

### Volume tasindiginda collation uyarisi

Bir volume farkli glibc surumune sahip bir imaja tasinirsa log
`collation version mismatch` uyarisi verir. Metin indekslerinin siralamasi isletim
sisteminkiyle uyusmaz: arama eksik sonuc dondurebilir, benzersizlik kisitlari
delinmis olabilir. Coken bir hata degil, fark edilmesi zor bir hata — uygulama
acilmadan once duzeltilir:

```sql
REINDEX DATABASE railway;
ALTER DATABASE railway REFRESH COLLATION VERSION;
```

---

## Docker Compose Servisleri

```yaml
services:
  db:        # PostgreSQL 15 — Port 5432
  backend:   # Spring Boot — Port 8080
  frontend:  # React + Nginx — Port 80
  pgadmin:   # pgAdmin 4 — Port 5050
```

### Faydali Docker Komutlari

```bash
# Tum servisleri baslat
docker-compose up -d

# Sadece backend'i yeniden build et
docker-compose up -d --build backend

# Loglari izle
docker-compose logs -f backend

# Veritabanina baglan
docker-compose exec db psql -U warehouse_user -d warehouse_db

# Servisleri durdur
docker-compose down

# Her seyi sifirla (veritabani dahil)
docker-compose down -v
```

---

## CI/CD Pipeline Onerisi

```yaml
# .github/workflows/deploy.yml (ornek)
name: Deploy
on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21' }
      - run: mvn test

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: docker build -t warehouse-backend .
      - run: docker build -t warehouse-frontend ./frontend

  deploy:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - run: ssh deploy@server "cd /opt/warehouse-management && git pull && docker-compose up -d --build"
```

---

## Monitoring & Bakim

### Saglik Kontrolleri

```bash
# Backend
curl https://yourdomain.com/actuator/health

# Frontend (Nginx)
curl http://localhost:8090/health  # eger prod.conf'taki health server aktifse
```

### Log Yonetimi

```bash
# Backend loglari
docker-compose logs --tail=100 backend

# Nginx loglari
docker-compose exec frontend cat /var/log/nginx/error.log
```

### Veritabani Yedekleme

```bash
# Yedek al
docker-compose exec db pg_dump -U warehouse_user warehouse_db > backup_$(date +%Y%m%d).sql

# Yedekten geri yukle
cat backup_20260401.sql | docker-compose exec -T db psql -U warehouse_user -d warehouse_db
```

---

## Guvenlik Kontrol Listesi (Production Oncesi)

- [ ] `.env` dosyasinda tum default sifreleri degistir
- [ ] `JWT_SECRET` en az 64 karakter rastgele string
- [ ] `PAYMENT_SANDBOX=false` ve gercek iyzico key'leri
- [ ] `CORS_ALLOWED_ORIGINS` sadece gercek domain'ler
- [ ] SSL sertifikasi aktif (HTTPS zorunlu)
- [ ] Firewall: sadece 80, 443 portlari acik
- [ ] PostgreSQL: sadece Docker network icinden erisim (port expose etme)
- [ ] pgAdmin'i production'da kaldir veya VPN arkasina al
- [ ] `SPRING_PROFILES_ACTIVE=prod` olarak ayarla
- [ ] Log seviyesi: `logging.level.com.warehouse=INFO` (DEBUG degil)
