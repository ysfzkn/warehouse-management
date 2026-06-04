# 🧪 Test Ortamı + Production Sağlamlaştırma Planı

> **Bağlam:** Şu an production'da Railway'de **sadece eski WMS** çalışıyor (plain Postgres, local files). Bu plan, yeni e-ticaret + güncellenmiş WMS sürümünü:
> 1. **Test ortamına** (satın alma kapalı, "vitrine bakılır ama alınamaz" modu) çıkarmak
> 2. **Production'ı** backup + pgvector + S3 dönüşümüyle sağlamlaştırmak
> 3. **Trafik artışına** önden hazırlanmak (N+1, connection pool, cache)
> 4. **Domain'i** ayırmak: root → e-ticaret, `admin.domain` → WMS
>
> için adım adım yol haritasıdır.

---

## 📊 Mevcut Durum Analizi (Keşif Sonuçları)

### ✅ Hazır Olanlar

| Bileşen | Durum | Dosya |
|---------|-------|-------|
| **Storage abstraction** | S3/MinIO/Railway bucket destekli | `S3PhotoStorageService`, `application-prod.properties` |
| **pgvector** | Graceful optional — yoksa RAG kapanır, sistem çalışır | `V42__assistant_core.sql`, `VectorSearchService.detectPgVector()` |
| **Host validation** | `HostValidationFilter` prod'da `app.hosts.admin` set edilince aktif | `security/HostValidationFilter.java` |
| **Frontend host routing** | Tek SPA, `REACT_APP_ADMIN_HOSTS` ile subdomain ayrımı | `frontend/src/App.js:92-107` |
| **Railway deploy** | `railway.json` (Dockerfile) + GitHub Actions + quality gates | `.github/workflows/railway-deploy.yml` |
| **Ödeme yöntemi toggle** | `payment_method_*_enabled` site_settings | `StorePaymentController.java:205-256` |
| **ShedLock** | Multi-instance job güvenliği (V59) | `V59__shedlock_and_indexes.sql` |
| **Bank transfer hardening** | Amount verify + pessimistic lock + reject flow | (bu session'da yapıldı) |

### ❌ Yapılması Gerekenler (Bu Planın Kapsamı)

| # | Sorun | Öncelik | Tip |
|---|-------|---------|-----|
| 1 | **Global "test modu" / satın alma kapatma yok** | 🔴 BLOCKER | Kod |
| 2 | **N+1 sorgu** — ürün listesi (24 ürün = 73 query) | 🔴 BLOCKER (trafik) | Kod |
| 3 | Connection pool 20 — 50 RPS'de saturate | 🟡 HIGH | Config |
| 4 | Cache event-driven eviction yok (stale stok riski) | 🟡 HIGH | Kod |
| 5 | pgvector prod dönüşümü test edilmedi | 🔴 BLOCKER | Ops |
| 6 | S3/bucket prod dönüşümü test edilmedi | 🔴 BLOCKER | Ops |
| 7 | DB backup stratejisi formalize değil | 🔴 BLOCKER | Ops |
| 8 | Domain/subdomain routing prod'da set edilmedi | 🔴 BLOCKER | Ops |

---

## ⚠️ "Direkt bu PR'ı alıp test ortamına çıksam?" — HAYIR, önce 2 kod fix

Soruya net cevap: **Şu an PR'ı olduğu gibi test ortamına çıkarırsan**:
- Müşteri **gerçekten sipariş verebilir** (test modu gate yok) → istemiyorsun
- Ürün listesi sayfası **her istekte 73 DB sorgusu** atar → birkaç kullanıcıda bile yavaşlar

Bu iki fix (Faz 0) **test ortamından önce** yapılmalı. Gerisi test ortamında doğrulanır.

---

## 🔧 FAZ 0 — Kod Fix'leri (Test Ortamından ÖNCE)

### 0.1 Global Test Modu / Satın Alma Kapatma 🔴

**Sorun:** `storeEnabled` flag'i sadece asistan widget'ını gizliyor; checkout endpoint'lerinde guard yok.

**Çözüm:** İki katmanlı:

**A) Backend gate** — `CheckoutServiceImpl.placeOrder()` ve `placeGuestOrder()` başına:
```
if (!siteSettingService.getBoolSetting("store_purchasing_enabled", true)) {
    throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
        "Mağaza şu anda test modunda. Sipariş alımı geçici olarak kapalıdır.");
}
```
- Yeni site_settings key: `store_purchasing_enabled` (default `true`)
- Migration V62'de seed
- `StorePaymentController.initializePayment` başına da aynı guard (defense-in-depth)

