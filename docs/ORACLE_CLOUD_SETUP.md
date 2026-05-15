# Oracle Cloud Object Storage — Setup Rehberi

Oracle Free Tier (20 GB storage + 10 TB egress / ay, **sonsuza dek ücretsiz**)
ile S3-compatible backend kurulumu. Adım adım, ekran açıklamalarıyla.

---

## 0. Ön Bilgiler

Oracle Object Storage S3-compatible API sunar ama bazı **küçük farklar** var:
- ACL `PUBLIC_READ` desteklenmez (bunun yerine **Pre-Authenticated Request** veya bucket visibility)
- Endpoint formatı: `https://<namespace>.compat.objectstorage.<region>.oraclecloud.com`
- "Customer Secret Keys" (S3 API için) IAM user secret'larından farklı
- **Path-style** zorunlu

Hepsini `S3PhotoStorageService` zaten halledebilecek şekilde yapılandırdık —
sadece env değerlerini doldurman yeter.

---

## 1. Oracle Console — Bucket Oluştur (5 dk)

### 1.1 Hamburger menü → Storage → Object Storage & Archive Storage → **Buckets**

### 1.2 Compartment seç
Üst soldaki "Compartment" dropdown'dan root compartment'ı seç (veya yeni bir
compartment oluştur, örn. `warehouse`). Compartment Oracle'da "klasör" gibi.

### 1.3 **Create Bucket** butonuna bas

Form alanları:
- **Bucket Name:** `warehouse-uploads`
- **Default Storage Tier:** **Standard** (sık erişim için; Infrequent Access daha ucuz ama latency yüksek)
- **Object Versioning:** Disable (istersen Enable, yanlış silme korunma için)
- **Encryption:** Encrypt using Oracle managed keys (default)
- **Auto-Tiering:** Disabled

**Create** → birkaç saniye içinde bucket hazır.

### 1.4 Bucket Region'ını NOT ET

Bucket detay sayfasında "**Region**" yazıyor (örn. `eu-frankfurt-1`, `eu-amsterdam-1`,
`me-dubai-1`). **Bu değeri kopyala — env'e gerekecek.**

> **Türkiye için en yakın region'lar:** `eu-frankfurt-1` (Almanya), `me-dubai-1` (BAE),
> `eu-amsterdam-1` (Hollanda). Frankfurt genelde en hızlı latency verir.

---

## 2. Namespace'i Bul

Namespace, Oracle hesabının benzersiz ID'sidir — endpoint URL'inde kullanılır.

### Yöntem 1: Console
Bucket detay sayfasında "**Namespace**" alanı görünür. 14-20 karakterli string,
örn. `axdkmuabcdef`.

### Yöntem 2: User Profile
Sağ üst kullanıcı simgesi → "Tenancy Information" → "Object Storage Namespace"

**Bu değeri NOT ET.**

---

## 3. Customer Secret Keys (S3 API için)

Bu en kritik adım — Oracle'ın normal "API keys" S3-compatible çalışmaz. Ayrıca
"Customer Secret Keys" üretmek gerek.

### 3.1 Sağ üst kullanıcı simgesi → **My Profile**

### 3.2 Sol panel → **Customer Secret Keys** → **Generate Secret Key**

