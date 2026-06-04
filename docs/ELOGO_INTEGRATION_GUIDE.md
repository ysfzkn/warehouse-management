# 📄 eLogo (Logo e-Fatura) Entegrasyon Rehberi

> Müşterinin Logo eLogo panel'ine erişimin var. Bu doküman: **(1)** entegrasyonumuz nasıl çalışıyor, **(2)** eLogo panelinden hangi bilgileri al, **(3)** admin paneline nereye gir, **(4)** test ve canlı geçiş.

---

## 1. Nasıl Çalışıyor — Akış Diyagramı

```
Sipariş PAID olur (Iyzico/PayTR callback)
        ↓
InvoiceJob (scheduled @5dk veya event-driven)
        ↓
LogoInvoiceProvider.createInvoice(invoice, order, items)
        ↓
  1. site_settings'ten Logo credentials + şirket bilgileri oku
  2. UblTrInvoiceBuilder ile UBL-TR XML üret (Türkiye e-fatura standardı)
  3. XML → ZIP → Base64 encode
  4. SOAP Login → sessionId al (cache'lenir, 30 dk TTL)
  5. SOAP SendDocument çağrısı:
       - DOCUMENTTYPE: EINVOICE (tüzel müşteri) veya EARCHIVE (bireysel)
       - SIGNED: 0 (Logo imzalar)
       - ALIAS: (varsa, tüzel müşteri için)
  6. Response parse → ETTN (e-fatura takip no, GUID) al
        ↓
Invoice tablosuna kaydet:
  - status = SENT
  - ettn = "550e8400-..."
  - provider_response = "OK"
        ↓
EmailService müşteriye fatura PDF linki gönderir
        ↓
(Sonra) InvoiceStatusJob periyodik check → APPROVED / REJECTED
```

### Kaynak dosyalar

