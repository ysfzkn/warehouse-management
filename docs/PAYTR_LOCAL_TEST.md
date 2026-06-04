# PayTR Lokal Test Rehberi

PayTR iFrame API entegrasyonunu lokalde uçtan uca test etmek için adım adım rehber.

---

## 0. Mimari Özet

PayTR **iFrame API** kullanıyoruz (kart bilgileri bizim sunucumuza dokunmaz — PCI SAQ-A):

```
┌─────────┐  1. POST /odeme/api/get-token (HMAC-SHA256)
│ Backend ├─────────────────────────────────────────────┐
└─────────┘                                              ▼
                                                  ┌──────────┐
                                                  │  PayTR   │
                                                  │  iFrame  │
                                                  └─────┬────┘
                                                        │ token
┌─────────┐  2. iFrame URL'i client'a HTML olarak       │
│ Browser │  ◄─────────────────────────────────────────┤
└────┬────┘                                              │
     │   3. iFrame içinde müşteri kartı girer            │
     ▼                                                   │
┌─────────────┐  4. ödeme sonucu                         │
│ paytr.com   ├─────────────────────────────────────────►│
│ secure page │                                          │
└──────┬──────┘                                          │
       │ 5. S2S POST /api/store/payment/callback/paytr/{configCode}
       │    (merchant_oid, status, total_amount, hash)
       ▼
┌─────────────┐  6. Verify hash + update order → respond "OK"
│ Backend     │                                         
└─────────────┘
```

**3 endpoint** sürecte:
- `POST /api/store/payment/initialize` — sipariş + idempotency-key
- `GET /api/store/payment/iframe/{token}` — frontend iframe'i embed eder
- `POST /api/store/payment/callback/paytr/{configCode}` — S2S notification (her dakika 24h retry)

---

## 1. PayTR Mağaza Hesabı

1. **https://www.paytr.com** üzerinden mağaza hesabı aç (TEST modu ücretsiz, kayıt anında verilir)
2. Panel → **Bilgi → API Bilgileri** sayfasından şunları kopyala:
   - `Mağaza No` (merchant_id)
   - `Mağaza Parolası` (merchant_key)
   - `Mağaza Gizli Anahtarı` (merchant_salt)
3. Panel → Bilgi → Mağaza Bilgileri sayfasından **Test Modu** aktif olduğunu doğrula

### Test kartları (PayTR sandbox)

| Tip | Kart No | Son Kullanma | CVV | 3D |
|-----|---------|--------------|-----|-----|
| Başarılı | 4355084355084358 | 12/26 | 000 | 123456 |
| Başarısız | 4355084355084366 | 12/26 | 000 | — |
| 3D başarısız | 4355084355084350 | 12/26 | 000 | herhangi |

> Tüm test kartları test_mode=1 iken kullanılır. Production geçişinde mağaza panelinden **canlı moda al** butonuna basılır.

---

## 2. Backend Yapılandırma

### Yöntem A — Admin Panel (önerilen)

