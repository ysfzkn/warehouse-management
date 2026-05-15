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

    public CheckoutServiceImpl(CartRepository cartRepository, CartItemRepository cartItemRepository,
                                CustomerRepository customerRepository, CustomerAddressRepository addressRepository,
                                OrderRepository orderRepository, StockService stockService,
                                StockRepository stockRepository, CartService cartService,
                                CargoProviderRepository cargoProviderRepository,
                                StockEventRepository stockEventRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService) {
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
            if (!product.isActive()) {
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
        // --- Doğrulama (6502 sayılı Tüketicinin Korunması + KVKK 6698) ---
        if (!request.isDistanceSalesContractAccepted()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Mesafeli satış sözleşmesini onaylamanız gerekiyor.");
        }
        if (!request.isPreliminaryInfoAccepted()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Ön bilgilendirme formunu onaylamanız gerekiyor.");
        }
        if (!request.isKvkkConsent()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "KVKK aydınlatma metnini onaylamanız gerekiyor.");
        }

        // E-posta zaten kayıtlı mı?
        Optional<Customer> existing = customerRepository.findByEmail(request.getEmail().toLowerCase().trim());
        if (existing.isPresent()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Bu e-posta ile daha önce kayıt olunmuş. Lütfen giriş yapın ya da farklı bir e-posta kullanın.");
        }

        // Misafir sepeti bul
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepet bulunamadı. Lütfen sayfayı yenileyin.");
        }
        Cart cart = cartRepository.findBySessionId(request.getSessionId())
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepet bulunamadı. Lütfen sayfayı yenileyin."));

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepetiniz boş. Lütfen ürün ekleyerek devam edin.");
        }

        // --- Yeni müşteri kaydı oluştur (misafir) ---
        Customer customer = new Customer();
        customer.setEmail(request.getEmail().toLowerCase().trim());
        // Geçici rastgele şifre; müşteri "hesabımı tamamla" akışında gerçek şifresini belirleyecek
        String tempPassword = UUID.randomUUID().toString();
        customer.setPasswordHash(passwordEncoder.encode(tempPassword));
        customer.setFirstName(request.getFirstName().trim());
        customer.setLastName(request.getLastName().trim());
        customer.setPhone(request.getPhone().trim());
        customer.setEmailVerified(false);
        customer.setActive(true);

        // "Hesabı tamamla" için password reset token mekanizmasını kullan (7 gün geçerli)
        String setupToken = UUID.randomUUID().toString();
        customer.setPasswordResetToken(setupToken);
        customer.setPasswordResetSentAt(LocalDateTime.now());

        customer.setKvkkConsent(request.isKvkkConsent());
        if (request.isKvkkConsent()) {
            customer.setKvkkConsentAt(LocalDateTime.now());
        }

        customer = customerRepository.save(customer);
        logger.info("Misafir müşteri oluşturuldu: id={}, email={}", customer.getId(), customer.getEmail());

        // --- Teslimat ve fatura adreslerini oluştur ---
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

        // --- Misafir sepetini yeni müşteriye bağla ---
        cart.setCustomer(customer);
        cart.setSessionId(null);
        cartRepository.save(cart);

        // --- Siparişi oluştur (yasal sözleşme zaman damgaları snapshot olarak Order'a iliştirilir) ---
        ConsentSnapshot consents = new ConsentSnapshot(
                request.getDistanceSalesContractAcceptedAt(),
                request.getPreliminaryInfoAcceptedAt(),
                request.getKvkkConsentAt() != null ? request.getKvkkConsentAt() : LocalDateTime.now()
        );
        PlaceOrderResponse response = createOrderInternal(customer, items, shippingAddr, billingAddr,
                request.getCargoCompany(), request.getCargoProviderId(),
                request.getPaymentMethod(), request.getCustomerNote(),
                ipAddress, userAgent, true, consents);

        // --- "Hesabını tamamla" e-postası gönder ---
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
     * Üye ve misafir siparişlerinin ortak sipariş oluşturma mantığı.
     */
    /**
     * Yasal sözleşme kabul zaman damgalarını taşıyan immutable snapshot.
     * Client tarafında yakalanan timestamp'ler (sözleşmeyi okuduğu an) bu kayda
     * dahil edilir; null ise server-now() fallback'i kullanılır.
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
        // Generate order number up-front so stock events created during reservation
        // can reference the order that caused them.
        String orderNumber = "ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // Calculate totals
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem ci : items) {
            Product p = ci.getProduct();
            BigDecimal price = p.getSalePrice() != null && p.getSalePrice().compareTo(BigDecimal.ZERO) > 0 ? p.getSalePrice() : p.getPrice();
            if (price == null) price = BigDecimal.ZERO;
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(ci.getQuantity()));
            BigDecimal vat = p.getVatRate() != null ? lineTotal.multiply(p.getVatRate()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;

            subtotal = subtotal.add(lineTotal);
            vatTotal = vatTotal.add(vat);

            // Pessimistic lock stock reservation (SELECT FOR UPDATE)
            List<Stock> availableStocks = stockRepository.findAvailableByProductForUpdate(p.getId());
            int remaining = ci.getQuantity();
            Long reservedWarehouseId = null;
            Long reservedStockId = null;
            for (Stock stock : availableStocks) {
                int avail = stock.getAvailableQuantity();
                if (avail <= 0) continue;
                int toReserve = Math.min(remaining, avail);
                int oldReserved = stock.getReservedQuantity();
                stock.setReservedQuantity(oldReserved + toReserve);
                stockRepository.save(stock);

                // Log stock reservation event
                StockEvent event = new StockEvent();
                event.setStockId(stock.getId());
                event.setProductId(p.getId());
                event.setEventType(StockEventType.RESERVED);
                event.setOldValue(oldReserved);
                event.setNewValue(stock.getReservedQuantity());
                event.setSource(StockEventSource.ORDER);
                event.setSourceDetail("Sipariş #" + orderNumber + " rezervasyonu (" + toReserve + " adet)");
                event.setOrderNumber(orderNumber);
                stockEventRepository.save(event);

                remaining -= toReserve;
                reservedWarehouseId = stock.getWarehouse().getId();
                reservedStockId = stock.getId();
                if (remaining <= 0) break;
            }
            if (remaining > 0) {
                throw new WarehouseManagementException(ErrorCode.STOCK_RESERVATION_FAILED,
                    p.getName() + " için yeterli stok bulunamadı.");
            }

            OrderItem oi = new OrderItem();
            oi.setProduct(p);
            oi.setProductSnapshot(Map.of("name", p.getName(), "sku", p.getSku(), "price", price.toString()));
            oi.setQuantity(ci.getQuantity());
            oi.setUnitPrice(price);
            oi.setVatRate(p.getVatRate() != null ? p.getVatRate() : BigDecimal.ZERO);
            oi.setSctRate(p.getSctRate() != null ? p.getSctRate() : BigDecimal.ZERO);
            oi.setLineTotal(lineTotal);
            oi.setWarehouseId(reservedWarehouseId);
            oi.setStockId(reservedStockId);
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

        BigDecimal grandTotal = subtotal.add(shippingCost).add(shippingVat).add(vatTotal);

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
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setSctTotal(BigDecimal.ZERO);
        order.setPaymentMethod(paymentMethod);
        order.setCustomerNote(customerNote);
        order.setIpAddress(ipAddress);
        order.setUserAgent(userAgent);
        // Yasal sözleşme kanıtları (client-side capture, yoksa server-now fallback)
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
            // Authenticated kullanıcı için customer'dan snapshot
            order.setKvkkConsentAt(customer.getKvkkConsentAt());
        }

        if (cargoProvider != null) {
            order.setCargoProviderId(cargoProvider.getId());
            order.setCargoProviderName(cargoProvider.getName());
            // Also set legacy enum for backward compatibility
            try { order.setCargoCompany(com.warehouse.enums.CargoCompany.valueOf(cargoProvider.getCode())); } catch (Exception ignored) {}
        } else if (cargoCompanyStr != null) {
            try { order.setCargoCompany(com.warehouse.enums.CargoCompany.valueOf(cargoCompanyStr)); } catch (Exception ignored) {}
        }

        order = orderRepository.save(order);

        for (OrderItem oi : orderItems) {
            oi.setOrder(order);
        }
        order.setItems(orderItems);
        order = orderRepository.save(order);

        // NOTE: Cart is NOT cleared here — cleared after successful payment
        // This allows cart recovery if payment fails

        logger.info("Order created: {} for customer {} (guest={})", orderNumber, customer.getId(), isGuest);

        return PlaceOrderResponse.builder()
            .orderId(order.getId())
            .orderNumber(order.getOrderNumber())
            .grandTotal(order.getGrandTotal())
            .status(order.getStatus().name())
            .build();
    }

    /**
     * Misafir checkout request'inden CustomerAddress nesnesi oluşturur.
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