- **Name:** `warehouse-s3-key` (istediğin bir isim)
- **Generate** → bir popup açılır:
  - **Access Key:** kısa string (UI'da görünür, sonradan da göreceğin)
  - **Secret Key:** **uzun string — sadece BİR KEZ gösterilir, hemen kopyala!**

> ⚠️ Secret Key'i kapatırsan bir daha gösterilmez. Mutlaka kopyalayıp güvenli yere yapıştır.

---

## 4. Endpoint URL'ini Oluştur

Format:
```
https://<NAMESPACE>.compat.objectstorage.<REGION>.oraclecloud.com
```

Örnek: namespace `axdkmuabcdef`, region `eu-frankfurt-1` →
```
https://axdkmuabcdef.compat.objectstorage.eu-frankfurt-1.oraclecloud.com
```

---

## 5. Local Test Konfigürasyonu

`application-dev.properties` dosyasına (veya environment variables) ekle:

```properties
# === Oracle Cloud Object Storage ===
storage.provider=s3
storage.s3.endpoint=https://<NAMESPACE>.compat.objectstorage.eu-frankfurt-1.oraclecloud.com
storage.s3.bucket=warehouse-uploads
storage.s3.access-key=<CUSTOMER_ACCESS_KEY>
storage.s3.secret-key=<CUSTOMER_SECRET_KEY>
storage.s3.region=eu-frankfurt-1
storage.s3.path-style=true
storage.s3.public-acl-supported=false
```

> **`public-acl-supported=false` ÖNEMLİ.** Oracle ACL'i reddediyor. False olduğunda
> kodumuz site asset upload'unda ACL set'lemez, sadece dosya yüklenir. Public erişim
> için ayrı bir adım var — adım 7'ye bak.

Veya environment variable ile:
```bash
export STORAGE_PROVIDER=s3
export STORAGE_S3_ENDPOINT=https://...
export STORAGE_S3_BUCKET=warehouse-uploads
export STORAGE_S3_ACCESS_KEY=...
export STORAGE_S3_SECRET_KEY=...
export STORAGE_S3_REGION=eu-frankfurt-1
export STORAGE_S3_PATH_STYLE=true
export STORAGE_S3_PUBLIC_ACL_SUPPORTED=false
```

---

## 6. Smoke Test

### 6.1 Backend'i başlat

```bash
mvn spring-boot:run
```

Loglarda görmen gereken:
```
S3 storage hazır: endpoint=https://...oraclecloud.com, bucket=warehouse-uploads, pathStyle=true
```

Hata olursa:
- `403 Forbidden` → Access Key veya Secret Key yanlış
- `404 NoSuchBucket` → Bucket adı veya region yanlış
- `UnknownHostException` → Endpoint URL yanlış (namespace ve region kontrol et)

### 6.2 MC CLI ile dışarıdan doğrula (opsiyonel)

```bash
# MinIO mc CLI ile Oracle bucket'ına bağlan
docker run --rm -it minio/mc:latest sh

# İçeride:
mc alias set oracle https://<NAMESPACE>.compat.objectstorage.eu-frankfurt-1.oraclecloud.com \
   <ACCESS_KEY> <SECRET_KEY>
mc ls oracle/warehouse-uploads
# Boş bucket → çıktı yok ama hata da olmaz
```

### 6.3 Admin panelden dosya yükle

1. Admin → Site Ayarları → Logo Yükle
2. Backend loglarında: `S3 asset stored: assets/logo-abc.png (15234 bytes)`
3. Oracle Console → Bucket → Objects sekmesi → `assets/logo-abc.png` görünmeli

### 6.4 Ürün görseli yükle

1. Admin → Ürün düzenle → Görsel ekle
2. Loglar: `S3 image stored: products/42/abc.webp (45KB main, 12KB thumb)`
3. Bucket'ta `products/42/abc.webp` + `_thumb.webp`

---

## 7. Public URL Erişimi (logo, banner için)

Oracle ACL desteklemediği için 3 seçenek var:

### Option A: Bucket Visibility = Public (en basit)

> ⚠️ Bu bucket'taki **tüm** objeler public read olur — sadece public-only bir bucket
> oluşturup oraya assets yazmayı öneririm.

1. Console → Buckets → bucket adı → "Edit Visibility"
2. **Public** seç → save
3. URL formatı:
   ```
   https://objectstorage.eu-frankfurt-1.oraclecloud.com/n/<NAMESPACE>/b/warehouse-uploads/o/assets/logo-abc.png
   ```

### Option B: Ayrı bir "warehouse-public" bucket (önerilen)

1. Yeni bucket oluştur: `warehouse-public` — visibility **Public**
2. `warehouse-uploads` private kalır (ürün görselleri, kullanıcı yüklemeleri)
3. Backend'i 2 bucket olarak yapılandır — şu an tek bucket destekliyoruz, ileride
   `storage.s3.public-bucket` ayrı bir property eklenebilir
4. Şimdilik en kolay: tek bucket public + custom domain ile cache

### Option C: Pre-Authenticated Request (PAR) — özel obje için

PAR = bir objenin geçici imzalı URL'i. Site asset'leri için zaman aşımı yok yapabilirsin.

1. Console → Bucket → Object → "More Actions" → "Create Pre-Authenticated Request"
2. Type: **Object** (sadece bu obje için)
3. Access: **Permit object reads**
4. Expiration: 10 yıl sonrası
5. Create → URL kopyala

Bu method script edilemez frontendde — tek tek obje için manuel. Logo gibi 1-2 asset için OK.

> **Pratik öneri:** Option B (ayrı public bucket) en yaygın. Asset yüklediğinde
> `STORAGE_S3_PUBLIC_BASE_URL` settings'i public bucket URL'ine pointing yap.

---

## 8. Public Asset URL'i — `STORAGE_S3_PUBLIC_BASE_URL`

Bucket'ı public yaptıktan sonra, frontendde görsel URL'i için:

```properties
storage.s3.public-base-url=https://objectstorage.eu-frankfurt-1.oraclecloud.com/n/<NAMESPACE>/b/warehouse-uploads/o
```

> Sonunda **`/o`** önemli — Oracle objesinin URL formatı `/n/NS/b/BUCKET/o/KEY` şeklinde.

Backend `publicUrl(key)` çağrısı şuna dönüşür:
```
https://objectstorage.eu-frankfurt-1.oraclecloud.com/n/.../b/warehouse-uploads/o/assets/logo-abc.png
```

---

## 9. Maliyet İzleme

Free tier 20 GB + 10 TB egress / ay'ın altında kalmak için:

### 9.1 Limit Alert

Console → **Limits, Quotas and Usage** → "Object Storage" → Usage trafiğini gör.

### 9.2 Cost Analysis
**Cost Analysis** → "Object Storage" grubuna filtrele → aylık tüketim chart.

### 9.3 Budget Alert
**Budgets** → "Create Budget":
- Amount: $1
- Alert at: 50% (yarısına gelirse uyarı mail)

20 GB'a varmıyorsan ve 10 TB egress'i aşmıyorsan, kart **kesinlikle charge edilmez**.

---

## 10. Production Deploy (Railway)

1. Railway dashboard → Project → Variables sekmesi
2. Aynı env'leri ekle:
   ```
   STORAGE_PROVIDER=s3
   STORAGE_S3_ENDPOINT=https://...oraclecloud.com
   STORAGE_S3_BUCKET=warehouse-uploads
   STORAGE_S3_ACCESS_KEY=...
   STORAGE_S3_SECRET_KEY=...
   STORAGE_S3_REGION=eu-frankfurt-1
   STORAGE_S3_PATH_STYLE=true
   STORAGE_S3_PUBLIC_ACL_SUPPORTED=false
   STORAGE_S3_PUBLIC_BASE_URL=https://objectstorage.eu-frankfurt-1.oraclecloud.com/n/<NS>/b/warehouse-uploads/o
   ```
3. Railway otomatik redeploy → backend yeni env ile başlar
4. Backend loglarında: `S3 storage hazır: ...oraclecloud.com`

---

## 11. Cheat Sheet

```bash
# Bucket listele
mc ls oracle/

# Bucket içeriği
mc ls --recursive oracle/warehouse-uploads/

# Dosya yükle (test)
echo "test" > /tmp/test.txt
mc cp /tmp/test.txt oracle/warehouse-uploads/test.txt

# Dosya indir
mc cat oracle/warehouse-uploads/test.txt

# Bucket boyutu
mc du oracle/warehouse-uploads/

# Tüm bucket'ı yedekle (B2 ücretsiz tier'a)
mc mirror oracle/warehouse-uploads b2/warehouse-backup
```

---

## 12. Hata Sözlüğü

| Hata | Sebep | Çözüm |
|---|---|---|
| `AuthorizationFailureException` | Access/Secret key yanlış veya tenancy farklı | Customer Secret Keys'i yeniden oluştur |
| `NotImplemented: PutObjectAcl` | ACL public_read denedi | `STORAGE_S3_PUBLIC_ACL_SUPPORTED=false` |
| `RequestSignatureDoesNotMatch` | Clock skew >15dk veya `path-style=false` | `STORAGE_S3_PATH_STYLE=true` |
| `UnknownHostException ...oraclecloud.com` | Region yanlış veya namespace boş | Tenancy → Object Storage Namespace |
| `NoSuchBucket` | Bucket adı yanlış veya farklı region | Console'da bucket sayfasında region kontrol |

---

## 13. Yapacağın Şey Şimdi

1. ✅ Hesap aç (yaptın)
2. **Şimdi:** Adım 1-3 → bucket + customer keys
3. Adım 5 → backend'e env'leri yaz
4. Adım 6 → backend restart + log doğrula
5. Adım 6.3 → admin panelden logo yükle, Oracle Console'da görmeyi test et

Sorun çıkarsa: adım 6'daki "Hata olursa" bölümüne veya §12 hata sözlüğüne bak.
