package com.warehouse.service;

import com.warehouse.dto.store.CartDto;
import com.warehouse.entity.*;
import com.warehouse.enums.DiscountType;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.*;
import com.warehouse.service.impl.CartServiceImpl;
import com.warehouse.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock private CartRepository cartRepo;
    @Mock private CartItemRepository cartItemRepo;
    @Mock private ProductRepository productRepo;
    @Mock private CouponRepository couponRepo;
    @Mock private StockService stockService;
    @Mock private com.warehouse.repository.ProductImageRepository productImageRepo;
    @Mock private com.warehouse.service.ShippingCostService shippingCostService;

    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(cartRepo, cartItemRepo, productRepo, couponRepo,
                stockService, productImageRepo, shippingCostService);
    }

    @Test
    void should_create_cart_for_new_customer() {
        Long customerId = 1L;
        when(cartRepo.findByCustomerId(customerId)).thenReturn(Optional.empty());
        when(cartRepo.save(any(Cart.class))).thenAnswer(inv -> { Cart c = inv.getArgument(0); c.setId(1L); return c; });
        when(cartItemRepo.findByCartId(any())).thenReturn(List.of());

        CartDto result = cartService.getCart(customerId, null);

        assertThat(result).isNotNull();
        assertThat(result.getItemCount()).isZero();
        verify(cartRepo).save(any(Cart.class));
    }

    @Test
    void should_create_guest_cart_with_session_id() {
        String sessionId = "sess_abc123";
        when(cartRepo.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(cartRepo.save(any(Cart.class))).thenAnswer(inv -> { Cart c = inv.getArgument(0); c.setId(1L); return c; });
        when(cartItemRepo.findByCartId(any())).thenReturn(List.of());

        CartDto result = cartService.getCart(null, sessionId);

        assertThat(result).isNotNull();
        verify(cartRepo).save(argThat(cart -> cart.getSessionId().equals(sessionId) && cart.getExpiresAt() != null));
    }

    @Test
    void should_add_item_to_cart() {
        Customer customer = TestDataFactory.createCustomer();
        Cart cart = TestDataFactory.createCart(customer);
        Product product = TestDataFactory.createProduct();

        when(cartRepo.findByCustomerId(customer.getId())).thenReturn(Optional.of(cart));
        when(productRepo.findById(product.getId())).thenReturn(Optional.of(product));
        when(cartItemRepo.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.empty());
        when(cartItemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepo.save(any())).thenReturn(cart);
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of());

        CartDto result = cartService.addItem(customer.getId(), null, product.getId(), 2);

        assertThat(result).isNotNull();
        verify(cartItemRepo).save(argThat(item -> item.getQuantity() == 2));
    }

    @Test
    void should_increment_quantity_when_adding_existing_item() {
        Customer customer = TestDataFactory.createCustomer();
        Cart cart = TestDataFactory.createCart(customer);
        Product product = TestDataFactory.createProduct();
        CartItem existing = TestDataFactory.createCartItem(cart, product, 1);

        when(cartRepo.findByCustomerId(customer.getId())).thenReturn(Optional.of(cart));
        when(productRepo.findById(product.getId())).thenReturn(Optional.of(product));
        when(cartItemRepo.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.of(existing));
        when(cartItemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepo.save(any())).thenReturn(cart);
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of());

        cartService.addItem(customer.getId(), null, product.getId(), 3);

        verify(cartItemRepo).save(argThat(item -> item.getQuantity() == 4)); // 1 + 3
    }

    @Test
    void should_throw_when_adding_inactive_product() {
        Customer customer = TestDataFactory.createCustomer();
        Cart cart = TestDataFactory.createCart(customer);
        Product product = TestDataFactory.createProduct();
        product.setActive(false);

        when(cartRepo.findByCustomerId(customer.getId())).thenReturn(Optional.of(cart));
        when(productRepo.findById(product.getId())).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> cartService.addItem(customer.getId(), null, product.getId(), 1))
            .isInstanceOf(WarehouseManagementException.class);
    }

    @Test
    void should_update_item_quantity() {
        Customer customer = TestDataFactory.createCustomer();
        Cart cart = TestDataFactory.createCart(customer);
        Product product = TestDataFactory.createProduct();
        CartItem item = TestDataFactory.createCartItem(cart, product, 2);

        when(cartRepo.findByCustomerId(customer.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepo.findById(item.getId())).thenReturn(Optional.of(item));
        when(cartItemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of());

        cartService.updateItem(customer.getId(), null, item.getId(), 5);

        verify(cartItemRepo).save(argThat(i -> i.getQuantity() == 5));
    }

    @Test
    void should_throw_when_updating_item_from_different_cart() {
        Customer customer = TestDataFactory.createCustomer();
        Cart cart = TestDataFactory.createCart(customer);
        Cart otherCart = new Cart();
        otherCart.setId(999L);
        Product product = TestDataFactory.createProduct();
        CartItem item = TestDataFactory.createCartItem(otherCart, product, 2);

        when(cartRepo.findByCustomerId(customer.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepo.findById(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.updateItem(customer.getId(), null, item.getId(), 5))
            .isInstanceOf(WarehouseManagementException.class);
    }

    @Test
    void should_remove_item_from_cart() {
        Customer customer = TestDataFactory.createCustomer();
        Cart cart = TestDataFactory.createCart(customer);
        Product product = TestDataFactory.createProduct();
        CartItem item = TestDataFactory.createCartItem(cart, product, 2);

        when(cartRepo.findByCustomerId(customer.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepo.findById(item.getId())).thenReturn(Optional.of(item));
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of());

        cartService.removeItem(customer.getId(), null, item.getId());

        verify(cartItemRepo).delete(item);
    }

    @Test
    void should_apply_percentage_coupon() {
        Customer customer = TestDataFactory.createCustomer();
        Cart cart = TestDataFactory.createCart(customer);
        Coupon coupon = TestDataFactory.createCoupon("SAVE10", DiscountType.PERCENTAGE, new BigDecimal("10"));

        when(cartRepo.findByCustomerId(customer.getId())).thenReturn(Optional.of(cart));
        when(couponRepo.findActiveByCode(eq("SAVE10"), any(LocalDateTime.class))).thenReturn(Optional.of(coupon));

        Product product = TestDataFactory.createProduct();
        product.setPrice(new BigDecimal("1000.00"));
        CartItem item = TestDataFactory.createCartItem(cart, product, 1);
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of(item));
        when(stockService.getStocksByProduct(any())).thenReturn(List.of());

        CartDto result = cartService.applyCoupon(customer.getId(), null, "SAVE10");

        assertThat(result.getCouponCode()).isEqualTo("SAVE10");
        assertThat(result.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("100.00")); // 10% of 1000
    }

    @Test
    void should_apply_free_shipping_coupon() {
        Customer customer = TestDataFactory.createCustomer();
        Cart cart = TestDataFactory.createCart(customer);
        Coupon coupon = TestDataFactory.createCoupon("FREESHIP", DiscountType.FREE_SHIPPING, new BigDecimal("1"));

        when(cartRepo.findByCustomerId(customer.getId())).thenReturn(Optional.of(cart));
        when(couponRepo.findActiveByCode(eq("FREESHIP"), any(LocalDateTime.class))).thenReturn(Optional.of(coupon));

        Product product = TestDataFactory.createProduct();
        product.setPrice(new BigDecimal("100.00")); // Below 500 threshold
        CartItem item = TestDataFactory.createCartItem(cart, product, 1);
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of(item));
        when(stockService.getStocksByProduct(any())).thenReturn(List.of());

        CartDto result = cartService.applyCoupon(customer.getId(), null, "FREESHIP");

        assertThat(result.getShippingCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_throw_for_invalid_coupon() {
        Customer customer = TestDataFactory.createCustomer();
        Cart cart = TestDataFactory.createCart(customer);

        when(cartRepo.findByCustomerId(customer.getId())).thenReturn(Optional.of(cart));
        when(couponRepo.findActiveByCode(eq("INVALID"), any(LocalDateTime.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.applyCoupon(customer.getId(), null, "INVALID"))
            .isInstanceOf(WarehouseManagementException.class);
    }

    @Test
    void should_calculate_free_shipping_above_threshold() {
        Customer customer = TestDataFactory.createCustomer();
        Cart cart = TestDataFactory.createCart(customer);
        Product product = TestDataFactory.createProduct();
        product.setPrice(new BigDecimal("600.00"));
        CartItem item = TestDataFactory.createCartItem(cart, product, 1);

        when(cartRepo.findByCustomerId(customer.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of(item));
        when(stockService.getStocksByProduct(any())).thenReturn(List.of());
        // Subtotal 600 → ShippingCostService eşik üstünde ücretsiz kargo döner
        when(shippingCostService.calculate(any(), any())).thenReturn(BigDecimal.ZERO);

        CartDto result = cartService.getCart(customer.getId(), null);

        assertThat(result.getShippingCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_calculate_shipping_cost_below_threshold() {
        Customer customer = TestDataFactory.createCustomer();
        Cart cart = TestDataFactory.createCart(customer);
        Product product = TestDataFactory.createProduct();
        product.setPrice(new BigDecimal("100.00"));
        CartItem item = TestDataFactory.createCartItem(cart, product, 1);

        when(cartRepo.findByCustomerId(customer.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of(item));
        when(stockService.getStocksByProduct(any())).thenReturn(List.of());
        // Subtotal 100 → ShippingCostService eşik altında 29.99 TL kargo döner
        when(shippingCostService.calculate(any(), any())).thenReturn(new BigDecimal("29.99"));

        CartDto result = cartService.getCart(customer.getId(), null);

        assertThat(result.getShippingCost()).isEqualByComparingTo(new BigDecimal("29.99"));
    }
}
