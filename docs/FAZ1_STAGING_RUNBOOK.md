# 🏗️ Faz 1 — Production In-Place Upgrade Runbook (Satış Kapalı Yayın)

> **YAKLAŞIM (güncellendi):** Ayrı staging ortamı YOK. **Production'ı doğrudan yeni sürüme yükseltiyoruz** — e-ticaret + güncellenmiş WMS, ama `STORE_PURCHASING_ENABLED=false` ile **satış kapalı**. Gerçek prod verisiyle (mevcut WMS warehouses/stocks/transfers) canlıya alınır, her şey test edilir, adım adım iyileştirilir, sonunda satış açılır.
>
> **Bu neden güvenli?** Faz 0'da yaptığımız test modu gate → site herkese açık ama kimse alışveriş yapamaz. WMS ekibi yeni paneli kullanır, sen e-ticareti dış gözle test edersin. Sorun çıkarsa satış zaten kapalı, müşteri etkilenmez.
>
> **Mimari (keşiften):** Frontend tek React build → nginx, `/api/*`'ı backend'e proxy'ler (`API_ORIGIN` env). **Browser same-origin görür → CORS bile tetiklenmez.** Backend → Postgres + Bucket.
>
> ⚠️ **KRİTİK FARK:** Bu production data. Backup ZORUNLU, pgvector + S3 dönüşümü gerçek veri üzerinde. Aşağıdaki sıra buna göre.

---

## 📐 Hedef Mimari (Staging)

```
                    yourdomain.com  (root → store, SATIŞ KAPALI)
                    admin.yourdomain.com  (→ WMS admin, aynı build)
                            ↓
                    ┌───────────────────┐
                    │  Frontend (nginx) │  React SPA + /api proxy
                    └─────────┬─────────┘
                              │ API_ORIGIN=backend.railway.internal:8080
                    ┌─────────▼─────────┐
                    │  Backend (Spring) │  Dockerfile
                    └────┬─────────┬────┘
                         │         │
              ┌──────────▼──┐  ┌───▼──────────┐
              │ Postgres    │  │ Bucket (S3)  │
              │ pgvector:pg15│  │ Object Store │
              └─────────────┘  └──────────────┘
```

---

## 🛑 ADIM 0 — BACKUP (Her Şeyden Önce, Pazarlık Yok)

Production data ile çalışıyoruz. Tek yanlış adım = WMS verisi gider.

```bash
# 1. Mevcut prod DB tam yedek
railway run --service <prod-postgres> pg_dump $DATABASE_URL > prod-backup-$(date +%Y%m%d-%H%M).sql

# 2. Yedeği lokale indir + doğrula
#    head -100 ile CREATE TABLE'lar, grep -c "^INSERT" ile satır sayısı

# 3. Eski upload dosyaları (transfer fotoları)
railway run --service <prod-backend> tar czf /tmp/uploads.tar.gz /data/uploads
railway run --service <prod-backend> cat /tmp/uploads.tar.gz > prod-uploads.tar.gz
```

**VERIFY:**
- [ ] `prod-backup.sql` lokalde, > 1 MB, `CREATE TABLE` + `INSERT` içeriyor
- [ ] `prod-uploads.tar.gz` indirildi
- [ ] Railway Dashboard → Postgres → Backups → otomatik snapshot da aktif

---

## ✅ Adım Adım (Sırayla)

### ADIM 1 — Karar: Aynı environment mı, yeni Postgres mi?

Production'ı in-place yükseltiyoruz ama **pgvector dönüşümü** gerekiyor (mevcut Postgres vanilla). İki yol:

**Yol A — Yeni pgvector Postgres + restore (önerilen, güvenli)**
1. Production env'e **yeni** Postgres `pgvector/pgvector:pg15` servisi ekle (`postgres-v2`)
2. Backup'tan restore et → Flyway yeni migration'ları (V42-V62) uygular
3. Backend `DATABASE_URL`'unu yeni servise çevir
4. **Eski Postgres'i SİLME** — rollback için 1 hafta dursun

**Yol B — Mevcut Postgres'e extension (Railway izin verirse)**
- `CREATE EXTENSION vector` dene; reddedilirse Yol A'ya geç
- Mevcut data yerinde, sadece yeni migration'lar uygulanır

> **Önerim: Yol A.** Eski Postgres dokunulmadan durur, sorun çıkarsa anında geri dönülür.

