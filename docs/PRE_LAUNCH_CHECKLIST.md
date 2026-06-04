# 🚀 Pre-Launch Deployment Checklist

> **Hedef:** Mevcut Railway prod (basit WMS + plain Postgres + local files) → yeni sürüm (e-ticaret + WMS + pgvector + S3 bucket + tüm yeni özellikler) — **sıfır veri kaybı, sıfır downtime kabul edilebilir bir süre.**
>
> Bu checklist'i baştan sona sırayla takip et. Her adımda **VERIFY** kısmı var — tamamlamadan sonraki adıma geçme.

---

## ✅ Müşteri Tarafında Hazır Olanlar (atlanabilir adımlar)

Aşağıdakiler tamam, sadece **prod env var aktarımı** ve **prod URL/whitelist güncellemesi** kalıyor:

| Hazır | Yapılacak |
|-------|-----------|
| ✅ **Logo eLogo** prod hesabı | Prod API credentials'ı env'e gir, mock disable |
| ✅ **ETBİS** kaydı | QR kodunu admin panelden site_settings'e yükle |
| ✅ **Domain** sahibi | DNS panelinden subdomain (`admin` veya `wms`) tanımla |
| ✅ **Iyzico** hesabı | Prod merchant key'leri env'e, callback URL whitelist |
| ✅ **PayTR** hesabı | Merchant ID/Key/Salt env'e, callback URL whitelist |
| ✅ **Kargonomi vb.** hesapları | Prod API key env'e |

### ⚠️ Müşteri Tarafında Hâlâ Hazırlanması Gereken

| Eksik | Aksiyon |
|-------|---------|
| **Şirket emaili / SMTP** | Aşağıda Faz 5.5'te detay — Workspace/Yandex/Custom |
| **KEP adresi** | PTT veya KEP sağlayıcıdan al |
| **Hesap silme / veri ihracı yasal metni** | Avukat onayı |

---

## ⏱️ Tahmini süre (güncel — müşteri hazırlıkları çıkarıldı)

| Faz | Süre | Not |
|-----|------|-----|
| Faz 0 — Code health | 2-3 saat | mvn test + frontend build |
| Faz 1 — Staging environment | 3-4 saat | Railway 2. env |
| Faz 2 — DB migration (pgvector) | 2-4 saat | **EN RİSKLİ** |
| Faz 3 — Storage migration | 1-2 saat | Eski transfer fotoları |
| Faz 4 — Env vars | 1-2 saat | Sadece kopyala-yapıştır |
| Faz 5 — External services | 1 saat | ✅ Hesaplar hazır; sadece env aktarımı |
| Faz 5.5 — SMTP setup | **2-4 saat** | ⚠️ DNS propagation bekleme |
| Faz 6 — Domain/SSL/DNS | 1 saat | ✅ Domain hazır; subdomain + Railway |
| Faz 7 — Security hardening | 2-3 saat | Headers, secrets, rate limit |
| Faz 8 — Observability | 1-2 saat | Loki + Sentry + UptimeRobot |
| Faz 9 — Yasal (eksikler) | 2-4 saat | KEP + KVKK Madde 13 metinleri |
| Faz 10 — SEO | 2-3 saat | robots/sitemap/JSON-LD |
| Faz 11 — Staging smoke test | 4-6 saat | Tüm kullanıcı yolları |
| Faz 12 — Production cutover | 1-2 saat | Cutover penceresi |
| Faz 13 — Post-launch monitoring | 24-48 saat | Aktif izleme |

**Toplam aktif iş: ~25-35 saat** = 3-5 iş günü (paralel çalışabilen ekipte 2-3 gün).

**Critical path:**
1. SMTP setup → DNS propagation 24 saate kadar bekleyebilir (DKIM özellikle)
2. Staging DB migration → veri taşıma + doğrulama
3. Staging smoke test → her şey geçinceye kadar tekrarla

İlk 2-3 gün staging'de her şey çalışana kadar uğraş, sonra **Cumartesi sabahı 9:00** production cutover.

---

## FAZ 0 — Code Health & Pre-Flight

### 0.1 Backend derlemesi temiz mi?

```bash
mvn clean compile
mvn test
mvn package -DskipTests
```

**VERIFY:**
- [ ] `BUILD SUCCESS`
- [ ] Test'ler yeşil (en az `Tests run: 172, Failures: 0, Errors: 0`)
- [ ] `target/*.jar` üretildi, boyutu makul (50-150 MB civarı)

### 0.2 Frontend derlemesi temiz mi?

```bash
cd frontend
npm ci
npm run build
```

**VERIFY:**
- [ ] `Compiled successfully` veya warning'siz
- [ ] `frontend/build/static/js/main.*.js` boyutu < 1 MB (gzip)
- [ ] Console error yok build log'unda

### 0.3 Flyway migration sırası tutarlı mı?

```bash
# Migration dosyalarını listele
ls -1 src/main/resources/db/migration | sort
```

**VERIFY:**
- [ ] V1 → V60 arası gap yok (V21 yoksa V22 olmaz)
- [ ] Aynı versiyon numarasında iki dosya yok (V58__a.sql + V58__b.sql ÇAKIŞIR)
- [ ] Production'da en son uygulanan migration `SELECT MAX(installed_rank), version FROM flyway_schema_history` ile teyit

### 0.4 Git temizliği

```bash
git status
git log --oneline -20
gitleaks detect --source . --verbose   # yoksa: brew install gitleaks
```

**VERIFY:**
- [ ] Working tree clean
- [ ] `.env`, `application-dev.properties` git history'sinde değil (gitleaks kontrol)
- [ ] `.gitignore`'da `uploads/`, `.env`, `*.local.properties` var

### 0.5 Test profile'ı çalışır durumda mı?

`mvn test` Phase 0.1'de çalıştı; ayrıca:

**VERIFY:**
- [ ] `application-test.properties`'te `app.admin.password=Admin1234` (PasswordPolicy uyumlu)
- [ ] `storage.provider=local` (S3 connection deneme)

---

## FAZ 1 — Staging Environment

> **Kural:** Production'a doğrudan dokunma. Önce **staging** kur, oradan test geç. Railway'de aynı projeye 2. environment ekle (`Production` + `Staging`).

### 1.1 Railway'de Staging environment oluştur

1. Railway Dashboard → Project → Settings → Environments → **+ New Environment** → "staging"
2. Production'ı duplicate **etme** — boş başla, kasıtlı kontrol istiyoruz
3. Services ekle:
   - PostgreSQL **pgvector image** (aşağıda detay)
   - Bucket (Object Storage)
   - Backend service (deploy edilecek)
   - Frontend service (deploy edilecek)

**VERIFY:**
- [ ] 4 service çalışıyor (Postgres, Bucket, Backend, Frontend)
- [ ] Backend `/actuator/health` 200 dönüyor

### 1.2 Frontend için ayrı service mi, monorepo mu?

