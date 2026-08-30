# Güvenlik

Bu doküman uygulamanın güvenlik modelini, production'da **zorunlu** yapılandırmayı ve
bilinçli olarak ertelenen maddeleri anlatır.

---

## 1. Deploy öncesi zorunlu adımlar

Aşağıdakiler ayarlanmadan production'a çıkmayın. Hiçbiri uygulamayı boot ederken
düşürmez (site ayakta kalır), ama eksik olan her biri bir korumayı devre dışı bırakır ve
başlangıçta `ERROR` seviyesinde log basar.

| Değişken | Neden zorunlu |
|---|---|
| `JWT_SECRET` | 32+ karakter, rastgele. Zayıf/varsayılan değerde prod profili **boot etmez**. `openssl rand -base64 48` |
| `APP_HOSTS_ADMIN` | Admin endpoint'lerinin hangi host'tan çağrılabileceği. Tanımsızsa mağaza domainindeki bir XSS `/api/admin/**` çağırabilir. Örn: `admin.example.com` |
| `APP_HOSTS_STORE` | Mağaza host'ları. Örn: `example.com,www.example.com` |
| `APP_ENCRYPTION_KEY` | TC kimlik no ve ödeme gateway sırlarının kolon şifrelemesi. Boş bırakılırsa `JWT_SECRET`'tan türetilir (şifreleme yine açık kalır), ama ayrı anahtar verirseniz JWT secret'ını veritabanını yeniden şifrelemeden döndürebilirsiniz. `openssl rand -base64 32` |
| `CORS_ALLOWED_ORIGINS` | Tam origin listesi. Joker (`*`) girilirse **yok sayılır** ve hata log'lanır — `allowCredentials=true` ile birlikte joker, her siteye kimlikli istek izni demektir. |
| `CAPTCHA_ENABLED=true` + `CAPTCHA_SECRET` | Kayıt / şifre sıfırlama / iletişim formunda bot koruması. |
| `kargonomi_webhook_secret` (admin panel → ayarlar) | Kargo webhook'u imzasız istek **kabul etmez**; secret tanımlanana kadar 503 döner ve kargo durumları güncellenmez. |

Anahtar rotasyonu: `APP_ENCRYPTION_KEY` değişirse eski satırlar çözülemez (ilgili alan
`null` döner, uygulama çalışmaya devam eder). Rotasyon öncesi şifreli kolonları yeni
anahtarla yeniden yazın.

---

## 2. Kimlik doğrulama ve oturum

- **Admin**: `POST /api/admin/auth/login` → JWT (varsayılan 8 saat, `JWT_EXPIRATION_HOURS`).
  Kullanıcı adı başına 5 hatalı denemede 15 dakika kilit, ayrıca IP başına limit.
- **Müşteri**: JWT (7 gün) + refresh token (30 gün, kullanımda rotasyonlu).
  Token'lar HttpOnly+Secure+SameSite=Lax cookie olarak da set edilir.
- **Çıkış**: `POST /api/admin/auth/logout` ve `POST /api/store/auth/logout` token'ı
  sunucu tarafında iptal listesine alır ve refresh token'ı revoke eder.
- **Şifre sıfırlama** sonrası o müşterinin tüm oturumları sonlandırılır.
- **Rol değişimi / admin parola sıfırlama** sonrası hedef kullanıcının token'ları iptal edilir.
- Refresh token'lar, e-posta doğrulama ve şifre sıfırlama token'ları veritabanında
  **SHA-256 hash** olarak tutulur; ham değer yalnızca e-postada / ilk yanıtta bulunur.

İptal listesi süreç içi (Caffeine) tutulur. Tek instance'ta tamdır; birden fazla replikaya
çıkıldığında Redis'e taşınmalıdır — aksi halde logout yalnızca isteği alan instance'ta
etkili olur (token yine de kendi süresi dolunca ölür).

### Admin güvenlik şifresi (ikinci faktör)

Yıkıcı admin işlemleri `X-ADMIN-SECURITY-CODE` ister: kullanıcı oluşturma/silme, rol
değişimi, **parola sıfırlama**, kupon/gateway/kargo ayarları. Minimum 8 karakter, bcrypt
ile saklanır, 5 hatalı denemede 15 dakika kilit.

---

## 3. Yetkilendirme modeli

