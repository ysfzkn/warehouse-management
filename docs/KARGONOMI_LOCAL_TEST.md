# Kargonomi Lokal Test Rehberi

Bu doküman lokalde Kargonomi entegrasyonunu uçtan uca test etmek için adımları içerir.

---

## 0.1 Postman Koleksiyonu

`docs/postman/` altinda Kargonomi'nin 19 ucunun tamamini iceren bir koleksiyon var:

1. Postman > Import > `Kargonomi-API.postman_collection.json` ve
   `Kargonomi-API.postman_environment.json`
2. Sag ustten **Kargonomi** environment'ini sec
3. Environment'taki `token` alanina API token'ini yapistir
   (**collection'a degil environment'a** — collection repoda duruyor)
4. `1 - Hesap > Bakiye Sorgula` ile basla

Klasorler numarali ve zincirleme calisiyor: il/ilce istekleri `state_id`/`city_id`
degiskenlerini, gonderi olusturma `shipment_id`'yi otomatik dolduruyor.

> **Para harcayan tek adim:** `3 - Gonderi Olusturma > Tasiyici Sec (ONAYLA)`.
> Taslak olusturma ve fiyat karsilastirma ucretsiz; taslagi `Taslak Sil` ile temizle.
> Kargonomi'nin ayri test ortami yok, her sey gercek hesaba gidiyor.

**JSON yerine HTML donerse:** istek API'ye degil `www.kargonomi.com.tr` tanitim
sitesine dusmustur. Iki sebebi olur — `Accept: application/json` basligi yoktur
(Kargonomi Laravel; bu baslik olmadan hata durumunda JSON degil yonlendirme doner)
ya da govde dogrulamadan gecmemistir (ornegin `webhook_url` hala ornek deger).
Koleksiyon artik Accept basligini her istege kendisi ekliyor ve HTML gelirse
"Yanit JSON" testi kirmizi yanip konsola gercek adresi ve yanitin ilk 300 karakterini
yaziyor. Kendi curl'unde de `--header 'Accept: application/json'` kullan —
dokumandaki her ornek boyle.

---

## 0. Ön Hazırlık — Kargonomi Hesabı

1. **https://www.kargonomi.com.tr** üzerinden hesap aç (ticari)
2. Panel → Ayarlar → **API Anahtarları** sekmesinden:
   - `API Token` (Bearer) — **zorunlu olan tek kimlik bilgisi budur**
   - `X-App-Key` (partner identifier) — **opsiyonel.** Resmî API dokümanında
     geçmiyor; hesabına böyle bir anahtar verilmediyse boş bırak, entegrasyon
     yine çalışır (boşken bu header hiç gönderilmez).

   > Müşteri kodu / müşteri numarası API token'ı **değildir**. Panelde API
   > anahtarı görmüyorsan Kargonomi'den API erişimi talep etmen gerekir.
