# Refactoring Log

## Faz 6: Kod Kalitesi Iyilestirme

Tarih: 2026-04-01
Test sonucu: 103 unit test PASS (0 fail)

---

### Magic Numbers → Named Constants

| Dosya | Sorun | Yapilan | Kategori |
|-------|-------|---------|----------|
| CartServiceImpl.java | `new BigDecimal("29.99")` ve `"500"` hard-coded kargo ucreti | `ShippingConstants.calculateShippingCost(subtotal)` kullanildi | Magic Number |
| CheckoutServiceImpl.java (2 yer) | Ayni kargo hesaplama tekrari | `ShippingConstants.calculateShippingCost(subtotal)` kullanildi | Magic Number + DRY |
| **YENİ: ShippingConstants.java** | — | `DEFAULT_SHIPPING_COST`, `FREE_SHIPPING_THRESHOLD`, `calculateShippingCost()` static metodu | Magic Number |

### Hard-coded Strings → Enum.name()

| Dosya | Sorun | Yapilan | Kategori |
|-------|-------|---------|----------|
| PaymentServiceImpl.java | `"PENDING_PAYMENT"`, `"PAID"`, `"CANCELLED"`, `"REFUNDED"` string literal'lari | `OrderStatus.PENDING_PAYMENT.name()` vb. enum kullanimlarina cevirildi | Hard-coded String |
| PaymentServiceImpl.java | `"BANK_TRANSFER".equals(order.getPaymentMethod())` | `PaymentProvider.BANK_TRANSFER.name().equals(...)` olarak guncellendi | Hard-coded String |
| PaymentTimeoutJob.java | `"PENDING_PAYMENT"` ve `"CANCELLED"` string | `OrderStatusHistoryFactory` ile enum bazli olusturma | Hard-coded String |
| BankTransferExpiryJob.java | Ayni string literal sorunu | `OrderStatusHistoryFactory` ile duzeltildi | Hard-coded String |

### DRY — Tekrar Eden Kod Cikarilmasi

| Dosya | Sorun | Yapilan | Kategori |
|-------|-------|---------|----------|
| StoreCartController.java | `extractCustomerId()` private metod 69 satir | `CustomerTokenExtractor.extractCustomerId(request, jwtService)` utility'ye tasindi | DRY |
| StoreCheckoutController.java | Ayni `extractCustomerId()` kopyasi | Ayni utility kullanildi, private metod silindi | DRY |
| **YENİ: CustomerTokenExtractor.java** | — | JWT token'dan customerId cikarma (header + cookie destegi) | DRY |
| PaymentServiceImpl.java | 8 satirlik `logStatusChange` helper | `OrderStatusHistoryFactory.create()` factory metodu | DRY |
| PaymentTimeoutJob.java | 12 satirlik OrderStatusHistory olusturma | Factory metodu ile tek satira indirildi | DRY |
| BankTransferExpiryJob.java | Ayni 12 satirlik blok | Factory metodu ile tek satira indirildi | DRY |
| AdminOrderController.java | Ayni pattern | Factory metodu ile duzeltildi | DRY |
| **YENİ: OrderStatusHistoryFactory.java** | — | Overloaded `create()` metotlari (enum + string) | DRY |

### Config & Timeout Degerleri → ConfigProperties

| Dosya | Sorun | Yapilan | Kategori |
|-------|-------|---------|----------|
| SecurityProperties.java | Sadece JWT secret ve expiration vardi | `customerTokenExpirationDays(7)`, `refreshTokenExpirationDays(30)`, `accountLockoutMinutes(15)`, `maxFailedLoginAttempts(5)`, `cartExpirationDays(30)` eklendi | Config |
| JwtService.java | `plusDays(7)` hard-coded musteri token suresi | `securityProperties.getCustomerTokenExpirationDays()` | Magic Number |
| CustomerAuthServiceImpl.java | `plusMinutes(15)` lockout, `>= 5` attempts, `plusDays(30)` token | Tumu `securityProperties` uzerinden configurable hale getirildi | Magic Number |
| **YENİ: .env.example** | Env degiskenleri dokumante edilmemisti | Tum zorunlu/opsiyonel env var'lar sablonlandi | Config |

### Test Uyumlu Guncellemeler

| Dosya | Sorun | Yapilan | Kategori |
|-------|-------|---------|----------|
| CustomerAuthServiceImplTest.java | Constructor degisti (SecurityProperties eklendi) | `SecurityProperties` real instance eklendi, constructor guncellendi | Test Fix |

---

## Yapilmayan (Bilinçli Kararlar)

| Sorun | Neden Yapilmadi |
|-------|-----------------|
| N+1 query fixleri (CartService, AdminCustomerService, StoreProductController) | Davranis degistirebilir, ayri performans fazi gerektirir |
| SecurityConfig role string'leri (`"ADMIN"`, `"STOCK_IN"`) | Spring Security convention, degistirmek risk |
| Admin default credentials (`admin/admin`) | Env degiskeni zaten mevcut, .env.example'da belirtildi |
| JWT secret `secret123` (dev profile) | Dev-only, production env var zorunlu |

---

## Metrikler

- **Yeni dosya:** 4 (ShippingConstants, CustomerTokenExtractor, OrderStatusHistoryFactory, .env.example)
- **Degistirilen dosya:** 12
- **Silinen tekrar kod:** ~120 satir
- **Eklenen utility kod:** ~80 satir
- **Net azalma:** ~40 satir
- **Configurable hale getirilen magic number:** 7
- **Enum'a cevrilen string literal:** 8
- **Test sonucu:** 103 unit test PASS