1. `https://localhost:3000/admin` → giriş
2. **Ödeme** → **Ödeme Gateway'leri** → **+ Yeni Gateway**
3. Form doldur:
   - **Kod**: `paytr-test` (URL'de kullanılır)
   - **Provider**: `PAYTR`
   - **Aktif**: ✅
   - **Varsayılan**: ✅ (CREDIT_CARD için bu gateway'i seç)
   - **Sandbox**: ✅
   - **Merchant ID**: PayTR'den aldığın mağaza no
   - **API Key**: merchant_key
   - **Secret Key**: merchant_salt
   - **Callback URL**: `https://<NGROK_URL>/api/store/payment/callback/paytr/paytr-test`
     - Lokalde **mutlaka ngrok kullan** (aşağıda) — PayTR public HTTPS ister
   - **Max Installments**: 12
4. **extraConfig** (JSON tabında):
```json
{
  "merchant_ok_url": "https://<NGROK_URL>/odeme/sonuc?success=true",
  "merchant_fail_url": "https://<NGROK_URL>/odeme/sonuc?success=false",
  "timeout_limit": 30
}
```
5. **Kaydet**

### Yöntem B — Direkt SQL (hızlı dev)

```sql
INSERT INTO payment_gateway_configs (code, gateway_protocol, active, default_gateway,
  sandbox, merchant_id, api_key, secret_key, callback_url, max_installments, priority,
  extra_config, created_at, updated_at)
VALUES ('paytr-test', 'PAYTR', true, true, true,
  '<MERCHANT_ID>', '<MERCHANT_KEY>', '<MERCHANT_SALT>',
  'https://<NGROK_URL>/api/store/payment/callback/paytr/paytr-test',
  12, 0,
  '{"merchant_ok_url":"https://<NGROK_URL>/odeme/sonuc?success=true","merchant_fail_url":"https://<NGROK_URL>/odeme/sonuc?success=false","timeout_limit":30}'::jsonb,
  NOW(), NOW());
```

> **NOT:** Bizim PayTRProtocol absolute URL validasyonu yapıyor; `/odeme/sonuc?...` gibi relative path girersen 400 + açıklayıcı hata mesajı döner.

---

## 3. ngrok ile Public Tunnel (Lokal Test Şart)

PayTR callback'i lokal IP'lere POST yapamaz. **ngrok** ile public HTTPS tunnel:

```bash
# Terminal 1: backend
mvn spring-boot:run    # localhost:8080

# Terminal 2: ngrok (HTTPS forward)
ngrok http 8080
```

Çıktıdan public URL'i al:
```
Forwarding   https://abc-123-456.ngrok-free.app -> http://localhost:8080
```

`<NGROK_URL>` = `abc-123-456.ngrok-free.app`. Gateway config'i bu URL ile güncelle.

> ngrok URL ücretsiz planda restart'ta değişir. Test seansı sonunda gateway config'i güncellemeyi unutma.

---

## 4. Test Sırası (E2E)

### 4.1 Smoke test — gateway aktivasyonu

```bash
# Admin login token al
TOKEN=$(curl -s -X POST http://localhost:8080/api/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"YOUR_PASS"}' | jq -r .token)

# Gateway aktif mi kontrol
curl -s http://localhost:8080/api/admin/payment-gateways \
  -H "Authorization: Bearer $TOKEN" | jq '.[] | {code, gatewayProtocol, active, defaultGateway}'
```

Beklenen:
```json
{"code":"paytr-test","gatewayProtocol":"PAYTR","active":true,"defaultGateway":true}
```

### 4.2 Müşteri checkout flow

1. `http://localhost:3000` → store
2. Bir ürünü sepete ekle
3. `/checkout` → adres + Mesafeli Satış + Ön Bilgilendirme onay
4. **Kredi/Banka Kartı** seç
5. "Siparişi Onayla ve Öde" → backend `POST /api/store/payment/initialize` çağrılır
6. **PayTR iframe açılır** — yukarıdaki test kartını gir
7. iframe içinde "Ödemeyi Tamamla" → PayTR kart doğrulama → callback
8. Browser `merchant_ok_url`'e redirect olur → `/odeme/sonuc?success=true`

Backend loglarında görmen gereken:
```
PayTR token obtained: merchantOid=ORD-..., token=abc12345...
PayTR notification received: configCode=paytr-test, merchant_oid=ORD-..., status=success
PayTR hash verified: merchantOid=ORD-..., status=success
Payment successful: txId=..., orderId=..., provider=paytr
```

### 4.3 Idempotency testi (PayTR retry simülasyonu)

Aynı callback'i 5 kez peş peşe gönder:

```bash
# Önceki PayTR call'undan hash + body'yi al, sonra:
for i in 1 2 3 4 5; do
  curl -s -X POST https://<NGROK>/api/store/payment/callback/paytr/paytr-test \
    -d "merchant_oid=ORD-XXX&status=success&total_amount=10000&hash=<HASH>"
  echo " — call $i"
done
```

Backend loglarında:
- 1. çağrı: "Payment successful" + event publish + email gönderim
- 2-5. çağrılar: "Idempotent callback hit (already SUCCESS)" — order tekrar UPDATE edilmiyor, event tekrar publish edilmiyor

> **Bu davranış PayTR retry'larında çift fatura/email/stok hareketi olmasını engeller.**

### 4.4 Hash doğrulama testi (güvenlik)

Bozuk hash ile callback gönder:

```bash
curl -s -X POST https://<NGROK>/api/store/payment/callback/paytr/paytr-test \
  -d "merchant_oid=ORD-XXX&status=success&total_amount=10000&hash=FAKE_HASH"
```

Beklenen:
- HTTP 200 + body "OK" (PayTR retry'lar durması için)
- Backend log: `SECURITY ALERT: PayTR callback hash verification FAILED`
- Order **PAID olmaz** — sahtekar engelleniyor

### 4.5 Refund testi

Admin panelden:
- `/admin/payments` → ilgili tx'i bul → "İade Et" butonu

Veya API ile:
```bash
curl -X POST http://localhost:8080/api/admin/payments/<TX_ID>/refund \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-ADMIN-SECURITY-CODE: 12345" \
  -d '{"amount":50.00, "reason":"Kismi iade testi"}'
```

PayTR loglarında:
```
PayTR refund OK: merchantOid=ORD-..., amount=50.00 TL
```

PayTR panelinde "İade Geçmişi" sekmesinden de doğrulayabilirsin.

---

## 5. Hata Senaryoları

### 5.1 Geçersiz merchant_oid
PayTR sadece **A-Z, 0-9** kabul eder, max 64 char. Siparişin order number'ı bir özel karakter içeriyorsa init başarısız olur. Çözüm: `OrderNumberGenerator` zaten alfanumerik üretir, ama dikkat.

### 5.2 Relative URL hatası
`callback_url` veya `merchant_ok_url` `https://` ile başlamıyorsa 400 + "absolute URL olmalı" mesajı dönülür. **Production'a geçerken her zaman https://api.siteniz.com/... formatında.**

### 5.3 Hash mismatch
Genelde nedeni:
- `merchant_salt` config'te yanlış girilmiş (PayTR panelindeki `merchant_salt` ile ham karşılaştırma yap)
- Kuruş hesabında hatası (1.50 TL → 150 değil, 150L olarak gönderilmeli)

Backend log:
```
PayTR HASH VERIFICATION FAILED! merchantOid=ORD-..., incoming=<X>, calculated=<Y>
```

`<X>` ve `<Y>`'i karşılaştır, hangi parametrede sorun var anla.

### 5.4 PayTR `failed_reason_msg`
PayTR red durumunda neden bilgisini callback'te `failed_reason_msg` ile gönderir. Loglarda görünür:
```
PayTR callback: merchantOid=..., status=failed, failCode=10, msg=Kart limiti yetersiz
```

### 5.5 Network down
PayTR API'sine erişilemiyorsa (örn. firewall) `initialize3DPayment` `PayTR token alinamadi: ...` döner. Şu an PayTR için Resilience4j retry **yok**, gerekirse `@Retry(name="paytr")` annotate edilebilir.

---

## 6. Production Geçişi Kontrol Listesi

- [ ] PayTR panelinde **Canlı Mod**'a geç (test_mode=0)
- [ ] Gateway config sandbox = false
- [ ] Callback URL **HTTPS + production domain** (`https://api.siteniz.com/api/store/payment/callback/paytr/paytr-main`)
- [ ] `merchant_ok_url` / `merchant_fail_url` production frontend URL'leri
- [ ] PayTR panel → **IP Kısıtlama** sekmesinde production server IP'sini ekle (S2S callback için)
- [ ] PayTR panel → **3D Secure Zorunlu** ayarı aktif (default true zaten)
- [ ] Bankalar arası installment ayarları (Garanti/Akbank/QNB vb. komisyon hesapları)
- [ ] Test 5+ siparişle gerçek kartla doğrula (küçük tutarlar, ardından iade)
- [ ] Refund flow gerçek kartla test edildi
- [ ] Idempotency davranışı yük testi altında doğrulandı (k6 ile 50 paralel callback)
- [ ] PayTR webhook IP whitelist (`193.192.59.0/24` ve benzeri PayTR S2S IP'leri) firewall'da allow

---

## 7. Production Ready Mi? — Final Audit

| Konu | Durum | Not |
|------|-------|-----|
| HMAC-SHA256 hash verify | ✅ | İlk işlem; mismatch'te işlem reddedilir |
| Idempotency (retry double-pay önleme) | ✅ | SUCCESS tx'e tekrar callback no-op döner |
| Token mapping (merchant_oid → tx.token) | ✅ | Controller'da otomatik mapping |
| Provider name dynamic (log + event) | ✅ | "iyzico" hardcoded değil artık |
| PROCESSING status set | ✅ | PayTR + NESTPAY + GVP için eklendi |
| Customer info (user_name/phone/address) | ✅ | PaymentInitRequest'ten haritalanıyor |
| Absolute URL validation | ✅ | Relative URL prod'da redirect bozar |
| merchant_oid format validation | ✅ | A-Z, 0-9, max 64 char |
| Amount overflow (long vs int) | ✅ | longValueExact() — 21M TL+ siparişlerde overflow yok |
| Refund API | ✅ | `POST /odeme/iade` implement edildi (full + kısmi) |
| PCI compliance | ✅ | Kart verisi sunucumuza dokunmuyor (SAQ-A) |
| Hash verification BEFORE processing | ✅ | Order asla hash invalidken PAID olmaz |
| Amount mismatch koruması | ✅ | result.paidPrice vs order.grandTotal kontrol |
| Late callback (timeout sonrası) | ✅ | Re-reservation deneme; başarısızsa auto-refund |
| Sensitive data sanitization (log) | ✅ | card/cvv/pan log'a yazılmaz |
| OK response (retry durdur) | ✅ | Her zaman 200 + "OK", hata olsa bile |
| Stack trace leak | ✅ | GlobalExceptionHandler client'a generic mesaj döner |
| Resilience4j retry (PayTR API'ye) | ⚠️ | Eklenmemiş — ileride opsiyonel |
| Webhook IP whitelist | ⚠️ | PayTR'in S2S IP'lerini nginx'de allow et |
| Test coverage | ⚠️ | Hash verify unit test eklenebilir |

**Sonuç:** PayTR entegrasyonu **PRODUCTION-READY**. Kritik bug'ların hepsi kapatıldı. Resilience4j retry ve unit test ileride iyileştirme olarak yapılabilir; mevcut hali ile canlıya alınabilir.

---

## 8. Hızlı Komut Cheat Sheet

```bash
# Gateway listele
curl -s http://localhost:8080/api/admin/payment-gateways \
  -H "Authorization: Bearer $TOKEN" | jq

# PayTR test connection
curl -s -X POST http://localhost:8080/api/admin/payment-gateways/paytr-test/test \
  -H "Authorization: Bearer $TOKEN"

# Bir tx'in callback raw response'unu gör (debug)
curl -s http://localhost:8080/api/admin/payments/<TX_ID> \
  -H "Authorization: Bearer $TOKEN" | jq .rawResponse

# Manuel hash hesapla (Python ile)
python3 -c "
import hmac, hashlib, base64
merchant_id='123456'
user_ip='127.0.0.1'
merchant_oid='ORD123'
email='test@test.com'
amount=10000
basket='W1tcIlNpcGFyaXNcIixcIjEwMDAwXCIsMV1d'
no_inst='0'
max_inst='12'
currency='TL'
test_mode='1'
salt='MY_SALT'
key=b'MY_KEY'
hash_str = merchant_id+user_ip+merchant_oid+email+str(amount)+basket+no_inst+max_inst+currency+test_mode+salt
print(base64.b64encode(hmac.new(key, hash_str.encode(), hashlib.sha256).digest()).decode())
"
```