3. Hesabına **bakiye yükle** (test gönderileri için ~50-100 TL yeterli)
4. Panel → Depolar → en az 1 depo oluştur. Bu depo ID'sini panelden al
   (veya `POST /warehouses` ile API'den oluşturup ID'yi otomatik al — adım 4'e bak)

> **Not:** Kargonomi'nin ayrı bir sandbox/test ortamı yoktur — direkt prod
> API'sine bağlanılır. Bu yüzden test gönderileri **gerçek bakiyeden düşer**.
> Gönderi olusturduktan sonra hemen iptal edersen ücretin çoğu iade edilir.

---

## 1. Backend'i Hazırla

### 1.1 Admin panelden settings doldur

`/admin/cargo-providers` veya doğrudan `site_settings` tablosundan:

```sql
UPDATE site_settings SET setting_value = 'true'        WHERE setting_key = 'cargo_api_enabled';
UPDATE site_settings SET setting_value = 'KARGONOMI'   WHERE setting_key = 'cargo_api_provider';
UPDATE site_settings SET setting_value = 'true'        WHERE setting_key = 'cargo_api_auto_create';
UPDATE site_settings SET setting_value = '<TOKEN>'     WHERE setting_key = 'kargonomi_api_token';
-- app_key opsiyonel; hesabinda yoksa bu satiri atla
UPDATE site_settings SET setting_value = '<APP_KEY>'   WHERE setting_key = 'kargonomi_app_key';
UPDATE site_settings SET setting_value = '<WAREHOUSE_ID>' WHERE setting_key = 'kargonomi_warehouse_id';
UPDATE site_settings SET setting_value = '<RANDOM_HMAC_SECRET>' WHERE setting_key = 'kargonomi_webhook_secret';
```

> HMAC secret üretimi: `openssl rand -hex 32`

### 1.2 Spring Boot'u başlat

```bash
mvn spring-boot:run
# veya
mvn clean package -DskipTests && java -jar target/warehouse-management-1.0.0.jar
```

Loglarda görmeli olduğun:
```
KargonomiCargoProvider hazır. Token: 64 char, AppKey: 32 char.
```

---

## 2. Bakiye Kontrolü (En Hızlı Smoke Test)

JWT admin token al → bakiyeyi sorgula:

```bash
# 1) Admin login
TOKEN=$(curl -s -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"YOUR_ADMIN_PASS"}' | jq -r .token)

# 2) Bakiye sorgu
curl -s http://localhost:8080/api/admin/cargo/balance \
  -H "Authorization: Bearer $TOKEN" | jq
```

Beklenen response: `{"balance": 47.50, "balanceState": "OK", ...}`.

`balanceState` ne diyorsa odur:
- `OK` → bakiye okundu
- `NOT_REPORTED` → **token çalışıyor** ama Kargonomi bakiye bildirmiyor; hesaba
  bakiye yüklenmemiş demektir. Bakiyesiz gönderi oluşturulamaz.
- `UNREACHABLE` → Kargonomi'ye ulaşılamadı ya da token reddedildi (401)
- `UNSUPPORTED` → entegrasyon kapalı (`cargo_api_enabled=false`) ya da sağlayıcı MOCK

Ham API'yi doğrudan denemek istersen (token'ı kendi terminalinde kullan):

```bash
curl -s -H "Authorization: Bearer <KARGONOMI_TOKEN>"   https://app.kargonomi.com.tr/api/v1/user/credit
```

`401` = token yanlış. `{"data":{"credit":null}}` = token doğru, bakiye yok.

---

## 3. Şehir/İlçe Lookup Testi (Geo Cache Warm-up)

```bash
# Internal endpoint (örnek; varsa /api/admin/cargo/geo/states gibi)
# Veya doğrudan KargonomiGeoLookupService unit testte:

# Postman/curl ile:
curl -s http://localhost:8080/api/admin/cargo/balance \
  -H "Authorization: Bearer $TOKEN"
# Cache ilk istekte doldurulur. Backend loglarında:
# "[Kargonomi Geo] 81 il cache'lendi"
# "[Kargonomi Geo] İstanbul için 39 ilçe cache'lendi" (city seçiminde)
```

---

## 4. Depo Kaydı (İlk Kurulum İçin Bir Kez)

Panelden manuel oluşturmadıysan, API ile:

```bash
curl -s -X POST http://localhost:8080/api/admin/cargo/warehouses \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-ADMIN-SECURITY-CODE: 12345" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Ana Depo",
    "main": true,
    "contactName": "Ali Veli",
    "contactPhone": "05551234567",
    "address": "Test Mah. Test Sok. No:1 Kat:2",
    "city": "İstanbul",
    "district": "Kadıköy",
    "taxNumber": "1234567890"
  }' | jq
```

Beklenen: `{"warehouseId": 12345, "message": "Depo Kargonomi'ye kaydedildi..."}`.

ID'yi `kargonomi_warehouse_id` setting'ine yaz.

---

## 5. Sipariş + Otomatik Kargo Oluşturma (E2E)

### 5.1 Test ürünü hazır mı?
- Bir test ürünü oluştur (admin panel veya seed)
- Stok ekle (en az 1 adet)
- Aktif et

### 5.2 Misafir checkout ile sipariş ver

`/checkout`'a git, sepete ürün ekle, kapıda ödeme veya havale yöntemiyle ilerle
(Iyzico kart sandbox gerektirir, havale en hızlı yol).

Sipariş PAID veya PREPARING durumuna geçtiğinde **otomatik kargo gönderi
oluşturulur** (eğer `cargo_api_auto_create=true`):

Loglarda:
```
[Kargonomi] POST /shipments — orderNumber=SIP-2026-001
[Kargonomi] Draft shipment yaratıldı: kargonomi_id=98765
[Kargonomi] confirm-shipping-price: provider_id=-1 (auto)
[Kargonomi] Order SIP-2026-001 → Kargonomi shipment 98765 OK
```

