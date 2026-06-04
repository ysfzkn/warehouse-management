# Production Runbook

Bu doküman lansman sonrası operasyon ekibi için hızlı başvuru kaynağıdır.
Olağan dışı durumlar, deploy, backup/restore ve secret yönetimi konularını
kapsar. Hedef platform: **Railway / PaaS**.

---

## Hızlı Başvuru — En Sık Kullanılanlar

| İhtiyaç | Komut / Lokasyon |
|---------|------------------|
| Health check | `https://api.<domain>/actuator/health` |
| Logs tail | `railway logs --service backend --tail` |
| DB shell | `railway run --service backend psql $DATABASE_URL` |
| Restart | Railway dashboard → Service → "Restart" |
| Deploy | `git push origin main` (CI otomatik) |
| Rollback | Railway → Deployments → "Redeploy" eski commit |

---

## 1. Ortam Değişkenleri

Tüm secret'lar Railway environment variables üzerinde tutulur. **Hiçbiri**
`application*.properties` veya repo'da hardcoded olmamalıdır.

### Zorunlu (boş bırakılırsa servis fail-fast)
- `DATABASE_URL` — Postgres connection (Railway managed plugin)
- `JWT_SECRET` — 32+ karakter base64 (üretim: `openssl rand -base64 48`)
- `CORS_ALLOWED_ORIGINS` — Virgülle ayrılmış allowed origin'ler (örn. `https://siteniz.com,https://wms.siteniz.com`)

### Zorunlu (ödeme/fatura/kargo aktif olduğunda)
- `IYZICO_API_KEY`, `IYZICO_SECRET_KEY`, `IYZICO_BASE_URL`
- `LOGO_EFATURA_USERNAME`, `LOGO_EFATURA_PASSWORD`, `LOGO_EFATURA_ENDPOINT`
- Kargonomi credentials → admin panelden site_settings'e girilir

### Spring profile
- `SPRING_PROFILES_ACTIVE=prod` — production guard'ları aktive eder
  (MockInvoiceProvider devre dışı, fail-fast JWT, security headers, hostValidation).

### Host validation (opsiyonel ama önerilen)
- `APP_HOSTS_ADMIN=wms.siteniz.com,admin.siteniz.com`
- `APP_HOSTS_STORE=siteniz.com,www.siteniz.com`

---

## 2. Database Backup & Restore

### Otomatik snapshot (Railway Postgres)
Railway managed Postgres günlük otomatik snapshot alır. Dashboard → Data →
Backups sekmesinden manuel da tetiklenebilir. Restore prosedürü:

1. Dashboard → Data → "Restore" → Snapshot seç
2. Yeni DB instance'a restore et (eskisi korunur)
3. `DATABASE_URL` env var'ını yeni instance'a yönlendir
4. Service restart

### Manuel haftalık dış backup
```bash
# Cron veya GitHub Actions ile haftada bir:
railway run pg_dump $DATABASE_URL > backup-$(date +%Y%m%d).sql.gz
# S3 / Backblaze B2 / external storage'a yükle
```

### Felaket kurtarma
- RPO (Recovery Point Objective): 24 saat (Railway daily snapshot)
- RTO (Recovery Time Objective): 1-2 saat (restore + DNS cutover)

---

## 3. Deploy & Rollback

### Normal deploy
1. `main` branch'e merge — `.github/workflows/railway-deploy.yml` tetiklenir
2. CI test/lint/build çalışır
3. Railway otomatik deploy

### Acil rollback
1. Railway dashboard → Deployments
2. Sorunsuz son deploy'u bul → "..." → "Redeploy"
3. ~30s içinde önceki version aktif
4. **NOT:** Flyway migration'lar geri alınmaz; schema-breaking değişiklik
   yapan deploy rollback edilirse manuel `flyway repair` veya hotfix gerekir.

### Migration kontrol
Deploy öncesi yeni Vxx migration için:
```bash
mvn flyway:validate -Dflyway.url=$DATABASE_URL
```

---

## 4. Olağan Dışı Durumlar

### Uygulama başlamıyor — JWT_SECRET hatası
```
FATAL: Production'da JWT_SECRET zayıf/eksik
```
**Çözüm:** `openssl rand -base64 48` ile yeni secret üret, Railway env'e ekle,
restart. **Mevcut tokenları geçersiz kılar** — tüm kullanıcılar yeniden login.

### Uygulama başlamıyor — Flyway migration error
1. Loglarda hata mesajını oku (`Migration V58 failed: ...`)
2. Migration script'i düzelt → yeni commit
3. Eğer migration kısmen çalıştıysa: `mvn flyway:repair` ile metadata table'ı düzelt
4. Production'da elle SQL düzeltme **kaçınılmaz** ise: `mvn flyway:repair`'den önce
   manuel düzeltmeyi yap, sonra repair.

### Iyzico ödeme akışı çalışmıyor
1. `/api/admin/payment-transactions` → status filter
2. Iyzico admin paneli → Conversations
3. Loglarda `IyzicoPaymentGateway` exception'ı ara
4. Test mode yanlış olabilir: `application-prod.properties` `IYZICO_BASE_URL`
   `https://api.iyzipay.com` (live) olmalı, `https://sandbox-api.iyzipay.com` değil