Üç filter chain: `/api/admin/**` + `/api/cezeri/**`, `/api/store/**`, ve geri kalan her şey.
**Üçü de `denyAll()` ile biter.** Yeni bir controller açıkça listelenmeden erişilemez;
hata modu geliştirme sırasında 403 almaktır, production'da açık kapı bırakmak değil.

Ödeme başlatma (`POST /api/store/payment/initialize`) misafir alışverişi nedeniyle public
kalmak zorunda; bu yüzden sahiplik kanıtı ister:

- giriş yapmış ve siparişin sahibi olan müşteri, **veya**
- checkout'un o siparişe özel ürettiği tek seferlik `paymentToken` (256 bit, 6 saat ömür).

---

## 4. Girdi ve çıktı

- **HTML sanitizasyonu**: jsoup allowlist (`XssSanitizer`). Gelen tüm JSON string'leri
  markup içeriyorsa temizlenir; düz metin (şifreler dâhil) değiştirilmez.
- **Frontend**: `dangerouslySetInnerHTML` kullanılan her yerde DOMPurify
  (`src/utils/sanitizeHtml.js`).
- **Dosya yükleme**: `Content-Type` başlığına güvenilmez. Magic byte ile format tespiti,
  uzantı ve servis edilen content-type tespit sonucundan türetilir. SVG kabul edilmez.
- **CSP**: hem uygulamadan hem nginx'ten gönderilir. SPA'nın HTML'i nginx'ten servis
  edildiği için asıl etkili olan `frontend/nginx.conf`'taki başlıktır.

---

## 5. İstemci IP'si ve rate limit

İstemci IP'si Tomcat `RemoteIpValve` (`server.forward-headers-strategy=native`) ile
çözülür: `X-Forwarded-For` sağdan sola taranır ve yalnızca
`server.tomcat.remoteip.internal-proxies` ile eşleşen hop'lar atlanır. Ek bir proxy
(örn. Cloudflare) varsa onun aralığı bu regex'e eklenmelidir.

> Not: `framework` stratejisi X-Forwarded-For'un **en soldaki** girdisini koşulsuz kabul
> eder — yani `getRemoteAddr()` istemci tarafından belirlenir hâle gelir ve tüm sayaçlar
> yönlendirilebilir. Bu yüzden `native` kullanılıyor.

Rate limit kuralları `RateLimitService` içinde; ilk eşleşen kural uygulanır, en sonda
`/api/**` için IP başına dakikalık genel tavan vardır. Ödeme/kargo callback'leri muaftır.
Sayaçlar süreç içidir (bkz. Redis notu).

---

## 6. Bilinçli olarak ertelenenler

| Konu | Neden ertelendi | Sonraki adım |
|---|---|---|
| `script-src 'unsafe-inline'` kaldırılması | GA4/Meta/Hotjar/Clarity snippet'leri ve iyzico/PayTR'nin döndürdüğü ödeme HTML'i inline script enjekte ediyor. Backend'den tek başına çözülemez. | Bu snippet'leri nonce/hash tabanlı yüklemeye taşımak |
| Token'ların tamamen cookie'ye taşınması | Admin paneli mağaza ile aynı site (eTLD+1) altında; cookie'ye geçiş SameSite'ı etkisiz bırakıp CSRF yüzeyi açar. Şu an Bearer + iptal listesi + DOMPurify ile riski düşürüldü. | Ayrı origin + CSRF token |
| Admin için TOTP/2FA | Kayıt akışı, kurtarma kodları ve UI gerektiriyor | Ayrı iş kalemi |
| Rate limit / iptal listesi için Redis | Tek instance'ta gerek yok | Yatay ölçeklenmeden önce |
| Spring Boot 3.5.x | Spring AI 1.0.0 eski hatta sabit. 3.3.13'e yükseltildi (CVE-2025-22228 dâhil yamalar alındı). | Spring AI ile birlikte planlı yükseltme |

---

## 7. Güvenlik testleri

```bash
mvn test
```

- `PenetrationTest` — çalışan uygulamaya gerçek HTTP istekleri: yetkisiz erişim,
  parola hash sızıntısı, varsayılan-deny, actuator/swagger, logout iptali, SSE ticket,
  ödeme sahipliği, ayar sızıntısı, güvenlik başlıkları, XFF sahteciliğine karşı rate limit.
- `ClientIpResolverTest` — proxy hop sayımı ve sahtecilik.
- `XssSanitizerTest` — tırnaksız event handler dâhil bilinen XSS payload'ları.
- `UploadValidatorTest` — SVG/HTML/sahte PDF reddi.
- `KargonomiWebhookSecurityTest` — imzasız webhook reddi.
- `DirectoryAccessSecurityTest` — rol bazlı erişim matrisi.