### 5.3 Sipariş detay (admin)

```bash
curl -s http://localhost:8080/api/admin/orders/SIP-2026-001 \
  -H "Authorization: Bearer $TOKEN" | jq .cargoProviderShipmentId
```

`98765` döner — Kargonomi'deki shipment ID.

### 5.4 Etiket / barkod indir

```bash
curl -s -o label.pdf http://localhost:8080/api/admin/cargo/orders/<ORDER_ID>/label \
  -H "Authorization: Bearer $TOKEN"

open label.pdf   # macOS
start label.pdf  # Windows
```

PDF veya görsel barcode dönmesi gerekir.

### 5.5 Manuel iptal

```bash
# Sipariş iptal edilince Kargonomi'ye cancel request gider
curl -s -X PUT http://localhost:8080/api/admin/orders/<ORDER_ID>/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status":"CANCELLED","reason":"Test cancellation"}'
```

---

## 6. Webhook Lokal Test (ngrok ile)

Kargonomi webhook'ları için **public URL** gerekir. Lokal için **ngrok**:

```bash
# Terminal 1: backend ayakta (localhost:8080)
mvn spring-boot:run

# Terminal 2: ngrok ile public tunnel
ngrok http 8080
# Çıktıdan public URL'i al: https://abc123.ngrok-free.app
```

### 6.1 Webhook kaydı

> **Panelden:** Ayarlar > Kargo API bolumunun altinda **Kargonomi Webhook Kaydi**
> paneli var. Secret'i oradan uretip (Uret butonu), ayarlari kaydedip,
> "Kargonomi'ye Kaydet" diyebilirsin. Asagidaki curl ayni isi yapar.
>
> Secret'i once **kaydetmen** sart: Kargonomi'ye gonderilen anahtarla bizim
> dogrulamada kullandigimiz ayni olmali.
>
> **Secret'i kim uretiyor — acik soru.** Kargonomi dokumaninda webhook olusturma
> parametreleri `name`, `url`, `event_type`, `is_active`; **`secret` yok.** Ama imza
> dogrulama bolumu bir `secret_key`'den bahsediyor. Yani anahtari Kargonomi'nin
> kendisi uretiyor olabilir. Kayit yanitinda `secret` / `secret_key` / `signature_key`
> alanlarindan biri donerse panel onu otomatik alana yazar ve seni Kaydet'e yonlendirir —
> **o an kaydetmezsen anahtar kaybolur**, kaydi silip yeniden acman gerekir. Hicbiri
> donmezse anahtarin nereden alinacagini Kargonomi'ye sor; o zamana kadar alici uc
> her bildirime 503 doner (503 yeniden denenir, ~35 dakika icinde 5 deneme).

```bash
curl -s -X POST http://localhost:8080/api/admin/cargo/webhook/register \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-ADMIN-SECURITY-CODE: 12345" \
  -H "Content-Type: application/json" \
  -d '{
    "callbackUrl": "https://abc123.ngrok-free.app/api/public/cargo/kargonomi/webhook",
    "secret": "your-hmac-secret-here"
  }' | jq
```

### 6.2 Kayıtlı webhook'ları listele

```bash
curl -s http://localhost:8080/api/admin/cargo/webhooks \
  -H "Authorization: Bearer $TOKEN" | jq
```

### 6.3 Manuel webhook tetiklemesi (simülasyon)

Kargonomi'den gerçek event beklemeden test için:

```bash
# Önce HMAC signature üret (Python örneği):
python3 -c "
import hmac, hashlib
secret = b'your-hmac-secret-here'
payload = b'{\"shipment_id\":98765,\"status\":\"webservice_shipment_started\",\"tracking_number\":\"TRK123\"}'
sig = hmac.new(secret, payload, hashlib.sha256).hexdigest()
print(sig)
"
# Sonuç: a1b2c3...

# Webhook'a POST et
curl -X POST https://abc123.ngrok-free.app/api/public/cargo/kargonomi/webhook \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Signature: <yukarıdaki sig>" \
  -d '{"shipment_id":98765,"status":"webservice_shipment_started","tracking_number":"TRK123"}'
```

Backend loglarında:
```
[Kargonomi Webhook] HMAC doğrulandı, shipment=98765, status=webservice_shipment_started
[Kargonomi Webhook] Order SIP-2026-001 → SHIPPED, tracking=TRK123
```

