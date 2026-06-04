# Odeme Sistemleri Entegrasyon Dokumani

> Kapsamli teknik dokuman - PayTR, iyzico, Banka POS (NestPay/GVP), Havale/EFT, Kapida Odeme

---

## ICINDEKILER

1. [Mimari Genel Bakis](#1-mimari-genel-bakis)
2. [iyzico Entegrasyonu](#2-iyzico-entegrasyonu)
3. [PayTR Entegrasyonu](#3-paytr-entegrasyonu)
4. [Banka Sanal POS (NestPay / GVP)](#4-banka-sanal-pos)
5. [Havale/EFT](#5-havaleeft)
6. [Kapida Odeme](#6-kapida-odeme)
7. [Guvenlik Kontrollistesi](#7-guvenlik-kontrollistesi)
8. [Odeme Akis Diyagramlari](#8-odeme-akis-diyagramlari)
9. [Admin Yonetim Paneli](#9-admin-yonetim-paneli)
10. [Test ve Sandbox Rehberi](#10-test-ve-sandbox-rehberi)
11. [Hata Kodlari ve Troubleshooting](#11-hata-kodlari-ve-troubleshooting)
12. [Production Checklist](#12-production-checklist)

---

## 1. MIMARI GENEL BAKIS

### 1.1 Strategy Pattern

```
PaymentGateway (interface)
  |-- IyzicoPaymentGateway     -> iyzico Checkout Form
  |-- VirtualPosGateway         -> NestPay / GVP / PayTR
  |-- BankTransferGateway       -> Havale/EFT
  |-- DoorPaymentGateway        -> Kapida Odeme

PaymentGatewayFactory -> Provider enum'a gore dogru gateway'i dondurur
```

### 1.2 Odeme Akisi (Genel)

```
1. Musteri "Siparis Onayla" tiklar
2. CheckoutPage -> POST /api/store/payment/initialize
3. PaymentServiceImpl:
   a. Idempotency kontrolu (ayni siparis icin tekrar istek gelirse)
   b. Siparis dogrulama (PENDING_PAYMENT durumu)
   c. PaymentTransaction olusturma (INITIATED)
   d. Gateway.initializePayment() cagrisi
   e. Sonuca gore: token/HTML/iframe URL dondurme
4. Frontend: gateway tipine gore form/iframe render
5. Musteri odemeyi tamamlar
6. Gateway -> callback endpoint'e bildirim (server-to-server)
7. PaymentServiceImpl.handlePaymentCallback():
   a. Hash dogrulama
   b. Tutar kontrolu (1 TL tolerans)
   c. Basarili: Order->PAID, Sepet temizle
   d. Basarisiz: Order->CANCELLED, Stok serbest birak
```

### 1.3 Dosya Yapisi

```
Backend:
  service/payment/
    PaymentGateway.java              -- Interface
    PaymentGatewayFactory.java       -- Factory
    IyzicoPaymentGateway.java        -- iyzico
    VirtualPosGateway.java           -- Sanal POS (delege)
    BankTransferGateway.java         -- Havale
    DoorPaymentGateway.java          -- Kapida
    protocol/
      PayTRProtocol.java             -- PayTR iFrame API
      NestPayProtocol.java           -- NestPay 3D Pay Hosting
      GvpProtocol.java               -- Garanti BBVA Virtual POS
  service/impl/
    PaymentServiceImpl.java          -- Orkestrasyon
    PaymentConfigServiceImpl.java    -- DB + Properties config

Frontend:
  pages/store/CheckoutPage.js        -- Odeme sayfasi
  pages/store/PaymentResultPage.js   -- Sonuc sayfasi
  pages/AdminPaymentGateways.js      -- Admin gateway yonetimi
```

---

## 2. IYZICO ENTEGRASYONU

### 2.1 Entegrasyon Yontemi: Checkout Form

iyzico Checkout Form, kart bilgilerinin sizin sunucunuza ulasmadigi (PCI SAQ-A) guvenli bir yontemdir.

### 2.2 Akis

```
1. Backend: CreateCheckoutFormInitializeRequest olustur
2. Backend -> iyzico API: POST /payment/iyzipos/checkoutform/initialize/auth/ecom
3. iyzico -> checkoutFormContent (HTML) + token doner
4. Frontend: HTML'i render et, musteri iyzico formunda odeme yapar
5. iyzico -> callbackUrl'e POST (server-to-server)
6. Backend: Token ile sonuc sorgula
   POST /payment/iyzipos/checkoutform/auth/ecom/detail
7. Sonuc islenr: PAID veya CANCELLED
```

### 2.3 Konfigruasyon

```properties
# application-dev.properties
app.payment.iyzico.api-key=sandbox-xxxxxxxxxxxxxxx
app.payment.iyzico.secret-key=sandbox-xxxxxxxxxxxxxxx
app.payment.iyzico.base-url=https://sandbox-api.iyzipay.com
app.payment.iyzico.callback-url=http://localhost:3000/store/odeme/callback
app.payment.iyzico.timeout-minutes=15
```

**Production:**
```properties
app.payment.iyzico.base-url=https://api.iyzipay.com
```

### 2.4 Onemli Parametreler

| Parametre | Aciklama |
|-----------|----------|
| `price` | Sepet toplami (kuruslu: 1.50) |
| `paidPrice` | Odenen tutar (taksit farki dahil) |
| `currency` | TRY, USD, EUR |
| `basketItems[].price` toplami | `price` ile esit OLMALI |
| `buyer.identityNumber` | TC Kimlik No (11 hane) |
| `forceThreeDS` | 1 = 3D Secure zorunlu |
| `callbackUrl` | Sonuc bildirim URL'i (HTTPS olmali) |

### 2.5 Java SDK Kullanimi

```xml
<!-- pom.xml -->
<dependency>
  <groupId>com.iyzipay</groupId>
  <artifactId>iyzipay-java</artifactId>
  <version>2.0.141</version>
</dependency>
```

```java
Options options = new Options();
options.setApiKey("sandbox-xxx");
options.setSecretKey("sandbox-xxx");
options.setBaseUrl("https://sandbox-api.iyzipay.com");

CreateCheckoutFormInitializeRequest request = new CreateCheckoutFormInitializeRequest();
request.setPrice(new BigDecimal("100.00"));
request.setPaidPrice(new BigDecimal("100.00"));
request.setCurrency(Currency.TRY.name());
request.setCallbackUrl("https://yourdomain.com/api/store/payment/callback");
// ... buyer, address, basketItems ekle

CheckoutFormInitialize form = CheckoutFormInitialize.create(request, options);
String htmlContent = form.getCheckoutFormContent();
String token = form.getToken();
```

### 2.6 Callback Dogrulama

```java
// Webhook imza dogrulama (X-IYZ-SIGNATURE-V3 header)
String expectedSignature = HMAC_SHA256(
    secretKey + iyziEventType + paymentId + paymentConversationId + status
);
// Hex cikti ile karsilastir
```

### 2.7 Iade/Iptal

```java
// Iade (kismi veya tam)
CreateRefundRequest refundRequest = new CreateRefundRequest();
refundRequest.setPaymentTransactionId("transactionId");
refundRequest.setPrice(new BigDecimal("50.00"));
Refund refund = Refund.create(refundRequest, options);

// Iptal (sadece ayni gun, tam tutar)
CreateCancelRequest cancelRequest = new CreateCancelRequest();
cancelRequest.setPaymentId("paymentId");
Cancel cancel = Cancel.create(cancelRequest, options);
```

---

## 3. PAYTR ENTEGRASYONU

### 3.1 Entegrasyon Yontemi: iFrame API

PayTR iFrame API, 2 adimli guvenli bir entegrasyondur. Kart bilgileri PayTR tarafinda islenir.

### 3.2 Akis

```
1. Backend: Token parametrelerini hazirla
2. Backend -> PayTR API: POST https://www.paytr.com/odeme/api/get-token
3. PayTR -> iframe_token doner
4. Frontend: <iframe src="https://www.paytr.com/odeme/guvenli/{TOKEN}">
5. Musteri iframe icinde odemeyi tamamlar
6. PayTR -> notify_url'e server-to-server POST gonderir
7. Backend: Hash dogrula, islem sonucunu isle
8. Backend: "OK" text donmeli (baska bir sey DONMEMELI)
9. Musteri: merchant_ok_url veya merchant_fail_url'e yonlendirilir
```

### 3.3 Konfigurasyon

```properties
# PaymentGatewayConfig tablosundaki alanlar:
merchantId=XXXXXX
apiKey=XXXXXXXXX          # merchant_key
secretKey=XXXXXXXX        # merchant_salt
callbackUrl=https://yourdomain.com/api/store/payment/callback/paytr/PAYTR_1
sandbox=true              # test_mode=1
```

**extraConfig JSON:**
```json
{
  "merchant_ok_url": "https://yourdomain.com/store/odeme/sonuc?status=success",
  "merchant_fail_url": "https://yourdomain.com/store/odeme/sonuc?status=fail"
}
```

### 3.4 Token Olusturma (HMAC-SHA256)

```java
// iFrame API Token
String hashStr = merchantId + userIp + merchantOid + email
    + paymentAmount + userBasket + noInstallment
    + maxInstallment + currency + testMode;
String paytrToken = Base64(HMAC_SHA256(hashStr + merchantSalt, merchantKey));
```

```java
// Callback Hash Dogrulama
String hashInput = merchantOid + merchantSalt + status + totalAmount;
String expectedHash = Base64(HMAC_SHA256(hashInput, merchantKey));
// Gelen hash ile karsilastir
```

### 3.5 Sepet Formati

```java
// user_basket = Base64 encoded JSON array
String basket = Base64.encode(
  "[" +
    "[\"Urun Adi\",\"18.00\",1]," +
    "[\"Kargo\",\"5.00\",1]" +
  "]"
);
```

### 3.6 Callback Parametreleri

| Parametre | Aciklama |
|-----------|----------|
| `merchant_oid` | Siparis numarasi |
| `status` | "success" veya "failed" |
| `total_amount` | Odenen tutar (kurusuz, x100) |
| `hash` | HMAC dogrulama token'i |
| `failed_reason_code` | Hata kodu (basarisizsa) |
| `failed_reason_msg` | Hata mesaji |
| `payment_type` | "card" veya "eft" |
| `installment_count` | Taksit sayisi |

**KRITIK:** Callback'e "OK" text donulmezse PayTR tekrar tekrar bildirim gonderir.

### 3.7 Iade

```java
// POST https://www.paytr.com/odeme/iade
String hashInput = merchantId + merchantOid + returnAmount + merchantSalt;
String paytrToken = Base64(HMAC_SHA256(hashInput, merchantKey));
```

---

## 4. BANKA SANAL POS

### 4.1 NestPay (3D Pay Hosting)

**Desteklenen Bankalar:** Is Bankasi, Akbank, Halkbank, TEB, DenizBank, ING, Anadolubank, Ziraat, Kuveyt Turk

**Akis:**
```
1. Backend: Hash hesapla, auto-submit HTML form olustur
2. Frontend: Formu otomatik submit et -> banka 3D sayfasi
3. Musteri SMS OTP girer
4. Banka -> callbackUrl'e POST
5. Backend: HASHPARAMS ile hash dogrula
6. mdStatus kontrolu (1=basarili, diger=basarisiz)
```

**Hash Algoritmasi (SHA-512):**
```
Input: clientId|oid|amount|okUrl|failUrl|TranType|Instalment|rnd|storeKey
Hash: Base64(SHA-512(input))
```

**Callback Hash Dogrulama:**
```
1. HASHPARAMS field'ini oku (ornek: "clientid:oid:mdStatus:...")
2. Her param icin degerini birlestir
3. Sonuna storeKey ekle
4. SHA-512 + Base64
5. HASHPARAMSVAL ile karsilastir
```

### 4.2 GVP (Garanti BBVA)

**Hash Algoritmasi (SHA-512 hex):**
```
securityData = SHA512(provisionPassword + pad(terminalId, 9))
hashData = SHA512(terminalId + orderId + amount + okUrl + failUrl
           + "sales" + installment + storeKey + securityData)
```

**Not:** GVP hex cikti kullanir (Base64 degil), NestPay'den farkli.

---

## 5. HAVALE/EFT

### 5.1 Akis

```
1. Musteri "Havale/EFT" secer, siparis onayla tiklar
2. Backend: Referans no olustur (HVL-ORDER123-7842)
3. Frontend: IBAN, banka adi, alici adi ve referans no goster
4. Musteri bankasindan havale yapar (aciklamaya referans no yazar)
5. Admin panel: Havale onay ekranindan islem onaylanir
6. Siparis -> PAID, ardsindan PREPARING
```

### 5.2 Konfigurasyon

```properties
app.payment.bank-transfer.bank-name=Garanti BBVA
app.payment.bank-transfer.iban=TR00 0000 0000 0000 0000 0000 00
app.payment.bank-transfer.account-holder=FIRMA ADI
app.payment.bank-transfer.deadline-hours=48
```

---

## 6. KAPIDA ODEME

### 6.1 Akis

```
1. Musteri "Kapida Odeme" secer
2. Backend: Otomatik SUCCESS, siparis PREPARING'e gecer
3. Kurye teslimat sirasinda odemeyi tahsil eder
4. Admin: Siparis durumunu DELIVERED yapar
```

**Not:** Kapida odemede siparis direkt PREPARING'e gecer (PAID degil).

---

## 7. GUVENLIK KONTROLLISTESI

### 7.1 Mevcut Guvenlik Onlemleri

| Onlem | Durum | Aciklama |
|-------|-------|----------|
| PCI SAQ-A | AKTIF | Kart bilgileri sunucumuza ulasmaz |
| Hash Dogrulama | AKTIF | Tum callback'ler hash ile dogrulanir |
| Idempotency | AKTIF | Ayni siparis icin tekrar odeme engellenir |
| Tutar Kontrolu | AKTIF | 1 TL toleransli tutar dogrulama |
| Token Routing | AKTIF | Siparis manipulasyonu onlenir |
| Kart Veri Sanitizasyonu | AKTIF | Kart/CVV bilgileri loglardan temizlenir |
| Admin Guvenlik Kodu | AKTIF | Hassas islemler icin ek dogrulama |
| Credential Maskeleme | AKTIF | API anahtarlari API yanitlarinda maskelenir |

### 7.2 Onerilen Ek Guvenlik Onlemleri

1. **Webhook IP Whitelist** - PayTR/iyzico IP adreslerini whitelist'e al
2. **Replay Attack Koruması** - Callback nonce/timestamp kontrolu
3. **DB Sifreleme** - Hassas alanlar icin at-rest encryption
4. **Rate Limiting** - Odeme endpoint'lerine rate limit
5. **Audit Log** - Tum odeme islemlerini detayli logla
6. **Timeout Enforcement** - Suresi gecmis callback'leri reddet

---

## 8. ODEME AKIS DIYAGRAMLARI

### 8.1 iyzico Checkout Form

```
Musteri         Frontend          Backend            iyzico
  |                |                 |                  |
  |--Siparis Onayla-->|              |                  |
  |                |--POST init----->|                  |
  |                |                 |--SDK create----->|
  |                |                 |<--HTML+token-----|
  |                |<--HTML form-----|                  |
  |--Kart bilgileri-->|              |                  |
  |                |------(iframe icinde)------->|      |
  |                |                 |<--callback POST--|
  |                |                 |--token sorgu---->|
  |                |                 |<--sonuc----------|
  |<--sonuc sayfasi-|<--redirect-----|                  |
```

### 8.2 PayTR iFrame

```
Musteri         Frontend          Backend            PayTR
  |                |                 |                  |
  |--Siparis Onayla-->|              |                  |
  |                |--POST init----->|                  |
  |                |                 |--get-token------>|
  |                |                 |<--iframe_token---|
  |                |<--iframe URL----|                  |
  |--Odeme bilgileri->(iframe)------>|                  |
  |                |                 |<--notify POST----|
  |                |                 |--"OK" dondur---->|
  |<--ok/fail URL----->|             |                  |
```

### 8.3 NestPay/GVP 3D Secure

```
Musteri         Frontend          Backend         Banka POS
  |                |                 |                 |
  |--Siparis Onayla-->|              |                 |
  |                |--POST init----->|                 |
  |                |                 |--hash hesapla---|
  |                |<--auto-submit---|                 |
  |                |------form POST---------------->|  |
  |<------3D Secure SMS sayfasi-----|                  |
  |--SMS OTP gir--->|               |                  |
  |                |                 |<--callback POST--|
  |                |                 |--hash dogrula---|
  |<--sonuc sayfasi-|<--redirect-----|                 |
```

---

## 9. ADMIN YONETIM PANELI

### 9.1 Odeme Gateway Yonetimi

Admin panelinden (`/admin/odeme-gecitleri`) su islemler yapilir:

- **Gateway Ekleme:** NestPay, GVP, PayTR, iyzico turleri
- **Aktif/Pasif:** Gateway'leri aktif/deaktif etme
- **Varsayilan Gateway:** Bir gateway'i varsayilan olarak atama
- **Sandbox Modu:** Test/production gecisi
- **Oncelik Sirasi:** Birden fazla gateway varsa oncelik
- **Credential Yonetimi:** merchantId, apiKey, secretKey vs.

### 9.2 Site Ayarlari

Admin panelinden (`/admin/ayarlar`) odeme yontemleri acilir/kapatilir:

- Kredi Karti ile Odeme (AKTIF/PASIF)
- Havale/EFT ile Odeme (AKTIF/PASIF)
- Kapida Nakit Odeme (AKTIF/PASIF)
- Kapida Kartla Odeme (AKTIF/PASIF)

### 9.3 Siparis Yonetimi

- Havale onaylama
- Siparis durumu guncelleme (gecis kurallarina uygun)
- Iade talebi islem
- Odeme detaylarini goruntuleme

---

## 10. TEST VE SANDBOX REHBERI

### 10.1 iyzico Sandbox

**Sandbox Kayit:** https://sandbox-merchant.iyzipay.com/auth/register

**Sandbox API URL:** `https://sandbox-api.iyzipay.com`

**Sandbox Credential:** Merchant Panel > Ayarlar > API Anahtarlari

**Test Kartlari (Basarili):**

| Kart Numarasi | Banka | Marka |
|---------------|-------|-------|
| 5528790000000008 | Halkbank | Mastercard |
| 5526080000000006 | Akbank | Mastercard |
| 4603450000000000 | Denizbank | Visa |
| 5400360000000003 | Garanti | Mastercard |
| 374427000000003 | Garanti | Amex |
| 4543590000000006 | Is Bankasi | Visa |
| 4157920000000002 | Vakifbank | Visa |
| 5451030000000000 | YKB | Mastercard |

**Test Kartlari (Hata Senaryolari):**

| Kart Numarasi | Hata |
|---------------|------|
| 4111111111111129 | Yetersiz bakiye |
| 4129111111111111 | Islem reddedildi |
| 4128111111111112 | Gecersiz islem |
| 4127111111111113 | Kayip kart |
| 4126111111111114 | Calinti kart |
| 4125111111111115 | Suresi gecmis kart |
| 4124111111111116 | Gecersiz CVV |

**3D Secure SMS OTP:** Sandbox ortaminda her zaman `123456`

**CVV/Son Kullanma:** Herhangi bir gecerli deger kullanilabilir.

### 10.2 PayTR Sandbox

**Test Modu:** `test_mode=1` parametresi ile aktif edilir
**Debug Modu:** `debug_on=1` detayli hata mesajlari icin

**Test Kartlari:**

| Kart Numarasi | SKT | CVV | Kart Sahibi |
|---------------|-----|-----|-------------|
| 4355 0843 5508 4358 | 12/30 | 000 | PAYTR TEST |
| 5406 6754 0667 5403 | 12/30 | 000 | PAYTR TEST |
| 9792 0303 9444 0796 | 12/30 | 000 | PAYTR TEST |

**Basarisiz Test:** `non3d_test_failed=1` parametresi ile test edilir.

**PayTR Test Araclari:**
- Hash Hesaplayici - Token olusturma dogrulamasi
- Servis Yanit Gozlem Araci - Callback izleme
- Postman Collection - Hazir API koleksiyonlari

### 10.3 Adim Adim Mock Test Yapimi

#### A. iyzico Sandbox Test

**Onkosullar:**
1. Sandbox hesabi olustur: https://sandbox-merchant.iyzipay.com/auth/register
2. API Key ve Secret Key'i al
3. `application-dev.properties` dosyasini guncelle

**Test Adimlari:**

```
Adim 1: Konfigurasyon
  - application-dev.properties:
    app.payment.iyzico.api-key=sandbox-xxxxxxx
    app.payment.iyzico.secret-key=sandbox-xxxxxxx
    app.payment.iyzico.base-url=https://sandbox-api.iyzipay.com
    app.payment.sandbox=true

Adim 2: Urun ekle ve sepete at
  - Admin panelden urun ekle (fiyat: 100 TL)
  - Store'dan sepete ekle

Adim 3: Checkout'a git
  - Adres sec veya yeni adres ekle
  - Odeme yontemi: "Kredi Karti" sec

Adim 4: iyzico formunda test karti kullan
  - Kart No: 5528790000000008
  - SKT: 12/2030
  - CVV: 123
  - Ad Soyad: Test User

Adim 5: Sonucu dogrula
  - Odeme basarili sayfasi gorunmeli
  - Admin panelde siparis PAID durumunda olmali
  - PaymentTransaction tablosunda SUCCESS kaydi olmali

Adim 6: Hata senaryosu test et
  - Kart No: 4111111111111129 (yetersiz bakiye)
  - Hata mesaji gorunmeli
  - Siparis CANCELLED olmali
```

#### B. PayTR Sandbox Test

```
Adim 1: Konfigurasyon
  - Admin Panel > Odeme Gecitleri > PayTR ekle
  - merchantId, merchantKey, merchantSalt gir
  - sandbox=true isaretle
  - callbackUrl: https://yourdomain.com/api/store/payment/callback/paytr/PAYTR_1

Adim 2: Sepet hazirla ve checkout'a git

Adim 3: PayTR iframe'de test karti kullan
  - Kart No: 4355084355084358
  - SKT: 12/30
  - CVV: 000

Adim 4: Sonucu dogrula
  - PayTR server-to-server callback gelecek
  - Backend loglarinda "OK" donusu gorunmeli
  - Siparis PAID durumunda olmali

Adim 5: Callback test (Postman ile)
  - POST /api/store/payment/callback/paytr/PAYTR_1
  - Body: merchant_oid, status=success, total_amount, hash
  - Hash: Base64(HMAC-SHA256(oid+salt+status+amount, key))
```

#### C. Havale/EFT Test

```
Adim 1: Admin panelden havale odemesini aktif et
Adim 2: Checkout'ta "Havale/EFT" sec
Adim 3: IBAN, referans no gosterilmeli
Adim 4: Admin panelden siparisi bul
Adim 5: "Havale Onayla" butonu ile onayla
Adim 6: Siparis PAID -> PREPARING gecmeli
```

#### D. Kapida Odeme Test

```
Adim 1: Admin panelden kapida odemeyi aktif et
Adim 2: Checkout'ta "Kapida Nakit" veya "Kapida Kart" sec
Adim 3: Siparis otomatik PREPARING olmali (PAID degil!)
Adim 4: Admin panelden SHIPPED -> DELIVERED yap
```

### 10.4 Postman Collection ile API Test

```json
// POST /api/store/payment/initialize
{
  "orderId": 1,
  "paymentMethod": "CREDIT_CARD",
  "installmentCount": 1
}

// Beklenen Yanit (iyzico):
{
  "paymentId": 123,
  "redirectUrl": null,
  "htmlContent": "<div id='iyzipay-checkout-form'...",
  "token": "xxx-xxx-xxx"
}

// Beklenen Yanit (PayTR):
{
  "paymentId": 124,
  "redirectUrl": null,
  "htmlContent": "<iframe src='https://www.paytr.com/odeme/guvenli/xxx'..."
}
```

---

## 11. HATA KODLARI VE TROUBLESHOOTING

### 11.1 PayTR Hata Kodlari

| Kod | Aciklama | Cozum |
|-----|----------|-------|
| 0 | Banka reddi (bakiye/limit) | Musteriye farkli kart onerisi |
| 1 | Dogrulama tamamlanmadi | Musteri telefon numarasini girmedi |
| 2 | Yanlis OTP | Musteri yanlis SMS sifresi girdi |
| 3 | Guvenlik kontrolu basarisiz | Bankayla iletisim |
| 6 | Musteri odemeyi terk etti | Musteri sayfayi kapatti veya sure doldu |
| 8 | Taksit uygun degil | Bu kart icin taksit secenegi yok |
| 9 | Kart tipi yetkisiz | Bu kart tipi icin yetkiniz yok |
| 10 | 3D Secure zorunlu | Non-3D denenemez |
| 11 | Dolandiricilik suphesi | PayTR destekle iletisim |
| 99 | Teknik hata | debug_on=1 ile detayli hata al |

### 11.2 iyzico Hata Kodlari

| Kod | Aciklama |
|-----|----------|
| 10005 | Islem reddedildi (Do not honour) |
| 10051 | Yetersiz bakiye |
| 10054 | Suresi gecmis kart |
| 10084 | Gecersiz CVV |
| 10215 | Gecersiz kart numarasi |
| 10217 | Debit kartlar 3DS gerektirir |
| 5062 | basketItems toplami price ile esit degil |

### 11.3 Sik Karsilasilan Sorunlar

| Sorun | Sebep | Cozum |
|-------|-------|-------|
| Callback gelmiyor | Firewall/erisilebilirlik | URL'in disaridan erisilebilir oldugundan emin ol |
| Hash dogrulama basarisiz | Yanlis key/salt | Credential'lari kontrol et, hash hesaplamasini dogrula |
| iyzico 5062 hatasi | Sepet toplami uyumsuz | basketItems.price toplami = price olmali |
| PayTR token alinamiyor | IP/credential hatasi | test_mode=1, debug_on=1 ile dene |
| 3D sayfasi acilmiyor | Yanlis callbackUrl | URL format ve erisilebilirlik kontrol |
| Siparis CANCELLED oluyor | Tutar uyumsuzlugu | Gonderilen ve odenen tutarlari karsilastir |

---

## 12. PRODUCTION CHECKLIST

### 12.1 Go-Live Oncesi

- [ ] Tum sandbox credential'lari production ile degistir
- [ ] `app.payment.sandbox=false` yap
- [ ] Gateway config'lerinde `sandbox=false` isaretle
- [ ] Callback URL'leri production domain ile guncelle (HTTPS zorunlu)
- [ ] iyzico base URL: `https://api.iyzipay.com`
- [ ] SSL sertifikasi gecerli ve guncel oldugundan emin ol
- [ ] Firewall kurallari: callback IP'lerine izin ver
- [ ] Rate limiting aktif et
- [ ] Hata loglama ve monitoring kur
- [ ] Odeme islem loglarini retention policy ile ayarla

### 12.2 Credential Guvenligi

- [ ] API anahtarlarini environment variable olarak sakla
- [ ] properties dosyasina hardcode ETME
- [ ] `.env` dosyasini `.gitignore`'a ekle
- [ ] Production credential'lari sadece DevOps ekibinde olsun
- [ ] Credential rotasyonu plani olustur

### 12.3 Izleme ve Alarm

- [ ] Basarisiz odeme orani monitoru (%5 ustu alarm)
- [ ] Callback gecikmesi monitoru (15dk ustu alarm)
- [ ] Gateway erisebilirlik kontrolu (health check)
- [ ] Gunluk odeme raporu
- [ ] Anormal islem deseni tespiti

### 12.4 Yasal Gereksinimler

- [ ] KVKK uyumlulugu (kisisel veri isleme)
- [ ] Mesafeli satis sozlesmesi
- [ ] On bilgilendirme formu
- [ ] Cayma hakki bilgilendirmesi
- [ ] Fatura entegrasyonu (e-fatura/e-arsiv)

---

## EK: KONFIGURASYON SABLONLARI

### application.properties (Production)

```properties
# Genel
app.payment.provider=IYZICO
app.payment.sandbox=false

# iyzico
app.payment.iyzico.api-key=${IYZICO_API_KEY}
app.payment.iyzico.secret-key=${IYZICO_SECRET_KEY}
app.payment.iyzico.base-url=https://api.iyzipay.com
app.payment.iyzico.callback-url=https://yourdomain.com/api/store/payment/callback
app.payment.iyzico.timeout-minutes=15

# Havale
app.payment.bank-transfer.bank-name=Garanti BBVA
app.payment.bank-transfer.iban=TR00 0000 0000 0000 0000 0000 00
app.payment.bank-transfer.account-holder=FIRMA ADI
app.payment.bank-transfer.deadline-hours=48
```

### PaymentGatewayConfig DB Kaydi (PayTR)

```sql
INSERT INTO payment_gateway_configs
(code, display_name, gateway_protocol, merchant_id, api_key, secret_key,
 callback_url, active, default_gateway, sandbox, priority, extra_config)
VALUES
('PAYTR_1', 'PayTR Sanal POS', 'PAYTR', 'MERCHANT_ID', 'MERCHANT_KEY', 'MERCHANT_SALT',
 'https://yourdomain.com/api/store/payment/callback/paytr/PAYTR_1',
 true, true, false, 1,
 '{"merchant_ok_url":"https://yourdomain.com/store/odeme/sonuc?status=success","merchant_fail_url":"https://yourdomain.com/store/odeme/sonuc?status=fail"}');
```

---

*Bu dokuman, projedeki mevcut odeme altyapisinin kapsamli analizidir. PayTR ve iyzico resmi dokumantasyonlarindan derlenmistir.*
*Son guncelleme: 2026-04-03*
