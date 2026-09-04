# Teslimat Makbuzu

Müşteriye çıkan sevkiyatlarda şoförün elinde götürdüğü, karşı tarafın imzaladığı ve
firmaya geri getirdiği kâğıdın sistemdeki karşılığı.

---

## 1. Akış

```
Sevkiyat oluştur
      │
      ▼
Makbuz Düzenle ──────► PDF / Yazdır  ──► şoför iki nüsha götürür
   (TM-2026-000001)                        (firma + müşteri)
      │                                          │
      │                                    müşteri imzalar
      │                                          │
      ▼                                          ▼
Teslimatı Onayla ◄──────────────────  İmzalı Nüsha Yükle
 (teslim alan, tarih)                  (fotoğraf veya PDF)
```

Ekranlar:

| Nerede | Ne yapılır |
|---|---|
| Stok Yönetimi → Transfer Geçmişi → satır | Makbuz varsa **tek tıkla PDF iner**; yoksa detay açılır |
| Aynı liste → seçim → **Makbuzları İndir** | Seçili sevkiyatların makbuzları **tek PDF** |
| Transfer detayı → Teslimat Makbuzu paneli | Düzenle, yazdır, indir, yeniden bas, teslim onayı, imzalı nüsha yükle |
| Teslimat Makbuzları (admin) | Arşiv: arama, durum/tarih filtresi, **imzalı nüsha bekleyenler** |

Satırdaki **Makbuz** düğmesinin köşesindeki nokta imzalı nüsha durumunu gösterir:
yeşil = geldi, sarı = bekleniyor.

---

## 2. Neden alanlar kopyalanıyor

`delivery_receipts` tablosundaki müşteri, şoför, plaka ve kalem bilgileri
`stock_transfers`'ta zaten var ama makbuza **kopyalanır**. Sebep: makbuz basıldıktan
sonra transferde şoför değişirse ya da adres düzeltilirse, imzalanan kâğıtla sistemdeki
kayıt birbirini tutmaz hâle gelir — ve anlaşmazlıkta geçerli olan imzalanan kâğıttır.

Makbuz basıldığı andaki gerçeği dondurur. Düzeltme gerekiyorsa **Yeniden Bas**:
numara aynı kalır, `revision` artar, yeni değerlerle yeni bir kâğıt çıkar. İki farklı
numaralı kâğıdın dolaşıma girmesi böylece engellenir.

---

## 3. PDF üretimi

Tek Thymeleaf şablonu (`templates/receipt/delivery-receipt.html`) iki çıktı verir:

- **Yazdırılabilir HTML** — `GET .../receipt/print`, tarayıcıda `Ctrl+P`
- **PDF** — aynı HTML, openhtmltopdf ile render edilir

Kaynak tek olduğu için ekranda görünen ile arşivlenen belge ayrışamaz.

### Dikkat edilecek iki nokta

**Font.** `src/main/resources/fonts/NotoSans-*.ttf` zorunlu. PDFBox'ın yerleşik
Helvetica'sı WinAnsi'dir ve `ı ğ ş İ Ğ Ş` harflerini içermez — "Işık Mobilya" ya "Isik"
diye ya da boş kutu olarak basılırdı. Prod imajı (`eclipse-temurin:21-jre-alpine`) hiç
font içermediği için font uygulamayla birlikte gelmek zorunda.
`DeliveryReceiptServiceTest.pdfContainsTurkishCharacters` PDF'ten metni geri çıkarıp
kontrol eder; font kaldırılırsa test kırmızı olur.

