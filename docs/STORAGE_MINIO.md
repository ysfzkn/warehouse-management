# Object Storage — MinIO (Dev) + S3 (Prod)

S3-compatible storage entegrasyonu için pratik rehber.

---

## 1. Niye S3-compatible?

| | Local disk | MinIO (dev) | S3 / R2 / B2 (prod) |
|--|--|--|--|
| Kod değişikliği | — | yok | yok |
| Disk dolma riski | ⚠️ var | yok (Docker volume) | yok (cloud) |
| CDN entegrasyonu | manuel | manuel | builtin (R2 + Cloudflare) |
| Egress maliyeti | yok | yok | **R2: 0$**, B2: 0.01$/GB, S3: 0.09$/GB |
| Multi-instance | ⚠️ NFS gerekir | ortak bucket | ortak bucket |
| Backup | manuel | manuel | versioning + lifecycle |

**Sonuç:** Lokalde MinIO ile geliştir, prod'da **Cloudflare R2** (zero egress) öneriyoruz.

---

## 2. Lokal Setup (1 dakika)

```bash
# 1) MinIO + bucket auto-init başlat
docker compose up -d minio minio-init

# 2) Console'u aç (opsiyonel — bucket'ı UI'da görmek için)
open http://localhost:9001
# Login: minioadmin / minioadmin

# 3) Backend'in S3 modunda çalışması için application-dev.properties veya env:
echo "STORAGE_PROVIDER=s3" >> .env
# (veya application-dev.properties'e: storage.provider=s3)

# 4) Spring Boot'u restart et
mvn spring-boot:run
```

Backend loglarında görmeli olduğun:
```
S3 storage hazır: endpoint=http://localhost:9000, bucket=warehouse-uploads, pathStyle=true
```

Şimdi `LocalPhotoStorageService` bean'i devre dışı (`@ConditionalOnProperty(matchIfMissing=true)` — provider=s3 olduğunda match etmez), `S3PhotoStorageService` aktif.

---

## 3. Test

### 3.1 Smoke test — admin upload