---

## 7. Reconciliation Check

Kargonomi tarafındaki son shipment'ları listele, bizim DB ile karşılaştır:

```bash
curl -s "http://localhost:8080/api/admin/cargo/shipments?page=1" \
  -H "Authorization: Bearer $TOKEN" | jq '.data[].id'
```

DB sorgusu:
```sql
SELECT order_number, cargo_provider_shipment_id, status
FROM orders
WHERE cargo_provider_shipment_id IS NOT NULL
ORDER BY created_at DESC LIMIT 20;
```

Set fark varsa: Kargonomi'de var ama bizde yok → manuel araştır.

---

## 8. CargoTrackingJob (5 dakikalık polling)

Webhook yerine (veya ek olarak) polling de çalışıyor:

```
@Scheduled(fixedRate = 30 * 60 * 1000, initialDelay = 3 * 60 * 1000)
@SchedulerLock(name = "cargoTracking", ...)
public void pollShippedOrders() { ... }
```

İlk çalıştırma uygulama başladıktan 3 dk sonra; sonraki her 30 dk'da bir. Lokalde
test için manuel tetiklemek istersen:

```bash
# Spring Boot actuator schedule trigger (admin only)
curl -X POST http://localhost:8080/actuator/scheduledtasks \
  -H "Authorization: Bearer $TOKEN"
# (Veya backend'i restart et — initialDelay=3min sonra çalışır)
```

---

## 9. Hata Senaryosu Testleri

### 9.1 Geçersiz token
Settings'te `kargonomi_api_token` boşalt → `Balance` endpoint `null` döner, `createShipment` "NOT_CONFIGURED" hatası verir.

### 9.2 Geçersiz şehir/ilçe
`/checkout`'ta "Bilinmeyen Şehir" girersen `GEO_NOT_FOUND` döner.

### 9.3 Network down (resilience4j retry test)
Internet'i kapat, sipariş ver → 3 retry sonrası `createShipmentFallback`
çağrılır → kuyruğa alınır, admin'e bildirilir.

### 9.4 Webhook HMAC mismatch
Yanlış secret ile webhook gönder → backend `401 Invalid signature` ile reddeder.
Log: `[Kargonomi Webhook] HMAC mismatch — reddedildi.`

---

## 10. Production'a Geçiş Öncesi Checklist

- [ ] `kargonomi_api_token` env'den geliyor (DB'de plain text TUTMA, encrypt et veya Railway secret kullan). `kargonomi_app_key` opsiyonel
- [ ] `kargonomi_webhook_secret` üretildi (`openssl rand -hex 32`) ve hem Kargonomi panelinde hem bizim setting'de aynı
- [ ] Webhook URL `https://api.siteniz.com/api/public/cargo/kargonomi/webhook` (public, HTTPS zorunlu)
- [ ] `cargo_api_auto_create=true` (otomatik gönderi oluşumu aktif)
- [ ] `kargonomi_warehouse_id` doğru ID ile dolu
- [ ] Yük testi: 10 paralel sipariş → tüm kargo gönderileri oluşmalı, çakışma yok (ShedLock devrede)
- [ ] CargoTrackingJob loglarda her 30 dk çalışıyor
- [ ] Backup: en az 1 manuel oluşturulmuş kargo etiketi PDF'i indirildi ve doğrulandı
- [ ] Kargolanacak ürünlerde ağırlık + en/boy/yükseklik dolu (desi bunlardan hesaplanıyor)
- [ ] Kendi başına taşınan ürünlerde `products.packages_per_unit` işaretli (mobilya, beyaz eşya)
- [ ] `cargo_max_desi_per_package` mağazaya uygun (varsayılan 30 desi)
- [ ] `/api/admin/cargo/outbox` boş — bekleyen ya da düşmüş gönderi yok

---

## 10.0 Canliya Cikis Kontrolu (tek tikla)

Ayarlar > Kargo API bolumunun altinda **Canliya Cikis Kontrolu** paneli var.
Tek tikta su dokuzu birden kontrol eder ve eksikleri "nasil duzeltilir" notuyla listeler:

entegrasyon acik mi - token + bakiye - webhook secret - Kargonomi'de webhook kaydi -
gonderici deposu / gonderici bilgileri - il listesi - kargo firmasi slug eslemeleri -
urun olculeri - otomatik gonderi olusturma

API'den de cagrilabilir:

