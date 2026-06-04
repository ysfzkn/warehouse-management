# E-Fatura / E-Arşiv Entegrasyonu — Mimari ve En İyi Pratikler

Bu doküman Türkiye e-fatura ekosistemini, mevcut implementasyonumuzu ve best
practice'leri kapsar. Lansman öncesi mutlaka tüm bölümler okunmalı.

---

## 1. Türkiye E-Fatura Ekosistemi (Hızlı Özet)

### 1.1 Yasal Çerçeve

- **535 sayılı VUK** (Vergi Usul Kanunu) — e-fatura zorunluluğunu düzenler
- **GİB** (Gelir İdaresi Başkanlığı) — sistem operatörü
- **Mali mühür** (TÜBİTAK KamuSM) — fatura imzalama sertifikası, **zorunlu**

### 1.2 Kim Zorunlu?

| Senaryo | E-Fatura Zorunlu mu? |
|---------|---------------------|
| Brüt ciro **3M TL+** (mal/hizmet) | ✅ Evet |
| E-ticaret yapan ve ciro **500K TL+** | ✅ Evet |
| Markette satış yapan, ciro **5M TL+** | ✅ Evet |
| Kamu işletmesi | ✅ Evet |
| Avukat/Doktor (kademeli, bkz. tebliğler) | ✅ Genelde |
| Küçük esnaf, freelancer (ciro altında) | ❌ Hayır (ama önerilir) |

> Eşikler her yıl düşüyor. 2025-2026'da büyük olasılıkla tüm tüzel mükellefler
> zorunlu olacak.

### 1.3 E-Fatura vs E-Arşiv (Kritik Ayrım)

| | **E-Fatura** | **E-Arşiv** |
|---|---|---|
| Alıcı | GİB sisteminde **kayıtlı** mükellef (B2B) | Sisteme **kayıtlı değil** (B2C / küçük tüzel) |
| Format | UBL 2.1 TR1.2 | UBL 2.1 TR1.2 |
| ProfileID | `TICARIFATURA` / `TEMELFATURA` | `EARSIVFATURA` |
| Karşı taraf kabul | Karşı tarafın sistemde onayı gerekir | Otomatik geçer |
| İptal süresi | İptal **yok** — credit note kesilir | 8 gün içinde iptal edilebilir |

**Karar mekaniği** (bizdeki implementasyon):
```
Alıcı bireysel mi? (VKN yok, sadece TCKN)
  EVET → E_ARSIV
  HAYIR → CheckGibUser(VKN) çağrısı yap
    Sonuç true → E_FATURA
    Sonuç false → E_ARSIV (zorunlu, çünkü alıcı sisteme kayıtlı değil)
```

### 1.4 Üç Entegrasyon Modeli

| Yöntem | Maliyet | Karmaşıklık | Kim için? |
|--------|---------|--------------|-----------|
| **GİB Portal** | Ücretsiz | Manuel (web form) | Aylık <50 fatura |
| **Doğrudan entegrasyon** | Mali mühür + sertifika + SOAP altyapısı | Çok yüksek | Büyük kurumsal, dev ekibi şart |
| **Özel entegratör** ⭐ | Aylık 150-800 TL + kontör | Düşük (REST/SOAP API) | Çoğunluk (Logo, QNB eFinans, NES, Uyumsoft, İzibiz, Foriba…) |

**Bizim seçim:** Özel entegratör (Logo eLogo). `InvoiceProvider` interface'i ile
sağlayıcı abstrakte edilmiş — gelecekte QNB eFinans veya başka birine geçiş
sadece yeni bir bean eklemekle olur.

### 1.5 Logo eLogo Başvuru Süreci