| Dosya | Görevi |
|-------|--------|
| `service/invoice/InvoiceProvider.java` | Soyut interface (mock + Logo iki implementasyon) |
| `service/invoice/logo/LogoInvoiceProvider.java` | SOAP istemcisi (~600 satır) |
| `service/invoice/logo/UblTrInvoiceBuilder.java` | UBL-TR XML üretici |
| `service/invoice/MockInvoiceProvider.java` | Dev/test için sahte (gerçek SOAP'a gitmez) |
| `db/migration/V51__logo_efatura_settings.sql` | Settings key'leri seed |

### Hangi referansı baz aldık?

- **Resmi:** eLogo Postbox Service WSDL (`https://pb-demo.elogo.com.tr/PostboxService.svc?wsdl`)
- **Pratik referans:** [Hasokeyk/elogo-php](https://github.com/Hasokeyk/elogo-php/blob/main/src/Elogo/Elogo.php) — açık kaynak PHP istemcisi
- **UBL-TR standart:** [GİB UBL-TR 1.2 paketi](https://www.gib.gov.tr/E-Fatura_Mukellef_Bilgileri)

**Resmi dokümantasyon istersen:** Logo destek hattından (`destek@elogo.com.tr`) WSDL + örnek XML iste. Türkiye'de e-fatura çok evrim geçirdi, WSDL'i nadiren değişir ama UBL alanları (özellikle KDV istisna kodları) güncellenebilir.

---

## 2. eLogo Panelinden Toplayacağın Bilgiler

### 2.1 SOAP/API Erişim Bilgileri

eLogo paneline giriş → **Profil** veya **Web Servis Bilgileri** menüsü altında:

| Logo Panelindeki Adı | Bizdeki Setting Key | Örnek |
|----------------------|---------------------|-------|
| Web Servis Kullanıcı Adı | `logo_efatura_username` | `firma_demo` veya gerçek |
| Web Servis Şifresi | `logo_efatura_password` | (şifre) |
| Test/Demo modu var mı? | `logo_efatura_test_mode` | `true` / `false` |

⚠️ **Genellikle "Web servis kullanıcı adı" ≠ "Logo panel login kullanıcı adı".** Web service için ayrı bir kullanıcı oluşturulur. Bulamıyorsan eLogo destek hattını ara, "Web servis bilgilerim nerede" diye sor.

### 2.2 Şirket / Mükellef Bilgileri (faturanın "satıcı" tarafı)

Bunlar **fatura PDF'inde yazacak** bilgiler — Logo zaten biliyor ama UBL-TR XML içinde de bizim göndermemiz lazım. Logo panel → **Firma Bilgileri** veya **Profil**'den toplayabilirsin; ya da müşterinin Vergi Levhası'ndan da alabilirsin.

| Logo / Vergi Levhası | Setting Key | Format |
|----------------------|-------------|--------|
| Vergi Kimlik No (VKN) | `logo_company_vkn` | 10 hane (tüzel) |
| Resmi Ticari Unvan | `logo_company_title` | "ŞAHİNLER DTM SAN. VE TİC. LTD. ŞTİ." |
| Vergi Dairesi | `logo_company_tax_office` | "Ostim", "Kavaklıdere", vb. |
| MERSİS No | `logo_company_mersis_no` | 16 hane |
| Ticaret Sicil No | `logo_company_trade_registry` | (varsa) |
| Adres | `logo_company_address` | "Ostim OSB Mah. 100. Yıl Cd. No:5 ..." |
| Şehir | `logo_company_city` | "Ankara" |
| İlçe | `logo_company_district` | "Yenimahalle" |
| Posta Kodu | `logo_company_postal_code` | "06370" |
| Ülke | `logo_company_country` | `TR` (zaten default) |
| Email | `logo_company_email` | `info@yourdomain.com` |
| Telefon | `logo_company_phone` | `+903121234567` (uluslararası format) |
| Website | `logo_company_website` | `https://yourdomain.com` |
| Banka Adı | `logo_company_bank_name` | (varsa fatura altında görünmesini istediğin) |
| Banka IBAN | `logo_company_bank_iban` | "TR00 0000 ..." |

### 2.3 Fatura Tasarım Şablonları

Logo paneli → **e-Fatura Tasarımları** veya **Şablon Yönetimi** menüsü:

| Logo Panelindeki Adı | Setting Key | Açıklama |
|----------------------|-------------|----------|
| e-Arşiv Tasarım Adı | `logo_earsiv_design_file` | Bireysel müşterilere kesilen faturalar için PDF tasarımı |
| e-Fatura Tasarım Adı | `logo_efatura_design_file` | Tüzel (şirket) müşterilere kesilen faturalar için |

⚠️ Bu alanlar **opsiyonel**. Boş bırakılırsa Logo default şablonu kullanılır. Genelde "Standart" veya "Default" diye bir şablon hazır gelir. Marka kimliğine uygun custom tasarım istersen Logo'dan tasarımcı talep et (genelde paralı).

### 2.4 E-Fatura Müşteri Alias'ı (sadece B2B varsa)

| Logo Panelindeki Adı | Setting Key | Açıklama |
|----------------------|-------------|----------|
| Posta Kutusu Etiketi | `logo_customer_alias` | Sadece **tüzel** müşteri faturaları için. Logo paneli → **Adres Defteri** → müşteri VKN'sini sor, "Alias" alanı görünür (örn. `urn:mail:defaultpk@firma.com.tr`) |

⚠️ **Bireysel müşteri (TC kimlikli) için alias gerekmez** — e-Arşiv olarak kesilir, doğrudan müşteri emailine gider.

### 2.5 GİB Mükellef Kontrolü (otomatik)

Müşteri checkout'ta VKN girerse, sistem `CheckGibUser` SOAP çağrısıyla otomatik kontrol eder: "Bu VKN e-fatura mükellefi mi?"
- **Evet** → e-Fatura kesilir (Logo, GİB üzerinden o şirketin posta kutusuna gönderir)
- **Hayır** → e-Arşiv kesilir (PDF müşteri emailine direkt gider)

Bu manuel ayar değil — sistem otomatik yapar.

---

## 3. Admin Paneline Nereye Gireceksin?

Admin panel → **Site Ayarları** → **e-Fatura / Logo** sekmesi (varsa).

Frontend kod: `frontend/src/pages/AdminSiteSettings.js` — bu sayfada Logo bölümü var mı bak. Yoksa düz `site_settings` tablosuna SQL ile gir:

```sql
-- Şirket bilgileri (örneğin)
UPDATE site_settings SET setting_value = 'firma_demo' WHERE setting_key = 'logo_efatura_username';
UPDATE site_settings SET setting_value = 'demo123' WHERE setting_key = 'logo_efatura_password';
UPDATE site_settings SET setting_value = 'true' WHERE setting_key = 'logo_efatura_test_mode';  -- önce test
UPDATE site_settings SET setting_value = '1234567890' WHERE setting_key = 'logo_company_vkn';
UPDATE site_settings SET setting_value = 'ŞAHİNLER DTM SAN. VE TİC. LTD. ŞTİ.' WHERE setting_key = 'logo_company_title';
UPDATE site_settings SET setting_value = 'Ostim' WHERE setting_key = 'logo_company_tax_office';
-- ... ve diğerleri
```

### Provider'ı Logo'ya çevir

```sql
-- "Hangi fatura sistemi aktif?" — global switch
UPDATE site_settings SET setting_value = 'LOGO' WHERE setting_key = 'invoice_provider';
```

Eğer `'MOCK'` olarak kalırsa sahte fatura kesilir (geliştirme için ideal, prod'da KESİNLİKLE `'LOGO'` olsun).

### Sanity check sorgusu

```sql
SELECT setting_key, LEFT(setting_value, 60) AS value
FROM site_settings
WHERE setting_key LIKE 'logo_%' OR setting_key = 'invoice_provider'
ORDER BY setting_key;
```

Eksik alanları boş gör, doldur.

---

## 4. Test Süreci

### 4.1 Dev'de Mock ile başla

```sql
UPDATE site_settings SET setting_value = 'MOCK' WHERE setting_key = 'invoice_provider';
```

`MockInvoiceProvider` her PAID order için sahte ETTN üretir, DB'ye yazar. SOAP'a gitmez. Order/email flow'u test etmek için kullanılır.

### 4.2 eLogo Test/Demo ortamı

Müşterinin Logo paneli **test/demo ortamı** sağlıyorsa:

```sql
UPDATE site_settings SET setting_value = 'LOGO' WHERE setting_key = 'invoice_provider';
UPDATE site_settings SET setting_value = 'true' WHERE setting_key = 'logo_efatura_test_mode';
UPDATE site_settings SET setting_value = 'test_kullanici' WHERE setting_key = 'logo_efatura_username';
UPDATE site_settings SET setting_value = 'test_sifre' WHERE setting_key = 'logo_efatura_password';
```

Endpoint otomatik olarak `https://pb-demo.elogo.com.tr/PostboxService.svc` olur.

**Test adımları:**

1. Dev'de bir test ürünü için sipariş oluştur → PAID yap
2. Backend log'larında bu satırı ara:
   ```
   INFO LogoInvoiceProvider — Logo Login OK, sessionId=...
   INFO LogoInvoiceProvider — Logo SendDocument OK, ettn=550e8400-...
   ```
3. Admin panel → Siparişler → o siparişi aç → "Fatura İndir" butonu çalışmalı (PDF gelir)
4. Logo demo paneline gir → Faturalarım → orada görünmeli

**Hata sinyalleri:**
- `Logo Login FAILED: invalid credentials` → username/password yanlış
- `SOAP fault: SchemaValidationFailed` → UBL-TR XML'de eksik alan (genelde şirket bilgisi boş)
- `getInvoiceStatus: REJECTED` → GİB tarafında ret (vergi numarası uyumsuz, vb.)

### 4.3 Production geçişi

```sql
-- Production credential'ları gir (genellikle DEMO'dan farklı)
UPDATE site_settings SET setting_value = 'false' WHERE setting_key = 'logo_efatura_test_mode';
UPDATE site_settings SET setting_value = 'prod_kullanici' WHERE setting_key = 'logo_efatura_username';
UPDATE site_settings SET setting_value = 'prod_sifre' WHERE setting_key = 'logo_efatura_password';
```

Endpoint otomatik olarak `https://pb.elogo.com.tr/PostboxService.svc` olur.

⚠️ **Production'da ilk 1-2 faturayı manuel takip et:**
- Logo paneli → e-Faturalarım → görünüyor mu
- GİB e-Arşiv portalı (https://earsivportal.efatura.gov.tr) → bireysel faturalar burada
- Müşteri emailine fatura PDF linki gitti mi
- VKN girdiğinde tüzel fatura mı kesildi, TC girdiğinde bireysel mi

### 4.4 application-prod.properties Kontrol

`invoice.mock-enabled` kesinlikle false olmalı:

```properties
invoice.mock-enabled=false
```

`true` kalırsa `MockInvoiceProvider` bean kayıtlı kalır ve potansiyel olarak yanlış provider seçilir.

---

## 5. Sık Sorulanlar

### "VKN ile TC nasıl ayırt ediliyor?"

`Invoice.is_individual` alanı:
- `TRUE` → e-Arşiv (TC kimlik veya VKN olmadan, bireysel)
- `FALSE` → e-Fatura (VKN ile, tüzel)

Checkout sayfasında müşteri "Bireysel/Kurumsal" seçer; backend `Order.is_individual` set eder; Invoice oluştururken o devralır.

### "Session cache nasıl çalışıyor?"

`AtomicReference<SessionCache>` — Login bir kere yapılır, sessionId 30 dk cache'lenir. Süre dolunca otomatik yeniden login. Multi-instance'da her instance kendi cache'ini tutar (sorun değil, eLogo paralel oturum kabul eder).

### "Fatura iptali nasıl?"

`LogoInvoiceProvider.cancelInvoice(ettn)` → `SendDocument(DOCUMENTTYPE=CANCELEARCHIVEINVOICE, UUID=ettn)`. **Sadece e-Arşiv** için. e-Fatura iptal edilmez, ancak "iade faturası" kesilir (henüz implement değil — gerekirse eklenecek).

### "PDF nereden geliyor?"

İki yer:
1. `LogoInvoiceProvider.downloadInvoicePdf(ettn)` → SOAP `GetDocumentData(uuid, EINVOICE/EARCHIVE, PDF)` çağrısı yapar, byte[] döner
2. Bu byte'ları Caffeine cache'te 24h tutuyoruz (her isteğinde Logo'ya gitmemek için)
3. Admin/store endpoint'leri bu byte'ları response'a yazar

### "Tasarım şablonu nasıl değiştirilir?"

Logo panel → Tasarım Yönetimi → yeni şablon yükle (XSLT veya görsel editör). Sonra `logo_earsiv_design_file` / `logo_efatura_design_file` setting'lerine **şablon adını** gir. SendDocument paramList'inde gönderilir.

---

## 6. eLogo Panel'inde Tipik Menüler

Muhtemelen göreceğin başlıklar (Logo arayüzü versiyonlara göre değişir):

| Menü | Burada bulacakların |
|------|---------------------|
| **Profil / Hesap Bilgileri** | Web servis kullanıcı adı, şifre, firma bilgileri |
| **e-Faturalarım / Giden Faturalar** | Gönderilmiş faturalar listesi, durum, PDF indir |
| **Posta Kutusu / Adres Defteri** | Tüzel müşteri alias'ları |
| **Tasarım Yönetimi** | Fatura PDF şablonları |
| **Web Servis Bilgileri** | API endpoint, credentials, test modu |
| **Mükellef Listesi** | Hangi VKN'ler e-fatura mükellefi |
| **Raporlar** | Aylık kesilen fatura sayısı, kullanım kotası |

### Yapacağın Sırayla

1. **Profil/Firma Bilgileri** → Vergi no, unvan, adres bilgilerini doğrula (`logo_company_*` alanlarına gir)
2. **Web Servis Bilgileri** → username/password al (`logo_efatura_username/password`)
3. **Test mi prod mu?** → Eğer demo hesap verdiyse önce test_mode=true, çalıştır gör
4. **Tasarım Yönetimi** → Default şablon var mı, isim ne (`logo_earsiv_design_file`)
5. **Adres Defteri** → Boş ise sorun yok (yeni tüzel müşteri geldiğinde otomatik dolar)

---

## 7. Diagnostic Komutları

### Backend log'unda Logo'ya özel filtre

Grafana Loki'de:
```logql
{service="warehouse-backend"} | json | logger=~".*LogoInvoiceProvider.*"
```

Veya doğrudan Railway logs:
```
LogoInvoiceProvider
```
ile filtrele.

### SQL — son faturaların durumu

```sql
SELECT
    i.id,
    i.invoice_number,
    i.ettn,
    i.status,
    i.is_individual,
    i.created_at,
    i.error_message,
    o.order_number,
    o.total_amount
FROM invoices i
LEFT JOIN orders o ON i.order_id = o.id
ORDER BY i.created_at DESC
LIMIT 20;
```

### SQL — kaç fatura ERROR durumda

```sql
SELECT status, COUNT(*) FROM invoices GROUP BY status;
```

---

## 8. Müşteriden Senin İstemen Gerekenler

Aşağıdaki listeyi müşteriye gönder, doldurup geri yollasın:

```
─────────────────────────────────────
eLogo Entegrasyonu — Bilgi Talebi
─────────────────────────────────────

1. Web Servis Bilgileri:
   - Web servis kullanıcı adı : _______________
   - Web servis şifresi       : _______________
   - Test/Demo hesabı var mı? : Evet ☐  Hayır ☐
     Varsa test username       : _______________
     Varsa test password       : _______________

2. Şirket Bilgileri (Vergi Levhası'ndan):
   - VKN (Vergi No)          : _______________
   - Resmi Unvan             : _______________
   - Vergi Dairesi           : _______________
   - MERSİS No               : _______________
   - Ticaret Sicil No        : _______________
   - Tam Adres               : _______________
   - Şehir / İlçe / Posta    : _______________
   - Kurumsal Email          : _______________
   - Telefon (uluslararası)  : _______________
   - Website                 : _______________
   - Banka adı + IBAN (ops.) : _______________

3. Tasarım:
   - e-Arşiv şablon adı      : _______________
     (boşsa Logo default kullanılacak)
   - e-Fatura şablon adı     : _______________

4. KEP Adresi:
   - KEP adresi              : _______________
     (B2B yasal iletişim için zorunlu)
```

Bu bilgiler eline geçince, admin panel veya SQL ile `site_settings` tablosuna gir. Sonra test sipariş ver → fatura otomatik kesilmesi gerekir.

---

## 9. Eğer Bir Yerde Takılırsan

| Hata | Olası Sebep | Çözüm |
|------|-------------|-------|
| `Logo Login FAILED` | Yanlış credentials veya test/prod karışıklığı | `logo_efatura_test_mode` ile endpoint'in uyumunu kontrol |
| `SchemaValidationFailed` | UBL-TR'de eksik alan (genelde `logo_company_*` boş) | Sanity check SQL'i çalıştır, eksikleri doldur |
| `Invalid VKN` | Şirket VKN'si 10 hane değil veya başında 0 var | 10 hane formatla |
| `Customer not e-invoice user` | Tüzel müşteri henüz e-fatura mükellefi değil | Otomatik e-Arşiv'e düşmeli — kodda fallback var |
| `Timeout` | eLogo SOAP servisi yavaş | RestTemplate timeout artır (varsayılan 30sn) |
| Fatura kesildi ama email gitmedi | SMTP problem (Faz 5.5) | EmailService log'larına bak |

---

**Tebrikler — eLogo entegrasyonu hazır. Test, prod credentials geçişi, ve 2-3 gerçek sipariş izleme sonrası tam canlı.**
