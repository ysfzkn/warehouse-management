# Kargo Entegrasyonu — Kullanım Kılavuzu

Bu belge mağaza yöneticisi içindir. Teknik bilgi gerektirmez.

---

## Ne işe yarar?

Siparişi kargoya verdiğinizde gönderi **otomatik** oluşur, **en uygun fiyatlı** kargo firmasını
sistem seçer, takip numarası hem size hem müşterinize düşer. Kargo teslim edilince sipariş
kendiliğinden "Teslim Edildi"ye geçer.

Elle yapılan işler: siparişin durumunu "Kargoda" yapmak ve etiketi yazdırmak. Gerisi otomatik.

---

## Bir defaya mahsus kurulum

Bu adımlar yalnızca ilk kez yapılır.

### 1. API bilgilerini girin

**Ayarlar → Kargo API**

- Kargonomi'den aldığınız **API Token**'ı yapıştırın
- Sayfanın altından **Kaydet**

### 2. Bildirimleri açın

Aynı sayfadaki **Kargonomi Webhook Kaydı** bölümünde **Kargonomi'ye Kaydet** düğmesine basın.

Bu, kargo hareketlerinin (yola çıktı, dağıtımda, teslim edildi) sisteme anlık düşmesini sağlar.
Yapılmazsa durumlar yarım saatte bir kontrol edilerek gelir — çalışır, ama gecikmeli.

> Kargonomi bir **imza anahtarı** üretip ekrana yazar. O anahtar yalnızca bir kez görünür;
> ekranda gördüğünüzde **mutlaka Kaydet'e basın.**

### 3. Kontrol edin

**Canlıya Çıkış Kontrolü → Kontrol Et**

Her madde tek tek kontrol edilir ve sonucu yazılır:

| İşaret | Anlamı |
|---|---|
| 🟢 Yeşil | Hazır |
| 🟡 Sarı | Çalışır ama iyileştirilebilir |
| 🔴 Kırmızı | Bu giderilmeden kargo gönderilemez |

Kırmızı maddenin altında **ne yapmanız gerektiği** yazar. Hepsi yeşil/sarı olunca hazırsınız.

> **Önemli:** Kargonomi hesabınızda **bakiye** olması şart. Bakiyesiz gönderi oluşturulamaz;
> kontrol ekranı bunu kırmızı olarak gösterir.

---

## Günlük kullanım

### Kargo gönderme

1. **Siparişler** ekranında siparişin durumunu **"Kargoda"** yapın.
2. Gönderi otomatik oluşur. Satırda **Kargo Firması** ve **Kargo Takip No** belirir.
3. **Kargo Etiketi İndir** ile barkodu yazdırıp kolinin üstüne yapıştırın.

### Çok sipariş varsa

Göndermek istediğiniz siparişleri işaretleyip **Etiketleri Tek PDF İndir** deyin.
Hepsi tek dosyada gelir, arka arkaya yazdırırsınız.

### Kargo nerede?

Sipariş detayında **Kargo Hareketleri** bölümü var: kargo ne zaman çıktı, nerede, teslim edildi
mi — tarih sırasıyla listelenir. **Kargo Takip Sayfasına Git** ile kargo firmasının kendi takip
sayfasına geçebilirsiniz.

### Teslimat

Kargo teslim edildiğinde sipariş **otomatik olarak** "Teslim Edildi" durumuna geçer.
Elle işaretlemenize gerek yoktur. Stok düşümü de bu anda yapılır.

Teslim edilemeyen, kaybolan veya geri dönen kargolarda size **bildirim** gelir.

---

## Müşteriniz ne görüyor?

Müşteri kendi hesabındaki **Siparişlerim** sayfasında:

- **Takip Numarası**
- Kargonun nerede olduğu (**Kargo Hareketleri** — sizin gördüğünüz listenin aynısı)
- **Kargo Takip Sayfasına Git** bağlantısı

Yani "kargom nerede" sorusu için sizi aramasına gerek kalmıyor.

---

## Ayarlar ekranındaki diğer bölümler

### Kargo Firmaları

Hangi firmalarla çalışacağınızı buradan yönetirsiniz. Her firma için:

- **Azami desi** — bu firmanın kabul ettiği en büyük paket. Boş bırakılırsa sınır yok.
- **Gitmediği yerler** — firmanın hizmet vermediği il/ilçeler. Buraya yazdıklarınız o firma
  için otomatik olarak elenir, gönderi başka firmaya yönlendirilir.

### Ürün ölçüleri

Kargo ücreti **desi** ile hesaplanır: paketin ağırlığı ile hacminden hangisi büyükse o.

Ürünlerin ağırlık ve en/boy/yükseklik bilgisi girilmemişse gönderi **olduğundan hafif**
bildirilir, kargo firması farkı sonradan faturaya yansıtır. Kontrol ekranı kaç üründe eksik
olduğunu söyler; **Ürünler** ekranından girebilirsiniz.

### Gönderici deposu

Gönderici adresiniz. Kargonomi'de bir depo tanımlayıp kimliğini buraya kaydederseniz gönderiler
daha güvenilir oluşturulur. Tanımlanmazsa adres bilgileri her gönderide tek tek yollanır —
çalışır, ama depo tanımlamak daha sağlamdır.

---

## Bir şey ters giderse

**Canlıya Çıkış Kontrolü → Kontrol Et** sizi doğru yere götürür. Kırmızı maddenin altındaki
açıklama ne yapılacağını söyler.

Sık karşılaşılanlar:

| Belirti | Sebebi |
|---|---|
| "Token reddedildi" | API token'ı hatalı ya da süresi dolmuş — Kargonomi'den yenisini alın |
| "Bakiye bildirmiyor" | Kargonomi hesabında yüklü bakiye yok |
| "Webhook kaydı yok" | Kurulum 2. adımı yapılmamış |
| Sipariş "Kargoda" ama takip no gelmedi | Gönderi kuyruğa alınmıştır, birkaç dakika içinde tekrar denenir |

Son maddede sistem kendi kendine **5 kez** tekrar dener. Beşi de başarısız olursa size bildirim
gelir ve gönderiyi elle oluşturmanız gerekir.
