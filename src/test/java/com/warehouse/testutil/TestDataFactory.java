package com.warehouse.testutil;

import com.warehouse.entity.*;
import com.warehouse.enums.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Factory for creating test entities with sensible defaults.
 * All methods return mutable objects that can be customized before use.
 */
public final class TestDataFactory {

    private static long idCounter = 1;

    private TestDataFactory() {}

    public static long nextId() { return idCounter++; }

    // ── Product ──────────────────────────────────────
    public static Product createProduct() {
        Product p = new Product();
        long id = nextId();
        p.setId(id);
        p.setName("Test Product " + id);
        p.setSku("SKU-" + id);
        p.setSlug("test-product-" + id);
        p.setPrice(new BigDecimal("100.00"));
        p.setVatRate(new BigDecimal("20"));
        p.setSctRate(BigDecimal.ZERO);
        p.setShippingRate(new BigDecimal("15.00"));
        p.setActive(true);
        p.setWeight(5.0);
        p.setLengthCm(30.0);
        p.setWidthCm(20.0);
        p.setHeightCm(10.0);
        p.setVersion(0L);
        p.setViewCount(0L);
        p.setSoldCount(0);
        Category cat = new Category();
        cat.setId(1L);
        cat.setName("Default Category");
        cat.setSlug("default");
        p.setCategory(cat);
        return p;
    }

    // ── Stock ────────────────────────────────────────
    public static Stock createStock(Product product, int quantity) {
        Stock s = new Stock();
        s.setId(nextId());
        s.setProduct(product);
        Warehouse w = createWarehouse();
        s.setWarehouse(w);
        s.setQuantity(quantity);
        s.setReservedQuantity(0);
        s.setConsignedQuantity(0);
        s.setMinStockLevel(5);
        s.setVersion(0L);
        s.setLastUpdated(LocalDateTime.now());
        return s;
    }

    // ── Warehouse ────────────────────────────────────
    public static Warehouse createWarehouse() {
        Warehouse w = new Warehouse();
        long id = nextId();
        w.setId(id);
        w.setName("Warehouse " + id);
        w.setLocation("Istanbul");
        w.setActive(true);
        return w;
    }