CI (`.github/workflows/security-scan.yml`) her push'ta bu testleri, OWASP
Dependency-Check'i, `npm audit`'i, gitleaks'i ve CodeQL'i çalıştırır; ayrıca haftalık
zamanlanmıştır (deploy sonrası yayınlanan CVE'ler için).

---

## 8. OWASP Top 10 (2021) durumu

OWASP Top 10 çalıştırılabilir bir test paketi değil, risk kategorisi listesidir. Kod
tabanı kategori kategori geçirildi; her satır bu depodaki karşılığını gösterir.

| Kategori | Durum | Notlar |
|---|---|---|
| **A01 Broken Access Control** | ✅ | Üç filter chain de `denyAll` ile biter. Ödeme başlatmada sahiplik kanıtı; sepet/adres/sipariş/talep/iade uçlarında ownership kontrolü doğrulandı. Kimliksiz erişilen medya uçları (iade fotoğrafı, transfer fotoğrafı) imzalı URL'ye taşındı — sıralı id ile arşiv taranamaz. Kalan: `products/images`, `reviews/images`, site asset'leri bilerek public (katalog içeriği). |
| **A02 Cryptographic Failures** | ✅ | Parolalar bcrypt(12); TC kimlik no ve ödeme gateway sırları AES-256-GCM; refresh / e-posta doğrulama / şifre sıfırlama token'ları SHA-256 hash. HSTS + `upgrade-insecure-requests`. Zayıf `JWT_SECRET` ile prod boot etmez. |
| **A03 Injection** | ✅ | SQL: her yerde parametreli (native sorgular dâhil, `AssistantDiagnosticsController` kontrol edildi). Komut: tek `ProcessBuilder` çağrısı (`dwebp`) sabit argüman + sunucu üretimi geçici dosya yolu, shell yok. XSS: jsoup allowlist + DOMPurify. Şablon enjeksiyonu: kullanıcı girdisiyle şablon derlenmiyor. |
| **A04 Insecure Design** | ⚠️ | Rate limit, idempotency, stok rezervasyonu ve kupon kullanım sayacı yerinde. Sayaçlar süreç içi — çok instance'ta zayıflar (Redis maddesi). |
| **A05 Security Misconfiguration** | ✅ | Actuator/Swagger admin'e kapalı, `info.env` kapalı, varsayılan-deny, güvenlik başlıkları hem uygulamada hem nginx'te, container non-root, hata mesajları DB detayı sızdırmıyor. |
| **A06 Vulnerable Components** | ✅ | Spring Boot 3.3.13. CI'da OWASP Dependency-Check + `npm audit` + haftalık zamanlama. |
| **A07 Auth Failures** | ⚠️ | Kilitleme, oturum iptali, logout, enumerasyon giderildi, yaygın parola reddi eklendi. **Admin için 2FA yok** (bilinçli, madde 6). |
| **A08 Integrity Failures** | ✅ | CDN'den yüklenen Bootstrap ve Font Awesome'a SRI eklendi — daha önce yoktu, yani ele geçirilen bir CDN admin panelinde kod çalıştırabilirdi. Jackson polymorphic typing kullanılmıyor. Yüklenen dosyalar magic byte ile doğrulanıyor. |
| **A09 Logging & Monitoring** | ⚠️ | Yapılandırılmış log + audit trail + `SECURITY ALERT` kayıtları var, PII maskeleniyor. **Alarm/uyarı yok** — başarısız admin girişi ve imza doğrulama hatası için bir hedefe (Sentry/Slack) bağlanmalı. |
| **A10 SSRF** | ✅ | Crawler `SsrfGuard`'a taşındı: tüm A kayıtları, IPv6 ULA ve IPv4-mapped, tam 100.64.0.0/10, ve **her redirect hop'u yeniden doğrulanıyor** (önce sadece ilk URL kontrol ediliyordu). DNS rebinding bilinçli olarak açık bırakıldı (sınıf javadoc'unda gerekçesiyle). |

Çalışan siteye karşı DAST için OWASP ZAP baseline scan:

```bash
docker run --rm -t ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t https://siteniz.com -r zap-report.html
```

---

## 9. Açık bildirimi

Güvenlik açığı bildirimi için issue açmayın; doğrudan sistem yöneticisine ulaşın.
