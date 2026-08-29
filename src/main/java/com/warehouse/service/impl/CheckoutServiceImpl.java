package com.warehouse.service.impl;

import com.warehouse.dto.store.*;
import com.warehouse.entity.*;
import com.warehouse.constants.ShippingConstants;
import com.warehouse.enums.AddressType;
import com.warehouse.enums.OrderStatus;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.*;
import com.warehouse.service.CartService;
import com.warehouse.service.CheckoutService;
import com.warehouse.service.EmailService;
import com.warehouse.service.SiteSettingService;
import com.warehouse.entity.Stock;
import com.warehouse.entity.StockEvent;
import com.warehouse.enums.StockEventType;
import com.warehouse.enums.StockEventSource;
import com.warehouse.repository.StockRepository;
import com.warehouse.repository.StockEventRepository;
import com.warehouse.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional
public class CheckoutServiceImpl implements CheckoutService {

    private static final Logger logger = LoggerFactory.getLogger(CheckoutServiceImpl.class);

    private static final java.security.SecureRandom PAYMENT_TOKEN_RANDOM = new java.security.SecureRandom();
    /** Comfortably longer than the payment-gateway timeout, short enough to be useless later. */
    private static final int PAYMENT_TOKEN_TTL_HOURS = 6;

    private final com.warehouse.service.CouponService couponService;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final StockService stockService;
    private final StockRepository stockRepository;
    private final CartService cartService;
    private final CargoProviderRepository cargoProviderRepository;
    private final StockEventRepository stockEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SiteSettingService siteSettingService;

    public CheckoutServiceImpl(com.warehouse.service.CouponService couponService,
                               CartRepository cartRepository, CartItemRepository cartItemRepository,
                                CustomerRepository customerRepository, CustomerAddressRepository addressRepository,
                                OrderRepository orderRepository, StockService stockService,
                                StockRepository stockRepository, CartService cartService,
                                CargoProviderRepository cargoProviderRepository,
                                StockEventRepository stockEventRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService,
                                SiteSettingService siteSettingService) {
        this.couponService = couponService;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.orderRepository = orderRepository;
        this.cargoProviderRepository = cargoProviderRepository;
        this.stockEventRepository = stockEventRepository;
        this.stockService = stockService;
        this.stockRepository = stockRepository;
        this.cartService = cartService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.siteSettingService = siteSettingService;
    }