Mevcut yapıda `frontend/` repo içinde — Railway'de **Root Directory: frontend** ile ayrı service yapmak en temizi. Ya da Vercel/Netlify'a koy (CDN+ücretsiz).

**VERIFY:**
- [ ] Frontend bağımsız deploy edilebilir
- [ ] Frontend env: `REACT_APP_API_BASE_URL=https://api.domain.com` doğru pointing

---

## FAZ 2 — Database Migration (pgvector) 🔥

> **EN RİSKLİ ADIM.** Mevcut prod data var, kaybetmemeliyiz.

### 2.1 Mevcut prod DB'yi yedekle

```bash
# Railway CLI ile prod DB'ye bağlan
railway login
railway link  # production projeyi seç
railway run --service postgres bash

# Container içinde
pg_dump $DATABASE_URL > /tmp/prod-backup-$(date +%Y%m%d-%H%M).sql
# Backup'ı host'a indir
exit
railway run --service postgres cat /tmp/prod-backup-*.sql > prod-backup.sql
```

Veya Railway Dashboard → Postgres service → **Backups** → Manual snapshot.

**VERIFY:**
- [ ] `prod-backup.sql` lokal'de var, > 1 MB
- [ ] `head -100 prod-backup.sql` çıktısında `CREATE TABLE` ifadeleri görünüyor
- [ ] `grep -c "^INSERT" prod-backup.sql` makul sayıda satır (binlerce)

### 2.2 Postgres image'i pgvector'a değiştir

Railway managed Postgres normalde **vanilla postgres**. pgvector extension yüklü değil. Üç yol var:

**Yöntem A: Postgres service'i pgvector image'iyle değiştir (önerilen)**

1. Staging'de yeni Postgres service ekle, **Source: Docker Image** → `pgvector/pgvector:pg15`
2. Eski DB'yi yedekten restore et:
   ```bash
   railway run --service postgres-new psql $DATABASE_URL < prod-backup.sql
   ```
3. `CREATE EXTENSION vector;` çalıştır (psql ile veya Flyway V53 zaten yapıyor)
4. Backend'in `DATABASE_URL`'unu yeni service'e yönlendir

**Yöntem B: Mevcut Postgres'e extension yükle**

Railway managed Postgres genellikle superuser yetkisi VERMEZ → `CREATE EXTENSION vector` reddedilir. Denemeden bilmek zor:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```
Hata verirse Yöntem A'ya geç.

**Yöntem C: pgvector'sız çalıştır (RAG devre dışı)**

`VectorSearchService` zaten "pgvector yoksa graceful skip" yapıyor. RAG asistanı çalışmaz ama diğer her şey çalışır. Acil değilse böyle başla, sonra A'ya geç.

**VERIFY:**
- [ ] `SELECT * FROM pg_extension WHERE extname = 'vector';` 1 satır döner
- [ ] `SELECT MAX(installed_rank) FROM flyway_schema_history;` = 60 (yeni migration'lar geçti)
- [ ] `SELECT COUNT(*) FROM warehouses;` eski production'daki sayıyla aynı (data kaybı yok)
- [ ] `SELECT COUNT(*) FROM transfer_items;` da aynı

### 2.3 Flyway migration'ları staging'de uygula

Backend ilk başlangıçta otomatik çalıştırır. Manuel yapacaksan:
```bash
railway run --service backend mvn flyway:migrate
```

**VERIFY:**
- [ ] Backend startup log'unda `Schema "public" is up to date.` ya da `Successfully applied N migrations`
- [ ] Hiçbir migration `FAILED` durumda değil:
  ```sql
  SELECT version, description, success FROM flyway_schema_history WHERE NOT success;
  -- 0 satır olmalı
  ```
- [ ] Yeni tablolar var: `product_images`, `orders`, `cart_items`, `customers`, `assistant_documents`, `payment_gateways`, vb. (toplam 50+ tablo)

### 2.4 Eski data ile yeni şema uyumlu mu — özel kontroller

```sql
-- Eski transfer_item_photos varsa path'leri ne formatta?
SELECT id, photo_path FROM transfer_item_photos LIMIT 5;

-- Eski user'ların password hash'i bcrypt mi?
SELECT username, LENGTH(password), LEFT(password, 4) FROM users LIMIT 3;
-- Beklenen: hash length 60, prefix "$2a$" veya "$2b$"

-- Yeni tablolarda PK çakışması var mı (eski data + yeni seed)
SELECT MAX(id) FROM warehouses; -- sequence değeri buradan büyük olmalı
SELECT last_value FROM warehouses_id_seq;
```

**VERIFY:**
- [ ] Sequence'lar tablo MAX(id)'sinin üzerinde
- [ ] Yeni constraint'ler eski data'da ihlal etmiyor (NOT NULL kolonlara default verildi mi?)

### 2.5 Rollback planı

Bir şey bozulursa:
1. Backend'i durdur
2. `psql $NEW_DATABASE_URL < prod-backup.sql` ile DB'yi sıfırla
3. Postgres service'i eski image'e geri al
4. Backend'in `DATABASE_URL`'unu eski service'e döndür

---

## FAZ 3 — Storage Migration (Local Files → Bucket)

### 3.1 Mevcut prod'da hangi dosyalar var?

Eski WMS sadece **transfer photo upload** kullanıyordu. Yine de SQL ile teyit:

```sql
-- Path içeren tüm kolonları gözden geçir
SELECT 'product_images' AS table_name, COUNT(*) FILTER (WHERE relative_path LIKE '/data/%' OR relative_path LIKE 'C:/%' OR relative_path LIKE 'uploads/%') AS legacy_count FROM product_images
UNION ALL
SELECT 'transfer_item_photos', COUNT(*) FILTER (WHERE relative_path LIKE '/data/%' OR relative_path LIKE 'uploads/%') FROM transfer_item_photos
UNION ALL
SELECT 'orders.invoice_url', COUNT(*) FILTER (WHERE invoice_url LIKE '/data/%' OR invoice_url LIKE 'uploads/%') FROM orders
UNION ALL
SELECT 'assistant_documents', COUNT(*) FILTER (WHERE storage_path LIKE '/data/%' OR storage_path LIKE 'uploads/%') FROM assistant_documents
UNION ALL
SELECT 'site_settings (asset paths)', COUNT(*) FILTER (WHERE setting_value LIKE '/data/%' OR setting_value LIKE 'uploads/%') FROM site_settings WHERE setting_key LIKE '%logo%' OR setting_key LIKE '%banner%';
```

**VERIFY:**
- [ ] Çıktıyı kaydet — hangi tabloda kaç legacy row var

### 3.2 Eski upload klasörünü Railway'den indir

Eski prod'da Railway volume mount edilmiş olabilir (`/data/uploads`). İçeriği indir:

```bash
railway run --service backend tar czf /tmp/uploads.tar.gz /data/uploads
railway run --service backend cat /tmp/uploads.tar.gz > prod-uploads.tar.gz