**Logo.** Antetteki logo `site_logo` ayarından okunur ve PDF'e **data URI olarak
gömülür** — dışarıdan çekilseydi her PDF üretimi sunucudan giden bir HTTP isteğine
dönerdi. Medya tipi dosya adından değil **baytlardan** tespit edilir: içerik doğrulaması
eklenmeden önce yüklenen logolar çoğunlukla `.png` yolunda duran JPEG baytlarıdır
(production'daki logo şu an tam olarak böyle). Tarayıcı bunu sniff'leyip geçiyor ama
`data:image/png` altına JPEG gömmek PDF'te logoyu **sessizce yok eder**.
`logoIsEmbeddedEvenWhenTheExtensionLies` testi bu durumu sabitler.

PDFBox yalnızca JPEG ve PNG gömebilir. **WebP logo** sitede kusursuz görünüp makbuzun
antetini boş bırakıyordu — yine hatasız, sadece logosuz. Gömmeden önce PNG'ye
çevriliyor (ImageIO WebP'yi twelvemonkeys eklentisiyle okuyabiliyor).
`webpLogoIsTranscodedForThePdf` bunu sabitler.

Antet kutusu geniş lockup logolar için 74×26 mm; ~700×300 px ve üzeri bir görsel
baskıda net çıkar.

**CSS.** openhtmltopdf **flexbox ve grid desteklemez**. Yerleşim bilerek tablo ile
kurulmuştur. Şablonu "modernleştirmeden" önce PDF çıktısına bakın: ekranda düzgün
görünen flex kutular PDF'te üst üste biner. `height` de td üzerinde yok sayılır,
bunun yerine `padding` kullanılır.

### Şablonu gözle kontrol etme

```bash
mvn test -Dtest=ReceiptPreviewDumpTest -Dreceipt.dump=target/receipt-preview
```

Dolu bir makbuzu `makbuz.pdf`, `makbuz.html` ve sayfa başına PNG olarak yazar.
Normal test koşusunda çalışmaz (`@EnabledIfSystemProperty`).

---

## 4. Güvenlik

- **İmzalı nüsha görüntüleme** (`/api/admin/delivery-receipts/attachments/{id}/view`)
  oturum istemez, çünkü panel bunları `<img>`/`<iframe>` içinde gösteriyor ve bu
  elemanlar Bearer token gönderemez. Yetki URL'nin kendisindedir: sunucunun o ek için
  ürettiği, süresi dolan HMAC imzası (`SignedUrlService`). İmza olmadan sıralı id ile
  bütün müşterilerin imzalı teslimat belgeleri taranabilirdi.
- **Yükleme** `Content-Type` başlığına güvenmez; dosya magic byte ile tanınır, saklanan
  uzantı ve geri servis edilen content-type tespitten türetilir. `.png` adı verilmiş bir
  HTML dosyası reddedilir.
- **Yazdırılabilir HTML** imzalı URL ile değil, kimlik doğrulamalı XHR ile alınır ve
  yeni pencereye yazılır. Aksi hâlde müşteri adı ve adresi içeren çalışan bir bağlantı
  tarayıcı geçmişine ve panoya düşerdi.
- **Rol ayrımı**: makbuz basma/yükleme `/api/admin/stock-transfers/**` altında olduğu
  için depo rollerine (STOCK_IN / STOCK_OUT) açıktır — sahadaki iş budur. Arşiv listesi
  ve ek silme `/api/admin/delivery-receipts/**` altındadır ve yalnızca ADMIN'e açıktır.

---

## 5. Denetim kaydı

Kâğıdın hayat döngüsünün tamamı `audit_logs`'a yazılır:

| Aksiyon | Ne zaman |
|---|---|
| `RECEIPT_ISSUE` | makbuz ilk kez düzenlendi |
| `RECEIPT_REISSUE` | yeniden basıldı (revizyon arttı) |
| `RECEIPT_DOWNLOAD` | PDF indirildi (tekil veya toplu) |
| `RECEIPT_DELIVERY_CONFIRM` | teslim alan ve tarih kaydedildi |
| `RECEIPT_ATTACHMENT_UPLOAD` | imzalı nüsha yüklendi |
| `RECEIPT_ATTACHMENT_DELETE` | imzalı nüsha silindi |

---

## 6. Uçlar

| Metot | Yol | Rol |
|---|---|---|
| POST | `/api/admin/stock-transfers/{id}/receipt` | depo + admin |
| GET | `/api/admin/stock-transfers/{id}/receipt` | depo + admin |
| GET | `/api/admin/stock-transfers/{id}/receipt/print` | depo + admin |
| GET | `/api/admin/stock-transfers/{id}/receipt/pdf` | depo + admin |
| POST | `/api/admin/stock-transfers/{id}/receipt/confirm` | depo + admin |
| POST | `/api/admin/stock-transfers/{id}/receipt/attachments` | depo + admin |
| POST | `/api/admin/stock-transfers/receipts/by-transfers` | depo + admin |
| POST | `/api/admin/stock-transfers/receipts/bulk-pdf` | depo + admin |
| GET | `/api/admin/delivery-receipts` | ADMIN |
| GET | `/api/admin/delivery-receipts/stats` | ADMIN |
| DELETE | `/api/admin/delivery-receipts/attachments/{id}` | ADMIN |
| GET | `/api/admin/delivery-receipts/attachments/{id}/view` | imzalı URL |