    /**
     * Test mode / maintenance gate. Blocks order intake while
     * store_purchasing_enabled=false. Shared check for both authenticated and guest checkout.
     */
    private void assertPurchasingEnabled() {
        if (!siteSettingService.getBoolSetting("store_purchasing_enabled", true)) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Mağaza şu anda test modundadır. Sipariş alımı geçici olarak kapalıdır. "
                + "Anlayışınız için teşekkür ederiz.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutValidationResponse validateCheckout(Long customerId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepet bulunamadı. Lütfen giriş yaparak ürün ekleyin."));

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            return CheckoutValidationResponse.builder().valid(false).errors(List.of("Sepetiniz boş. Lütfen ürün ekleyerek devam edin.")).build();
        }

        List<String> errors = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem item : items) {
            Product product = item.getProduct();
            if (!product.isActive() || !product.isEcommerceVisible()) {
                errors.add(product.getName() + " artik satilamaz.");
                continue;
            }
            List<Stock> stocks = stockService.getStocksByProduct(product.getId());
            int available = stocks.stream().mapToInt(Stock::getAvailableQuantity).sum();
            if (available < item.getQuantity()) {
                errors.add(product.getName() + " icin yeterli stok yok. Mevcut: " + available);
            }
            BigDecimal price = product.getSalePrice() != null ? product.getSalePrice() : product.getPrice();
            if (price != null) subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        BigDecimal shippingCost = ShippingConstants.calculateShippingCost(subtotal);
        BigDecimal total = subtotal.add(shippingCost);

        return CheckoutValidationResponse.builder()
            .valid(errors.isEmpty())
            .subtotal(subtotal)
            .shippingCost(shippingCost)
            .total(total)
            .errors(errors)
            .build();
    }

    @Override
    public PlaceOrderResponse placeOrder(Long customerId, PlaceOrderRequest request, String ipAddress, String userAgent) {
        assertPurchasingEnabled();
        if (!request.isDistanceSalesContractAccepted()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Mesafeli satış sözleşmesini onaylamanız gerekiyor.");
        }
        if (!request.isPreliminaryInfoAccepted()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Ön bilgilendirme formunu onaylamanız gerekiyor.");
        }

        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.CUSTOMER_NOT_FOUND));

        Cart cart = cartRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepet bulunamadı. Lütfen giriş yaparak ürün ekleyin."));

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepetiniz boş. Lütfen ürün ekleyerek devam edin.");
        }

        CustomerAddress shippingAddr = addressRepository.findById(request.getShippingAddressId())
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Teslimat adresi bulunamadı."));

        Long billingAddrId = request.getBillingAddressId() != null ? request.getBillingAddressId() : request.getShippingAddressId();
        CustomerAddress billingAddr = addressRepository.findById(billingAddrId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Fatura adresi bulunamadı."));

        ConsentSnapshot consents = new ConsentSnapshot(
                request.getDistanceSalesContractAcceptedAt(),
                request.getPreliminaryInfoAcceptedAt(),
                null /* kvkk timestamp not collected on auth checkout; comes from Customer */
        );
        return createOrderInternal(customer, items, shippingAddr, billingAddr,
                request.getCargoCompany(), request.getCargoProviderId(),
                request.getPaymentMethod(), request.getCustomerNote(),
                ipAddress, userAgent, false, consents);
    }

    @Override
    public PlaceOrderResponse placeGuestOrder(GuestPlaceOrderRequest request, String ipAddress, String userAgent) {
        assertPurchasingEnabled();
        // --- Validation (Law No. 6502 on Consumer Protection + KVKK 6698) ---
        if (!request.isDistanceSalesContractAccepted()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Mesafeli satış sözleşmesini onaylamanız gerekiyor.");
        }
        if (!request.isPreliminaryInfoAccepted()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Ön bilgilendirme formunu onaylamanız gerekiyor.");
        }
        if (!request.isKvkkConsent()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "KVKK aydınlatma metnini onaylamanız gerekiyor.");
        }

        // Is the email already registered?
        Optional<Customer> existing = customerRepository.findByEmail(request.getEmail().toLowerCase().trim());
        if (existing.isPresent()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Bu e-posta ile daha önce kayıt olunmuş. Lütfen giriş yapın ya da farklı bir e-posta kullanın.");
        }

        // Find the guest cart
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepet bulunamadı. Lütfen sayfayı yenileyin.");
        }
        Cart cart = cartRepository.findBySessionId(request.getSessionId())
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepet bulunamadı. Lütfen sayfayı yenileyin."));

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepetiniz boş. Lütfen ürün ekleyerek devam edin.");
        }

        // --- Create a new customer record (guest) ---
        Customer customer = new Customer();
        customer.setEmail(request.getEmail().toLowerCase().trim());
        // Temporary random password; the customer will set their real password in the "complete my account" flow
        String tempPassword = UUID.randomUUID().toString();
        customer.setPasswordHash(passwordEncoder.encode(tempPassword));
        customer.setFirstName(request.getFirstName().trim());
        customer.setLastName(request.getLastName().trim());
        customer.setPhone(request.getPhone().trim());
        customer.setEmailVerified(false);
        customer.setActive(true);

        // Use the password reset token mechanism for "complete account" (valid for 7 days).
        // Only the hash is persisted: this token is a bearer credential that can set a
        // password, so a database dump must not contain anything usable. The raw value
        // exists only in the email that is about to be sent.
        String setupToken = UUID.randomUUID().toString();
        customer.setPasswordResetToken(com.warehouse.security.EncryptionService.hashToken(setupToken));
        customer.setPasswordResetSentAt(LocalDateTime.now());

        customer.setKvkkConsent(request.isKvkkConsent());
        if (request.isKvkkConsent()) {
            customer.setKvkkConsentAt(LocalDateTime.now());
        }

        customer = customerRepository.save(customer);
        logger.info("Misafir müşteri oluşturuldu: id={}, email={}", customer.getId(), customer.getEmail());

        // --- Create shipping and billing addresses ---
        CustomerAddress shippingAddr = buildGuestAddress(customer, request, true);
        shippingAddr = addressRepository.save(shippingAddr);

        CustomerAddress billingAddr;
        if (request.isBillingSameAsShipping()
                || request.getBillingAddressLine() == null || request.getBillingAddressLine().isBlank()) {
            billingAddr = shippingAddr;
        } else {
            billingAddr = buildGuestAddress(customer, request, false);
            billingAddr = addressRepository.save(billingAddr);
        }

        // --- Attach the guest cart to the new customer ---
        cart.setCustomer(customer);
        cart.setSessionId(null);
        cartRepository.save(cart);

        // --- Create the order (legal contract timestamps are attached to the Order as a snapshot) ---
        ConsentSnapshot consents = new ConsentSnapshot(
                request.getDistanceSalesContractAcceptedAt(),
                request.getPreliminaryInfoAcceptedAt(),
                request.getKvkkConsentAt() != null ? request.getKvkkConsentAt() : LocalDateTime.now()
        );
        PlaceOrderResponse response = createOrderInternal(customer, items, shippingAddr, billingAddr,
                request.getCargoCompany(), request.getCargoProviderId(),
                request.getPaymentMethod(), request.getCustomerNote(),
                ipAddress, userAgent, true, consents);

        // --- Send the "complete your account" email ---
        try {
            emailService.sendCompleteAccountSetup(
                customer.getEmail(),
                customer.getFirstName(),
                response.getOrderNumber(),
                setupToken
            );
        } catch (Exception e) {
            logger.warn("Hesabi tamamla e-postasi gonderilemedi (misafir siparisi {}): {}",
                    response.getOrderNumber(), e.getMessage());
        }

        return response;
    }

    /**
     * Shared order-creation logic for member and guest orders.
     */
    /**
     * Immutable snapshot carrying the legal contract acceptance timestamps.
     * Timestamps captured on the client side (the moment the contract was read) are
     * included in this record; if null, the server-now() fallback is used.
     */
    private record ConsentSnapshot(
            LocalDateTime distanceSalesAcceptedAt,
            LocalDateTime preliminaryInfoAcceptedAt,
            LocalDateTime kvkkConsentAt
    ) {}

    private PlaceOrderResponse createOrderInternal(Customer customer, List<CartItem> items,
                                                    CustomerAddress shippingAddr, CustomerAddress billingAddr,
                                                    String cargoCompanyStr, Long cargoProviderId,
                                                    String paymentMethod, String customerNote,
                                                    String ipAddress, String userAgent, boolean isGuest,
                                                    ConsentSnapshot consents) {
        // Order number — alphanumeric, NO dashes. Format: ORDYYYYMMDDXXXXXX
        // Dashes would violate PayTR's merchant_oid format rules (only A-Z, 0-9
        // accepted); also, some bank POS systems like NestPay/GVP restrict special
        // characters. A single representation looks identical across the payment
        // provider, the DB, support, and the customer email.
        String orderNumber = "ORD"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + java.util.UUID.randomUUID().toString().replace("-", "")
                        .substring(0, 6).toUpperCase();

        // Calculate totals
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem ci : items) {
            Product p = ci.getProduct();
            if (!p.isActive() || !p.isEcommerceVisible()) {
                throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    p.getName() + " e-ticarette satışa açık değil. Sepetinizi güncelleyin.");
            }
            BigDecimal price = p.getSalePrice() != null && p.getSalePrice().compareTo(BigDecimal.ZERO) > 0 ? p.getSalePrice() : p.getPrice();
            if (price == null) price = BigDecimal.ZERO;
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(ci.getQuantity()));
            BigDecimal vat = p.getVatRate() != null ? lineTotal.multiply(p.getVatRate()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;

            subtotal = subtotal.add(lineTotal);
            vatTotal = vatTotal.add(vat);

            OrderItem oi = new OrderItem();
            oi.setProduct(p);
            oi.setQuantity(ci.getQuantity());
            oi.setUnitPrice(price);
            oi.setVatRate(p.getVatRate() != null ? p.getVatRate() : BigDecimal.ZERO);
            oi.setSctRate(p.getSctRate() != null ? p.getSctRate() : BigDecimal.ZERO);
            oi.setLineTotal(lineTotal);

            if (p.getProductType() == ProductType.BUNDLE) {
                // A set has no stock of its own — reserve EACH member's stock.
                List<BundleItem> members = p.getBundleItems();
                if (members == null || members.isEmpty()) {
                    throw new WarehouseManagementException(ErrorCode.STOCK_RESERVATION_FAILED, p.getName() + " seti boş.");
                }
                List<Map<String, Object>> reservedMembers = new ArrayList<>();
                for (BundleItem bi : members) {
                    Product m = bi.getProduct();
                    int memberNeed = (bi.getQuantity() != null && bi.getQuantity() > 0 ? bi.getQuantity() : 1) * ci.getQuantity();
                    reservedMembers.addAll(reserveStockFor(m.getId(), m.getName(), memberNeed, orderNumber));
                }
                Map<String, Object> snap = new LinkedHashMap<>();
                snap.put("name", p.getName());
                snap.put("sku", p.getSku());
                snap.put("price", price.toString());
                snap.put("isBundle", true);
                snap.put("reservedMembers", reservedMembers); // for release/re-reserve on cancel
                oi.setProductSnapshot(snap);
                oi.setWarehouseId(null);
                oi.setStockId(null);
            } else {
                List<Map<String, Object>> allocs = reserveStockFor(p.getId(), p.getName(), ci.getQuantity(), orderNumber);
                Map<String, Object> last = allocs.get(allocs.size() - 1);
                oi.setWarehouseId(((Number) last.get("warehouseId")).longValue());
                oi.setStockId(((Number) last.get("stockId")).longValue());
                oi.setProductSnapshot(Map.of("name", p.getName(), "sku", p.getSku(), "price", price.toString()));
            }
            orderItems.add(oi);
        }

        // Calculate shipping cost — dynamic from CargoProvider or fallback to ShippingConstants
        BigDecimal shippingCost;
        BigDecimal shippingVat = BigDecimal.ZERO;
        CargoProvider cargoProvider = null;

        if (cargoProviderId != null) {
            cargoProvider = cargoProviderRepository.findById(cargoProviderId).orElse(null);
        }

        if (cargoProvider != null) {
            // Calculate total desi from cart items
            BigDecimal totalDesi = BigDecimal.ZERO;
            for (var ci : items) {
                Product prod = ci.getProduct();
                if (prod != null) {
                    // Physical weight
                    BigDecimal weight = prod.getWeight() != null ? BigDecimal.valueOf(prod.getWeight()) : BigDecimal.ZERO;
                    // Volumetric weight: (L x W x H) / 3000
                    BigDecimal volumetric = BigDecimal.ZERO;
                    if (prod.getLengthCm() != null && prod.getWidthCm() != null && prod.getHeightCm() != null) {
                        volumetric = BigDecimal.valueOf(prod.getLengthCm())
                            .multiply(BigDecimal.valueOf(prod.getWidthCm()))
                            .multiply(BigDecimal.valueOf(prod.getHeightCm()))
                            .divide(new BigDecimal("3000"), 2, java.math.RoundingMode.HALF_UP);
                    }
                    BigDecimal desi = weight.max(volumetric);
                    totalDesi = totalDesi.add(desi.multiply(BigDecimal.valueOf(ci.getQuantity())));
                }
            }
            shippingCost = cargoProvider.calculateShippingCost(totalDesi, subtotal);
            shippingVat = cargoProvider.calculateVat(shippingCost);
        } else {
            shippingCost = ShippingConstants.calculateShippingCost(subtotal);
        }

        // ─── Coupon ───────────────────────────────────────────────────────────
        // Re-validated against the final subtotal: the basket may have changed since the code
        // was applied, and the coupon's window or remaining uses may have moved on. A coupon
        // that no longer qualifies fails the checkout loudly rather than silently charging
        // the customer the undiscounted total they were never shown.
        Cart sourceCart = items.isEmpty() ? null : items.get(0).getCart();
        Coupon coupon = null;
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (sourceCart != null && sourceCart.getCouponCode() != null) {
            coupon = couponService.validate(sourceCart.getCouponCode(), subtotal, customer.getId());
            discountAmount = couponService.calculateDiscount(coupon, subtotal);
            if (couponService.isFreeShipping(coupon)) {
                shippingCost = BigDecimal.ZERO;
                shippingVat = BigDecimal.ZERO;
            }
        }

        BigDecimal grandTotal = subtotal.subtract(discountAmount)
                .add(shippingCost).add(shippingVat).add(vatTotal);
        if (grandTotal.signum() < 0) grandTotal = BigDecimal.ZERO;

        Order order = new Order();
        order.setOrderNumber(orderNumber);
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setShippingAddressSnapshot(addressToMap(shippingAddr));
        order.setBillingAddressSnapshot(addressToMap(billingAddr));
        order.setSubtotal(subtotal);
        order.setShippingCost(shippingCost);
        order.setShippingVat(shippingVat);
        order.setVatTotal(vatTotal);
        order.setGrandTotal(grandTotal);
        order.setDiscountAmount(discountAmount);
        if (coupon != null) {
            order.setCouponId(coupon.getId());
            order.setCouponCode(coupon.getCode());
            order.setCouponDiscount(discountAmount);
        }
        order.setSctTotal(BigDecimal.ZERO);
        order.setPaymentMethod(paymentMethod);
        order.setCustomerNote(customerNote);
        order.setIpAddress(ipAddress);
        order.setUserAgent(userAgent);
        // Legal contract evidence (client-side capture, server-now fallback otherwise)
        LocalDateTime now = LocalDateTime.now();
        order.setDistanceSalesContractAccepted(true);
        order.setDistanceSalesContractAcceptedAt(
                consents != null && consents.distanceSalesAcceptedAt() != null
                        ? consents.distanceSalesAcceptedAt() : now);
        order.setPreliminaryInfoAccepted(true);
        order.setPreliminaryInfoAcceptedAt(
                consents != null && consents.preliminaryInfoAcceptedAt() != null
                        ? consents.preliminaryInfoAcceptedAt() : now);
        if (consents != null && consents.kvkkConsentAt() != null) {
            order.setKvkkConsentAt(consents.kvkkConsentAt());
        } else if (customer.getKvkkConsentAt() != null) {
            // Snapshot from the customer for an authenticated user
            order.setKvkkConsentAt(customer.getKvkkConsentAt());
        }

        if (cargoProvider != null) {
            order.setCargoProviderId(cargoProvider.getId());
            order.setCargoProviderName(cargoProvider.getName());
            // Also set legacy enum for backward compatibility. A carrier the enum does not know
            // becomes OTHER rather than leaving the order with no carrier recorded at all.
            order.setCargoCompany(toCargoCompany(cargoProvider.getCode()));
        } else if (cargoCompanyStr != null && !cargoCompanyStr.isBlank()) {
            order.setCargoCompany(toCargoCompany(cargoCompanyStr));
        }

        order = orderRepository.save(order);

        for (OrderItem oi : orderItems) {
            oi.setOrder(order);
        }
        order.setItems(orderItems);

        // Ownership proof for the public payment-initialisation endpoint. 256 bits from
        // a CSPRNG, valid only as long as the order can still be paid for, so a leaked
        // token from an old checkout is worthless.
        byte[] tokenBytes = new byte[32];
        PAYMENT_TOKEN_RANDOM.nextBytes(tokenBytes);
        String paymentAccessToken = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        order.setPaymentAccessToken(paymentAccessToken);
        order.setPaymentAccessTokenExpiresAt(LocalDateTime.now().plusHours(PAYMENT_TOKEN_TTL_HOURS));

        order = orderRepository.save(order);

        if (coupon != null) {
            // Committed together with the stock reservation; released again by the cancel /
            // timeout paths, so a failed payment does not burn one of a limited coupon's uses.
            couponService.redeem(coupon.getId(), customer, order, subtotal);
            sourceCart.setCouponCode(null);
            cartRepository.save(sourceCart);
        }

        // NOTE: Cart is NOT cleared here — cleared after successful payment
        // This allows cart recovery if payment fails

        logger.info("Order created: {} for customer {} (guest={})", orderNumber, customer.getId(), isGuest);

        return PlaceOrderResponse.builder()
            .orderId(order.getId())
            .orderNumber(order.getOrderNumber())
            .grandTotal(order.getGrandTotal())
            .status(order.getStatus().name())
            .paymentToken(paymentAccessToken)
            .build();
    }

    private static com.warehouse.enums.CargoCompany toCargoCompany(String raw) {
        if (raw == null || raw.isBlank()) return com.warehouse.enums.CargoCompany.OTHER;
        try {
            return com.warehouse.enums.CargoCompany.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return com.warehouse.enums.CargoCompany.OTHER;
        }
    }

    /**
     * Pessimistically reserves {@code needed} units of a product across its warehouse stocks
     * (SELECT FOR UPDATE), logging a RESERVED stock event per allocation. Returns the list of
     * allocations ({@code {productId, stockId, warehouseId, quantity}}) so a bundle can record
     * exactly what it reserved (for release/re-reserve on cancel). Throws if stock is insufficient.
     */
    private List<Map<String, Object>> reserveStockFor(Long productId, String productName, int needed, String orderNumber) {
        List<Stock> availableStocks = stockRepository.findAvailableByProductForUpdate(productId);
        int remaining = needed;
        List<Map<String, Object>> allocations = new ArrayList<>();
        for (Stock stock : availableStocks) {
            int avail = stock.getAvailableQuantity();
            if (avail <= 0) continue;
            int toReserve = Math.min(remaining, avail);
            int oldReserved = stock.getReservedQuantity();
            stock.setReservedQuantity(oldReserved + toReserve);
            stockRepository.save(stock);

            StockEvent event = new StockEvent();
            event.setStockId(stock.getId());
            event.setProductId(productId);
            event.setEventType(StockEventType.RESERVED);
            event.setOldValue(oldReserved);
            event.setNewValue(stock.getReservedQuantity());
            event.setSource(StockEventSource.ORDER);
            event.setSourceDetail("Sipariş #" + orderNumber + " rezervasyonu (" + toReserve + " adet)");
            event.setOrderNumber(orderNumber);
            stockEventRepository.save(event);

            Map<String, Object> alloc = new LinkedHashMap<>();
            alloc.put("productId", productId);
            alloc.put("stockId", stock.getId());
            alloc.put("warehouseId", stock.getWarehouse().getId());
            alloc.put("quantity", toReserve);
            allocations.add(alloc);

            remaining -= toReserve;
            if (remaining <= 0) break;
        }
        if (remaining > 0) {
            throw new WarehouseManagementException(ErrorCode.STOCK_RESERVATION_FAILED,
                productName + " için yeterli stok bulunamadı.");
        }
        return allocations;
    }

    /**
     * Builds a CustomerAddress object from the guest checkout request.
     */
    private CustomerAddress buildGuestAddress(Customer customer, GuestPlaceOrderRequest request, boolean shipping) {
        CustomerAddress addr = new CustomerAddress();
        addr.setCustomer(customer);
        addr.setFirstName(request.getFirstName().trim());
        addr.setLastName(request.getLastName().trim());
        addr.setPhone(request.getPhone().trim());
        addr.setTitle(shipping ? "Teslimat Adresi" : "Fatura Adresi");
        addr.setAddressType(shipping ? AddressType.SHIPPING : AddressType.BILLING);
        addr.setDefault(false);

        if (shipping) {
            addr.setAddressLine(request.getShippingAddressLine().trim());
            addr.setCity(request.getShippingCity().trim());
            addr.setDistrict(request.getShippingDistrict().trim());
            addr.setPostalCode(request.getShippingPostalCode());
        } else {
            addr.setAddressLine(request.getBillingAddressLine().trim());
            addr.setCity(request.getBillingCity().trim());
            addr.setDistrict(request.getBillingDistrict().trim());
            addr.setPostalCode(request.getBillingPostalCode());
        }
        return addr;
    }

    private Map<String, Object> addressToMap(CustomerAddress addr) {
        Map<String, Object> map = new HashMap<>();
        map.put("firstName", addr.getFirstName());
        map.put("lastName", addr.getLastName());
        map.put("phone", addr.getPhone());
        map.put("city", addr.getCity());
        map.put("district", addr.getDistrict());
        map.put("addressLine", addr.getAddressLine());
        map.put("postalCode", addr.getPostalCode());
        return map;
    }
}
