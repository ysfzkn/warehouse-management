# Local Development Guide

## Gereksinimler

| Yazilim | Versiyon | Kontrol Komutu |
|---------|----------|----------------|
| Java JDK | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Node.js | 18+ | `node -v` |
| npm | 9+ | `npm -v` |
| PostgreSQL | 15+ | `psql --version` |
| Docker (opsiyonel) | 20+ | `docker --version` |

---

## Yontem 1: Docker Compose ile (Onerilir)

Tum servisleri tek komutla ayaga kaldirir. Veritabani, backend, frontend, pgAdmin otomatik baslar.

### Adimlar

```bash
# 1. Projeyi klonla
git clone <repo-url>
cd warehouse-management

# 2. Docker Compose baslar
docker-compose up --build

# 3. Bekle — ilk build 3-5 dakika surebilir
# Loglarida su mesajlari gorunce hazir:
#   backend  | Started WarehouseManagementApplication
#   frontend | ready
```

### Erişim Adresleri

| Servis | URL | Aciklama |
|--------|-----|----------|
| **Admin Panel** | http://localhost | WMS dashboard (Nginx uzerinden) |
| **Storefront** | http://localhost/store | E-ticaret vitrin |
| **Backend API** | http://localhost:8080 | Spring Boot direkt |
| **pgAdmin** | http://localhost:5050 | DB yonetim araci |

### pgAdmin Giris Bilgileri

- Email: `ozkan.development@gmail.com`
- Password: `admin123`
- Server ekle: Host=`db`, Port=`5432`, DB=`warehouse_db`, User=`warehouse_user`, Pass=`warehouse_pass`

### Admin Panel Giris

- URL: http://localhost/login
- Kullanici: `admin`
- Sifre: `admin`

### Storefront

- URL: http://localhost/store
- Kayit: http://localhost/store/kayit
- Giris: http://localhost/store/giris

---

## Yontem 2: Manuel Kurulum (Docker'siz)

### 2.1 PostgreSQL Kurulumu

```bash
# PostgreSQL'de veritabani olustur
psql -U postgres
CREATE DATABASE warehouse_db;
CREATE USER warehouse_user WITH PASSWORD 'warehouse_pass';
GRANT ALL PRIVILEGES ON DATABASE warehouse_db TO warehouse_user;
\q
```

### 2.2 Backend Baslat

```bash
cd warehouse-management

# Ortam degiskenleri ayarla
export DATABASE_URL=jdbc:postgresql://localhost:5432/warehouse_db
export DB_USERNAME=warehouse_user
export DB_PASSWORD=warehouse_pass
export SPRING_PROFILES_ACTIVE=dev

# Maven ile calistir
mvn spring-boot:run

# Backend http://localhost:8080 adresinde baslar
# Flyway migration'lar otomatik uygulanir
# Varsayilan admin kullanici olusturulur (admin/admin)
```

### 2.3 Frontend Baslat

```bash
cd frontend

# Bagimliliklar yuklenmemisse:
npm install

# Dev server baslat
npm start

# Frontend http://localhost:3000 adresinde baslar
# API istekleri otomatik olarak localhost:8080'e yonlendirilir (proxy)
```

### Erişim Adresleri (Manuel Kurulum)

| Servis | URL | Not |
|--------|-----|-----|
| **Admin Panel** | http://localhost:3000 | React dev server |
| **Storefront** | http://localhost:3000/store | Ayni React app |
| **Backend API** | http://localhost:8080/api/admin/* | Direkt backend |
| **Store API** | http://localhost:8080/api/store/* | Direkt backend |

---

## Proje Yapisi

