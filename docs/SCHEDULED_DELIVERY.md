# Planlı (İleri Tarihli) Teslimat

Depo çıkış makbuzu bugün kesiliyor, mal ileri bir tarihte teslim ediliyor. Müşteri
elindeki kâğıtla çıkıyor; mal o tarihe kadar depoda duruyor ve **stoktan ancak teslimat
kapatıldığında düşüyor.**

---

## 1. Neden ayrı bir hâl

Depo çıkışının klasik hâlinde mal, makbuz imzalanırken fiziken binadan çıkıyor: stok o an
düşüyor ve sevkiyat aynı adımda `COMPLETED` oluyor. Sahadaki akış çoğu zaman böyle değil —
kâğıt bugün veriliyor, teslimat önümüzdeki salı.

O aradaki birkaç gün için iki yanlış cevap var:

- **Stoktan düşmek.** Mal rafta duruyorken sayım onu görmez; raf ile kayıt ayrışır.
- **Hiç dokunmamak.** Aynı mal başka bir müşteriye satılır ve salı günü teslim edilecek
  bir şey kalmaz.

Doğru cevap ikisinin arası: **rezervasyon.** Mal rafta, ama kimseye söz verilemez.
Sistemde bu hâl zaten vardı (`stocks.reserved_quantity`, transfer `IN_TRANSIT`) ve planlı
çıkış onu kullanıyor — yeni bir stok yolu açmıyor.

---

## 2. Akış

```
Depo Çıkış Makbuzu → "İleri tarihli teslimat"
      │
      │  scheduled_delivery_at girilir
      ▼
Mal REZERVE edilir  ──► makbuz basılır (DC-2026-000042, üzerinde planlanan tarih)
      │                       stok DÜŞMEZ
      │
      ├── teslimden 1 gün önce ─► bildirim + e-posta
      ├── teslim günü          ─► bildirim + e-posta
      └── tarih geçtiyse       ─► gecikme uyarısı (tek sefer)
      │
      ▼
"Teslimatı Tamamla"  ──►  STOK BURADA DÜŞER
 (teslim alan + tarih)     rezervasyon kapanır, sevkiyat COMPLETED,
                           makbuz DELIVERED
```

Alternatif uçlar:

- **Tarihi Değiştir** — mal rezervede kalır, hatırlatma damgaları sıfırlanır ve yeni tarih
  kendi "yarın teslim" uyarısını alır. İptal edip yeniden açmanın alternatifi: o yol aynı
  mal için ikinci bir makbuz numarası üretir ve müşterinin elindeki kâğıt karşılıksız kalırdı.
- **İptal** — sıradan sevkiyat iptali. `IN_TRANSIT` dalı rezervasyonu zaten geri bırakıyor,
  planlı çıkış için ek bir kod yok.

---

## 3. Ekranlar

| Nerede | Ne görünür / yapılır |
|---|---|
| Depo Çıkış Makbuzu modalı | Üstte iki seçenek: **Şimdi teslim ediliyor** / **İleri tarihli teslimat**. İkincisi seçilince "Planlanan Teslim Tarihi" alanı açılır ve "Çıkış Tarihi" etiketi **Belge Tarihi**'ne döner |
| Transfer listesi → Durum sütunu | Planlı sevkiyatta "Yolda" yerine **Planlandı / Yarın Teslim / Bugün Teslim / Gecikmiş** ve altında tarih |
| Transfer listesi → sayaçlar ve filtre | **Planlı Teslimat** kartı ve **Planlı** filtresi — açık planların kuyruğu |
| Transfer listesi → İşlemler | Planlı sevkiyatta "Tamamla" yerine **Teslimatı Tamamla** — detayı makbuz panelinde açar |
| Transfer detayı → Makbuz paneli | Planlanan teslimat kartı: geri sayım, **Teslimatı Tamamla**, **Tarihi Değiştir** |
| Teslimat Makbuzları (arşiv) | **Planlı · tarih** rozeti; tarih sütununda teslim olmamışsa "(planlanan)" |
| Ayarlar → Depo → Teslimat Hatırlatmaları | Ana şalter ve hatırlatma e-posta adresi |

Listedeki durum rozeti neden `status` alanından türetilmiyor: planlı sevkiyatın durumu
`IN_TRANSIT` ("rezerve tutuluyor") ama mal depoda. Rozete "Yolda" yazmak, listeye bakan
kişiyi malı aramaya gönderirdi.