---

### ADIM 2 — Postgres (pgvector) servisi

1. Production env → **+ New** → **Docker Image**
2. **Docker Image** seç → `pgvector/pgvector:pg15`
3. Servis adı: `postgres-v2`
4. Railway otomatik `DATABASE_URL` üretir
5. Volume otomatik bağlanır (veri kalıcı)

**VERIFY:**
- [ ] Servis "Active" yeşil
- [ ] Variables sekmesinde `DATABASE_URL`, `PGHOST`, `PGUSER` vb. var

---

### ADIM 3 — Bucket (Object Storage) servisi

1. Production env → **+ New** → **Bucket** (veya Railway object storage)
2. Servis adı: `Bucket`
3. Oluşunca **Variables** sekmesinden şu değerleri NOT AL (backend'e geçeceğiz):
   - `BUCKET_ENDPOINT` (veya benzeri)
   - `BUCKET_NAME`
   - `BUCKET_ACCESS_KEY` / `BUCKET_ACCESS_KEY_ID`
   - `BUCKET_SECRET_KEY` / `BUCKET_SECRET_ACCESS_KEY`

> **Railway bucket yoksa:** MinIO'yu Docker image olarak deploy edebilirsin (`minio/minio`), ya da geçici olarak `STORAGE_PROVIDER=local` ile başlayıp sonra bucket eklersin.

**VERIFY:**
- [ ] Bucket servisi aktif
- [ ] 4 credential değeri elde

---

### ADIM 4 — Backend servisi (bu PR'dan)

> **Mevcut prod backend'i mi güncelliyorsun, yeni mi?** Mevcut WMS backend servisi varsa, branch'ini bu PR'a çevirip redeploy edebilirsin (in-place). Veya yeni `backend-v2` servisi ekleyip domain'i ona taşırsın (daha güvenli — eski durur).

1. Production env → mevcut backend servisi → Settings → **Branch'i bu PR'a çevir**
   (veya **+ New → GitHub Repo** → `backend-v2`)
2. **Root Directory:** boş (repo kökü — `Dockerfile` orada)
3. Railway `railway.json` → Dockerfile build otomatik algılar
4. **Variables** sekmesine ADIM 6'daki env'leri gir
5. `DATABASE_URL` → yeni `postgres-v2` servisine point et

**VERIFY:**
- [ ] Build başarılı (Dockerfile multi-stage Maven build)
- [ ] `/actuator/health` → UP (healthcheck geçer)
- [ ] Startup log: Flyway migrate OK, "S3 storage hazır" veya "Local storage"

---

### ADIM 5 — Frontend servisi (bu PR'dan)

1. Production env → mevcut frontend varsa branch'i çevir, yoksa **+ New → GitHub Repo**
2. **Branch:** bu PR branch
3. **Root Directory:** `frontend`
4. `frontend/Dockerfile` algılanır (nginx + React build)
5. **Variables:**
   ```
   API_ORIGIN=http://<backend-servis-adı>.railway.internal:8080
   STORE_HOST=yourdomain.com
   ADMIN_HOST=admin.yourdomain.com
   PORT=80
   ```

> **`admin.` prefix'i frontend default'unu (admin,wms) yakalar — ekstra build arg GEREKMEZ.** ✅

**VERIFY:**
- [ ] Frontend build başarılı
- [ ] Açılınca store anasayfa (henüz domain bağlamadan Railway URL'inden)

---

### ADIM 6 — Backend Env Değişkenleri (Tam Liste)

Backend service → Variables → şunları gir:

```bash
# Profil + SATIŞ KAPALI yayın
SPRING_PROFILES_ACTIVE=prod
STORE_PURCHASING_ENABLED=false   # ← SATIŞ KAPALI: site açık, kimse alamaz

# DB (yeni pgvector Postgres — Yol A)
DATABASE_URL=${{postgres-v2.DATABASE_URL}}
DB_USERNAME=${{postgres-v2.PGUSER}}
DB_PASSWORD=${{postgres-v2.PGPASSWORD}}

# Storage — production bucket
STORAGE_PROVIDER=s3
STORAGE_S3_ENDPOINT=${{Bucket.BUCKET_ENDPOINT}}
STORAGE_S3_BUCKET=${{Bucket.BUCKET_NAME}}
STORAGE_S3_ACCESS_KEY=${{Bucket.BUCKET_ACCESS_KEY}}
STORAGE_S3_SECRET_KEY=${{Bucket.BUCKET_SECRET_KEY}}
STORAGE_S3_REGION=us-east-1
STORAGE_S3_PATH_STYLE=true
STORAGE_S3_PUBLIC_ACL_SUPPORTED=true   # NotImplemented dönerse false

# Host routing — GERÇEK domain
APP_HOSTS_ADMIN=admin.yourdomain.com
APP_HOSTS_STORE=yourdomain.com,www.yourdomain.com
APP_BASE_URL=https://yourdomain.com
CORS_ALLOWED_ORIGINS=https://yourdomain.com,https://www.yourdomain.com,https://admin.yourdomain.com

# Admin & güvenlik — GERÇEK güçlü değerler (production!)
APP_ADMIN_USERNAME=<gerçek admin kullanıcı>
APP_ADMIN_PASSWORD=<güçlü, min 8 char + büyük + rakam>
ADMIN_SECURITY_CODE=<6-12 char güçlü>
JWT_SECRET=<openssl rand -base64 48 — production için yeni>
JWT_EXPIRATION_HOURS=24

# Email — production SMTP (sipariş/şifre maili için)
MAIL_HOST=smtp.gmail.com   # veya Yandex/Brevo
MAIL_PORT=587
MAIL_USERNAME=<şirket mail>
MAIL_PASSWORD=<app password>
MAIL_FROM=<noreply@yourdomain.com>
APP_MAIL_ENABLED=true

# Ödeme — satış kapalı olduğu için şimdilik fark etmez,
# ama doğru değerleri girip hazır tut (satış açılınca aktif olur)
PAYMENT_SANDBOX=false   # gerçek hesaplar (Iyzico/PayTR onaylanınca)
INVOICE_MOCK_ENABLED=false   # Logo eLogo gerçek

# Azure OpenAI (asistan — opsiyonel)
AZURE_OPENAI_ENDPOINT=<gerçek>
AZURE_OPENAI_API_KEY=<gerçek>

# Pool
DB_POOL_MAX=30
```

> **Önemli:** Satış kapalı olsa da bunlar production. Secret'lar gerçek ve güçlü olmalı. Eski WMS prod'unda zaten kullandığın değerler varsa onları taşı.

> **`${{servis.DEGISKEN}}` syntax** = Railway variable reference — servisler arası paylaşım. Gerçek değişken adlarını ilgili servisin Variables sekmesinden teyit et.

**VERIFY:**
- [ ] Backend restart sonrası health UP
- [ ] Log: Flyway V1→V62 uygulandı (yeni DB'de hepsi)
- [ ] Log: "Assistant RAG: ENABLED" (pgvector var) veya "DISABLED" (graceful)

---

### ADIM 7 — DNS + Custom Domain (GERÇEK domain)

DNS sağlayıcında:

| Subdomain | Type | Value | Açıklama |
|-----------|------|-------|----------|
| `@` (root) | CNAME/ALIAS | Railway frontend domain | **E-ticaret store** |
| `www` | CNAME | yourdomain.com | root'a |
| `admin` | CNAME | (aynı Railway frontend domain) | **WMS admin** (aynı build, host'a göre ayrılır) |

Railway tarafı:
1. Frontend service → Settings → Networking → **Custom Domain**
2. `yourdomain.com`, `www.yourdomain.com`, `admin.yourdomain.com` ekle → her biri için Railway CNAME hedefi → DNS'e
3. Backend → custom domain GEREKMEZ (frontend nginx internal proxy)

> **`admin.` prefix'i frontend default'unu (admin,wms) yakalar — ekstra build arg gerekmez.** ✅
> **TTL:** 300 saniye (cutover sırasında hızlı rollback için)

**VERIFY:**
- [ ] `yourdomain.com` → store, SSL yeşil, **satış kapalı banner**
- [ ] `admin.yourdomain.com` → admin login
- [ ] `curl https://admin.yourdomain.com/api/store/products` → 403 (host guard)
- [ ] `curl https://yourdomain.com/api/admin/dashboard` → 403

---

## 🧪 ADIM 8 — Smoke Test (Test Modunda)

### Store (satın alma KAPALI)
- [ ] Anasayfa açılıyor, **sarı test banner** görünür
- [ ] Kategori filtresi, arama çalışıyor
- [ ] Ürün detay → resimler bucket'tan geliyor
- [ ] "Sepete Ekle" çalışıyor (showcase)
- [ ] Checkout'a git → "Siparişi Onayla" **disabled** + uyarı
- [ ] Zorla place-order denersen → backend 400 "test modunda"

### Admin (WMS)
- [ ] `admin.yourdomain.com` → login (gerçek admin credentials)
- [ ] Dashboard yükleniyor
- [ ] **WMS: eski warehouse/stock/transfer verisi YERİNDE** (backup restore başarılı)
- [ ] **WMS: eski transfer fotoları görünüyor** (bucket migration sonrası — ADIM 9)
- [ ] Ürün ekle → resim yükle → bucket'a gidiyor
- [ ] Stok import Excel → işlendi

### Sistemsel
- [ ] Cron job'lar çalışıyor (log'da)
- [ ] N+1 yok — `/api/store/products?size=24` hızlı yanıt
- [ ] Memory stabil (Railway metrics)

---

## 📦 ADIM 9 — Eski WMS Dosyaları → Bucket (Data Migration)

ADIM 0'da indirdiğin `prod-uploads.tar.gz` → yeni bucket'a:

```bash
# 1. Aç ve bucket'a mirror
tar xzf prod-uploads.tar.gz
mc alias set prod https://<bucket-endpoint> <access> <secret>
mc mirror data/uploads prod/warehouse-uploads/

# 2. DB path normalize (yeni Postgres'te)
#    Eski: /data/uploads/shipments/... → Yeni: shipments/...
UPDATE transfer_item_photos
SET relative_path = REGEXP_REPLACE(relative_path, '^.*/uploads/', ''),
    thumbnail_path = REGEXP_REPLACE(thumbnail_path, '^.*/uploads/', '')
WHERE relative_path LIKE '%/uploads/%';
```

**VERIFY:**
- [ ] Eski transfer fotosu admin panelde görünüyor (bucket'tan)
- [ ] Bucket'ta dosya sayısı tar arşiviyle eşleşiyor

---

## ⚙️ Bilinmesi Gerekenler / Tuzaklar

1. **`admin.` prefix'i ✅ çözüldü** — frontend default'u (admin,wms) yakalar, ekstra build arg yok. `admin.yourdomain.com` kullanıyoruz.

2. **pgvector** — `pgvector/pgvector:pg15` image extension'ı önceden yüklü içerir. Flyway V42 DO block ile koşullu çalışır. Log'da "RAG ENABLED" görmelisin.

3. **Bucket public URL** — ürün/asset resimleri `viewAsset`/`serveSiteAsset` endpoint'leri üzerinden backend proxy'leniyor (presigned gerekmez). Public bucket olması şart değil.

4. **Eski Postgres'i SİLME** — rollback için en az 1 hafta dursun. Sorun çıkarsa `DATABASE_URL`'u geri çevir.

5. **Satış kapalıyken bile production** — secret'lar gerçek, mail aktif (şifre sıfırlama vb. çalışsın), domain gerçek. Sadece `STORE_PURCHASING_ENABLED=false`.

6. **Flyway** — Yeni `postgres-v2`'ye backup restore edince mevcut migration geçmişi de gelir. Backend başlayınca sadece V42-V62 arası YENİ migration'lar uygulanır (eski data zaten var).

---

## 🎯 Bu Faz Bitince

Production yeni sürümde, satış kapalı, gerçek veri korunmuş, WMS + e-ticaret çalışıyorsa:
1. **WMS ekibi** yeni paneli kullanmaya devam eder (kesinti minimum)
2. **Sen** e-ticareti dış gözle test edersin (satış kapalı)
3. **Adım adım** iyileştirme → ödeme entegrasyonları onaylanınca → `STORE_PURCHASING_ENABLED=true` ile satış açılır

> **Closed beta opsiyonu:** Satışı tüm dünyaya açmadan önce, davetli birkaç kişiyle gerçek sipariş testi yapmak istersen, uygulama seviyesinde whitelist eklenebilir (ayrı iş).

---

## ✅ Kararlar (Netleşti)

- **Yaklaşım:** Production in-place upgrade, satış kapalı, gerçek veri
- **Admin subdomain:** `admin.yourdomain.com` (default yakalar, build arg yok)
- **DNS:** Erişim var, gerçek domain kullanılacak
- **pgvector:** Yol A — yeni `postgres-v2` + backup restore (eski durur, rollback için)
