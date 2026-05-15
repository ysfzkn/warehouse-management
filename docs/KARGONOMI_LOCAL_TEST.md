# Kargonomi Lokal Test Rehberi

Bu doküman lokalde Kargonomi entegrasyonunu uçtan uca test etmek için adımları içerir.

---

## 0. Ön Hazırlık — Kargonomi Hesabı

1. **https://www.kargonomi.com.tr** üzerinden hesap aç (ticari)
2. Panel → Ayarlar → **API Anahtarları** sekmesinden:
   - `API Token` (Bearer)
   - `X-App-Key` (partner identifier)
   kopyala
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

Beklenen response: `{"credit": 47.50}` (TL).

Hata durumları:
- `401 Unauthorized` → token/app-key yanlış
- `null credit` → KargonomiCargoProvider.isEnabled() false dönüyor (settings boş)

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

```bash
curl -s -X POST http://localhost:8080/api/admin/cargo/webhook/register \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-ADMIN-SECURITY-CODE: 12345" \
  -H "Content-Type: application/json" \
  -d '{
    "callbackUrl": "https://abc123.ngrok-free.app/api/admin/cargo/webhook/kargonomi",
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
curl -X POST https://abc123.ngrok-free.app/api/admin/cargo/webhook/kargonomi \
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

- [ ] `kargonomi_api_token` ve `kargonomi_app_key` env'den geliyor (DB'de plain text TUTMA, encrypt et veya Railway secret kullan)
- [ ] `kargonomi_webhook_secret` üretildi (`openssl rand -hex 32`) ve hem Kargonomi panelinde hem bizim setting'de aynı
- [ ] Webhook URL `https://api.siteniz.com/api/admin/cargo/webhook/kargonomi` (public, HTTPS zorunlu)
- [ ] `cargo_api_auto_create=true` (otomatik gönderi oluşumu aktif)
- [ ] `kargonomi_warehouse_id` doğru ID ile dolu
- [ ] Yük testi: 10 paralel sipariş → tüm kargo gönderileri oluşmalı, çakışma yok (ShedLock devrede)
- [ ] CargoTrackingJob loglarda her 30 dk çalışıyor
- [ ] Backup: en az 1 manuel oluşturulmuş kargo etiketi PDF'i indirildi ve doğrulandı

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