```bash
curl -s http://localhost:8080/api/admin/cargo/readiness   -H "Authorization: Bearer $TOKEN" | jq
```

Kirmizi (FAIL) maddeler giderilmeden canliya cikilmamali; sari (WARN) maddeler calisir
ama olmasi gerekenden kotudur.

---

## 10.1 Kargo Tablolarını İzleme

Entegrasyon canlıdayken bakılacak üç yer:

| Ne | Nerede |
|---|---|
| Bir siparişin kargo hareketleri | `GET /api/admin/cargo/orders/{orderId}/events` — admin sipariş detayında da görünür |
| Oluşturulamamış gönderiler | `GET /api/admin/cargo/outbox` — `PENDING` yeniden denenecek, `ABANDONED` elle açılmalı |
| Ham webhook günlüğü | `cargo_webhook_deliveries` tablosu (30 gün saklanır) — imza/parse sorunlarını burada ayıklarsın |

```sql
-- Son gelen webhook'lar ve sonuçları
SELECT received_at, status, order_number, error_message
FROM cargo_webhook_deliveries ORDER BY received_at DESC LIMIT 20;

-- Takılmış gönderiler
SELECT order_number, attempts, next_attempt_at, last_error
FROM cargo_shipment_outbox WHERE status <> 'SUCCEEDED';
```

---

## 10.3 Iade Kargosu — Kargonomi'nin Cevabi

Kargonomi SSS'sine gore iade kargosu **ayni sartlar ve maliyetlerle** kullanilabiliyor,
ama tarif edilen iki yol da **panel uzerinden**:

1. Gonderiler sayfasindan mevcut bir kargoyu bulup ayarlar simgesinden iade olusturma
2. Gonderiler > yeni gonderi olustur ile iade kodu uretip musteriyle paylasma

SSS API'den nasil yapilacagini soylemiyor; `is_return` alani hala dogrulanmis degil.
Bu yuzden `cargo_return_label_enabled` kapali kaliyor. **Pratikte kaybimiz kucuk:**
iade onaylandiginda admin Kargonomi panelinden iade olusturup kodu musteriye
iletebilir; kod `return_requests.cargo_tracking_no` alanina elle girilebilir.

---

## 10.2 Kargonomi'den Cevap Bekleyen Iki Ayar

Bu iki ozellik yazildi ama **varsayilan kapali**; acmadan once Kargonomi'ye sorulmasi gerekenler var.

| Ayar | Ne yapar | Acmadan once sorulacak |
|---|---|---|
| `cargo_checkout_live_pricing` | Checkout'ta Kargonomi'nin gercek fiyatlarini gosterir ve o fiyati tahsil eder | Her fiyat sorgusu bir taslak gonderi acip siler. Taslak kotasi/ucreti var mi? |
| `cargo_return_label_enabled` | Iade onaylaninca musteri adina iade kargosu acar, takip no'yu mail atar | Iade gonderisi `is_return` alaniyla mi isaretleniyor? Dokumanda gecmiyor. |

Canli fiyat acilirsa: fiyatlar ilce + desi dilimi bazinda `cargo_price_cache_minutes`
(varsayilan 720 dk) boyunca onbellekte tutulur; musteri bilgisi taslaga yazilmaz,
yer tutucu alici kullanilir.

---

## 11. Hızlı Komut Cheat Sheet

```bash
# Bakiye
curl -s http://localhost:8080/api/admin/cargo/balance -H "Authorization: Bearer $TOKEN"

# Webhook listele
curl -s http://localhost:8080/api/admin/cargo/webhooks -H "Authorization: Bearer $TOKEN"

# Webhook sil
curl -X DELETE http://localhost:8080/api/admin/cargo/webhooks/<id> \
  -H "Authorization: Bearer $TOKEN" -H "X-ADMIN-SECURITY-CODE: 12345"

# Etiket indir
curl -s -o label.pdf http://localhost:8080/api/admin/cargo/orders/<orderId>/label \
  -H "Authorization: Bearer $TOKEN"

# Kargonomi shipments listele (reconciliation)
curl -s "http://localhost:8080/api/admin/cargo/shipments?page=1" \
  -H "Authorization: Bearer $TOKEN" | jq

# Manuel webhook test (HMAC ile)
python3 -c "import hmac,hashlib; print(hmac.new(b'SECRET',b'PAYLOAD',hashlib.sha256).hexdigest())"
```
