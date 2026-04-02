# Integration Specification: Payment, Auth, Stock Sync, External Services

## 1. Odeme Entegrasyonu (iyzico)

### 1.1 Genel Bakis

iyzico, Turkiye'nin en yaygin online odeme altyapi saglayicisidir. Kredi karti, banka karti, taksit, 3D Secure ve BKM Express destegi saglar.

**Neden iyzico:**
- Turkiye pazarinda en genis banka/kart destegi
- 3D Secure zorunlulugu (BDDK duzenlemesi) yerleik destegi
- Taksit secenekleri (2, 3, 6, 9, 12 ay)
- PCI DSS Level 1 uyumlulugu (kart bilgileri backend'e ulasmaz)
- Marketplace ve alt bayi destegi (gelecek icin)

### 1.2 Teknik Entegrasyon

**Maven Dependency:**
```xml
<dependency>
    <groupId>com.iyzipay</groupId>
    <artifactId>iyzipay-java</artifactId>
    <version>2.0.134</version>
</dependency>
```

**Konfigürasyon:**
```properties
# application.properties
app.payment.iyzico.api-key=${IYZICO_API_KEY}
app.payment.iyzico.secret-key=${IYZICO_SECRET_KEY}
app.payment.iyzico.base-url=https://sandbox-api.iyzipay.com  # Production: https://api.iyzipay.com
app.payment.iyzico.callback-url=${APP_BASE_URL}/api/store/payment/callback
```

### 1.3 Odeme Akisi

```
[1. Musteri "Siparisi Onayla" tiklar]
         |
         v
[2. Backend: Order olustur (PENDING_PAYMENT)]
         |
         v
[3. Backend: iyzico CheckoutFormInitialize API cagir]
    - Sepet bilgileri
    - Musteri bilgileri
    - Teslimat adresi
    - Taksit secenekleri
         |
         v
[4. Frontend: iyzico Checkout Form goster (iframe/redirect)]
    - Musteri kart bilgilerini girer
    - 3D Secure dogrulama
    - Taksit secimi
         |
         v
[5. iyzico -> Backend callback: POST /api/store/payment/callback]
    - token parameter ile sonuc sorgulama
         |
         v
[6. Backend: CheckoutForm.retrieve() ile dogrulama]
         |
    +----+----+
    |         |
  Basarili  Basarisiz
    |         |
    v         v
[7a. Order   [7b. Order
 status:      status:
 PAID]        PENDING_PAYMENT
              + hata mesaji]
         |
         v
[8. Stok islemleri (basarili odeme sonrasi)]
    - reserved_quantity onaylanir
    - Admin'e bildirim gonderilir (SSE + Notification)
```

### 1.4 Backend Service Yapisi

```java
// PaymentService interface
public interface PaymentService {
    // Checkout baslatma
    CheckoutFormInitResponse initializeCheckout(Order order, Customer customer, CustomerAddress shippingAddress);

    // Callback isleme
    PaymentResult handleCallback(String token);

    // Iade/iptal
    RefundResult refundPayment(Payment payment, BigDecimal amount);

    // Taksit sorgulama
    InstallmentInfo getInstallmentInfo(String binNumber, BigDecimal price);
}
```

```java
// iyzico CheckoutFormInitialize ornegi
public CheckoutFormInitResponse initializeCheckout(Order order, Customer customer, CustomerAddress address) {
    CreateCheckoutFormInitializeRequest request = new CreateCheckoutFormInitializeRequest();
    request.setLocale(Locale.TR.getValue());
    request.setConversationId(order.getOrderNumber());
    request.setPrice(order.getSubtotal());
    request.setPaidPrice(order.getGrandTotal());
    request.setCurrency(Currency.TRY.name());
    request.setBasketId(order.getOrderNumber());
    request.setPaymentGroup(PaymentGroup.PRODUCT.name());
    request.setCallbackUrl(callbackUrl);
    request.setEnabledInstallments(List.of(2, 3, 6, 9, 12));

    // Alici bilgileri
    Buyer buyer = new Buyer();
    buyer.setId(String.valueOf(customer.getId()));
    buyer.setName(customer.getFirstName());
    buyer.setSurname(customer.getLastName());
    buyer.setEmail(customer.getEmail());
    buyer.setIdentityNumber(customer.getTcKimlikNo() != null ? customer.getTcKimlikNo() : "11111111111");
    buyer.setRegistrationAddress(address.getAddressLine());
    buyer.setCity(address.getCity());
    buyer.setCountry("Turkey");
    buyer.setIp(order.getIpAddress());
    request.setBuyer(buyer);

    // Teslimat adresi
    Address shippingAddr = new Address();
    shippingAddr.setContactName(address.getFirstName() + " " + address.getLastName());
    shippingAddr.setCity(address.getCity());
    shippingAddr.setCountry("Turkey");
    shippingAddr.setAddress(address.getAddressLine());
    request.setShippingAddress(shippingAddr);
    request.setBillingAddress(shippingAddr); // Ayni veya farkli

    // Sepet kalemleri
    List<BasketItem> basketItems = new ArrayList<>();
    for (OrderItem item : order.getItems()) {
        BasketItem bi = new BasketItem();
        bi.setId(String.valueOf(item.getProductId()));
        bi.setName(item.getProductSnapshot().get("name").asText());
        bi.setCategory1(item.getProductSnapshot().get("category").asText());
        bi.setItemType(BasketItemType.PHYSICAL.name());
        bi.setPrice(item.getLineTotal());
        basketItems.add(bi);
    }
    request.setBasketItems(basketItems);

    return CheckoutFormInitialize.create(request, iyzicoOptions);
}
```

### 1.5 Kapida Odeme

```java
// Kapida odeme icin iyzico atlanir
public Order createDoorPaymentOrder(Order order) {
    order.setPaymentMethod("DOOR_CASH"); // veya "DOOR_CARD"
    order.setStatus("PREPARING"); // Odeme beklenmez, direkt hazirlama
    // Stok rezervasyonu onaylanir
    confirmStockReservation(order);
    // Admin'e bildirim
    notificationService.create("Yeni Kapida Odeme Siparisi", ...);
    return orderRepository.save(order);
}
```

### 1.6 Taksit Sorgulama

```java
// BIN numarasina gore taksit secenekleri
public InstallmentInfo getInstallmentInfo(String binNumber, BigDecimal price) {
    RetrieveInstallmentInfoRequest request = new RetrieveInstallmentInfoRequest();
    request.setLocale(Locale.TR.getValue());
    request.setBinNumber(binNumber); // Ilk 6 hane
    request.setPrice(price);
    return InstallmentInfo.retrieve(request, iyzicoOptions);
}
```

### 1.7 Iade (Refund)

```java
public RefundResult refundPayment(Payment payment, BigDecimal amount) {
    CreateRefundRequest request = new CreateRefundRequest();
    request.setLocale(Locale.TR.getValue());
    request.setConversationId(payment.getConversationId());
    request.setPaymentTransactionId(payment.getProviderPaymentId());
    request.setPrice(amount);
    request.setCurrency(Currency.TRY.name());
    request.setIp(payment.getIpAddress());
    return Refund.create(request, iyzicoOptions);
}
```

---

## 2. Musteri Auth Sistemi

### 2.1 Auth Mimarisi

```
+-------------------+     +-------------------+
|  Admin Auth       |     |  Customer Auth    |
|  (Mevcut)         |     |  (Yeni)           |
+-------------------+     +-------------------+
| Entity: User      |     | Entity: Customer  |
| Table: users      |     | Table: customers  |
| Roles: ADMIN,     |     | Role: CUSTOMER    |
|   STOCK_IN,       |     |                   |
|   STOCK_OUT       |     |                   |
| JWT exp: 8h       |     | JWT exp: 7d       |
| Endpoint:         |     | Endpoint:         |
|   /api/admin/auth |     |   /api/store/auth |
+-------------------+     +-------------------+
         |                          |
         +----------+---------------+
                    |
            +-------+-------+
            |  JwtService   |
            | (genisletilmis)|
            | claim: userType|
            +---------------+
```

### 2.2 JWT Token Yapisi

**Admin Token:**
```json
{
  "sub": "admin_username",
  "role": "ADMIN",
  "userType": "admin",
  "iat": 1711843200,
  "exp": 1711872000
}
```

**Customer Token:**
```json
{
  "sub": "customer@email.com",
  "customerId": 12345,
  "userType": "customer",
  "iat": 1711843200,
  "exp": 1712448000
}
```

### 2.3 Kayit Akisi

```
[1. Musteri kayit formu doldurur]
    - Email (zorunlu, unique)
    - Sifre (min 8 karakter, 1 buyuk, 1 kucuk, 1 rakam)
    - Ad, Soyad (zorunlu)
    - Telefon (opsiyonel)
    - KVKK onay checkbox (zorunlu)
    - Pazarlama izni checkbox (opsiyonel)
         |
         v
[2. POST /api/store/auth/register]
    - Email uniqueness kontrolu
    - Sifre BCrypt ile hash'lenir
    - Customer kaydi olusturulur (email_verified=false)
    - KVKK onay timestamp kaydedilir
         |
         v
[3. Email dogrulama maili gonderilir]
    - 24 saat gecerli token
    - Link: https://www.domain.com/email-dogrula?token=xxx
         |
         v
[4. Musteri email'deki linke tiklar]
    - POST /api/store/auth/verify-email
    - email_verified=true yapilir
         |
         v
[5. Musteri login olabilir]
    - JWT token + refresh token doner
```

### 2.4 Login Akisi

```java
// CustomerAuthController
@PostMapping("/api/store/auth/login")
public ResponseEntity<CustomerLoginResponse> login(@RequestBody LoginRequest request) {
    Customer customer = customerRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new AuthException("Gecersiz email veya sifre"));

    if (!customer.isActive()) throw new AuthException("Hesap devre disi");
    if (customer.getLockedUntil() != null && customer.getLockedUntil().isAfter(now()))
        throw new AuthException("Hesap kilitli. " + customer.getLockedUntil() + " sonra tekrar deneyin.");

    if (!passwordEncoder.matches(request.getPassword(), customer.getPasswordHash())) {
        customer.setFailedLoginCount(customer.getFailedLoginCount() + 1);
        if (customer.getFailedLoginCount() >= 5) {
            customer.setLockedUntil(now().plusMinutes(30));
        }
        customerRepository.save(customer);
        throw new AuthException("Gecersiz email veya sifre");
    }

    // Basarili login
    customer.setFailedLoginCount(0);
    customer.setLockedUntil(null);
    customer.setLastLoginAt(now());
    customer.setLastLoginIp(request.getIpAddress());
    customerRepository.save(customer);

    String token = jwtService.generateCustomerToken(customer.getId(), customer.getEmail());
    String refreshToken = generateRefreshToken(customer);

    return ResponseEntity.ok(new CustomerLoginResponse(
        customer.getId(), customer.getEmail(), customer.getFirstName(),
        token, refreshToken
    ));
}
```

### 2.5 Refresh Token Mekanizmasi

```
[Access token suresi doldu (7 gun)]
         |
         v
[Frontend: POST /api/store/auth/refresh]
    Body: { refreshToken: "xxx" }
         |
         v
[Backend: Token dogrula + yeni token cift dondur]
    - Eski refresh token revoke edilir
    - Yeni access token + refresh token uretilir
    Response: { token: "yeni_jwt", refreshToken: "yeni_refresh" }
```

**Refresh token suresi:** 30 gun
**Token rotation:** Her refresh'te yeni token cifti uretilir (guvenlik)

### 2.6 Sifre Sifirlama

```
[1. POST /api/store/auth/forgot-password { email }]
    - Password reset token uretilir (UUID, 1 saat gecerli)
    - Email gonderilir: https://www.domain.com/sifre-sifirla?token=xxx
         |
         v
[2. Musteri linke tiklar, yeni sifre girer]
         |
         v
[3. POST /api/store/auth/reset-password { token, newPassword }]
    - Token dogrulanir
    - Sifre guncellenir
    - Tum refresh token'lar revoke edilir (guvenlik)
```

### 2.7 Google OAuth (Opsiyonel - Faz 2)

```properties
# application.properties
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
spring.security.oauth2.client.registration.google.scope=email,profile
```

Google OAuth ile gelen kullanici:
- Email ile mevcut `Customer` eslesiyor mu kontrol edilir
- Eslesmiyorsa yeni `Customer` olusturulur (email_verified=true, sifre NULL)
- JWT token uretilir

---

## 3. Stok Senkronizasyonu

### 3.1 Stok Durumu Sorgulama

Mevcut `Stock` entity'sindeki `getAvailableQuantity()` metodu temel alinir:

```java
// Stock.java (mevcut)
public int getAvailableQuantity() {
    return quantity - reservedQuantity - consignedQuantity;
}
```

**Storefront icin stok sorgulama endpoint'i:**
```java
// StoreProductController
@GetMapping("/api/store/products/{slug}")
public StoreProductDetailDto getProduct(@PathVariable String slug) {
    Product product = productService.getProductBySlug(slug);
    List<Stock> stocks = stockService.getStocksByProduct(product.getId());

    int totalAvailable = stocks.stream()
        .mapToInt(Stock::getAvailableQuantity)
        .sum();

    return StoreProductDetailDto.builder()
        .slug(product.getSlug())
        .name(product.getName())
        .price(product.getPrice())
        .vatRate(product.getVatRate())
        .sctRate(product.getSctRate())
        .stockStatus(totalAvailable > 0 ? "IN_STOCK" : "OUT_OF_STOCK")
        .availableQuantity(totalAvailable)
        .images(product.getImages())
        // ... diger alanlar
        .build();
}
```

### 3.2 Stok Rezervasyon Stratejisi

**Kural: Sepete eklemede stok REZERVE EDILMEZ.**

Nedeni:
- Sepetler uzun sure acik kalabilir (gunler/haftalar)
- Phantom stock hold: Satin alinmayan urunler baskalarinin satin almasini engeller
- Sepet abandonment orani: ~70% (checkout'a girmeden terk)

**Checkout Sirasinda Atomik Rezervasyon:**

```java
@Service
@Transactional
public class CheckoutServiceImpl implements CheckoutService {

    // Adim 1: Stok kontrol (checkout baslatma)
    public CheckoutValidationResult validateCart(Cart cart) {
        List<CartItem> items = cart.getItems();
        List<ValidationError> errors = new ArrayList<>();

        for (CartItem item : items) {
            int available = stockService.getTotalAvailableQuantity(item.getProductId());
            if (available < item.getQuantity()) {
                errors.add(new ValidationError(
                    item.getProductId(),
                    "Yetersiz stok. Mevcut: " + available,
                    available
                ));
            }
        }

        return new CheckoutValidationResult(errors.isEmpty(), errors);
    }

    // Adim 2: Siparis olusturma + stok rezervasyonu (atomik)
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Order placeOrder(PlaceOrderRequest request, Customer customer) {
        Cart cart = cartService.getCartByCustomer(customer.getId());

        // 1. Stok kontrolu (SELECT ... FOR UPDATE ile kilitlenir)
        for (CartItem item : cart.getItems()) {
            List<Stock> stocks = stockRepository.findByProductIdForUpdate(item.getProductId());
            int totalAvailable = stocks.stream().mapToInt(Stock::getAvailableQuantity).sum();

            if (totalAvailable < item.getQuantity()) {
                throw new InsufficientStockException(item.getProductId(), totalAvailable, item.getQuantity());
            }
        }

        // 2. Order olustur
        Order order = createOrder(cart, customer, request);

        // 3. Stok rezervasyonu
        for (OrderItem orderItem : order.getItems()) {
            reserveStockForItem(orderItem);
        }

        // 4. Sepeti temizle
        cartService.clearCart(cart.getId());

        // 5. Admin'e bildirim
        notificationService.create(
            "Yeni Siparis: " + order.getOrderNumber(),
            order.getGrandTotal() + " TL degerinde yeni siparis alindi.",
            "Order", order.getId()
        );

        return order;
    }

    private void reserveStockForItem(OrderItem item) {
        // En yakin depodan (veya en cok stoku olan depodan) rezerve et
        List<Stock> stocks = stockRepository.findByProductIdOrderByQuantityDesc(item.getProductId());
        int remaining = item.getQuantity();

        for (Stock stock : stocks) {
            if (remaining <= 0) break;
            int available = stock.getAvailableQuantity();
            if (available <= 0) continue;

            int toReserve = Math.min(remaining, available);
            stock.setReservedQuantity(stock.getReservedQuantity() + toReserve);
            stockRepository.save(stock);
            remaining -= toReserve;

            // OrderItem'a hangi depodan karsilandigini kaydet
            if (remaining <= 0) {
                item.setWarehouseId(stock.getWarehouse().getId());
                item.setStockId(stock.getId());
            }
        }

        if (remaining > 0) {
            throw new InsufficientStockException(item.getProductId(), item.getQuantity() - remaining, item.getQuantity());
        }
    }
}
```

### 3.3 Stale Rezervasyon Temizligi

```java
@Component
public class StaleReservationCleanup {

    @Scheduled(fixedRate = 300000) // 5 dakikada bir
    @Transactional
    public void cleanupStaleReservations() {
        // 30 dakikadan eski odenmemis siparisler
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        List<Order> staleOrders = orderRepository
            .findByStatusAndCreatedAtBefore("PENDING_PAYMENT", threshold);

        for (Order order : staleOrders) {
            // Stok rezervasyonunu serbest birak
            for (OrderItem item : order.getItems()) {
                if (item.getStockId() != null) {
                    Stock stock = stockRepository.findById(item.getStockId()).orElse(null);
                    if (stock != null) {
                        stock.setReservedQuantity(
                            Math.max(0, stock.getReservedQuantity() - item.getQuantity())
                        );
                        stockRepository.save(stock);
                    }
                }
            }

            // Siparis iptal
            order.setStatus("CANCELLED");
            orderRepository.save(order);

            // Durum gecmisi
            orderStatusHistoryRepository.save(new OrderStatusHistory(
                order, "PENDING_PAYMENT", "CANCELLED", "system",
                "Odeme suresi doldu (30 dakika)"
            ));
        }
    }
}
```

### 3.4 Stok Dusumu (Transfer Tamamlandiginda)

```
Siparis PAID -> PREPARING
    |
    v
Admin StockTransfer olusturur (CUSTOMER_DELIVERY)
    |
    v
StockTransfer IN_TRANSIT (kargoya verildi)
    |
    v
StockTransfer COMPLETED (teslim edildi)
    |
    v
Stok dusumu:
    - stock.quantity -= quantity
    - stock.reservedQuantity -= quantity
    - Order status: DELIVERED
```

Bu akis mevcut `StockTransferServiceImpl.completeTransfer()` metodu ile zaten desteklenmektedir. Ek olarak sadece `Order.status` guncellemesi eklenir.

---

## 4. Siparis -> StockTransfer Pipeline

### 4.1 Otomatik Transfer Olusturma

Siparis `PAID` statusune gectiginde (veya kapida odeme icin `PREPARING`):

```java
@Service
public class OrderFulfillmentService {

    public StockTransfer createTransferFromOrder(Order order) {
        StockTransfer transfer = new StockTransfer();

        // Transfer tipi: CUSTOMER_DELIVERY (mevcut enum)
        transfer.setTransferType(TransferType.CUSTOMER_DELIVERY);

        // Kaynak depo: Siparis kalemlerinden belirlenir
        OrderItem firstItem = order.getItems().get(0);
        Warehouse sourceWarehouse = warehouseRepository.findById(firstItem.getWarehouseId())
            .orElseThrow();
        transfer.setSourceWarehouse(sourceWarehouse);

        // Musteri bilgileri (mevcut alanlar)
        JsonNode shippingAddress = order.getShippingAddressSnapshot();
        transfer.setCustomerFullName(
            shippingAddress.get("firstName").asText() + " " +
            shippingAddress.get("lastName").asText()
        );
        transfer.setCustomerPhone(shippingAddress.get("phone").asText());
        transfer.setCustomerAddress(
            shippingAddress.get("addressLine").asText() + ", " +
            shippingAddress.get("district").asText() + "/" +
            shippingAddress.get("city").asText()
        );

        // Transfer kalemleri
        for (OrderItem orderItem : order.getItems()) {
            StockTransferItem transferItem = new StockTransferItem();
            transferItem.setProduct(productRepository.getReferenceById(orderItem.getProductId()));
            transferItem.setStockId(orderItem.getStockId());
            transferItem.setQuantity(orderItem.getQuantity());
            transfer.addItem(transferItem);
        }

        // Transfer notlari
        transfer.setNotes("Siparis No: " + order.getOrderNumber());
        transfer.setCreatedBy("system");

        // Kaydet
        StockTransfer saved = stockTransferRepository.save(transfer);

        // Order'a transfer referansi ekle
        order.setStockTransferId(saved.getId());
        orderRepository.save(order);

        return saved;
    }
}
```

### 4.2 Transfer-Siparis Durum Eslesmesi

```
StockTransfer Status    ->    Order Status       Tetikleyici
─────────────────────────────────────────────────────────────
PENDING                 ->    PREPARING          Transfer olusturuldu
IN_TRANSIT              ->    SHIPPED            Admin "Baslat" tiklar
COMPLETED               ->    DELIVERED          Admin "Tamamla" tiklar
CANCELLED               ->    (degismez)         Transfer iptal
```

Bu eslestirme mevcut `StockTransferServiceImpl` event'lerine listener eklenerek saglanir:

```java
@Component
public class TransferOrderSyncListener {

    @EventListener
    public void onTransferStatusChange(TransferStatusChangedEvent event) {
        StockTransfer transfer = event.getTransfer();
        Order order = orderRepository.findByStockTransferId(transfer.getId()).orElse(null);
        if (order == null) return;

        switch (event.getNewStatus()) {
            case IN_TRANSIT:
                order.setStatus("SHIPPED");
                order.setCargoTrackingNo(transfer.getNotes()); // veya ayri alan
                // Musteriye kargo bildirimi (email + SMS)
                notifyCustomerShipped(order);
                break;
            case COMPLETED:
                order.setStatus("DELIVERED");
                order.setActualDeliveryDate(LocalDate.now());
                // Musteriye teslim bildirimi
                notifyCustomerDelivered(order);
                break;
        }

        orderRepository.save(order);
        orderStatusHistoryRepository.save(new OrderStatusHistory(
            order, event.getOldStatus().name(), event.getNewStatus().name(),
            "system", "Transfer durum degisikligi"
        ));
    }
}
```

### 4.3 Admin Panel Gorunumu

Admin panelinde (mevcut React app) siparis kaynakli transfer'ler normal transfer'lerden ayirt edilir:
- Transfer listesinde "Siparis" etiketi gosterilir
- Siparis numarasi ve musteri bilgileri goruntulenir
- Transfer tamamlandiginda siparis statusu otomatik guncellenir

Mevcut admin transfer workflow'u aynen kullanilir - ek UI degisikligi minimal.

---

## 5. Email Bildirimleri

### 5.1 Altyapi

**Maven Dependencies:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

**Konfigürasyon:**
```properties
spring.mail.host=${MAIL_HOST:smtp.gmail.com}
spring.mail.port=${MAIL_PORT:587}
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

app.mail.from-name=Domain E-Ticaret
app.mail.from-email=bilgi@domain.com
```

### 5.2 Email Template'leri

| Email | Tetikleyici | Icerik |
|-------|-------------|--------|
| Hosgeldiniz | Kayit | Email dogrulama linki + hosgeldiniz mesaji |
| Email Dogrulama | Kayit | Dogrulama linki (24 saat gecerli) |
| Sifre Sifirlama | Sifre unuttum | Reset linki (1 saat gecerli) |
| Siparis Onay | Odeme basarili | Siparis ozeti, kargo bilgisi, tahmini teslimat |
| Kargo Bildirimi | Transfer IN_TRANSIT | Kargo firması, takip no, takip linki |
| Teslim Bildirimi | Transfer COMPLETED | Teslim onay, yorum daveti, iade bilgisi |
| Siparis Iptal | Siparis iptal | Iptal nedeni, iade bilgisi |
| Iade Onay | Iade talebi onaylandi | Iade kargo bilgileri, gonderim adresi |
| Iade Tamamlandi | Para iade edildi | Iade tutari, banka bilgisi |

### 5.3 Email Service

```java
public interface EmailService {
    void sendWelcomeEmail(Customer customer, String verifyToken);
    void sendPasswordResetEmail(Customer customer, String resetToken);
    void sendOrderConfirmation(Order order, Customer customer);
    void sendShippingNotification(Order order, String trackingNo, String cargoCompany);
    void sendDeliveryConfirmation(Order order);
    void sendOrderCancellation(Order order, String reason);
    void sendReturnApproval(ReturnRequest returnRequest);
    void sendRefundConfirmation(ReturnRequest returnRequest);
}
```

### 5.4 Asenkron Gonderim

```java
@Service
public class EmailServiceImpl implements EmailService {

    @Async("emailExecutor")
    public void sendOrderConfirmation(Order order, Customer customer) {
        Context context = new Context();
        context.setVariable("order", order);
        context.setVariable("customer", customer);
        context.setVariable("items", order.getItems());

        String html = templateEngine.process("email/order-confirmation", context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail, fromName);
        helper.setTo(customer.getEmail());
        helper.setSubject("Siparis Onayiniz - " + order.getOrderNumber());
        helper.setText(html, true);

        mailSender.send(message);
    }
}
```

---

## 6. SMS Bildirimleri

### 6.1 SMS Saglayici: NetGSM

**Neden NetGSM:** Turkiye'nin en buyuk SMS altyapi saglayicisi, ILETI YONETIM SISTEMI (IYS) entegrasyonu mevcut.

**Konfigürasyon:**
```properties
app.sms.provider=NETGSM
app.sms.netgsm.username=${NETGSM_USERNAME}
app.sms.netgsm.password=${NETGSM_PASSWORD}
app.sms.netgsm.header=${NETGSM_HEADER}  # Gonderen adi (ornek: "DOMAIN")
app.sms.netgsm.api-url=https://api.netgsm.com.tr/sms/send/xml
```

### 6.2 SMS Gonderim Durumları

| SMS | Tetikleyici | Mesaj Ornegi |
|-----|-------------|--------------|
| Siparis Onay | Odeme basarili | "Siparisiz alindi. No: ORD-xxx. Toplam: 12.999 TL. Detay: domain.com/hesabim/siparisler" |
| Kargo | Transfer IN_TRANSIT | "Siparisiz kargoya verildi. Takip No: xxx. Takip: kargo.com.tr/takip/xxx" |
| Teslim | Transfer COMPLETED | "Siparisiz teslim edildi. Iyi gunlerde kullanin! Yorum yapin: domain.com/urun/xxx" |

### 6.3 SMS Service

```java
public interface SmsService {
    void sendSms(String phone, String message);
    void sendOrderConfirmationSms(Order order, Customer customer);
    void sendShippingSms(Order order, String trackingNo);
    void sendDeliverySms(Order order);
}
```

---

## 7. Kargo Entegrasyonu

### 7.1 Desteklenen Kargo Firmalari

| Firma | API Tipi | Dokumantasyon |
|-------|----------|---------------|
| Yurtici Kargo | REST API | developer.yurticikargo.com |
| Aras Kargo | REST API | developer.araskargo.com.tr |
| MNG Kargo | REST API | api.mngkargo.com.tr |
| PTT Kargo | REST API | api.ptt.gov.tr |

### 7.2 Cargo Service Abstraction

```java
public interface CargoService {
    // Gonderi olusturma
    ShipmentResult createShipment(ShipmentRequest request);

    // Takip sorgulama
    TrackingResult getTrackingInfo(String trackingNumber);

    // Gonderi iptal
    CancelResult cancelShipment(String trackingNumber);

    // Etiket (barkod) olusturma
    byte[] generateLabel(String trackingNumber);

    // Kargo ucreti hesaplama
    ShippingCostResult calculateCost(ShippingCostRequest request);
}
```

```java
// Firma bazli adapter
public class YurticiCargoAdapter implements CargoService {
    // Yurtici Kargo API implementasyonu
}

public class ArasCargoAdapter implements CargoService {
    // Aras Kargo API implementasyonu
}
```

### 7.3 Kargo Ucreti Hesaplama

Mevcut `Product` entity'sindeki boyut ve agirlik alanlari kullanilir:

```java
public BigDecimal calculateShippingCost(List<OrderItem> items) {
    BigDecimal totalDesi = BigDecimal.ZERO;

    for (OrderItem item : items) {
        Product product = item.getProduct();
        // Desi = (en x boy x yukseklik) / 3000
        double volumetricWeight = (product.getLengthCm() * product.getWidthCm() * product.getHeightCm()) / 3000.0;
        double actualWeight = product.getWeight() != null ? product.getWeight() : 0;
        double desi = Math.max(volumetricWeight, actualWeight);

        totalDesi = totalDesi.add(
            BigDecimal.valueOf(desi).multiply(BigDecimal.valueOf(item.getQuantity()))
        );
    }

    // Kargo ucreti = toplam desi * birim desi fiyati
    // Veya sabit ucret (ornek: 50 TL ustu ucretsiz kargo)
    BigDecimal baseCost = totalDesi.multiply(DESI_UNIT_PRICE);

    return baseCost;
}
```

### 7.4 Ucretsiz Kargo Kurali

```java
// Konfigurasyondan
app.shipping.free-shipping-threshold=500  # 500 TL ustu ucretsiz
app.shipping.default-cost-per-desi=15     # Desi basi 15 TL
app.shipping.min-shipping-cost=29.99      # Minimum kargo ucreti
```

---

## 8. e-Fatura Entegrasyonu

### 8.1 Servis Saglayici

**Oneri: Foriba/Sovos** - Turkiye'de en yaygin e-fatura entegrator.

**Alternatifler:** Logo, Uyumsoft, Edm Bilisim

### 8.2 Fatura Tipleri

| Tip | Kosul | Aciklama |
|-----|-------|----------|
| e-Fatura | Alici e-fatura mukellefiyse | GIB'e elektronik iletilir |
| e-Arsiv Fatura | Alici e-fatura mukellef degilse | Dijital arsivlenir |

### 8.3 Fatura Olusturma Akisi

```
Siparis DELIVERED (teslim edildi)
         |
         v
Fatura servisine API cagrisi
    - Satici bilgileri (sabit)
    - Alici bilgileri (customer + address)
    - Kalem detaylari (order_items)
    - Vergi bilgileri (vatRate, sctRate)
         |
         v
Fatura PDF olusturulur
    - invoice_number ve invoice_url Order'a kaydedilir
    - Musteriye email ile gonderilir
```

### 8.4 Fatura Verisi

Mevcut sistemdeki veriler fatura icin yeterlidir:

| Fatura Alani | Kaynak |
|-------------|--------|
| Satici VKN/TCKN | Konfigurasyondan |
| Alici TCKN | `customer_addresses.tc_kimlik_no` |
| Alici VKN | `customer_addresses.tax_number` |
| Vergi Dairesi | `customer_addresses.tax_office` |
| Sirket Adi | `customer_addresses.company_name` |
| Urun Adi | `order_items.product_snapshot.name` |
| Birim Fiyat | `order_items.unit_price` |
| KDV Orani | `order_items.vat_rate` |
| OTV Orani | `order_items.sct_rate` |
| Toplam | `orders.grand_total` |

---

## 9. KVKK (Kisisel Verilerin Korunmasi Kanunu) Uyumlulugu

### 9.1 Yasal Gereksinimler

KVKK (6698 sayili kanun), Turkiye'nin veri koruma yasasidir. AB GDPR'ye benzer.

### 9.2 Teknik Onlemler

| Gereksinim | Uygulama |
|------------|----------|
| Acik riza | Kayit formunda KVKK onay checkbox'i (zorunlu). `customers.kvkk_consent=true`, `kvkk_consent_at` timestamp |
| Aydinlatma metni | `/sayfa/kvkk-aydinlatma-metni` CMS sayfasi |
| Pazarlama izni | Ayri checkbox. `customers.marketing_consent` |
| Veri erisim hakki | `GET /api/store/account/data-export` -> JSON/PDF export |
| Silme hakki | `DELETE /api/store/account` -> Soft delete (30 gun), sonra hard delete |
| Veri tasima | `GET /api/store/account/data-export` -> Standart format (JSON) |
| Veri ihlal bildirimi | 72 saat icinde KVKK Kurumu'na bildirim (operasyonel prosedur) |
| Cerez izni | Cookie consent banner (varsayilan: reddedilmis) |
| IYS (Ileti Yonetim Sistemi) | SMS/email pazarlama icin IYS kaydı zorunlu |

### 9.3 Cookie Consent

```typescript
// Storefront: CookieConsent component
const COOKIE_CATEGORIES = {
  necessary: { required: true, description: 'Site calismasi icin zorunlu cerezler' },
  analytics: { required: false, description: 'Ziyaret istatistikleri (Google Analytics)' },
  marketing: { required: false, description: 'Pazarlama ve hedefli reklamlar' },
};
```

### 9.4 Hesap Silme Akisi

```
[1. Musteri "Hesabimi Sil" tiklar]
         |
         v
[2. Onay modali: "Tum verileriniz 30 gun icinde silinecek"]
         |
         v
[3. POST /api/store/account/delete]
    - customer.is_active = false
    - customer.deletion_requested_at = now()
    - Tum refresh token'lar revoke edilir
    - Email: "Hesabiniz 30 gun icinde silinecek"
         |
         v
[4. 30 gun sonra @Scheduled task:]
    - Kisisel veriler anonymize edilir:
      - email -> "deleted_12345@anonymized.local"
      - first_name, last_name -> NULL
      - phone, tc_kimlik_no -> NULL
    - Adresler silinir
    - Favori listesi silinir
    - Sepet silinir
    - Siparis kayitlari KORUNUR (yasal zorunluluk - 10 yil)
      ancak musteri bilgileri anonymize edilir
```

---

## 10. Performans ve Olceklenebilirlik

### 10.1 Cache Stratejisi Genisletme

Mevcut `CacheConfig.java` genisletilir:

```java
@Bean
public CacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager();
    manager.setCacheSpecification("maximumSize=500,expireAfterWrite=60s");

    // Mevcut cache'ler korunur
    // ...

    // Yeni e-ticaret cache'leri
    manager.registerCustomCache("categoryTree",
        Caffeine.newBuilder().maximumSize(1).expireAfterWrite(30, MINUTES).build());
    manager.registerCustomCache("productCatalog",
        Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(10, MINUTES).build());
    manager.registerCustomCache("brandList",
        Caffeine.newBuilder().maximumSize(1).expireAfterWrite(60, MINUTES).build());
    manager.registerCustomCache("installmentInfo",
        Caffeine.newBuilder().maximumSize(100).expireAfterWrite(1, HOURS).build());

    return manager;
}
```

### 10.2 Rate Limiting

```java
// Kritik endpointler icin rate limiting
@RateLimited(requests = 5, period = 60, unit = SECONDS)   // 5 istek/dakika
POST /api/store/auth/login

@RateLimited(requests = 3, period = 3600, unit = SECONDS) // 3 istek/saat
POST /api/store/auth/forgot-password

@RateLimited(requests = 10, period = 60, unit = SECONDS)  // 10 istek/dakika
POST /api/store/checkout/place-order
```

### 10.3 Veritabani Optimizasyonu

- **Connection Pool:** HikariCP max-pool-size 30'a cikarilir (mevcut: 20)
- **Read Replicas:** Okuma agirlikli storefront sorgulari icin PostgreSQL read replica (gelecek)
- **Query Optimization:** Urun listesi sorgulari icin pagination + count ayrimi
- **Batch Insert:** Toplu siparis isleme icin batch operations (mevcut: batch_size=50)

---

## 11. Monitoring ve Alerting

### 11.1 Saglik Kontrolleri

Mevcut `/actuator/health` genisletilir:

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "iyzico": { "status": "UP" },
    "email": { "status": "UP" },
    "sms": { "status": "UP" },
    "cargo-yurtici": { "status": "UP" }
  }
}
```

### 11.2 Kritik Metrikler

| Metrik | Alert Esigi |
|--------|-------------|
| Odeme basari orani | < %95 |
| Siparis olusturma suresi | > 5 saniye |
| Stok kontrol suresi | > 1 saniye |
| Email gonderim basarisi | < %99 |
| API response time (p95) | > 500ms |
| Hata orani (5xx) | > %1 |
| Stale reservation sayisi | > 100 |