1. Admin → Site Ayarları → Logo yükle
2. Backend log: `S3 asset stored: assets/logo-abc123.png (15234 bytes)`
3. MinIO console (http://localhost:9001):
   - "warehouse-uploads" bucket'ında **assets/** klasörü görünmeli
   - Dosyaya tıklayınca public URL ile görüntülenebilir

### 3.2 Ürün görseli upload

1. Admin → Ürün düzenle → Görsel ekle
2. Backend log: `S3 image stored: products/42/abc.webp (45KB main, 12KB thumb)`
3. Console'da: `products/{productId}/{uuid}.webp` + `_thumb.webp` çiftli

---

## 4. Production Geçişi — Ücret Karşılaştırması

| Sağlayıcı | Free tier | Sonrası | Karta ihtiyaç |
|---|---|---|---|
| **Oracle Cloud Object Storage** ⭐ | **20 GB + 10 TB egress/ay — always free** | $0.0255/GB/ay | Evet (charge etmez) |
| **Cloudflare R2 Free Tier** | 10 GB + 10M req/ay free | $0.015/GB + $0 egress | Hayır |
| **Backblaze B2 Free Tier** | 10 GB + 1 GB/gün egress free | $0.005/GB | Hayır |
| **Self-hosted MinIO + Oracle Free VM** | **200 GB always free** | $0 | Evet |
| **Self-hosted MinIO + Hetzner VPS** | — | €3.79/ay (40 GB) | Evet |
| ❌ Railway volume | — | $0.25/GB/ay (pahalı) | — |
| ❌ AWS S3 | 5 GB / 12 ay | $0.023/GB + **$0.09/GB egress** (pahalı) | Evet |

### ⭐ Option A: Oracle Cloud Object Storage — EN İYİ ÜCRETSİZ

**Neden:** 20 GB storage + **10 TB egress** sonsuza dek ücretsiz. Kart bilgisi
verirsiniz ama free tier limitleri içinde **charge edilmez** (Oracle uyarır,
otomatik kesmez).

**Setup:**
1. https://cloud.oracle.com → "Start for free" hesap aç (kart bilgisi + telefon doğrulama)
2. Console → **Storage → Object Storage → Buckets → Create Bucket**
   - Name: `warehouse-uploads`
   - Default Storage Tier: Standard
3. **User → Customer Secret Keys → Generate Secret Key** (S3-compatible auth için)
4. Bucket endpoint formatı: `https://<namespace>.compat.objectstorage.<region>.oraclecloud.com`
   - `namespace`: tenancy info'dan al
   - `region`: bucket'ı oluşturduğun region (örn. `eu-frankfurt-1`)
5. Env:
   ```bash
   STORAGE_PROVIDER=s3
   STORAGE_S3_ENDPOINT=https://<namespace>.compat.objectstorage.eu-frankfurt-1.oraclecloud.com
   STORAGE_S3_BUCKET=warehouse-uploads
   STORAGE_S3_ACCESS_KEY=<customer-secret-access-key>
   STORAGE_S3_SECRET_KEY=<customer-secret-key>
   STORAGE_S3_REGION=eu-frankfurt-1
   STORAGE_S3_PATH_STYLE=true
   ```

### Option B: Cloudflare R2 Free Tier (kart sormaz)

**10 GB / 10M req / ay tamamen ücretsiz.** Kart bilgisi gerekmez.

1. https://dash.cloudflare.com → R2 → "Create bucket" → `warehouse-uploads`
2. **Manage R2 API Tokens** → "Create API token" → Read & Write
3. Custom domain bind et (opsiyonel): bucket → Settings → Custom Domain → `cdn.siteniz.com`
4. Env:
   ```bash
   STORAGE_PROVIDER=s3
   STORAGE_S3_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
   STORAGE_S3_BUCKET=warehouse-uploads
   STORAGE_S3_ACCESS_KEY=<r2-access-key>
   STORAGE_S3_SECRET_KEY=<r2-secret-key>
   STORAGE_S3_REGION=auto
   STORAGE_S3_PATH_STYLE=true
   STORAGE_S3_PUBLIC_BASE_URL=https://cdn.siteniz.com   # opsiyonel
   ```

**10 GB doldurunca:** $0.015/GB + **zero egress** = CDN trafiği bedava (büyüyünce bile ucuz).

### Option C: Backblaze B2 + Cloudflare Bandwidth Alliance

**10 GB free + 1 GB/gün egress free.** Cloudflare CDN proxy ekleyince **bandwidth alliance**
sayesinde egress %100 free.

1. https://backblaze.com → B2 → bucket oluştur (Private)
2. App Key (readFiles + writeFiles capabilities)
3. Cloudflare CDN cache koy: https://www.backblaze.com/cloudflare
4. Env:
   ```bash
   STORAGE_PROVIDER=s3
   STORAGE_S3_ENDPOINT=https://s3.us-west-002.backblazeb2.com
   STORAGE_S3_BUCKET=warehouse-uploads
   STORAGE_S3_ACCESS_KEY=<b2-key-id>
   STORAGE_S3_SECRET_KEY=<b2-app-key>
   STORAGE_S3_REGION=us-west-002
   STORAGE_S3_PATH_STYLE=true
   ```

### Option D: Self-hosted MinIO (full kontrol — özellikle Oracle Free VM ile)

**En ucuz tam kontrol:**
- **Oracle Cloud Always Free VM**: 4 ARM CPU + 24 GB RAM + **200 GB disk** sonsuza dek ücretsiz
- veya **Hetzner CX22**: €3.79/ay 40 GB (Avrupa, hızlı)
- veya **Contabo Storage VPS**: €4.99/ay 1 TB (büyük katalog için)

Setup:
1. VPS al → Docker kur (Ubuntu için `curl -fsSL https://get.docker.com | sh`)
2. Bu repo'yu clone et → `docker compose up -d minio minio-init`
3. UFW: port 9000 sadece backend IP'sinden, 9001 admin için
4. Nginx + Let's Encrypt: `minio.siteniz.com` → reverse proxy http://localhost:9000
5. Backend env:
   ```bash
   STORAGE_PROVIDER=s3
   STORAGE_S3_ENDPOINT=https://minio.siteniz.com
   STORAGE_S3_BUCKET=warehouse-uploads
   STORAGE_S3_ACCESS_KEY=<minio-root-user>
   STORAGE_S3_SECRET_KEY=<minio-root-password>
   STORAGE_S3_PATH_STYLE=true
   ```

> **Backup ekle:** `cron job` ile `mc mirror local/warehouse-uploads /backup/$(date +%Y%m%d)` —
> haftalık off-site (örn. başka VPS / B2 free tier).

### ❌ Option E: Railway disk + MinIO (önerilmez)

Railway volume $0.25/GB/ay → 100 GB = $25/ay. Compute platform'u object storage için
optimize değil. **Oracle Cloud / R2 / B2'den 10-15x pahalı.**

### ❌ Option F: AWS S3 (önerilmez)

5 GB / 12 ay free; sonrası **egress $0.09/GB** — Türkiye trafiğinde aylık on dolarlar
ekler. R2 veya Oracle daha mantıklı.

---

## 5. Migration — Mevcut Yerel Dosyaları S3'e Taşı

```bash
# 1) MinIO mc CLI ile
docker run --rm -v /path/to/uploads:/src --network warehouse-network \
  minio/mc:latest sh -c "
    mc alias set local http://minio:9000 minioadmin minioadmin;
    mc mirror /src local/warehouse-uploads;
  "

# 2) Verify
docker run --rm --network warehouse-network minio/mc:latest \
  mc ls --recursive local/warehouse-uploads | head -20

# 3) DB kayıtlarındaki relativePath değerleri zaten /transfers/X/Y/Z.webp gibi,
#    bucket içindeki anahtarlarla 1:1 — sadece STORAGE_PROVIDER=s3 yapmak yeterli
```

---

## 6. Production Önerileri

- **Versioning aktif et** (R2/S3/B2): yanlışlıkla silinen logo geri alınabilir
- **Lifecycle rule**: 30 günden eski thumbnail'lar Glacier'a (S3) veya silinsin
- **CORS**: bucket'a custom domain bind ettiğinde, frontend origin'e Allow-Origin
- **CDN cache**: Cloudflare zaten CDN'i hazır; CloudFront/S3 için ayrıca tanımla
- **Backup**: S3 cross-region replication veya `mc mirror` cron job (3-2-1 kuralı)

---

## 7. Geri Alma (S3'ten local'e dön)

```bash
# 1) Bucket içeriğini local diske kopyala
mc mirror local/warehouse-uploads /var/uploads

# 2) Backend env:
STORAGE_PROVIDER=local
PHOTO_STORAGE_PATH=/var/uploads

# 3) Restart
```

LocalPhotoStorageService ve S3PhotoStorageService aynı `PhotoStorageService`
interface'ini implement ettiği için çağıran kod (controller'lar, servisler) hiç
değişmez — sadece bean swap olur.
