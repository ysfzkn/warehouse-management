package com.warehouse.service.impl;

import com.warehouse.dto.store.CartDto;
import com.warehouse.dto.store.CartItemDto;
import com.warehouse.entity.*;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.*;
import com.warehouse.constants.ShippingConstants;
import com.warehouse.service.CartService;
import com.warehouse.service.StockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CartServiceImpl implements CartService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CartServiceImpl.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final StockService stockService;
    private final com.warehouse.repository.ProductImageRepository productImageRepository;
    private final com.warehouse.service.ShippingCostService shippingCostService;
    private final com.warehouse.service.CouponService couponService;

    public CartServiceImpl(CartRepository cartRepository, CartItemRepository cartItemRepository,
                           ProductRepository productRepository, CouponRepository couponRepository,
                           StockService stockService,
                           com.warehouse.repository.ProductImageRepository productImageRepository,
                           com.warehouse.service.ShippingCostService shippingCostService,
                           com.warehouse.service.CouponService couponService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.couponRepository = couponRepository;
        this.stockService = stockService;
        this.productImageRepository = productImageRepository;
        this.shippingCostService = shippingCostService;
        this.couponService = couponService;
    }

    @Override
    public CartDto getCart(Long customerId, String sessionId) {
        // Okuma isteği hata vermemeli: sahibi belli olmayan bir istek için sepet zaten
        // boştur. Yazma yollarında (addItem vb.) aynı durum hata olarak dönüyor, çünkü
        // orada kullanıcının bir beklentisi var ve sessizce kaybolmamalı.
        if (customerId == null && (sessionId == null || sessionId.isBlank())) {
            return emptyCart();
        }
        Cart cart = findOrCreateCart(customerId, sessionId);
        return toCartDto(cart);
    }

    @Override
    public CartDto addItem(Long customerId, String sessionId, Long productId, int quantity) {
        Cart cart = findOrCreateCart(customerId, sessionId);
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.isActive() || !product.isEcommerceVisible()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Ürün e-ticarette satışa açık değil.");
        }

        var existingItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);
        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
            cartItemRepository.save(item);
        } else {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(quantity);
            cartItemRepository.save(item);
        }
        cart.setUpdatedAt(LocalDateTime.now());
        cartRepository.save(cart);
        return toCartDto(cart);
    }

    @Override
    public CartDto updateItem(Long customerId, String sessionId, Long cartItemId, int quantity) {
        Cart cart = findOrCreateCart(customerId, sessionId);
        CartItem item = cartItemRepository.findById(cartItemId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepet ürünü bulunamadı."));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepet ogesi bu sepete ait degil.");
        }

        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return toCartDto(cart);
    }

    @Override
    public CartDto removeItem(Long customerId, String sessionId, Long cartItemId) {
        Cart cart = findOrCreateCart(customerId, sessionId);
        CartItem item = cartItemRepository.findById(cartItemId)
            .orElseThrow(() -> new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepet ürünü bulunamadı."));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, "Sepet ogesi bu sepete ait degil.");
        }

        cartItemRepository.delete(item);
        return toCartDto(cart);
    }

    @Override
    public CartDto applyCoupon(Long customerId, String sessionId, String couponCode) {
        Cart cart = findOrCreateCart(customerId, sessionId);
        // Every rule (minimum basket, total limit, per-customer limit) is checked here, not
        // just the code's existence — and the code is stored so it survives a page reload.
        Coupon coupon = couponService.validate(couponCode, subtotalOf(cart), customerId);
        cart.setCouponCode(coupon.getCode());
        cartRepository.save(cart);
        return toCartDtoWithCoupon(cart, coupon);
    }

    @Override
    public CartDto removeCoupon(Long customerId, String sessionId) {
        Cart cart = findOrCreateCart(customerId, sessionId);
        cart.setCouponCode(null);
        cartRepository.save(cart);
        return toCartDtoWithCoupon(cart, null);
    }

    /** Basket total before discount and shipping — what every coupon rule is measured against. */
    private BigDecimal subtotalOf(Cart cart) {
        return cartItemRepository.findByCartId(cart.getId()).stream()
            .map(this::toCartItemDto)
            .map(CartItemDto::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public void clearCart(Long customerId, String sessionId) {
        Cart cart = findCart(customerId, sessionId);
        if (cart != null) {
            cartItemRepository.deleteByCartId(cart.getId());
        }
    }

    private Cart findOrCreateCart(Long customerId, String sessionId) {
        // Ne oturum açmış müşteri ne de tarayıcı oturumu var: kimin olduğu belli olmayan
        // bir sepet yazılamaz (carts.chk_cart_owner). Eskiden yine de INSERT deneniyor,
        // veritabanı reddediyor ve istek her seferinde ERROR seviyesinde log bırakıp
        // "veri bütünlüğü hatası" diye anlamsız bir mesajla dönüyordu. Oysa bu bir veri
        // bütünlüğü sorunu değil, eksik istek başlığı.
        if (customerId == null && (sessionId == null || sessionId.isBlank())) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                    "Sepet oturumu bulunamadı. Sayfayı yenileyip tekrar deneyin.");
        }
        Cart cart = findCart(customerId, sessionId);
        if (cart == null) {
            cart = new Cart();
            if (customerId != null) {
                Customer c = new Customer();
                c.setId(customerId);
                cart.setCustomer(c);
            } else {
                cart.setSessionId(sessionId);
                cart.setExpiresAt(LocalDateTime.now().plusDays(30));
            }
            cart = cartRepository.save(cart);
        }
        return cart;
    }

    private Cart findCart(Long customerId, String sessionId) {
        if (customerId != null) {
            return cartRepository.findByCustomerId(customerId).orElse(null);
        }
        if (sessionId != null) {
            return cartRepository.findBySessionId(sessionId).orElse(null);
        }
        return null;
    }

    /**
     * Re-validates whatever coupon the cart carries. A coupon that expired, ran out of uses or
     * no longer meets the minimum simply stops applying (and is cleared from the cart) instead
     * of blocking the basket.
     */
    private CartDto toCartDto(Cart cart) {
        if (cart.getCouponCode() == null) return toCartDtoWithCoupon(cart, null);
        Long customerId = cart.getCustomer() != null ? cart.getCustomer().getId() : null;
        Coupon coupon = couponService.validateQuietly(cart.getCouponCode(), subtotalOf(cart), customerId)
            .orElse(null);
        if (coupon == null) {
            cart.setCouponCode(null);
            cartRepository.save(cart);
        }
        return toCartDtoWithCoupon(cart, coupon);
    }

    /**
     * Henüz kimseye ait olmayan sepet: kaydı yok, kalemi yok, tutarı sıfır.
     *
     * <p>Kargo ücreti bile hesaplanmıyor — boş sepette gösterilecek bir kargo ücreti yok
     * ve ayarları okumak bu yolda gereksiz iş demek.</p>
     */
    private CartDto emptyCart() {
        return CartDto.builder()
            .items(List.of())
            .itemCount(0)
            .subtotal(BigDecimal.ZERO)
            .shippingCost(BigDecimal.ZERO)
            .discountAmount(BigDecimal.ZERO)
            .total(BigDecimal.ZERO)
            .vatBreakdown(new java.util.LinkedHashMap<>())
            .build();
    }

    private CartDto toCartDtoWithCoupon(Cart cart, Coupon coupon) {
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemDto> itemDtos = items.stream().map(this::toCartItemDto).collect(Collectors.toList());

        BigDecimal subtotal = itemDtos.stream()
            .map(CartItemDto::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discountAmount = BigDecimal.ZERO;
        String couponCode = null;
        String couponDesc = null;
        if (coupon != null) {
            couponCode = coupon.getCode();
            couponDesc = coupon.getDescription();
            // Same calculation the order will use, so the basket preview cannot drift from
            // what is actually charged.
            discountAmount = couponService.calculateDiscount(coupon, subtotal);
        }

        // Shipping cost: read from site settings configurable in the admin panel
        // (without provider selection; recalculated separately by CheckoutServiceImpl
        // once a provider is chosen during checkout).
        BigDecimal shippingCost = shippingCostService.calculate(subtotal, null);
        if (shippingCost == null) shippingCost = BigDecimal.ZERO;
        if (couponService.isFreeShipping(coupon)) {
            shippingCost = BigDecimal.ZERO;
        }

        BigDecimal total = subtotal.subtract(discountAmount).add(shippingCost);
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;

        // VAT-rate-based breakdown (since prices in Turkey are VAT-inclusive,
        // extract it from the gross price: vatAmount = gross * (rate / (100 + rate)))
        java.util.Map<String, BigDecimal> vatBreakdown = new java.util.LinkedHashMap<>();
        for (CartItemDto it : itemDtos) {
            if (it.getVatRate() == null || it.getVatRate().compareTo(BigDecimal.ZERO) <= 0) continue;
            String rateKey = it.getVatRate().stripTrailingZeros().toPlainString();
            BigDecimal lineGross = it.getLineTotal() != null ? it.getLineTotal() : BigDecimal.ZERO;
            BigDecimal divisor = BigDecimal.valueOf(100).add(it.getVatRate());
            BigDecimal vatAmount = lineGross.multiply(it.getVatRate())
                    .divide(divisor, 2, java.math.RoundingMode.HALF_UP);
            vatBreakdown.merge(rateKey, vatAmount, BigDecimal::add);
        }

        return CartDto.builder()
            .id(cart.getId())
            .items(itemDtos)
            .itemCount(itemDtos.stream().mapToInt(CartItemDto::getQuantity).sum())
            .subtotal(subtotal)
            .shippingCost(shippingCost)
            .discountAmount(discountAmount)
            .total(total)
            .couponCode(couponCode)
            .couponDescription(couponDesc)
            .vatBreakdown(vatBreakdown)
            .build();
    }

    private CartItemDto toCartItemDto(CartItem item) {
        Product product = item.getProduct();
        int available = 0;
        if (product.getProductType() == ProductType.BUNDLE) {
            // A set has no stock of its own — derive how many complete sets are assemblable.
            available = bundleAvailableSets(product);
        } else {
            try {
                List<Stock> stocks = stockService.getStocksByProduct(product.getId());
                available = stocks.stream().mapToInt(Stock::getAvailableQuantity).sum();
            } catch (Exception e) {
                // Falls back to "0 available", which shows the item as out of stock — worth a trace.
                logger.warn("Sepet stok sorgusu başarısız (productId={}): {}", product.getId(), e.toString());
            }
        }

        BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        BigDecimal salePrice = product.getSalePrice();
        BigDecimal effectivePrice = salePrice != null && salePrice.compareTo(BigDecimal.ZERO) > 0 ? salePrice : unitPrice;
        BigDecimal lineTotal = effectivePrice.multiply(BigDecimal.valueOf(item.getQuantity()));

        String imageUrl = null;
        try {
            var images = productImageRepository.findByProductOrderBySortOrderAscIdAsc(product);
            var img = com.warehouse.util.ProductImageUtil.displayCover(images).orElse(null);
            if (img != null) {
                imageUrl = "/api/admin/products/images/" + img.getId() + "/view?thumbnail=true";
            }
        } catch (org.hibernate.LazyInitializationException e) {
            // Detached product — the row simply renders without a cover image.
            imageUrl = null;
        }

        return CartItemDto.builder()
            .id(item.getId())
            .productId(product.getId())
            .productName(product.getName())
            .productSlug(product.getSlug())
            .productSku(product.getSku())
            .imageUrl(imageUrl)
            .unitPrice(unitPrice)
            .salePrice(salePrice)
            .lineTotal(lineTotal)
            .vatRate(product.getVatRate())
            .quantity(item.getQuantity())
            .availableStock(available)
            .stockStatus(available > 0 ? "IN_STOCK" : "OUT_OF_STOCK")
            .build();
    }

    /** How many complete sets can be assembled from current member stock. */
    private int bundleAvailableSets(Product bundle) {
        List<BundleItem> members = bundle.getBundleItems();
        if (members == null || members.isEmpty()) return 0;
        int minSets = Integer.MAX_VALUE;
        for (BundleItem bi : members) {
            Product m = bi.getProduct();
            if (m == null) continue;
            int qty = bi.getQuantity() != null && bi.getQuantity() > 0 ? bi.getQuantity() : 1;
            int memberAvail = 0;
            try {
                memberAvail = stockService.getStocksByProduct(m.getId()).stream()
                    .mapToInt(Stock::getAvailableQuantity).sum();
            } catch (Exception e) {
                logger.warn("Set üyesi stok sorgusu başarısız (productId={}): {}", m.getId(), e.toString());
            }
            minSets = Math.min(minSets, memberAvail / qty);
        }
        return minSets == Integer.MAX_VALUE ? 0 : minSets;
    }
}