# Lokal'de aç
tar xzf prod-uploads.tar.gz
ls -lR data/uploads | head -30
```

**VERIFY:**
- [ ] `data/uploads/` (veya benzer) klasörü dolu
- [ ] Boyutu ~1-100 MB arası (eski WMS'in büyüklüğüne göre)
- [ ] Resimler `data/uploads/shipments/...` altında

### 3.3 Bucket'a yükle

Staging'deki Railway bucket'a (veya MinIO local test için):

```bash
# mc CLI alias kurulumu
docker run --rm -v "$(pwd)/data/uploads:/data:ro" -e MC_HOST_target=https://ACCESS_KEY:SECRET_KEY@bucket.up.railway.app \
  minio/mc mirror /data target/warehouse-uploads/

# Doğrulama
docker run --rm -e MC_HOST_target=... minio/mc ls --recursive target/warehouse-uploads/ | wc -l
```

**VERIFY:**
- [ ] Bucket'taki dosya sayısı tar arşivindekiyle eşleşiyor
- [ ] Örnek bir resmi tarayıcıdan aç: `https://<public-base-url>/shipments/.../foto.jpg`

### 3.4 DB path'lerini normalize et

Eski path'ler `/data/uploads/shipments/...` veya `C:/...` formatında. Yeni format `shipments/...` (bucket key, prefix yok).

```sql
-- Transfer photos
UPDATE transfer_item_photos
SET relative_path = REGEXP_REPLACE(relative_path, '^.*/data/uploads/', ''),
    thumbnail_path = REGEXP_REPLACE(thumbnail_path, '^.*/data/uploads/', '')
WHERE relative_path LIKE '%/data/uploads/%';

UPDATE transfer_item_photos
SET relative_path = REGEXP_REPLACE(relative_path, '^uploads/', ''),
    thumbnail_path = REGEXP_REPLACE(thumbnail_path, '^uploads/', '')
WHERE relative_path LIKE 'uploads/%';

-- Aynı pattern diğer tablolar için tekrarla (orders.invoice_url, vb.)
```

**VERIFY:**
- [ ] `SELECT relative_path FROM transfer_item_photos LIMIT 5;` artık `shipments/...` ile başlıyor
- [ ] Admin paneli → bir eski transfer'ı aç → fotoğraf görünüyor

---

## FAZ 4 — Environment Variables

### 4.1 Backend env (Railway → Backend service → Variables)

**KRİTİK:** Eksiksiz hepsi olmalı, eksik olanlar startup'ta NPE veya silent default.

> **Müşteriden topla:** Iyzico/PayTR/Logo/Kargonomi prod credentials, vergi no, Mersis no, IBAN, KEP adresi. Tek bir secure dokuman'a kaydedip env'lere taşı (1Password, Bitwarden, vb.).

```bash
# ── Database ──
DATABASE_URL=postgresql://...  # Railway auto-injection
DB_USERNAME=                   # auto
DB_PASSWORD=                   # auto

# ── Server ──
PORT=8080                      # Railway auto
SPRING_PROFILES_ACTIVE=prod

# ── Storage (Railway Bucket) ──
STORAGE_PROVIDER=s3
STORAGE_S3_ENDPOINT=${{Bucket.BUCKET_ENDPOINT}}
STORAGE_S3_BUCKET=${{Bucket.BUCKET_NAME}}
STORAGE_S3_ACCESS_KEY=${{Bucket.BUCKET_ACCESS_KEY}}
STORAGE_S3_SECRET_KEY=${{Bucket.BUCKET_SECRET_ACCESS_KEY}}
STORAGE_S3_REGION=us-east-1
STORAGE_S3_PATH_STYLE=true
STORAGE_S3_PUBLIC_ACL_SUPPORTED=true
STORAGE_S3_PUBLIC_BASE_URL=https://<bucket>.up.railway.app   # public asset URL

# ── Admin & Security ──
APP_ADMIN_USERNAME=admin
APP_ADMIN_PASSWORD=GüçlüBirSifre1!  # min 8 char, 1 büyük, 1 rakam
ADMIN_SECURITY_CODE=<6-12 char güçlü kod>
JWT_SECRET=<openssl rand -base64 48>  # 32+ karakter, asla default kalmasın
JWT_EXPIRATION_HOURS=24
REFRESH_TOKEN_EXPIRATION_DAYS=14      # 30 yerine 14 önerilir

# ── Domain / CORS ──
APP_BASE_URL=https://yourdomain.com
CORS_ALLOWED_ORIGINS=https://yourdomain.com,https://wms.yourdomain.com

# ── Email (SMTP) ──
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=noreply@yourdomain.com
MAIL_PASSWORD=<gmail app password>     # https://myaccount.google.com/apppasswords
MAIL_FROM=noreply@yourdomain.com

# ── Google OAuth ──
GOOGLE_CLIENT_ID=<console.cloud.google.com'da prod credential>
GOOGLE_CLIENT_SECRET=
GOOGLE_REDIRECT_URI=https://yourdomain.com/auth/google/callback

# ── Iyzico (prod) ──
PAYMENT_PROVIDER=IYZICO
PAYMENT_SANDBOX=false
IYZICO_API_KEY=<prod>
IYZICO_SECRET_KEY=<prod>
IYZICO_BASE_URL=https://api.iyzipay.com
IYZICO_CALLBACK_URL=https://yourdomain.com/api/store/payment/callback

# ── PayTR (varsa) ──
PAYTR_MERCHANT_ID=
PAYTR_MERCHANT_KEY=
PAYTR_MERCHANT_SALT=
PAYTR_CALLBACK_URL=https://yourdomain.com/api/store/payment/paytr-callback

# ── Logo eLogo (prod) ──
LOGO_ELOGO_USERNAME=<gerçek prod>
LOGO_ELOGO_PASSWORD=
LOGO_ELOGO_BASE_URL=https://earsivportal.efatura.gov.tr   # gerçek prod URL
LOGO_ELOGO_MOCK_ENABLED=false

# ── Kargonomi (prod) ──
KARGONOMI_API_KEY=
KARGONOMI_BASE_URL=https://api.kargonomi.com.tr

# ── Bank Transfer (havale) ──
BANK_TRANSFER_DEADLINE_HOURS=48
BANK_TRANSFER_BANK_NAME=Garanti BBVA
BANK_TRANSFER_IBAN=TR00 0000 ...
BANK_TRANSFER_ACCOUNT_HOLDER=Şirket Adı A.Ş.

# ── Azure OpenAI ──
AZURE_OPENAI_ENDPOINT=https://...openai.azure.com
AZURE_OPENAI_API_KEY=
AZURE_OPENAI_GPT51_DEPLOYMENT=gpt-4o-mini   # veya hangi model
AZURE_OPENAI_EMBEDDING_DEPLOYMENT=text-embedding-3-small

# ── Invoice mock güvenliği ──
INVOICE_MOCK_ENABLED=false   # PROD'da KESİNLİKLE FALSE

# ── Flyway baseline (gerekirse) ──
SPRING_FLYWAY_BASELINE_ON_MIGRATE=false
```

