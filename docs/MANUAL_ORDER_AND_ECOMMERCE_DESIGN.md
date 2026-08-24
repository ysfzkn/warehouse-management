# Manuel Sipariş ve E-Ticaret Yayın Yönetimi

## Amaç

Admin; telefon, WhatsApp, mağaza ve diğer kanallardan gelen siparişi stok rezervasyonu ve ödeme kaydıyla oluşturabilir. Siparişin kanalı, referansı, oluşturan admin, ödeme planı ve hatırlatma zamanı izlenir. Ürünün depo operasyonlarındaki aktifliği ile e-ticarette yayınlanması birbirinden bağımsız yönetilir.

## Akış

```text
Admin formu -> ManuelOrderService -> Müşteri bul/oluştur
                              -> Ürün fiyatı + stok kilidi/rezervasyon
                              -> Order + OrderItem
                              -> PaymentTransaction
                              -> OrderStatusHistory

paymentReminderAt -> ManualPaymentReminderJob -> Admin bildirimi
```

## Kararlar

- `orderChannel`, ödeme yönteminden ayrıdır. WhatsApp siparişi havale veya kapıda ödeme olabilir.
- E-posta vermeyen manuel müşteriye benzersiz `@local.invalid` adres atanır; bu adres gerçek bildirim adresi olarak kullanılmamalıdır.
- Manuel fiyat, sipariş satırındaki `unitPrice` ve ürün snapshot'ında `manualPrice=true` ile korunur.
- Stok, mevcut iptal/teslim mekanizmasıyla uyum için tek bir depodan rezerve edilir. Toplam stok yeterli fakat tek depo yetersizse sipariş reddedilir. Çok depolu allocation desteği büyüme aşamasında ortak stok yaşam döngüsü servisine taşınmalıdır.
- `ecommerceVisible` varsayılan olarak `true` olur; migration sonrası mevcut ürünlerin tamamı yayında kalır.
- Storefront liste ve slug sorguları hem `isActive=true` hem `ecommerceVisible=true` ister. `isActive`, ürünün operasyonel kullanılabilirliğini korur.

## API

- `POST /api/admin/orders/manual`: manuel sipariş oluşturur.
- `PUT /api/admin/orders/{id}/payment-received`: bekleyen manuel ödemeyi alındı yapar.
- `GET /api/admin/orders?channel=WHATSAPP`: kanal filtresi.
- `GET /api/admin/products?ecommerceVisible=true&hasImage=true`: yayın/fotoğraf filtresi.
- `PUT /api/admin/products/bulk-ecommerce-visibility`: `{ "ids": [1,2], "visible": false }`.
- `POST /api/admin/orders/{id}/customer-confirmation-link`: yedi günlük, tek siparişe bağlı müşteri onay linki üretir.
- `GET/POST /api/store/public/orders/confirm/{token}`: onay özetini gösterir ve sözleşme zaman damgalarını kaydeder.
- `PUT /api/admin/orders/{id}/payment-plan`: manuel siparişin vade ve hatırlatma planını günceller.

## Havale akışı değerlendirmesi

Mevcut sistem; banka referansı, son ödeme zamanı, tekrar görüntüleme ekranı, admin tutar/tarih doğrulaması, tolerans kontrolü, güvenlik kodu ve süre sonu stok iadesine zaten sahiptir. Manuel siparişler ayrı bir havale modeli oluşturmadan aynı `PaymentTransaction` altyapısına bağlandı. Böylece ödeme listesi, onay ve raporlama tek kaynakta kalır.

## Büyüdüğünde yeniden ele alınacaklar

- WhatsApp Business webhook ile otomatik lead/message eşleştirme.
- Birden fazla depoya bölünmüş rezervasyonları ortak `OrderAllocation` tablosuyla yönetme.
- CMS hukuki metin snapshot'ları tam içerik, sayfa kimliği, güncelleme zamanı ve SHA-256 ile siparişte saklanır; müşteri görüntüledikten sonra metin değişirse onay reddedilip yeniden okuması istenir.
- Hatırlatma için tekrarlama/escalation politikası ve müşteri kanalına otomatik gönderim.
- Manuel özel fiyat için rol bazlı indirim limiti ve zorunlu gerekçe.
