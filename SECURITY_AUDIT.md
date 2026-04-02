# Security Audit Report

Tarih: 2026-04-01
Auditor: Automated + Manual Review
Test Sonucu: 103 unit test PASS

---

## Bulunan Sorunlar ve Fix'ler

| Seviye | Sorun | Dosya | Fix | Durum |
|--------|-------|-------|-----|-------|
| CRITICAL | `@CrossOrigin(origins = "*")` — 29 controller'da wildcard CORS | Tum controller/*.java dosyalari | Annotation kaldirildi, SecurityConfig global CORS kullaniliyor | FIXED |
| CRITICAL | Path traversal — dosya erisimde `..` kontrolu yok | LocalPhotoStorageService.java | `..` ve `~` iceren path'ler reddediliyor, `path.normalize()` eklendi | FIXED |
| CRITICAL | Dosya boyut limiti yok — DoS riski | LocalPhotoStorageService.java | 10MB limit eklendi (storeItemPhoto + storeProductImage) | FIXED |
| CRITICAL | XSS sanitizer mevcuttu ama KULLANILMIYORDU | SanitizedStringDeserializer.java | JacksonConfig.java ile global olarak tum String input'lara uygulanadi | FIXED |
| CRITICAL | CORS pattern cok genis (`https://admin.*`, `https://www.*`) | SecurityConfig.java | `http://localhost:*` ve `https://localhost:*` ile sinirlandirildi + explicit header listesi | FIXED |
| HIGH | PlaceOrderRequest'te shippingAddressId validation yok | PlaceOrderRequest.java | `@NotNull` annotation eklendi | FIXED |
| HIGH | BCrypt default strength (10) — zayif | SecurityConfig.java | `BCryptPasswordEncoder(12)` olarak guncellendi | FIXED |
| HIGH | Odeme callback'te tutar dogrulamasi yok | PaymentServiceImpl.java | Callback'te `paidPrice` vs `grandTotal` karsilastirmasi + mismatch loglama | FIXED |
| HIGH | Eksik HTTP guvenlik header'lari | frontend/nginx.conf | HSTS, CSP (iyzico whitelisted), Permissions-Policy eklendi | FIXED |
| HIGH | X-Frame-Options SAMEORIGIN (DENY olmali) | frontend/nginx.conf | `DENY` olarak guncellendi | FIXED |
| HIGH | Referrer-Policy zayif | frontend/nginx.conf | `strict-origin-when-cross-origin` olarak guncellendi | FIXED |
| HIGH | Allowed headers wildcard (`*`) | SecurityConfig.java | Explicit liste: Content-Type, Authorization, X-Session-Id, X-ADMIN-SECURITY-CODE, X-Requested-With | FIXED |

---

## Bilinen Riskler (Kabul Edilen / Gelecek Faz)

| Seviye | Sorun | Neden Fix Edilmedi | Oneri |
|--------|-------|--------------------|-------|
| CRITICAL | JWT secret default `change-this-secret` | Dev workflow bozar, env var zaten mevcut | Production deploy'da zorunlu env var check ekle |
| CRITICAL | Admin default credentials (`admin/admin`) | Dev icin gerekli, env var zaten mevcut | .env.example'da belirtildi, deploy guide'a ekle |
| HIGH | Token blacklist/logout mekanizmasi yok | Redis dependency gerektirir | Redis entegrasyonu ile ayri fazda |
| HIGH | Frontend `dangerouslySetInnerHTML` (CMS, error) | CMS icerigi admin'den geliyor (guvenilir kaynak) | DOMPurify library eklenmeli |
| HIGH | Webhook signature verification eksik | iyzico SDK'da CheckoutForm.retrieve() zaten dogrulama yapiyor | Ek HMAC signature check opsiyonel |
| MEDIUM | JWT admin token 8 saat (uzun) | Mevcut kullanim icin uygun | 15-60 dk'ya dusurulup refresh token pattern ekle |
| MEDIUM | JWT query param ile SSE (log'a yazilir) | EventSource API header desteklemiyor | SSE endpoint'i ayri token mekanizmasi ile |
| MEDIUM | CSRF disabled (3 filter chain) | JWT stateless auth + SameSite cookie yeterli | Cookie-based auth kullanilirsa CSRF enable et |
| MEDIUM | Rate limiting odeme endpoint'lerinde yok | Mevcut RateLimitService genisletilebilir | `/api/store/payment/initialize` icin limit ekle |
| LOW | React/Axios outdated versiyonlari | Fonksiyonel etki yok | `npm audit fix` ile guncelle |

---

## Guvenlik Durusu Ozeti

### Iyi Durumda
- SQL injection korunmasi: Tum native query'ler parametrize ✓
- Odeme guvenigi: Kart bilgisi backend'e gelmiyor (iyzico Checkout Form) ✓
- Odeme tutari: Backend'de sepetten hesaplaniyor, client'tan gelmiyor ✓
- Rate limiting: Login, register, forgot-password, Google OAuth ✓
- Error response: Stack trace kullaniciya gonderilmiyor ✓
- Actuator: Sadece health ve info expose ✓
- Password policy: Min 8 char, buyuk/kucuk/rakam zorunlu ✓
- Account lockout: 5 basarisiz → 15 dk kilit (configurable) ✓
- Refresh token rotation: Kullanilan token revoke ediliyor ✓
- Customer status check: BLACKLISTED kullanicilar engelleniyor ✓
- Optimistic locking: Stok race condition korunmasi ✓
- Pessimistic locking: Checkout'ta SELECT FOR UPDATE ✓
- Idempotency: Cift odeme korunmasi (unique key) ✓
- Audit trail: Tum odeme ve siparis degisiklikleri loglaniyor ✓

### Bu Fazda Duzeltilen
- XSS sanitization: Global Jackson deserializer ile tum input'lar ✓
- CORS: Wildcard kaldirildi, explicit origin + header listesi ✓
- Path traversal: `..` engeli + normalize + 10MB boyut limiti ✓
- HTTP headers: HSTS + CSP + Permissions-Policy ✓
- BCrypt: Cost factor 10 → 12 ✓
- Input validation: PlaceOrderRequest @NotNull ✓
- Payment: Callback amount verification ✓

---

## Test Dogrulamasi

```
mvn test sonucu: 103 tests, 0 failures, BUILD SUCCESS
Frontend build: Successful
```