    // ── Customer ─────────────────────────────────────
    public static Customer createCustomer() {
        Customer c = new Customer();
        long id = nextId();
        c.setId(id);
        c.setEmail("customer" + id + "@test.com");
        c.setPasswordHash("$2a$10$hashedpassword");
        c.setFirstName("Test");
        c.setLastName("Customer " + id);
        c.setPhone("+905001234567");
        c.setActive(true);
        c.setEmailVerified(true);
        c.setKvkkConsent(true);
        c.setKvkkConsentAt(LocalDateTime.now());
        c.setStatus(CustomerStatus.ACTIVE);
        c.setFailedLoginCount(0);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    // ── CustomerAddress ──────────────────────────────
    public static CustomerAddress createAddress(Customer customer) {
        CustomerAddress a = new CustomerAddress();
        a.setId(nextId());
        a.setCustomer(customer);
        a.setTitle("Ev Adresi");
        a.setAddressType(AddressType.SHIPPING);
        a.setFirstName(customer.getFirstName());
        a.setLastName(customer.getLastName());
        a.setPhone(customer.getPhone());
        a.setCity("Istanbul");
        a.setDistrict("Kadikoy");
        a.setAddressLine("Test Sokak No:1");
        a.setDefault(true);
        return a;
    }

    // ── Cart ─────────────────────────────────────────
    public static Cart createCart(Customer customer) {
        Cart c = new Cart();
        c.setId(nextId());
        c.setCustomer(customer);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    // ── CartItem ─────────────────────────────────────
    public static CartItem createCartItem(Cart cart, Product product, int quantity) {
        CartItem ci = new CartItem();
        ci.setId(nextId());
        ci.setCart(cart);
        ci.setProduct(product);
        ci.setQuantity(quantity);
        ci.setAddedAt(LocalDateTime.now());
        ci.setUpdatedAt(LocalDateTime.now());
        return ci;
    }

    // ── Order ────────────────────────────────────────
    public static Order createOrder(Customer customer) {
        Order o = new Order();
        long id = nextId();
        o.setId(id);
        o.setOrderNumber("ORD-TEST-" + String.format("%04d", id));
        o.setCustomer(customer);
        o.setStatus(OrderStatus.PENDING_PAYMENT);
        o.setSubtotal(new BigDecimal("200.00"));
        o.setShippingCost(new BigDecimal("29.99"));
        o.setDiscountAmount(BigDecimal.ZERO);
        o.setVatTotal(new BigDecimal("40.00"));
        o.setSctTotal(BigDecimal.ZERO);
        o.setGrandTotal(new BigDecimal("269.99"));
        o.setPaymentMethod("CREDIT_CARD");
        o.setInstallmentCount(1);
        o.setDistanceSalesContractAccepted(true);
        o.setShippingAddressSnapshot(Map.of("city", "Istanbul", "addressLine", "Test Sokak", "firstName", "Test", "lastName", "User", "phone", "05001234567"));
        o.setBillingAddressSnapshot(Map.of("city", "Istanbul", "addressLine", "Test Sokak", "firstName", "Test", "lastName", "User", "phone", "05001234567"));
        o.setIpAddress("127.0.0.1");
        o.setCreatedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        return o;
    }

    // ── OrderItem ────────────────────────────────────
    public static OrderItem createOrderItem(Order order, Product product, int quantity) {
        OrderItem oi = new OrderItem();
        oi.setId(nextId());
        oi.setOrder(order);
        oi.setProduct(product);
        oi.setQuantity(quantity);
        oi.setUnitPrice(product.getPrice());
        oi.setVatRate(product.getVatRate() != null ? product.getVatRate() : BigDecimal.ZERO);
        oi.setSctRate(BigDecimal.ZERO);
        oi.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        oi.setProductSnapshot(Map.of("name", product.getName(), "sku", product.getSku()));
        return oi;
    }

    // ── PaymentTransaction ───────────────────────────
    public static PaymentTransaction createPaymentTransaction(Order order) {
        PaymentTransaction pt = new PaymentTransaction();
        pt.setId(nextId());
        pt.setOrder(order);
        pt.setIdempotencyKey(UUID.randomUUID().toString());
        pt.setPaymentProvider(PaymentProvider.IYZICO);
        pt.setStatus(PaymentStatus.INITIATED);
        pt.setAmount(order.getGrandTotal());
        pt.setCurrency("TRY");
        pt.setInstallmentCount(1);
        pt.setIpAddress("127.0.0.1");
        pt.setCreatedAt(LocalDateTime.now());
        pt.setUpdatedAt(LocalDateTime.now());
        return pt;
    }

    // ── Coupon ───────────────────────────────────────
    public static Coupon createCoupon(String code, DiscountType type, BigDecimal value) {
        Coupon c = new Coupon();
        c.setId(nextId());
        c.setCode(code);
        c.setDiscountType(type);
        c.setDiscountValue(value);
        c.setActive(true);
        c.setStartsAt(LocalDateTime.now().minusDays(1));
        c.setExpiresAt(LocalDateTime.now().plusDays(30));
        c.setUsedCount(0);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    // ── CustomerRefreshToken ─────────────────────────
    public static CustomerRefreshToken createRefreshToken(Customer customer) {
        CustomerRefreshToken rt = new CustomerRefreshToken();
        rt.setId(nextId());
        rt.setCustomer(customer);
        rt.setToken(UUID.randomUUID().toString());
        rt.setExpiresAt(LocalDateTime.now().plusDays(30));
        rt.setCreatedAt(LocalDateTime.now());
        return rt;
    }
}