### E-fatura kesilmiyor
1. `/api/admin/invoices` → ERROR status filter
2. `InvoiceStatusPollingJob` çalışıyor mu? Log'da arar
3. Logo eLogo SOAP credentials doğru mu?
4. **Uyarı:** Loglarda `MockInvoiceProvider AKTİF — PRODUCTION ORTAMI!` görüyorsan
   `invoice.mock-enabled=false` ayarlanmamış demektir, hemen düzelt.

### Disk dolu (görsel uploads)
- `/var/lib/uploads/` (Railway volume) bulup eski/orphan görselleri temizle
- Plan: Cloudflare R2 / S3'e taşıma — bkz. Faz 3 NICE-TO-HAVE

### Rate-limit / brute-force saldırısı
- Loglarda `RateLimitFilter` ve `AdminLoginAttemptTracker` mesajları
- IP whitelist gerekirse Railway → Networking → IP Allow List
- Aşırı kötü durumda Cloudflare ekleyip rate-limit edge'e taşı

---

## 5. Domain ve SSL

### DNS kayıtları
| Subdomain | Tip | Hedef |
|-----------|-----|-------|
| `siteniz.com` (root) | A / ALIAS | Railway frontend |
| `www.siteniz.com` | CNAME | `siteniz.com` |
| `wms.siteniz.com` | CNAME | Railway frontend (admin host) |
| `api.siteniz.com` | CNAME | Railway backend |

### SSL
- Railway managed TLS — otomatik Let's Encrypt renewal (60 günde bir)
- Force HTTPS: nginx/Railway proxy katmanında HTTP→HTTPS redirect zorunlu
- HSTS header backend'den geliyor (1 yıl, includeSubDomains, preload)

### Cookie domain ayrımı
- Store cookies: `Domain=.siteniz.com` (subdomain'ler arası paylaşılır mı? Hayır,
  admin token'ı store'a sızmasın). 
- Admin cookies: `Domain=wms.siteniz.com` veya `Domain=admin.siteniz.com`
- **NOT:** JWT şu an Authorization header'ında. Cookie'ye geçilirse bu kuralı uygula.

---

## 6. Monitoring & Alerts

### Sağlık probları (uptime monitoring)
- `https://api.siteniz.com/actuator/health` → 200 + `{"status":"UP"}`
- `https://siteniz.com/` → 200
- UptimeRobot / BetterStack ile 1dk aralıkla kontrol

### Critical alerts (set up)
- Health probe down 2 dakika → operator notification
- Disk > 85% → email
- Iyzico callback 5xx → ALERT
- Logo eFatura submission ERROR son 24h → admin digest e-mail (zaten var)

### Loglar
- Railway → Logs (real-time tail)
- Hata yoğunluğu artarsa: Sentry/GlitchTip entegrasyonu (Faz 1)

---

## 7. KVKK / GDPR Müşteri Talebi Akışları

### Veri ihracı talebi
1. Müşteri `/hesabim/gizlilik` → "Verilerimi İndir" otomatik
2. Manuel destek talebi geldiyse: `GET /api/admin/customers/{id}/data-export` (TODO)

### Hesap silme talebi
1. Müşteri `/hesabim/gizlilik` → "Hesabımı Sil" otomatik (UI çift onaylı)
2. Yasal saklama gereği sipariş geçmişi korunur, PII anonimleştirilir
3. Aktif siparişi olan müşteri için 409 döner — operator destek vermeli

### Talep raporu
```sql
-- Son 30 günde veri ihracı talep edenler
SELECT id, email, data_export_requested_at FROM customers
WHERE data_export_requested_at >= NOW() - INTERVAL '30 days';

-- Anonimleştirilen hesaplar (denetim için)
SELECT id, status_note, anonymized_at FROM customers
WHERE anonymized_at IS NOT NULL ORDER BY anonymized_at DESC;
```

---

## 8. İletişim & Sorumluluk

| Konu | Kim |
|------|-----|
| Prod incidents | Tech lead (telefon) |
| Yasal/KVKK | DPO / hukuk müşaviri |
| Ödeme operasyon | Finance + Iyzico hesap yöneticisi |
| Kargo operasyon | Operasyon + Kargonomi destek |

---

## 9. Lansman Öncesi Final Kontrol Listesi

Checkout/payment/invoice tüm yollar smoke test:
- [ ] Misafir checkout → ödeme → fatura → e-posta
- [ ] Üye checkout → ödeme → fatura → e-posta
- [ ] Sözleşme modal açılıyor, scroll-to-bottom çalışıyor
- [ ] KVKK veri ihracı çalışıyor, JSON indiriliyor
- [ ] KVKK hesap silme çalışıyor (test kullanıcısıyla)
- [ ] Cookie banner kategori bazlı, GA reject edilince yüklenmiyor
- [ ] /sitemap.xml çıktısı doğru, ürünler+kategoriler+CMS sayfaları var
- [ ] /robots.txt erişilebilir
- [ ] Footer'da ETBİS QR + KEP + vergi kimliği görünüyor
- [ ] Security headers: `curl -I https://siteniz.com` → HSTS, X-Frame-Options
- [ ] securityheaders.com → A+
- [ ] SSL Labs → A+
- [ ] PageSpeed Insights mobile → 70+
- [ ] Iyzico sandbox 3DS akışı → tek charge
- [ ] Logo eFatura sandbox → XML gönderildi → APPROVED
- [ ] Kargonomi sandbox → label download
- [ ] DB backup restore → staging'de doğrulandı