Aynı gerekçeyle sayaç da ayrıldı: planlı teslimatlar `IN_TRANSIT` sayacından düşülüp
kendi **Planlı Teslimat** kartında toplanıyor — aksi hâlde aynı kayıt listede "Planlandı",
sayaçta "Yolda" görünür, iki ekran birbirini tutmazdı. Bunun için yeni bir sayım sorgusu
açılmadı: mevcut sorguya ikinci bir gruplama ekseni eklendi, çünkü o dosyadaki WHERE
koşulunun dördüncü bir kopyası zamanla diğerlerinden sessizce ayrışırdı.

**Planlı** filtresi sunucuya `status` olarak değil `scheduledOnly=true` olarak gidiyor:
planlı bir sevkiyat `PENDING` de `IN_TRANSIT` de olabilir, kesit durumun üstüne biniyor.

---

## 4. Hatırlatmalar

Tek bir kural var ve hem günlük job hem de plan kurulumu aynı metodu çağırıyor
(`ScheduledDeliveryReminderService`):

> **Vakti gelmiş aşama gönderilir, vakti geçmiş aşama gönderilmeden kapatılır.**

| Aşama | Ne zaman | Kanal |
|---|---|---|
| `DAY_BEFORE` | teslimden bir gün önce | panel bildirimi + e-posta |
| `DUE_TODAY` | teslim günü | panel bildirimi + e-posta |
| `OVERDUE` | tarih geçti, teslimat kapanmadı | panel bildirimi + e-posta, **tek sefer** |

**Tekrar etmemesini sağlayan şey bir bayrak değil, aşama başına damga:**
`reminder_day_before_at`, `reminder_due_day_at`, `reminder_overdue_at`. Job günde bir kez
koşuyor ama iki instance aynı anda koşsa, elle tetiklense veya gün içinde plan ertelenip
yeniden taransa bile damgalı aşama ikinci kez gitmiyor.

**Vakti geçmiş aşamanın postalanmadan damgalanması** gürültü kontrolü: teslim günü sabahı
"yarın teslim edilecek" maili atmak hatırlatma değil, hatırlatmalara güveni bitiren şeydir.

**Gün içinde kurulan planlar.** Job sabah 08:00'de koşuyor. Öğleden sonra bugüne ya da
yarına kurulan bir teslimat, o günün taraması geçtiği için her iki pencereyi de kaçırır ve
ancak günler sonra gecikme uyarısı olarak görünürdü. Bu yüzden plan kaydedilir kaydedilmez
(`ScheduledDeliveryPlannedEvent`, `AFTER_COMMIT`) aynı tarama o tek sevkiyat için
çalıştırılıyor — job'ı sıklaştırmak yerine, çünkü kural tek kopya kalmalı.

### Alıcı adresi

`delivery_reminder_email` → boşsa `invoice_admin_digest_email` → boşsa
`contact_form_email`. Hiçbiri yoksa **panel bildirimi yine düşer**, sadece mail gitmez ve
log'a uyarı yazılır. Özelliğin "çalışıyor görünüp kimseye ulaşmaması" bu yüzden mümkün değil.

`delivery_reminder_enabled` kapatılırsa hatırlatma gönderilmez; planlı çıkış oluşturmayı
engellemez. Kapalıyken **aşamalar damgalanmaz** — damgalansaydı, ayarı sonradan açan kişi o
güne kadar birikmiş bütün hatırlatmaları sessizce kaybederdi. Kapatma kararı geçmişi silmiyor.

---

## 5. Kâğıt

Aynı şablon (`templates/receipt/delivery-receipt.html`), `scheduled` bayrağıyla dallanıyor:

- Başlığın yanında **PLANLI TESLİMAT · tarih** rozeti — kâğıdı eline alanın ilk anlaması
  gereken şey malın henüz teslim edilmediği.
- "Çıkış Tarihi" satırı **Belge Tarihi** olur, altına **Planlanan Teslim** satırı eklenir.
- İmza bloklarındaki teslim tarihi çizgisi boş kalır: mal henüz çıkmadı, bugünün tarihini
  basmak imzalanmamış bir teslimi olmuş gibi gösterirdi.
- Kapanış paragrafı değişir. Standart metin "teslim alınmıştır" diyor; planlı çıkışta mal
  hâlâ depoda ve olmamış bir teslimi yazan belge imzalatmak, ihtilafta imzalayanın aleyhine
  delil üretirdi.