```
warehouse-management/
├── src/main/java/com/warehouse/    # Java backend kaynak kodu
│   ├── controller/                 # REST API endpoint'leri
│   │   ├── store/                  # Storefront (musteri) API'lari
│   │   └── *.java                  # Admin API'lari
│   ├── service/                    # Is mantigi katmani
│   │   ├── impl/                   # Service implementasyonlari
│   │   └── payment/                # Odeme gateway'leri (iyzico, havale)
│   ├── entity/                     # JPA entity'leri (DB tablolari)
│   ├── repository/                 # Spring Data JPA repository'leri
│   ├── dto/                        # Data transfer object'leri
│   │   ├── store/                  # Storefront DTO'lari
│   │   ├── admin/                  # Admin DTO'lari
│   │   └── payment/                # Odeme DTO'lari
│   ├── security/                   # JWT, filtreler, guvenlik
│   ├── config/                     # Spring config (cache, payment, OAuth)
│   ├── enums/                      # Enum tipleri
│   ├── event/                      # Stok event sistemi
│   ├── job/                        # Zamanlanmis gorevler (odeme timeout)
│   └── exception/                  # Hata yonetimi
├── src/main/resources/
│   ├── db/migration/               # Flyway SQL migration'lari (V1-V25)
│   ├── application.properties      # Ortak config
│   ├── application-dev.properties  # Gelistirme config
│   └── application-prod.properties # Uretim config
├── src/test/                       # Test dosyalari
├── frontend/                       # React frontend
│   ├── src/
│   │   ├── pages/                  # Admin sayfalari
│   │   │   └── store/              # Storefront sayfalari
│   │   ├── components/             # Admin componentleri
│   │   │   └── store/              # Storefront componentleri
│   │   ├── hooks/                  # React hooks (useCart, useAuth, vb.)
│   │   ├── layouts/                # Layout componentleri (Admin, Store)
│   │   ├── design-tokens.css       # Design token sistemi
│   │   ├── store.css               # Storefront stilleri
│   │   └── App.js                  # Ana routing
│   └── nginx.conf                  # Frontend Nginx config (Docker icin)
├── nginx/
│   └── prod.conf                   # Production Nginx config (subdomain routing)
├── docker-compose.yml              # Docker Compose
├── Dockerfile                      # Backend Docker image
├── .env.example                    # Ortam degiskenleri sablonu
└── pom.xml                         # Maven config
```

---

## API Endpoint Yapisi

### Admin API (`/api/admin/*`)
Admin paneli tarafindan kullanilir. JWT token (ADMIN/STOCK_IN/STOCK_OUT rolu) gerektirir.

```
POST   /api/admin/auth/login          # Admin giris
GET    /api/admin/stocks               # Stok listesi
GET    /api/admin/products             # Urun listesi
GET    /api/admin/orders               # Siparis listesi
GET    /api/admin/customers            # Musteri listesi
GET    /api/admin/payments             # Odeme listesi
GET    /api/admin/cms                  # CMS/Banner yonetimi
GET    /api/admin/settings/site        # Site ayarlari
```

### Store API (`/api/store/*`)
Storefront (musteri vitrin) tarafindan kullanilir. Katalog public, sepet/siparis icin CUSTOMER JWT gerektirir.

```
GET    /api/store/products             # Urun katalogu (public)
GET    /api/store/products/{slug}      # Urun detay (public)
GET    /api/store/categories/tree      # Kategori agaci (public)
POST   /api/store/auth/register        # Musteri kayit
POST   /api/store/auth/login           # Musteri giris
GET    /api/store/cart                  # Sepet (session veya JWT)
POST   /api/store/cart/items           # Sepete ekle
POST   /api/store/checkout/place-order # Siparis olustur (JWT)
POST   /api/store/payment/initialize   # Odeme baslat (JWT)
POST   /api/store/payment/callback     # iyzico webhook (public)
GET    /api/store/settings             # Site ayarlari (public)
GET    /api/store/pages/{slug}         # CMS sayfasi (public)
GET    /api/store/pages/banners        # Banner listesi (public)
```

---

## Test Calistirma

```bash
# Tum unit testleri calistir (103 test)
mvn test

# Belirli test sinifi calistir
mvn test -Dtest=PaymentServiceImplTest

# Frontend testleri (varsa)
cd frontend && npm test

# Frontend build dogrulama
cd frontend && npm run build
```

---

## Odeme Test Modu

iyzico sandbox modunda calisir (varsayilan). Test kart bilgileri:

| Kart Numarasi | Son Kullanma | CVV | 3D Secure |
|---------------|-------------|-----|-----------|
| 5528790000000008 | 12/30 | 123 | Basarili |
| 5400010000000004 | 12/30 | 123 | Basarisiz |

Sandbox'u aktif etmek icin `.env` dosyasinda:
```
PAYMENT_SANDBOX=true
IYZICO_API_KEY=sandbox-xxx
IYZICO_SECRET_KEY=sandbox-xxx
```

---

## Sik Karsilasilan Sorunlar

### Backend baslamiyor
```
Flyway: Found non-applied migration...
```
**Cozum:** Veritabanini sifirla: `DROP DATABASE warehouse_db; CREATE DATABASE warehouse_db;`

### Frontend API hatasi
```
Network Error / CORS blocked
```
**Cozum:** Backend'in calistigini kontrol et (`curl http://localhost:8080/api/info`). Frontend proxy ayarini kontrol et (`package.json` → `"proxy": "http://localhost:8080"`).

### Docker port cakismasi
```
Bind for 0.0.0.0:5432 failed: port already allocated
```
**Cozum:** Yerel PostgreSQL'i durdur: `sudo service postgresql stop` veya docker-compose'da port'u degistir.

### pgAdmin baglanti hatasi
**Cozum:** Server eklerken Host olarak `db` kullan (localhost degil, Docker network icinde).