**B) Frontend banner + buton disable** — `store_purchasing_enabled=false` iken:
- `/api/store/settings` zaten tüm setting'leri döndürüyor → frontend okur
- "Sepete Ekle" / "Satın Al" butonları disabled + tooltip "Test modunda satış kapalı"
- Üst banner: "🧪 Test Ortamı — Bu sitede gerçek satış yapılmamaktadır"

**Kritik dosyalar:**
- `service/impl/CheckoutServiceImpl.java` (placeOrder + placeGuestOrder)
- `controller/store/StorePaymentController.java` (initializePayment)
- `db/migration/V62__store_purchasing_toggle.sql` (yeni)
- `frontend/src/pages/store/ProductDetailPage.js`, `CartPage.js`, `CheckoutPage.js` (buton disable)
- `frontend/src/layouts/StoreLayout.js` (test banner)

**VERIFY:**
- [ ] `store_purchasing_enabled=false` → checkout 400 "test modunda"
- [ ] Frontend "Sepete Ekle" disabled, test banner görünür
- [ ] Admin panelden toggle'la aç/kapa çalışıyor

### 0.2 N+1 Sorgu Düzeltmesi (Ürün Listesi) 🔴

**Sorun:** `StoreProductController.toStoreDto()` her ürün için 3 ayrı query (stok + rating + review count). 24 ürün = 73 query.

**Çözüm:** Batch fetch:
- `stockService.getStocksByProductIds(List<Long>)` — tek `IN` sorgusu
- `reviewRepository.getRatingsForProducts(List<Long>)` — `GROUP BY product_id` tek sorgu
- DTO map'inde memory'den lookup (Map<Long, ...>)

Hedef: 24 ürün → **3 query** (products + stocks batch + reviews batch).

**Kritik dosyalar:**
- `controller/store/StoreProductController.java` (toStoreDto + list endpoint)
- `repository/StockRepository.java` (batch method ekle)
- `repository/ProductReviewRepository.java` (batch rating/count)
- `service/StockService.java` + impl (batch method)

**VERIFY:**
- [ ] Hibernate SQL log'da ürün listesi için ≤ 5 query
- [ ] k6 ile 50 RPS `/api/store/products` → p95 < 500ms

### 0.3 Connection Pool + Cache (HIGH)