**VERIFY (her bir env için):**
- [ ] Hiçbir env değeri `your-domain.com` veya `change-me` gibi placeholder içermiyor
- [ ] Backend startup log'unda "Environment variable X not set, using default" yok
- [ ] `curl https://api.yourdomain.com/actuator/health` → `{"status":"UP"}`

### 4.2 Frontend env

```bash
# frontend/.env.production
REACT_APP_API_BASE_URL=https://api.yourdomain.com
REACT_APP_GOOGLE_CLIENT_ID=<aynı backend'le>
REACT_APP_IYZICO_PUBLIC=...   # gerekiyorsa
GENERATE_SOURCEMAP=false       # prod'da source map sızdırma
```

**VERIFY:**
- [ ] Build sonrası `frontend/build/static/js/main.*.js` içinde `localhost` geçmiyor
- [ ] Build sonrası içinde gerçek API URL var

---

## FAZ 5 — External Services Setup

> Hesaplar zaten hazır — bu fazda sadece **prod credentials env'e geçişi** ve **prod URL whitelist** güncellemesi yapılır.

### 5.1 Iyzico (hesap hazır, sadece prod aktarım)

1. Iyzico Panel → API Anahtarları → **Production** ortamına geç
2. Production API Key / Secret Key'i kopyala → Railway env:
   ```
   IYZICO_API_KEY=<prod>
   IYZICO_SECRET_KEY=<prod>
   IYZICO_BASE_URL=https://api.iyzipay.com
   IYZICO_CALLBACK_URL=https://yourdomain.com/api/store/payment/callback
   PAYMENT_SANDBOX=false
   ```
3. Iyzico Panel → Ayarlar → **Callback URL Whitelist** → `https://yourdomain.com/api/store/payment/callback` ekle
4. Iyzico Panel → Ayarlar → **3D Secure: Zorunlu** (PCI-DSS gereği)
5. Admin paneli → Ödeme Sistemleri → Iyzico → **Aktif** toggle

**VERIFY:**
- [ ] Prod'da 1 TL'lik gerçek test ödemesi 3DS akışı tamamlandı, callback geldi, sipariş PAID oldu
- [ ] Iyzico panel'den o ödemeyi iade et — kart sahibine geri yansıdı
- [ ] Admin → Ödeme Sistemleri → Iyzico → "Bağlantı testi" yeşil

### 5.2 PayTR (hesap hazır, sadece prod aktarım)

1. PayTR Panel → Mağaza Bilgileri → **Merchant ID**, **Merchant Key**, **Merchant Salt** kopyala
2. Railway env:
   ```
   PAYTR_MERCHANT_ID=<prod>
   PAYTR_MERCHANT_KEY=<prod>
   PAYTR_MERCHANT_SALT=<prod>
   PAYTR_CALLBACK_URL=https://yourdomain.com/api/store/payment/paytr-callback
   PAYTR_TEST_MODE=0
   ```
3. PayTR Panel → Mağaza Ayarları → **Bildirim URL** (callback): `https://yourdomain.com/api/store/payment/paytr-callback`
4. PayTR Panel → **IP Kısıtlama** varsa Railway backend egress IP'sini ekle (Railway → Service → Settings → IP)
5. Admin paneli → Ödeme Sistemleri → PayTR → **Aktif** + credentials gir

**VERIFY:**
- [ ] PayTR iFrame test ödemesi → callback → sipariş PAID
- [ ] HMAC token doğrulaması başarılı (logda "PayTR HMAC valid" görünür)
- [ ] PayTR panel "Başarılı Ödemeler" listesinde test ödemesi var

### 5.3 Logo eLogo / E-Fatura (hesap hazır, sadece prod aktarım)

1. Logo portaldan **prod** kullanıcı adı/şifresi (test ortamından farklı)
2. Railway env:
   ```
   LOGO_ELOGO_USERNAME=<prod>
   LOGO_ELOGO_PASSWORD=<prod>
   LOGO_ELOGO_BASE_URL=https://earsivportal.efatura.gov.tr   # GİB prod
   INVOICE_MOCK_ENABLED=false   # ⚠️ ZORUNLU — true bırakırsan fatura gönderilmez
   ```