Makbuzdaki `scheduled_delivery_at` **basıldığı andaki** tarihi donduruyor. Teslim sonradan
ertelenirse müşterinin elindeki nüshada hâlâ eski tarih yazar ve yazmalı; panel güncel
tarihi sevkiyattan okur. Kâğıdı tazelemek için **Yeniden Bas** (numara değişmez).

---

## 6. Stokun tek kapısı

Planlı çıkış, stokun çıkabileceği ikinci bir kapı **değil**. Düşüm yine
`StockTransferServiceImpl.completeTransfer` üzerinden, `IN_TRANSIT` dalından yapılıyor —
sıradan bir sevkiyatın tamamlanmasıyla aynı kod, aynı denetim izi. Planlı çıkışın tek
yaptığı o çağrıyı teslim gününe ertelemek.

`ServiceHandoverService.completeScheduledDelivery` stok düşümü ile makbuzun imza kaydını
**tek işlemde** yapıyor. İkiye bölünseydi ikinci çağrının hatası, stoğu düşmüş ama malı
kimin aldığı yazmayan bir kayıt bırakırdı — kâğıt ile kaydın ayrıştığı yer tam olarak orası.

Sıradan teslim onayı (`.../receipt/confirm`) açık bir planda **reddediliyor**: o uç nokta
kâğıda bilgi işliyor, stoğa dokunmuyor. Kullanılabilseydi makbuz "teslim edildi" derken mal
rezervede kalır, stok ne çıkmış ne serbest olurdu ve fark ancak sayımda görünürdü. Teslimat
kapandıktan sonra aynı uç nokta yine açık — isim düzeltmek ikinci bir stok hareketi değil.

---

## 7. Şema

`stock_transfers`

| Sütun | Anlamı |
|---|---|
| `scheduled_delivery_at` | Planlanan teslim tarihi. `NULL` = klasik akış (mal çıkışta teslim edildi) |
| `reminder_day_before_at` | "1 gün önce" aşamasının işlendiği an |
| `reminder_due_day_at` | "Teslim günü" aşamasının işlendiği an |
| `reminder_overdue_at` | Gecikme uyarısının işlendiği an |

`delivery_receipts.scheduled_delivery_at` — makbuz basıldığı andaki plan tarihi.

Kısmi indeks `idx_stock_transfers_scheduled_open`: job'ın taradığı küme yalnızca planı olan
ve hâlâ açık (`PENDING` / `IN_TRANSIT`) sevkiyatlar. Tamamlanan ve iptal edilenler
çoğunluğu oluşturduğu için tablo büyüdükçe tarama sabit kalıyor.

Migration: `V114__scheduled_delivery.sql`.

---

## 8. Uçlar

| Metot | Yol | Rol | Stoğa etkisi |
|---|---|---|---|
| POST | `/api/admin/stock-transfers/service-handover` (`scheduledDeliveryAt` dolu) | ADMIN + STOCK_OUT | **rezerve eder** |
| POST | `/api/admin/stock-transfers/{id}/scheduled-delivery/complete` | ADMIN + STOCK_OUT | **stoktan düşer** |
| PUT | `/api/admin/stock-transfers/{id}/scheduled-delivery` | ADMIN + STOCK_OUT | dokunmaz |
| POST | `/api/admin/stock-transfers/{id}/cancel` | mevcut kural | rezervasyonu bırakır |
| GET | `/api/admin/stock-transfers?scheduledOnly=true` | mevcut kural | — (açık planların kuyruğu) |

---

## 9. Denetim ve bildirim

| Aksiyon / başlık | Ne zaman |
|---|---|
| `TRANSFER_START` (audit) | plan kuruldu, mal rezerve edildi |
| `TRANSFER_UPDATE` (audit) | teslim tarihi değiştirildi (eski → yeni, sebep) |
| `TRANSFER_COMPLETE` (audit) | teslimat kapatıldı, stok düştü |
| "Planlı Teslimat Oluşturuldu" | plan kurulduğunda |
| "Teslim Tarihi Değişti" | ertelendiğinde |
| "Teslimat Hatırlatması" | 1 gün önce / teslim günü |
| "Gecikmiş Teslimat" | tarih geçtiğinde |
| "Planlı Teslimat Tamamlandı" | teslimat kapatıldığında |

---

## 10. Testler

`src/test/java/com/warehouse/service/ScheduledDeliveryTest.java` — planın stoğu rezerve
ettiği, düşümün yalnızca teslimatta olduğu, iki kez kapatılamadığı, ertelemenin
hatırlatmaları yeniden açtığı ve her aşamanın tam bir kez gittiği burada sabitleniyor.