1. **Hesap oluştur:** https://siparis.elogo.com.tr/ → kayıt
2. **Kontör al:** Aylık tahmini fatura adedine göre paket seç (Logo fiyat
   listesi: https://cdn.logo.com.tr/files/logocomtr/Uploads/Documents/elogo-ecozumler-fiyat-listesi.pdf)
3. **Mali mühür al:** TÜBİTAK KamuSM portalı (https://kamusm.bilgem.tubitak.gov.tr).
   Şirket türüne göre yetkili imzalı talep formu, 350-500 TL, 5-7 iş günü
4. **GİB başvurusu:** https://portal.efatura.gov.tr/efaturabasvuru/ →
   "Özel Entegrasyon" → "Logo eFinans" → 1-3 iş günü onay
5. **API erişimi:** https://efatura.elogo.com.tr/ → kullanıcı/şifre + alias bilgisi
6. **Test:** `pb-demo.elogo.com.tr` test endpoint'inde sandbox kullanıcısı

### 1.6 Mevcut Provider Karşılaştırması (Bilgi Amaçlı)

| Sağlayıcı | Aylık Min | API Stili | Sandbox | Notlar |
|-----------|-----------|-----------|---------|---------|
| **Logo eLogo** | ~₺300-500 | SOAP | ✅ | En yaygın, kontör bazlı |
| **QNB eFinans** | ~₺200-400 | SOAP + REST | ✅ | Banka destekli |
| **NES** | ~₺150-300 | REST | ✅ | Modern, Swagger doc |
| **İzibiz** | ~₺200-400 | SOAP | ✅ | Büyük kurumsal |
| **Foriba (Sovos)** | ~₺400-800 | SOAP | ✅ | Pahalı, premium destek |
| **Uyumsoft** | ~₺200-500 | SOAP | ✅ | ERP entegrasyonlu |

---

## 2. Mevcut İmplementasyon

### 2.1 Mimari Genel Bakış

```
┌─────────────────────────────────────────────────────┐
│  PaymentService.completePayment()                    │
│    → publish OrderPaidEvent (AFTER_COMMIT)           │
└────────────────────┬────────────────────────────────┘
                     ↓
       ┌─────────────────────────────────────┐
       │ InvoiceAutoCreateListener (@Async)  │
       │   • feature flag kontrol            │
       │   • InvoiceService.createInvoice... │
       └────────────────────┬────────────────┘
                            ↓
   ┌──────────────────────────────────────────────────┐
   │ InvoiceServiceImpl                               │
   │   1. TCKN/VKN algoritmik doğrulama (Validator)  │
   │   2. Mevcut fatura kontrolü (idempotency)        │
   │   3. E-Fatura/E-Arşiv tipi tespit (CheckGibUser) │
   │   4. Invoice DRAFT olarak kaydet                 │
   │   5. Numara üret (V54 atomic sequence)           │
   │   6. UBL-TR XML build                            │
   │   7. activeProvider.createInvoice(...)           │
   │   8. Resilience4j retry + circuit breaker        │
   │   9. status = APPROVED / PENDING / ERROR         │
   │  10. "Faturanız Hazır" maili                     │
   └────────────────────┬─────────────────────────────┘
                        ↓
   ┌──────────────────────────────────────────────────┐
   │ LogoInvoiceProvider                              │
   │   • Login → sessionID (25dk cache)               │
   │   • SendDocument (XML+ZIP+Base64+MD5)            │
   │   • Response: resultCode, uuid (ETTN)            │
   └────────────────────┬─────────────────────────────┘
                        ↓
                  ┌────────────┐
                  │ Logo eLogo │
                  │  PostBox   │
                  └─────┬──────┘
                        ↓
                    ┌───────┐
                    │  GİB  │
                    └───────┘

Async (5dk polling):
   InvoiceStatusPollingJob (ShedLock'lu)
   → InvoiceService.refreshPendingStatuses()
   → activeProvider.queryStatus(ETTN) → APPROVED/REJECTED

Iade akışı:
   AdminOrderController.updateStatus(RETURNED/REFUNDED)
     → publish OrderReturnedEvent
     → InvoiceCancellationListener (@Async)
       • e-Arşiv + 8gün içinde → CANCELEARCHIVEINVOICE
       • e-Fatura veya 8gün geçmiş → credit note (manuel)
```

### 2.2 Kritik Dosyalar

| Katman | Dosya | Sorumluluk |
|--------|-------|-------------|
| Event | `event/OrderPaidEvent.java` | Sipariş ödendi sinyali |
| Event | `event/OrderReturnedEvent.java` | İade sinyali |
| Listener | `event/InvoiceAutoCreateListener.java` | Async fatura kesimi |
| Listener | `event/InvoiceCancellationListener.java` | Async iptal/credit note |
| Service | `service/InvoiceService.java` (interface) | Public API |
| Service | `service/impl/InvoiceServiceImpl.java` | Orkestrasyon |
| Provider | `service/invoice/InvoiceProvider.java` | Sağlayıcı abstraction |
| Provider | `service/invoice/MockInvoiceProvider.java` | Dev/test (prod'da disabled) |
| Provider | `service/invoice/logo/LogoInvoiceProvider.java` | Logo SOAP istemcisi |
| Provider | `service/invoice/logo/UblTrInvoiceBuilder.java` | UBL-TR XML üretici |
| Util | `util/TurkishTaxIdValidator.java` | VKN/TCKN algoritmik doğrulama |
| Util | `service/invoice/InvoiceNumberGenerator.java` | EFA/EAR{year}{seq} atomik üretim |
| Job | `job/InvoiceStatusPollingJob.java` | PENDING → APPROVED polling (5dk) |
| Job | `job/InvoiceAdminDigestJob.java` | Günlük ERROR/PENDING özeti maili |
| Controller | `controller/AdminInvoiceController.java` | Admin CRUD + regenerate |
| Migration | `db/migration/V45__create_invoice_table.sql` | invoices tablosu |
| Migration | `db/migration/V51__logo_efatura_settings.sql` | Logo ayarları + ek alanlar |
| Migration | `db/migration/V54__invoice_number_sequence.sql` | Sıralı numara için tablo |
| Migration | `db/migration/V55__invoice_admin_digest_setting.sql` | Digest e-postası |
| Migration | `db/migration/V60__invoice_credit_notes.sql` | Credit note alanları (is_credit_note, credited_invoice_id, credit_note_reason) |

---

## 3. Best Practices — Yapılanlar ve Yapılması Gerekenler

### 3.1 ✅ Yapılan En İyi Pratikler

- **Event-driven trigger** (`@TransactionalEventListener(AFTER_COMMIT) + @Async`):
  Sipariş thread'i Logo SOAP'ı beklemez; checkout response <500ms.
- **Idempotency**: Aynı sipariş için mevcut DRAFT/PENDING/APPROVED fatura varsa
  tekrar oluşturulmaz.
- **TCKN/VKN algoritmik doğrulama**: Logo'ya yanlış kimlik gitmez → reject loop önlenir.
- **Atomic invoice number**: PostgreSQL `INSERT ... ON CONFLICT` ile race-condition'sız
  EFA/EAR sıralaması.
- **Audit trail**: Gönderilen XML (`xml_content`) + Logo'nun full response'u
  (`gib_response`) DB'de tutuluyor (yasal denetim için zorunlu).
- **Resilience4j retry + CB**: Logo down'da exponential backoff retry,
  thresholda gelince circuit open.
- **Session cache**: Logo Login response'undaki sessionID 25dk cache'li (Logo
  default TTL 30dk).
- **Polling job**: PENDING faturaları 5dk'da bir kontrol eder; webhook
  beklemeden status güncellenir.
- **Admin digest mail**: ERROR + PENDING>24h faturaları her sabah 08:00'de
  tek mail ile admin'e bildirir.
- **PDF cache (Caffeine)**: APPROVED PDF'leri 24h cache → Logo SOAP'a tekrar
  tekrar gidilmez.
- **MockProvider prod-guard**: `@ConditionalOnProperty` ile prod profile'inde
  bean oluşturulmaz, yanlışlıkla sahte fatura kesme imkansız.

### 3.2 ⚠️ Bilinen Limitler / TODO

| Konu | Durum | Öncelik |
|------|-------|---------|
| Credit note (iade) tam otomatize | ✅ Tamam — V60 alanları + UBL ProfileID=IADE + BillingReference + `InvoiceCancellationListener` otomatik kesim | — |
| ÖTV (SCT) UBL alanları | ✅ Tamam — her satırda KDV + ÖTV (TaxTypeCode 4080) TaxSubtotal blokları; toplam vergi içine dahil | — |
| Kısmi iade (partial credit note) | ✅ Tamam — `refundAmount` parametresi ile destekli, KDV oransal | — |
| Manuel credit note endpoint | ✅ Tamam — `POST /api/admin/invoices/{id}/credit-note` (retry/admin için) | — |
| Customer-facing invoice download | ✅ Tamam — `/api/store/orders/{nr}/invoice` modern provider yolu + cache + legacy fallback | — |
| Çoklu provider failover | Şu an tek aktif provider; `InvoiceProvider` abstraction hazır, ikinci bean ekleme ile failover | Düşük |
| Webhook (Logo → bize) | Logo native webhook desteklemiyor; polling MVP yeterli | — |
| KDV exempt (vergi muafiyeti) | UBL alanı hazır değil | Düşük (e-ticarette nadir) |
| İhracat e-faturası (UBL.GIB.IHRACATFATURA) | Uluslararası satışa başlanınca eklenir | Düşük |

### 3.3 ❌ Yapılmaması Gerekenler (Anti-Patterns)

- ❌ **Synchronous fatura kesme** — checkout endpoint'inde `createInvoice(...)`
  çağırma. Logo down ise sipariş tamamen başarısız olur. (Düzeltildi)
- ❌ **Sahte VKN kabul etme** — algoritmik kontrol etmeden Logo'ya gönderme.
  GİB reject eder, müşteri kafası karışır. (Düzeltildi)
- ❌ **Prod'da MOCK provider** — Sahte fatura kesimi yasal risk. (Düzeltildi:
  `@ConditionalOnProperty(invoice.mock-enabled)`)
- ❌ **xml_content + gib_response kaydetmemek** — 10 yıllık denetim zorunluluğu var.
- ❌ **Same VKN için 2 paralel fatura kesimi** — `@TransactionalEventListener`
  + idempotency check sayesinde önlendi.
- ❌ **Logo credentials .properties'te plain** — env var olarak yerleştirilmeli.
- ❌ **Müşteriye Logo error mesajını gösterme** — admin'e gitsin, müşteriye
  generic "fatura hazırlanıyor" mesajı.

---

## 4. Operasyonel Akış (Production)

### 4.1 İlk Kurulum

```bash
# 1. Mali mühür sertifikasını al (KamuSM) → fiziksel kart veya yazılım
# 2. Logo eLogo'da hesap aç, kontör al → username/password al
# 3. GİB portal başvuru → Özel Entegrasyon → Logo seç → onay bekle
# 4. Admin panelden site_settings'i doldur:
```

```sql
-- Provider aktivasyonu
UPDATE site_settings SET setting_value='LOGO'  WHERE setting_key='invoice_provider';
UPDATE site_settings SET setting_value='true'  WHERE setting_key='invoice_auto_generate';

-- Logo credentials (prod'da env var önerilir)
UPDATE site_settings SET setting_value='https://pb.elogo.com.tr/PostboxService.svc' WHERE setting_key='logo_efatura_endpoint';
UPDATE site_settings SET setting_value='kullaniciadi'  WHERE setting_key='logo_efatura_username';
UPDATE site_settings SET setting_value='sifre'         WHERE setting_key='logo_efatura_password';
UPDATE site_settings SET setting_value='false'         WHERE setting_key='logo_efatura_test_mode';
UPDATE site_settings SET setting_value='urn:mail:efinanseinvoice@elogo.com.tr' WHERE setting_key='logo_customer_alias';

-- Satıcı firma bilgileri (UBL-TR'de zorunlu)
UPDATE site_settings SET setting_value='1234567890'           WHERE setting_key='logo_company_vkn';
UPDATE site_settings SET setting_value='Magaza A.Ş.'          WHERE setting_key='logo_company_title';
UPDATE site_settings SET setting_value='Beşiktaş'             WHERE setting_key='logo_company_tax_office';
UPDATE site_settings SET setting_value='0123456789012345'     WHERE setting_key='logo_company_mersis_no';
-- + adres, telefon, email, banka IBAN vs.

-- Digest e-mail
UPDATE site_settings SET setting_value='finans@magaza.com' WHERE setting_key='invoice_admin_digest_email';
```

### 4.2 Akış: Bir siparişin faturalanması

```
T+0:    Müşteri sipariş verir (PENDING_PAYMENT)
T+1s:   Iyzico 3DS akışı → success
T+1.5s: PaymentService → order.status=PAID, transaction.SUCCESS
        → eventPublisher.publishEvent(OrderPaidEvent)
        → DB commit
T+1.6s: Checkout response → kullanıcıya "Siparişiniz alındı"

  --- @Async listener tetiklendi ---

T+2s:   InvoiceAutoCreateListener (Async thread)
        → InvoiceService.createInvoiceForOrder(orderId)
        → TCKN/VKN doğrulama
        → CheckGibUser → E_ARSIV / E_FATURA
        → Invoice DRAFT kaydı, numara üretildi (örn. EAR2026000017)
        → UBL-TR XML build (~200KB)
        → Logo Login (cached) → sessionID
T+3s:   → SendDocument SOAP → ETTN döndü
        → Invoice.status=APPROVED (e-Arşiv hemen) veya PENDING (e-Fatura)
        → EmailService.sendInvoiceReady(customer)

  --- Eğer status=PENDING ise ---

T+5dk:  InvoiceStatusPollingJob (ShedLock'lu)
        → queryStatus(ETTN) → APPROVED veya REJECTED
        → DB update + müşteri mail
```

### 4.3 Hata Senaryoları

| Hata | Davranış |
|------|----------|
| Logo down (timeout/5xx) | Resilience4j retry (2x, 3s wait) → CB open → fallback: Invoice.status=ERROR + admin digest |
| Yanlış VKN/TCKN | InvoiceService validation aşamasında reject → log + sipariş tarafı etkilenmez |
| Logo response: ETTN yok | Invoice.status=ERROR, gib_response saklı; admin regenerate yapar |
| Logo sessionID expired | LogoInvoiceProvider auto-relogin (response'tan tespit ediyor) |
| Çift tetiklenme (event 2 kez) | Idempotency kontrol (Invoice.status DRAFT/PENDING/APPROVED ise atla) |
| Mock prod'da yanlışlıkla aktif | `@PostConstruct` ERROR-level loud banner emit |
| Kontör tükenmiş | Logo "balance insufficient" döner → Invoice.status=ERROR + dijital banner |

### 4.4 Manuel Müdahale Senaryoları

```bash
# Belirli bir sipariş için fatura tekrar oluştur (admin UI veya curl)
POST /api/admin/invoices/{invoiceId}/regenerate

# Bir siparişe manuel fatura kes
POST /api/admin/invoices/auto/{orderId}

# Faturayı iptal et (e-Arşiv 8 gün içinde)
POST /api/admin/invoices/{invoiceId}/cancel

# Müşteriye PDF gönder
GET /api/admin/invoices/{invoiceId}/pdf
```

---

## 5. Lokal Test Rehberi

### 5.1 MOCK provider ile test (Logo gerekmeden)

```properties
# application.properties (default)
invoice.mock-enabled=true
```

```bash
# Order PAID olunca → MockInvoiceProvider devreye girer
# Sahte invoice number: INV20260413000001
# SimplePdfBuilder dependency-free PDF üretir
# Admin invoice listesinde anında APPROVED görünür
```

### 5.2 Logo demo endpoint ile test

```sql
UPDATE site_settings SET setting_value='LOGO' WHERE setting_key='invoice_provider';
UPDATE site_settings SET setting_value='https://pb-demo.elogo.com.tr/PostboxService.svc'
  WHERE setting_key='logo_efatura_endpoint';
UPDATE site_settings SET setting_value='true' WHERE setting_key='logo_efatura_test_mode';
-- Logo'dan alacağın test credentials
```

```bash
# Backend restart → bir test siparişi ver → loglarda Logo SOAP call'ları görmeli olursun
# Loglarda:
# [Logo] Login OK, sessionID=abc123...
# [Logo] SendDocument → resultCode=1, ETTN=550e8400-e29b...
# Mock'tan farkı: GİB sandbox 5-30 saniye sonra status APPROVED'a döner.
```

### 5.3 VKN/TCKN Test Verileri

```
Geçerli test TCKN'ler (algoritmik):
  10000000146
  21862281962
  11111111110 (her hane aynı, edge case)

Geçerli test VKN'ler:
  1234567890 (bazıları geçerli olmayabilir; algoritma test)
  3270566052 (test için)

Geçersiz olanlar (validator reject etmeli):
  12345678901 (TCKN ama checksum yanlış)
  0123456789  (VKN baş 0 olamaz)
  abc1234567  (rakam olmayan)
```

---

## 6. Compliance Checklist (Lansman Öncesi)

- [ ] Mali mühür sertifikası alındı ve Logo'ya yüklendi
- [ ] GİB özel entegrasyon onayı tamam (Logo entegratör olarak görünür)
- [ ] Tüm site_settings `logo_*` alanları doldu (özellikle VKN, MERSİS, vergi
      dairesi — UBL'de zorunlu)
- [ ] `invoice.mock-enabled=false` prod profile'da
- [ ] Logo credentials env var olarak (DB plain text değil)
- [ ] `invoice_auto_generate=true` (otomatik tetikleme aktif)
- [ ] `invoice_admin_digest_email` doldu (operasyon ekibinin maili)
- [ ] Test siparişi → fatura → PDF download → müşteri mail uçtan uca geçti
- [ ] Iade test: RETURNED → fatura iptal/credit note tetiklendi
- [ ] InvoiceStatusPollingJob 5dk'da bir loglarda görünüyor
- [ ] InvoiceAdminDigestJob bir kez manuel tetiklenip mail geldi mi
- [ ] Logo kontör bakiyesi yeterli (en az 1 aylık trafiği karşılayacak)
- [ ] Backup: gib_response + xml_content kolonları DB backup'a dahil
- [ ] 10 yıl saklama planı (Türk Vergi Mevzuatı): DB retention politikası

---

## 7. Sık Sorulan Sorular

**S: Logo eLogo dışında bir provider'a geçmek mümkün mü?**
Evet. `InvoiceProvider` interface'i abstract; QNB eFinans, NES veya başka birini
implementeyen `@Component` ekleyince `invoice_provider` site setting'ini
değiştirerek geçilir. Mevcut faturalar Logo'da kalır (ETTN'ler değişmez).

**S: Hangi sağlayıcı en iyi?**
Hacme bağlı. <500/ay için NES (REST, modern, ucuz). >2000/ay için Logo veya
Foriba (kurumsal destek). Banka müşterisiysen QNB eFinans (ek banka entegrasyonu).

**S: Mock'u prod'da kullanabilir miyim?**
Hayır. Yasal risk: GİB'e gitmeyen "fatura" kesip müşteriye gönderirsen vergi
incelemesinde sıkıntı çıkar. `@ConditionalOnProperty` guard sayesinde prod'da
zaten bean oluşmaz.

**S: PDF nereden geliyor?**
Logo'nun `GetDocumentData` SOAP endpoint'inden Base64 ile dönüyor. Bizim
SimplePdfBuilder sadece MOCK için. Production PDF'leri Logo render eder
(satıcı template'leri Logo paneline yüklenir — `logo_earsiv_design_file` /
`logo_efatura_design_file`).

**S: Müşteri faturasını nasıl indirir?**
`/api/store/orders/{orderNumber}/invoice` endpoint'inden (auth gerekli).
Backend `InvoiceService.downloadInvoicePdf(invoiceId)` → Caffeine cache →
Logo SOAP (cache miss). 24h cache aynı PDF'in tekrar Logo'ya istek atmasını önler.

**S: Çoklu currency var mı?**
Şu an sadece TRY. UBL'de döviz alanı var ama TR mevzuatı yurt içi faturalar için
TRY zorunlu. İhracat e-faturası ayrı bir akış (`UBL.GIB.IHRACATFATURA`) — gerekirse
ileride eklenir.

---

## 8. İlgili Dokümantasyon

- [Resmi GİB e-Belge portalı](https://ebelge.gib.gov.tr/)
- [Onaylı özel entegratör listesi](https://ebelge.gib.gov.tr/efaturaozelentegratorlerlistesi.html)
- [Logo eLogo dokümanları](https://docs.logo.com.tr/)
- [TÜBİTAK KamuSM mali mühür](https://kamusm.bilgem.tubitak.gov.tr)
- [UBL-TR 1.2 spec (GİB)](https://ebelge.gib.gov.tr/dosyalar/UBLTRKilavuzlari/UBL-TR_Veri_Tipleri_Klavuzu.pdf)
- Bizim runbook: `docs/RUNBOOK.md` §7 — KVKK akışları, bakım pencereleri