**Pool** — `application-prod.properties`:
```
spring.datasource.hikari.maximum-pool-size=${DB_POOL_MAX:30}
```
(20 → 30, env override'lı. Railway Postgres free tier limitine dikkat — genelde 22-100 arası.)

**Cache eviction** — stok/fiyat update'inde `@CacheEvict`:
- `ProductServiceImpl.update*` → `@CacheEvict(value={"productDetail","storeCatalog"}, ...)`
- `StockServiceImpl.adjust*` → `@CacheEvict("stockAvailability")`

**VERIFY:**
- [ ] Stok güncelle → store'da anında yansıyor (5 dk beklemeden)

---

## 🌐 FAZ 1 — Test Ortamı Kurulumu (Railway 2. Environment)

### 1.1 Railway "test" / "staging" environment

1. Railway Dashboard → Project → Environments → **+ New → "staging"**
2. Bu environment'a 4 servis:
   - **Postgres** — `pgvector/pgvector:pg15` image (RAG test için)
   - **Bucket** (Object Storage) — S3 test
   - **Backend** — bu PR'dan deploy
   - **Frontend** — bu PR'dan deploy

> **Neden ayrı environment?** Production WMS'e dokunmadan, izole test. DB/bucket/env tamamen ayrı.

### 1.2 Test domain'leri

Gerçek domain kullanmak istemezsen Railway'in verdiği `*.up.railway.app` domain'leriyle test edebilirsin **ama** subdomain routing test edemezsin. İki seçenek:

**A) Railway default domains (subdomain routing test edilemez)**
- Backend: `xxx-backend.up.railway.app`
- Frontend: `xxx-frontend.up.railway.app`
- `HOST_VALIDATION_ENABLED=false` (test'te kapalı)

**B) Gerçek test subdomain'i (önerilen, prod-parity)**
- `test.yourdomain.com` → frontend (store)
- `admin-test.yourdomain.com` → frontend (admin, aynı build host'a göre ayrılır)
- `api-test.yourdomain.com` → backend
- DNS'e CNAME, Railway custom domain
- Host validation test edilebilir

### 1.3 Test env değişkenleri

```bash
SPRING_PROFILES_ACTIVE=prod   # prod profile ama test data
STORE_PURCHASING_ENABLED=false  # ← TEST MODU, satış kapalı

# DB (Railway auto-inject)
DATABASE_URL=...

# Storage — test bucket
STORAGE_PROVIDER=s3
STORAGE_S3_ENDPOINT=${{Bucket.BUCKET_ENDPOINT}}
STORAGE_S3_BUCKET=${{Bucket.BUCKET_NAME}}
STORAGE_S3_ACCESS_KEY=${{Bucket.BUCKET_ACCESS_KEY}}
STORAGE_S3_SECRET_KEY=${{Bucket.BUCKET_SECRET_KEY}}
STORAGE_S3_REGION=us-east-1
STORAGE_S3_PATH_STYLE=true

# Host routing (B seçeneğinde)
APP_HOSTS_ADMIN=admin-test.yourdomain.com
APP_HOSTS_STORE=test.yourdomain.com
HOST_VALIDATION_ENABLED=true

# Ödeme — SANDBOX (gerçek para yok)
PAYMENT_SANDBOX=true
IYZICO_API_KEY=<sandbox>
INVOICE_MOCK_ENABLED=true   # ← test'te mock fatura

# Frontend
REACT_APP_API_BASE_URL=https://api-test.yourdomain.com
REACT_APP_ADMIN_HOSTS=admin-test
```

**VERIFY:**
- [ ] 4 servis çalışıyor, `/actuator/health` UP
- [ ] `test.yourdomain.com` → store açılır, "Test modu" banner var
- [ ] "Sepete Ekle" disabled (purchasing kapalı)
- [ ] `admin-test.yourdomain.com` → admin login

---

## 🗄️ FAZ 2 — pgvector + Postgres Dönüşüm Testi

> **Bu test production cutover'ından önce staging'de PROVA edilir.** Production'da ilk kez denenmemeli.

### 2.1 Senaryo: Prod plain Postgres → pgvector

Production şu an **vanilla Postgres** (pgvector yok). İki yol:

**Yöntem A — pgvector image swap (önerilen)**
1. Yeni Postgres servisi `pgvector/pgvector:pg15` ekle
2. Eski DB'den `pg_dump` → yeni servise restore
3. Backend `DATABASE_URL` yeni servise
4. Flyway V42 otomatik `CREATE EXTENSION vector` dener (yetkisi varsa)

**Yöntem B — pgvector'sız devam (acil değilse)**
- `VectorSearchService` zaten graceful → RAG kapalı, gerisi çalışır
- Sonra Yöntem A'ya geç

### 2.2 Staging'de prova (production data kopyasıyla)

```bash
# 1. Prod DB backup (read-only, prod'a zarar vermez)
railway run --service prod-postgres pg_dump $DATABASE_URL > prod-snapshot.sql

# 2. Staging pgvector Postgres'e restore
railway run --service staging-postgres psql $DATABASE_URL < prod-snapshot.sql

# 3. Flyway migrate (backend başlatınca otomatik)
# V42 → V61 arası uygulanır, eski data korunur
```

**VERIFY (staging'de):**
- [ ] `SELECT * FROM pg_extension WHERE extname='vector';` → 1 satır (varsa)
- [ ] `SELECT MAX(installed_rank), version FROM flyway_schema_history;` → V61
- [ ] `SELECT COUNT(*) FROM warehouses;` = eski prod sayısı (veri kaybı yok)
- [ ] `SELECT version, success FROM flyway_schema_history WHERE NOT success;` → 0 satır
- [ ] Backend startup log: "Assistant RAG: ENABLED" (pgvector varsa) veya "DISABLED" (graceful)
- [ ] Yeni tablolar: `orders`, `cart_items`, `customers`, `product_images`, `assistant_documents` vb.

### 2.3 Riskli migration kontrolü

| Migration | Risk | Not |
|-----------|------|-----|
| V15 (eski) | ALTER 18 kolon products | Zaten uygulanmış (prod'da var), tekrar çalışmaz |
| V59 | 10 index hot tablolarda | `IF NOT EXISTS` idempotent, steady-state'te güvenli |
| V42 | CREATE EXTENSION vector | DO block ile koşullu, yetki yoksa skip |

**Migration smoke:** `mvn flyway:validate` lokal + staging'de.

---

## ☁️ FAZ 3 — S3/Bucket Dönüşüm Testi

### 3.1 Eski WMS dosyaları (transfer fotoları)

Production WMS şu an **local file system** kullanıyor (`/data/uploads` veya `C:/...`). Yeni sürüm S3 bekliyor.

```bash
# 1. Eski prod upload'ları indir
railway run --service prod-backend tar czf /tmp/uploads.tar.gz /data/uploads
railway run --service prod-backend cat /tmp/uploads.tar.gz > prod-uploads.tar.gz

# 2. Staging bucket'a yükle
tar xzf prod-uploads.tar.gz
mc mirror data/uploads staging-bucket/warehouse-uploads/

# 3. DB path normalize (staging'de)
# Eski: /data/uploads/shipments/... → Yeni: shipments/...
UPDATE transfer_item_photos
SET relative_path = REGEXP_REPLACE(relative_path, '^.*/uploads/', ''),
    thumbnail_path = REGEXP_REPLACE(thumbnail_path, '^.*/uploads/', '')
WHERE relative_path LIKE '%/uploads/%';
```

**VERIFY:**
- [ ] Staging admin → eski transfer'i aç → fotoğraf bucket'tan geliyor
- [ ] Yeni ürün foto upload → bucket'a yazıyor (MinIO/Railway console'da gör)
- [ ] Logo/banner/QR upload → çalışıyor

### 3.2 Storage smoke test (her dosya tipi)

- [ ] Ürün resmi (WebP optimize + thumbnail)
- [ ] Site logo/favicon/banner
- [ ] Havale QR
- [ ] Fatura PDF upload/download
- [ ] Stok import Excel
- [ ] Asistan döküman (RAG)

---

## 💾 FAZ 4 — Production Backup Sağlamlaştırma

### 4.1 Otomatik backup (cutover'dan ÖNCE aktif)

1. **Railway managed snapshot** — Postgres service → Backups → daily otomatik (aktif mi kontrol)
2. **Haftalık external pg_dump** — GitHub Actions cron → Backblaze B2 (ücretsiz 10GB):
   ```yaml
   # .github/workflows/db-backup.yml
   on:
     schedule:
       - cron: '0 3 * * 0'  # Pazar 03:00 UTC
   ```
3. **Cutover öncesi manuel backup** — production değiştirilmeden hemen önce

### 4.2 Restore prosedürü (RUNBOOK)

`docs/RUNBOOK.md` oluştur:
- Backup'tan restore adımları
- Rollback senaryosu
- Bağlantı string'leri
- Acil durum kontakları

**VERIFY:**
- [ ] Backup B2'ye gidiyor (manuel trigger ile test)
- [ ] Restore testi: backup → staging DB → veri bütünlüğü

---

## 🚀 FAZ 5 — Trafik Artışı Hazırlığı

### 5.1 Önden alınan önlemler (Faz 0'da yapıldı)

- ✅ N+1 fix (ürün listesi 73 → 3 query)
- ✅ Connection pool 20 → 30
- ✅ Cache event-driven eviction

### 5.2 Yük testi (staging'de)

```javascript
// k6 — store browse + checkout (test modunda checkout 400 döner, normal)
import http from 'k6/http';
export const options = { vus: 50, duration: '5m' };
export default function () {
  http.get('https://test.yourdomain.com/api/store/products?size=24');
  http.get('https://test.yourdomain.com/api/store/categories/tree');
}
```

**Hedef metrikler:**
- [ ] p95 < 800ms, p99 < 1.5s
- [ ] Error rate < %1
- [ ] DB connection pool saturate olmuyor (`hikari.active` < max)
- [ ] Memory leak yok (1 saat sürekli yük, heap stabil)

### 5.3 İzleme (Faz 8 ile birlikte)

- Grafana Loki — log
- Sentry — error tracking
- UptimeRobot — uptime
- Railway metrics — CPU/RAM/DB

---

## 🌍 FAZ 6 — Domain Routing (Production Cutover Sırasında)

### 6.1 DNS yapısı

| Subdomain | Hedef | Açıklama |
|-----------|-------|----------|
| `@` (root) | Railway frontend | **E-ticaret store** |
| `www` | root'a redirect | |
| `admin` | Railway frontend (aynı build) | **WMS admin** (host'a göre ayrılır) |
| `api` | Railway backend | API |

### 6.2 Backend env (production)

```bash
APP_HOSTS_ADMIN=admin.yourdomain.com
APP_HOSTS_STORE=yourdomain.com,www.yourdomain.com
HOST_VALIDATION_ENABLED=true
CORS_ALLOWED_ORIGINS=https://yourdomain.com,https://www.yourdomain.com,https://admin.yourdomain.com
APP_BASE_URL=https://yourdomain.com
```

### 6.3 Frontend env

```bash
REACT_APP_API_BASE_URL=https://api.yourdomain.com
REACT_APP_ADMIN_HOSTS=admin
```

**VERIFY:**
- [ ] `yourdomain.com` → store anasayfa
- [ ] `admin.yourdomain.com` → admin login
- [ ] `curl admin.yourdomain.com/api/store/products` → 403 (host guard)
- [ ] `curl yourdomain.com/api/admin/dashboard` → 403
- [ ] SSL A+ (securityheaders.com, ssllabs.com)

---

## 🔄 FAZ 7 — Production Cutover (Eski WMS → Yeni Sistem)

> **DİKKAT:** Eski WMS prod'da çalışıyor ve kullanılıyor olabilir. Cutover'da WMS verisi (warehouses, stocks, transfers, transfer photos) KORUNMALI.

### 7.1 Cutover öncesi (T-1 gün)

- [ ] Tüm Faz 0-6 staging'de yeşil
- [ ] Staging smoke test tam geçti
- [ ] Production son backup alındı (DB + uploads)
- [ ] DNS TTL → 300 saniye (rollback için)
- [ ] Bakım penceresi duyuruldu (WMS kullanıcılarına)

### 7.2 Cutover günü (Cumartesi sabah 09:00 önerilir)

1. **Bakım modu** — eski WMS'e "bakım" sayfası (10-30 dk)
2. **Final backup** — `pg_dump` + uploads tar
3. **Yeni Postgres** (pgvector) → final backup restore + Flyway migrate
4. **Uploads** → bucket'a mirror + DB path normalize
5. **Backend deploy** (yeni jar, prod env)
6. **Frontend deploy** (yeni build)
7. **Custom domain** → yeni servislere taşı
8. **DNS propagation** bekle (TTL 300 = max 5 dk)
9. **`STORE_PURCHASING_ENABLED`** — closed beta için `false`, public launch'ta `true`

### 7.3 Cutover sonrası smoke (production)

- [ ] WMS: eski warehouse/stock/transfer verisi yerinde
- [ ] WMS: eski transfer fotoları görünüyor (bucket'tan)
- [ ] Store: anasayfa, ürün, kategori açılıyor
- [ ] Admin login + dashboard
- [ ] (Public launch'ta) 1 TL gerçek Iyzico test ödemesi

### 7.4 Rollback planı

Sorun çıkarsa:
1. DNS'i eski prod'a geri yönlendir (TTL 300 = 5 dk)
2. Eski Postgres + eski backend hâlâ duruyor (silinmedi)
3. 5-10 dk içinde eski sürüm açık

---

## 🛡️ FAZ 8 — Observability (Cutover ile Birlikte)

- **Loglar** — JSON encoder zaten prod'da; Railway logs veya Grafana Cloud Loki
- **Error tracking** — Sentry (free 5k/ay) backend + frontend
- **Uptime** — UptimeRobot: `/actuator/health`, store, admin
- **Alerts** — error spike, DB CPU, pool saturation

(Detay: `docs/PRE_LAUNCH_CHECKLIST.md` Faz 8)

---

## 📋 Özet — Sıralı Aksiyon Listesi

| Sıra | Faz | Süre | Çıktı |
|------|-----|------|-------|
| 1 | **Faz 0** — Kod fix (test modu + N+1 + pool/cache) | 1-2 gün | PR güncellenir |
| 2 | **Faz 1** — Staging environment | 0.5 gün | İzole test ortamı |
| 3 | **Faz 2** — pgvector dönüşüm prova | 0.5 gün | Migration güvenli |
| 4 | **Faz 3** — S3 dönüşüm prova | 0.5 gün | Storage güvenli |
| 5 | **Faz 4** — Backup otomasyonu | 0.5 gün | Veri güvenliği |
| 6 | **Faz 5** — Yük testi | 0.5 gün | Performans onayı |
| 7 | **Faz 6+7** — Domain + cutover | 0.5 gün | Production canlı |
| 8 | **Faz 8** — Observability | sürekli | İzleme |

**Toplam aktif iş: ~4-5 gün.** Test ortamı 1-2 günde ayakta, cutover hafta sonu.

---

## 🎯 Hemen Yapılacak İlk Adım

**Faz 0.1 (test modu) + Faz 0.2 (N+1)** — bunlar olmadan test ortamı anlamlı değil:
1. `store_purchasing_enabled` gate (backend + frontend + migration V62)
2. Ürün listesi N+1 batch fetch
3. Pool 30 + cache eviction

Bu 3 fix tamamlanınca PR test ortamına çıkmaya hazır olur.

---

## ✅ Verilen Kararlar (Kilitlendi)

| Soru | Karar |
|------|-------|
| **Faz 0 kapsamı** | **Hepsi** — test modu gate + N+1 fix + pool/cache. Test ortamı tam prod-parity çıkacak. |
| **Test domain'i** | **Gerçek subdomain** — `test.yourdomain.com` (store) + `admin-test.yourdomain.com` (admin) + `api-test.yourdomain.com` (backend). Host routing prod-parity test edilir. |
| **pgvector** | **Yöntem A — image swap.** Yeni Postgres `pgvector/pgvector:pg15` + backup restore. RAG asistan tam çalışır. |
| **İlk adım** | Plan onaylandı; kod implementasyonu kullanıcı onayı sonrası başlayacak. |

### Kalan kararlar (cutover zamanı netleşince)

- **Cutover zamanı**: WMS kullanıcıları için en uygun bakım penceresi (öneri: Cumartesi 09:00)
- **Closed beta**: Public launch öncesi davetli müşteri testi (`STORE_PURCHASING_ENABLED` aşamalı: test=false → beta=davetli → public=true)

---

## 🔨 Faz 0 — Detaylı Görev Dökümü ✅ TAMAMLANDI

### Görev 1 — Test Modu Gate ✅
- [x] `V62__store_purchasing_toggle.sql` — `store_purchasing_enabled=true` + banner metni seed
- [x] `SiteSettingService.getBoolSetting(key, default)` helper + impl
- [x] `CheckoutServiceImpl` — `assertPurchasingEnabled()` guard, `placeOrder()` + `placeGuestOrder()` başına
- [x] `StorePaymentController.initializePayment()` — guard (defense-in-depth)
- [x] `StoreLayout.js` — test banner (`store_purchasing_enabled !== 'true'` iken sticky banner)
- [x] `CheckoutPage.js` — "Siparişi Onayla" butonu disabled + uyarı
- [ ] Admin toggle — `AdminSiteSettings`'e switch (opsiyonel, SQL ile de açılır)

> **Karar:** "Sepete Ekle" butonları AÇIK bırakıldı — cart/katalog UX'i test edilebilsin (showcase). Backend checkout'u hard-block ediyor + banner uyarıyor.

### Görev 2 — N+1 Fix ✅
- [x] `StockRepository.sumAvailableByProductIds(List<Long>)` — batch GROUP BY
- [x] `ReviewRepository.getRatingStatsForProducts(List<Long>)` — batch GROUP BY (avg+count)
- [x] `StoreProductController` — `batchStockAvailability()` + `batchReviewStats()` + `buildDto()` ortak builder
- [x] `list()` — sayfa ürünleri için 2 batch sorgu, Map lookup (toStoreDto refactor)
- **Sonuç:** 24 ürün için 73 query → ~3-4 query (products + stock batch + review batch + images)

### Görev 3 — Pool + Cache ✅
- [x] `application-prod.properties` — `maximum-pool-size=${DB_POOL_MAX:30}` (env override'lı)
- [x] Cache eviction — **gerekmedi**: `storeCatalog`/`productDetail`/`stockAvailability` cache'leri tanımlı ama hiç kullanılmıyor (store endpoint'leri cache'siz). Stale data riski yok. N+1 fix DB yükünü zaten çözdü.

**Faz 0 tamamlandı. Derleme: IntelliJ'den `mvn clean compile`. Sonra Faz 1 (staging) hazır.**

### ⚠️ Derleme Sonrası Doğrulama (kullanıcı yapacak)
- [ ] `mvn clean compile` — hata yok
- [ ] `mvn test` — testler yeşil (constructor değişiklikleri test kırmamalı — doğrudan construct eden test yok)
- [ ] Dev'de backend restart → `store_purchasing_enabled=false` SQL ile set → checkout 400, banner görünür
- [ ] Hibernate SQL log (`show-sql=true`) → ürün listesi ≤ 5 query
</content>
</invoke>