3. `CheckGibUser` çağrısıyla şirket VKN'sinin e-fatura mükellefi olduğunu teyit (otomatik yapar)
4. Logo portal → KEP/Adres bilgisi doğru mu kontrol (fatura PDF'inde görünür)

**VERIFY:**
- [ ] Prod'da gerçek bir 1 TL siparişten otomatik fatura kesildi
- [ ] GİB e-Arşiv portalında o fatura listede
- [ ] Müşteri emailine fatura PDF linki gitti
- [ ] Admin paneli → Sipariş → "Fatura İndir" çalışıyor

### 5.4 Kargonomi (hesap hazır)

1. Kargonomi prod API key → env:
   ```
   KARGONOMI_API_KEY=<prod>
   KARGONOMI_BASE_URL=https://api.kargonomi.com.tr
   ```
2. Kargonomi panel → anlaşmalı kargo şirketleri aktif mi (Yurtiçi, Aras, MNG vb.)
3. Webhook URL (gönderi statüsü değiştiğinde): `https://yourdomain.com/api/admin/cargo/webhook`

**VERIFY:**
- [ ] Admin → Sipariş → "Kargoya Ver" → etiket PDF oluşuyor
- [ ] `CargoTrackingJob` saatlik çalışıyor (log'da görünür)
- [ ] Müşteriye tracking link emaili gitti

### 5.5 Şirket SMTP — Aksiyon Gerektiren

> **Müşterinin şirket emaili için SMTP kaydı yapılacak.** Üç seçenek var, maliyet ve kurulum kolaylığına göre seç:

#### Seçenek A — Google Workspace (önerilen, ücretli)

- ~$6/kullanıcı/ay (Business Starter)
- En yüksek deliverability (Gmail büyük inbox provider'ı)
- 5+ kullanıcı için ekonomik
- Spam'e düşme oranı en düşük

**Kurulum:**
1. https://workspace.google.com → domain'i bağla (`noreply@yourdomain.com`)
2. DNS doğrulama (TXT record)
3. SPF + DKIM + DMARC kayıtları otomatik öneriliyor — DNS'e ekle
4. Kullanıcı oluştur: `noreply@yourdomain.com`, `siparisler@yourdomain.com`, `destek@yourdomain.com`
5. Her kullanıcı için **App Password** üret (2FA zorunlu)
6. Railway env:
   ```
   MAIL_HOST=smtp.gmail.com
   MAIL_PORT=587
   MAIL_USERNAME=noreply@yourdomain.com
   MAIL_PASSWORD=<app password>
   MAIL_FROM=noreply@yourdomain.com
   ```

#### Seçenek B — Yandex Mail for Domain (ücretsiz!)

- **Ücretsiz** (1000 kullanıcıya kadar)
- Türkiye'de yaygın, deliverability iyi
- Google'a göre kurulum biraz daha manuel

**Kurulum:**
1. https://360.yandex.com/business/ → domain ekle
2. MX kayıtlarını DNS'e ekle (Yandex panel yönlendiriyor)
3. SPF + DKIM + DMARC ekle
4. Kullanıcı oluştur
5. Railway env:
   ```
   MAIL_HOST=smtp.yandex.com
   MAIL_PORT=465
   MAIL_USERNAME=noreply@yourdomain.com
   MAIL_PASSWORD=<şifre>
   MAIL_FROM=noreply@yourdomain.com
   ```
6. `application-prod.properties`'e ek:
   ```
   spring.mail.properties.mail.smtp.ssl.enable=true   # Yandex SSL/465
   ```

#### Seçenek C — Transactional Email Service (orta hacim, ücretsiz tier var)

Sadece transactional (sipariş onay, şifre sıfırlama, vb.) için tercih edilir. Müşteri inbox'tan cevap atmayacaksa ideal.

| Servis | Free Tier | Sonrası |
|--------|-----------|---------|
| **Brevo (eski Sendinblue)** | 300 mail/gün | $25/ay 20k mail |
| **Resend** | 3.000 mail/ay | $20/ay 50k mail |
| **AWS SES** | 3.000 mail/ay (EC2'den) | $0.10/1000 |
| **Mailgun** | 100 mail/gün (sandbox) | $35/ay 50k |

**Kurulum (Brevo örneği):**
1. brevo.com → kayıt + domain auth
2. SPF + DKIM kayıtlarını DNS'e ekle
3. SMTP API key al
4. Railway env:
   ```
   MAIL_HOST=smtp-relay.brevo.com
   MAIL_PORT=587
   MAIL_USERNAME=<brevo login>
   MAIL_PASSWORD=<smtp key>
   MAIL_FROM=noreply@yourdomain.com
   ```

#### Tavsiyem

- Aylık 0-2000 mail → **Yandex** (free)
- Aylık 2000-20000 mail → **Brevo** (free → ucuz upgrade)
- Aylık 20000+ mail veya kurumsal e-imza isteniyor → **Workspace**

#### SPF/DKIM/DMARC kayıtları (HER ÜÇ SEÇENEK İÇİN ZORUNLU)

Bunlar olmadan emailler **doğrudan spam'e** düşer. DNS'e ekle:

```
yourdomain.com.    TXT    "v=spf1 include:_spf.google.com ~all"
                          # Yandex: include:_spf.yandex.net
                          # Brevo:  include:spf.brevo.com

google._domainkey.yourdomain.com.   TXT    "v=DKIM1; k=rsa; p=MIGfMA0G..."
                                            # Provider'dan kopyalanır

_dmarc.yourdomain.com.   TXT    "v=DMARC1; p=quarantine; rua=mailto:dmarc@yourdomain.com; pct=100"
```

**VERIFY:**
- [ ] [mail-tester.com](https://www.mail-tester.com) → 10/10 skor
- [ ] [mxtoolbox.com SPF/DKIM/DMARC](https://mxtoolbox.com/SuperTool.aspx) → tüm yeşil
- [ ] Gmail'e test mail → Inbox'a düştü, **spam değil**
- [ ] Outlook/Hotmail'e test mail → Inbox'a düştü
- [ ] Yahoo'ya test mail → Inbox'a düştü (Yahoo en sıkı)
- [ ] Order confirmation mail'i Türkçe karakterler doğru görünüyor (encoding UTF-8)
- [ ] Mail içinde resimler (logo) bucket'tan yükleniyor (CID değil URL)

### 5.6 Azure OpenAI (varsa — Asistan için)

1. Azure resource → prod subscription
2. Deployment isimleri doğru:
   ```
   AZURE_OPENAI_ENDPOINT=https://<resource>.openai.azure.com
   AZURE_OPENAI_API_KEY=<prod>
   AZURE_OPENAI_GPT51_DEPLOYMENT=<deployment adı>
   AZURE_OPENAI_EMBEDDING_DEPLOYMENT=text-embedding-3-small
   ```
3. Quota (TPM/RPM): Azure Portal → Resource → Quotas → yük tahminine göre artır
4. Cost alert: Azure Portal → Cost Management → Budget alert (örn. $50/ay)

**VERIFY:**
- [ ] Admin asistan paneli → "merhaba" → cevap < 5 saniye
- [ ] Cost tracking sayfası TL doğru hesaplıyor

### 5.7 Google OAuth (sadece "Google ile giriş" feature'ı için)

1. https://console.cloud.google.com → APIs & Services → Credentials → OAuth 2.0 Client (Production)
2. Authorized redirect URI: `https://yourdomain.com/auth/google/callback`
3. Authorized JavaScript origins: `https://yourdomain.com`, `https://www.yourdomain.com`
4. Railway env:
   ```
   GOOGLE_CLIENT_ID=<prod>
   GOOGLE_CLIENT_SECRET=<prod>
   GOOGLE_REDIRECT_URI=https://yourdomain.com/auth/google/callback
   ```
5. OAuth consent screen → Publishing status: **Production** (Testing değil)

**VERIFY:**
- [ ] Store'da "Google ile giriş" → Google OAuth ekranı açılıyor
- [ ] Kullanıcı seçtikten sonra kayıt + login başarılı
- [ ] DB'de `customers.google_id` doluyor

---

## FAZ 6 — Domain, SSL, DNS

> Domain müşteride hazır. Burada sadece **subdomain tanımlama + Railway'e bağlama** kalıyor.

### 6.1 Subdomain stratejisi

Mimari iki kısımlı:
- **Root** (`yourdomain.com`, `www.yourdomain.com`) → **E-ticaret store**
- **Subdomain** (`admin.yourdomain.com` veya `wms.yourdomain.com`) → **Admin/WMS panel**

Hangi subdomain'i seçeceksin?

| Seçenek | Avantaj | Dezavantaj |
|---------|---------|------------|
| `admin.yourdomain.com` | Kullanıcı dostu, hatırlanabilir | Açık şekilde admin → bot taraması artar |
| `wms.yourdomain.com` | Daha az fark edilebilir | Daha az kullanıcı dostu |
| **`panel.yourdomain.com`** | Orta yol — bilenler bulur, bot kolay tarayamaz | - |

Hangisini seçersen seç, kod tarafında `HOST_VALIDATION` filter'ı buna göre ayarlanacak.

### 6.2 DNS kayıtları (registrar / DNS host panelinde)

Domain registrar'ında (Namecheap, GoDaddy, Natro, vb.) DNS yönetim paneline gir:

| Subdomain | Type | Value | TTL | Açıklama |
|-----------|------|-------|-----|----------|
| `@` (root) | CNAME / ALIAS | Railway frontend domain | **300** | Store frontend |
| `www` | CNAME | `yourdomain.com` | 300 | Root'a redirect |
| `api` | CNAME | Railway backend domain | 300 | Backend API |
| `admin` (veya `wms`) | CNAME | Railway frontend domain (aynı) | 300 | Admin panel (frontend route guard ayırır) |

**⚠️ TTL = 300 (5 dk) ZORUNLU** — cutover sonrası sorun çıkarsa hızlı DNS rollback için. Cutover stabilize olduktan 1 hafta sonra 3600'e yükseltebilirsin.

Email için ek kayıtlar (Faz 5.5'te detay):
| Subdomain | Type | Value |
|-----------|------|-------|
| `@` | TXT | SPF kaydı |
| `_dmarc` | TXT | DMARC kaydı |
| `<provider>._domainkey` | TXT | DKIM kaydı |
| `@` | MX | Email host'un MX kaydı |

### 6.3 Railway custom domain bağlama

**Backend service:**
- Settings → Networking → **Custom Domain** → `api.yourdomain.com` ekle
- Railway sana doğrulama için "CNAME hedefi" verir → DNS'e ekle (yukarıdaki tabloda)
- 5-15 dk içinde Let's Encrypt SSL otomatik gelir

**Frontend service:**
- 3 custom domain ekle:
  - `yourdomain.com`
  - `www.yourdomain.com`
  - `admin.yourdomain.com` (veya `wms.yourdomain.com`)
- Tümü için Railway aynı service'i serve eder; frontend `App.js` içinde host'a göre route ayrımı yapar (yapılmadıysa Faz 0.5'e döner, eklersin)

**VERIFY:**
- [ ] `curl -I https://yourdomain.com` → 200 OK, valid certificate
- [ ] `curl -I http://yourdomain.com` → 301/302 → HTTPS (HTTP → HTTPS redirect)
- [ ] [SSL Labs](https://www.ssllabs.com/ssltest/) → **A** veya üstü
- [ ] [Security Headers](https://securityheaders.com/) → **A** veya üstü

### 6.3 Cookie domain ayrımı

JWT cookie'leri admin/store için ayrı domain'lerde olmalı:
- Store cookie: `Domain=.yourdomain.com; SameSite=Lax; Secure; HttpOnly`
- Admin cookie: `Domain=wms.yourdomain.com; SameSite=Strict; Secure; HttpOnly`

`SecurityConfig.java` veya `JwtAuthenticationFilter` içinde cookie set kısmını kontrol et — `Secure` ve `HttpOnly` zorunlu.

**VERIFY:**
- [ ] Browser DevTools → Application → Cookies → JWT cookie'sinde Secure ✓ HttpOnly ✓ SameSite uygun

### 6.4 Subdomain route guard

`HostValidationFilter` zaten var (`SecurityConfig`'te referans). Hangi subdomain'i seçtiysen ona göre env:

```bash
# admin.* seçtiysen:
ADMIN_HOSTS=admin.yourdomain.com
STORE_HOSTS=yourdomain.com,www.yourdomain.com

# wms.* seçtiysen:
ADMIN_HOSTS=wms.yourdomain.com
STORE_HOSTS=yourdomain.com,www.yourdomain.com

HOST_VALIDATION_ENABLED=true
```

Frontend tarafında `App.js` veya `routes.js`'te de aynı host kontrolü olmalı — yoksa kullanıcı `admin.yourdomain.com/products` (store URL'i) yazınca admin panelinden store'a girer. Mevcut frontend kod:
```js
// frontend/src/App.js (varsayılan)
const isAdminHost = window.location.host.startsWith('admin.') ||
                    window.location.host.startsWith('wms.');
```
seçtiğin pattern'e göre güncelle.

**VERIFY:**
- [ ] `curl https://admin.yourdomain.com/api/store/products` → 403/404 (admin host'tan store API'ye izin yok)
- [ ] `curl https://yourdomain.com/api/admin/dashboard` → 403/404
- [ ] Tarayıcıdan `admin.yourdomain.com` → admin login ekranı
- [ ] Tarayıcıdan `yourdomain.com` → store anasayfa
- [ ] Admin'de login olduktan sonra `yourdomain.com` açılınca admin token store domain'inde geçerli DEĞİL (cookie domain ayrı)

---

## FAZ 7 — Security Hardening (Production)

### 7.1 Secrets rotation

- [ ] `JWT_SECRET` dev'le aynı olmasın
- [ ] `ADMIN_SECURITY_CODE` dev'deki `12345` olmasın
- [ ] `APP_ADMIN_PASSWORD` güçlü
- [ ] Eski git history'de sızmış secret varsa **rotate** et (her birini değiştir)

### 7.2 Security headers (nginx veya Spring level)

Production'da bu header'ları aktif et — `SecurityConfig` veya nginx config:
```
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
Content-Security-Policy: default-src 'self'; script-src 'self' 'nonce-xxx'; img-src 'self' data: https://*.your-cdn.com; ...
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: geolocation=(), microphone=()
```

### 7.3 Actuator endpoint'leri

`application-prod.properties`:
```
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=never
```

**VERIFY:**
- [ ] `curl https://api.yourdomain.com/actuator/env` → 404 (kapalı)
- [ ] `curl https://api.yourdomain.com/actuator/health` → 200

### 7.4 Rate limiting

`RateLimitFilter` zaten var. Bucket4j config:
- Auth endpoints: 5 req/dakika/IP
- Public store: 60 req/dakika/IP
- Payment callbacks: rate limit YOK (Iyzico/PayTR sınırsız)

**VERIFY:**
- [ ] 10 saniyede 20 kez `/api/admin/auth/login` → 429 Too Many Requests
- [ ] `/api/store/products` 1 dakikada 100 → bir kısmı 429

### 7.5 Swagger / API docs

Production'da kapalı:
```
springdoc.swagger-ui.enabled=false
springdoc.api-docs.enabled=false
```

**VERIFY:**
- [ ] `curl https://api.yourdomain.com/swagger-ui.html` → 404
- [ ] `curl https://api.yourdomain.com/v3/api-docs` → 404

---

## FAZ 8 — Observability

### 8.1 Loglar — yapılandırılmış JSON

`logback-spring.xml` prod profile'da JSON encoder kullanıyor (mevcut). Railway log panelinde JSON satırları görüyor olmalısın.

**VERIFY:**
- [ ] Railway logs → arama: `level=ERROR` → JSON parse edilebiliyor
- [ ] Stack trace'lerde gerçek exception class adı görünüyor

### 8.2 Log aggregation (opsiyonel ama önerilen)

İki seçenek:

**A) Grafana Cloud Loki** (3 GB/ay free) — Railway'den external service'e log shipping:
1. https://grafana.com/products/cloud/ — ücretsiz hesap
2. Logback'e ek `LokiAppender` (`com.github.loki4j:loki-logback-appender`)
3. Loki endpoint + auth env'leri ver

**B) Self-hosted Loki+Grafana** — başka Railway service olarak ayağa kaldır. Mevcut `docker-compose.yml` var, Railway'de container deploy edilebilir.

**VERIFY:**
- [ ] Grafana'da `{service="warehouse-backend"} |= "ERROR"` query'sinden log akıyor
- [ ] Pre-built dashboard `Warehouse — Live Logs` import edildi

### 8.3 Error tracking (Sentry)

Free tier: 5k events/ay. Hem backend hem frontend için:

```xml
<!-- pom.xml -->
<dependency>
  <groupId>io.sentry</groupId>
  <artifactId>sentry-spring-boot-starter-jakarta</artifactId>
  <version>7.20.0</version>
</dependency>
```

```yaml
# application-prod.properties
sentry.dsn=${SENTRY_DSN}
sentry.environment=production
sentry.traces-sample-rate=0.1
```

Frontend:
```bash
npm i @sentry/react
```

**VERIFY:**
- [ ] Test exception fırlat → Sentry dashboard'unda görünüyor
- [ ] PII redaction çalışıyor (email/phone log'a düşmüyor)

### 8.4 Uptime monitoring

[UptimeRobot](https://uptimerobot.com/) (free, 50 monitor):
- `https://api.yourdomain.com/actuator/health` — 1 dk aralık
- `https://yourdomain.com` — 1 dk aralık
- `https://wms.yourdomain.com` — 5 dk aralık

**VERIFY:**
- [ ] Telegram/email/SMS alert kuruldu

### 8.5 PostgreSQL backup

Railway managed Postgres otomatik daily snapshot yapıyor. Ek olarak haftalık `pg_dump` → external storage (Backblaze B2 ücretsiz 10 GB):

```yaml
# .github/workflows/db-backup.yml (cron)
on:
  schedule:
    - cron: '0 3 * * 0'  # Pazar 03:00 UTC
jobs:
  backup:
    runs-on: ubuntu-latest
    steps:
      - run: |
          pg_dump $PROD_DATABASE_URL | gzip > backup.sql.gz
          aws s3 cp backup.sql.gz s3://b2-backup-bucket/ \
            --endpoint-url https://s3.us-west-002.backblazeb2.com
```

**VERIFY:**
- [ ] Manuel restore testi yap: backup'ı staging DB'sine geri yükle, data tutarlı

---

## FAZ 9 — Yasal Uyumluluk (Türkiye)

> Burada eksik bırakırsan TKHK + KVKK cezası gelir. Lansman ÖNCESİ tamamla.

### 9.1 Sözleşmeler (admin paneli üzerinden site_settings'e gir)

- [ ] Mesafeli Satış Sözleşmesi (TKHK md.48) — checkout'ta inline gösterim
- [ ] Ön Bilgilendirme Formu — checkout'ta dinamik (ürün, fiyat, kargo, iade)
- [ ] KVKK Aydınlatma Metni — register sayfasında
- [ ] Çerez Politikası — cookie banner'da link
- [ ] Üyelik Sözleşmesi
- [ ] İade & İptal Koşulları

**VERIFY:**
- [ ] Test bir misafir checkout → checkbox'ları işaretle, timestamp DB'de tutuluyor
- [ ] `consent_log` tablosunda kayıt var

### 9.2 ETBİS — ✅ Kayıt Hazır

ETBİS kaydı mevcut. Yapılacak tek şey:

1. ETBİS panel → site profili → **QR kod URL**'ini kopyala
2. Admin panel → Site Ayarları → `etbis_qr_url` alanına yapıştır
3. Footer'da otomatik render edilir

**VERIFY:**
- [ ] Footer'da ETBİS QR kodu görsel olarak var
- [ ] QR kodu telefonla okutunca firma ETBİS profili açılıyor
- [ ] Yeni domain'i ETBİS panel'inde de güncelle (eski URL varsa)

### 9.3 KEP Adresi (B2B yasal iletişim)

KEP (Kayıtlı Elektronik Posta) adresi al (PTT veya başka KEP sağlayıcı). admin paneli → site_settings → `kep_address`.

### 9.4 Şirket Bilgileri Footer'da

- [ ] Şirket adı + Anonim/Limited
- [ ] Vergi numarası
- [ ] Mersis numarası
- [ ] Adres
- [ ] Telefon
- [ ] KEP adresi
- [ ] Müşteri hizmetleri email

### 9.5 KVKK Madde 13 — Hesap silme & veri ihracı

Müşteri panel:
- [ ] "Hesabımı sil" butonu çalışıyor (sipariş anonim hale gelir, PII silinir)
- [ ] "Verilerimi indir" butonu JSON dosyası veriyor

### 9.6 Cookie banner

- [ ] Kategori bazlı (Gerekli / Analitik / Pazarlama)
- [ ] Reddet → GA4/Meta Pixel YÜKLENMEZ
- [ ] Consent timestamp DB'de tutuluyor

### 9.7 VAT/KDV Görünür

- [ ] Sepet özetinde KDV ayrı satır
- [ ] Fatura PDF'inde KDV detayı

---

## FAZ 10 — SEO

### 10.1 robots.txt

`frontend/public/robots.txt`:
```
User-agent: *
Allow: /
Disallow: /api/
Disallow: /admin/
Disallow: /my-account/
Sitemap: https://yourdomain.com/sitemap.xml
```

**WMS subdomain'i için ayrı:** `wms.yourdomain.com/robots.txt` → `Disallow: /` + `<meta name="robots" content="noindex">` admin SPA'larda.

### 10.2 Sitemap

`/sitemap.xml` endpoint zaten var (`SitemapController`). Daily cache.

**VERIFY:**
- [ ] `curl https://yourdomain.com/sitemap.xml` → valid XML, ürünler + kategoriler içeriyor
- [ ] Google Search Console'a sitemap submit et

### 10.3 Meta tags & JSON-LD

- [ ] Her ürün detay sayfasında `<meta name="description">`, `<meta property="og:image">`
- [ ] JSON-LD `Product` schema (`price`, `availability`, `sku`, `image`)
- [ ] JSON-LD `BreadcrumbList`
- [ ] Organization schema homepage'de

**VERIFY:**
- [ ] [Google Rich Results Test](https://search.google.com/test/rich-results) → 0 hata
- [ ] [Schema.org Validator](https://validator.schema.org/) → valid

### 10.4 Sosyal medya preview

Facebook Sharing Debugger, Twitter Card Validator ile test:
- [ ] og:image gerçek prod URL (CDN değil, doğrudan domain)
- [ ] og:title, og:description doğru ürün bilgisi

---

## FAZ 11 — Staging Smoke Test (cutover'dan ÖNCE)

> Staging environment hazır, prod-benzeri data var. Bütün kullanıcı yollarını manuel test et.

### Müşteri (Store) Yolu

- [ ] Ana sayfa açılıyor < 2 saniye
- [ ] Kategori filtresi çalışıyor
- [ ] Ürün arama (Türkçe karakter dahil)
- [ ] Ürün detay → resimler yükleniyor (bucket'tan!)
- [ ] Sepete ekle → sepet sayfası
- [ ] Kupon kodu uygula → indirim doğru
- [ ] Misafir checkout
  - [ ] Adres formu
  - [ ] Sözleşme checkbox'ları (Mesafeli Satış, Ön Bilgilendirme, KVKK)
  - [ ] Ödeme yöntemi seç (Iyzico 3DS / Havale / Kapıda)
  - [ ] **Iyzico ile 1 TL test ödemesi yap** (sonra iade et)
  - [ ] Callback geliyor, sipariş PAID
  - [ ] Email gönderildi (gerçek inbox'a bak)
  - [ ] Fatura otomatik oluşturuldu (Logo eLogo)
- [ ] Üyelik kaydı → email doğrulama → giriş
- [ ] Google ile giriş
- [ ] Sipariş geçmişi → fatura indir → PDF açılıyor
- [ ] İade talebi oluştur → admin paneline düşüyor
- [ ] Hesabımı sil → veriler anonim
- [ ] Cookie banner → reddet → GA4 yüklenmiyor (DevTools network)

### Admin (WMS) Yolu

- [ ] `https://wms.yourdomain.com` → admin login
- [ ] Dashboard yükleniyor
- [ ] Yeni ürün ekle → resim yükle → bucket'a gidiyor (kontrol: bucket console)
- [ ] **Image crawler** → bir kaynak URL'den fotoğraf çek → import et
- [ ] Stok import → Excel yükle → işlendi → history'de görünüyor → indir
- [ ] Sipariş listesi → bir siparişe fatura PDF yükle → indir
- [ ] Asistan paneli → "merhaba" → cevap
- [ ] Asistan döküman yükle → indexlendi → soru sor → cevap o döküman'dan
- [ ] Logo upload → footer'da görünüyor
- [ ] Banner upload → anasayfada görünüyor
- [ ] Site setting değiştir → frontend'e yansıyor
- [ ] Payment gateway aktif/pasif toggle → frontend etkilendi

### Sistemsel

- [ ] Cron job'lar çalışıyor (`PaymentTimeoutJob`, `BankTransferExpiryJob`, vb.) — log'da
- [ ] ShedLock distributed lock alıyor (multi-instance senaryosu varsa)
- [ ] DB connection pool sızıntısı yok (`hikari.active` metric)
- [ ] Memory leak yok (1 saat çalıştır, heap büyümüyor)
- [ ] 50 RPS k6 yük testi → p95 < 800ms

```bash
# k6 script örneği
import http from 'k6/http';
export const options = { vus: 50, duration: '5m' };
export default function () {
  http.get('https://yourdomain.com/api/store/products?size=20');
}
```

### Güvenlik Smoke

- [ ] `gitleaks detect` → 0 leak
- [ ] OWASP ZAP baseline scan → 0 High
- [ ] `sslscan yourdomain.com` → TLS 1.2+
- [ ] CSP header doğru — DevTools console'da CSP violation YOK

---

## FAZ 12 — Production Cutover

### 12.1 Bakım modu (kısa süreliğine)

Mevcut prod frontend'de basit bir "Bakım yapılıyor" sayfası göster (10-30 dk). Railway service'i pause edip statik HTML'e yönlendir.

### 12.2 Son production backup

```bash
# Eski prod DB son backup
pg_dump $OLD_PROD_DATABASE_URL > final-prod-backup-$(date +%s).sql

# Eski prod uploads son backup
railway run --service old-backend tar czf /tmp/final-uploads.tar.gz /data/uploads
```

### 12.3 Production'a deploy

1. Production environment'a yeni DB (pgvector) service ekle
2. Final backup'tan restore et + Flyway migrate (otomatik)
3. Final uploads'u bucket'a yükle + DB path normalize
4. Backend deploy (yeni jar)
5. Frontend deploy (yeni build)
6. Custom domain'leri yeni service'lere taşı
7. DNS bekle (TTL kadar, max 5 dk)

### 12.4 Smoke test (production, post-deploy)

Tüm Faz 11 checklist'i tekrar koş, ama PROD'da. Hızlı olsun:
- [ ] Anasayfa açılıyor
- [ ] Bir ürün detayı açılıyor (resim geliyor mu)
- [ ] Admin login
- [ ] 1 TL gerçek Iyzico test (3DS dahil)
- [ ] Email order confirmation geliyor

### 12.5 Geri sarılabilir mi?

Sorun varsa:
1. DNS'i eski prod'a geri yönlendir (TTL 5 dk önemli — yukarıda 300 yapmıştık)
2. Eski Postgres + eski backend hâlâ duruyor (silmeden devam ediyordu)
3. 5-10 dk içinde eski sürüm açık

---

## FAZ 13 — Post-Launch (İlk 48 saat)

### Aktif izleme

- [ ] Saatte bir Sentry / Grafana log dashboard'a bak
- [ ] Uptime monitoring alert'leri açık
- [ ] Iyzico panel'inde ödeme failure rate < %5
- [ ] Email deliverability > %95

### Hızlı tepki gerektirebilecekler

| Belirti | Aksiyon |
|---------|---------|
| 500 hata spike'ı | Sentry'de exception class'a bak → hotfix deploy |
| Resim 404 spike | Bucket erişimi kontrol et, CORS, public URL |
| Iyzico failure spike | Iyzico panel + callback URL teyit |
| DB CPU yüksek | Slow query log'a bak, eksik index varsa V61 ekle |
| Email bounced | SPF/DKIM kontrol, Gmail quota |

### İlk hafta sonu retrospective

- [ ] Hangi feature en çok kullanıldı / kullanılmadı
- [ ] Hangi sayfada bounce rate yüksek
- [ ] Error rate trend (Sentry haftalık)
- [ ] Average response time (P50/P95/P99)
- [ ] Bucket storage growth (ne kadar GB / 1 hafta?)

---

## 🛟 Emergency Contacts

| Servis | Panel URL | Support |
|--------|-----------|---------|
| Railway | railway.app/project/... | Discord #help |
| Iyzico | merchant.iyzipay.com | 0850-... |
| Logo | portal.elogo.com.tr | ... |
| Kargonomi | panel.kargonomi.com.tr | ... |
| Domain registrar | (Namecheap / GoDaddy) | ... |
| Azure | portal.azure.com | ... |

---

## 📌 Önemli Notlar

1. **Bu checklist'i staging'de bir kez baştan sona uyguladıktan sonra production'a geç.** Staging her şeyi simüle eder; orada yakaladığın her bug prod'a çıkmadan kapanır.

2. **Cuma akşamı veya hafta sonu cutover yapma.** Cumartesi sabahı 09:00 ideal — sorun çıkarsa hafta boyu çözmek için zaman var.

3. **Eski prod'u 1 hafta canlı tut** (DNS değişikliği sonrası bile bağlı kalsın). Kritik veri kaybı yaşarsan instant rollback için.

4. **Her cron job ilk gece manuel olarak da çalıştır.** Sandbox'taki test verisi prod'da gerçek davranır.

5. **Yasal sözleşmeleri avukatla son kez gözden geçir.** Özellikle Mesafeli Satış ve KVKK metinleri — şablonlar yetmez.

6. **Aşamalı go-live yap.** Önce 1 hafta sadece davetli müşterilerle (closed beta), sonra public launch.

---

**İyi launchlar! 🎯**
