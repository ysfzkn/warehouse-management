package com.warehouse.service;

import com.warehouse.entity.Coupon;
import com.warehouse.entity.CouponUsage;
import com.warehouse.entity.Customer;
import com.warehouse.entity.Order;
import com.warehouse.enums.DiscountType;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.CouponRepository;
import com.warehouse.repository.CouponUsageRepository;
import com.warehouse.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules on Coupon (minimum basket, total limit, per-customer limit) were configured but
 * never read before CouponService existed, so these cover each of them explicitly.
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock private CouponRepository coupons;
    @Mock private CouponUsageRepository usages;

    private CouponService service;

    @BeforeEach
    void setUp() {
        service = new CouponService(coupons, usages);
    }

    private void stubActive(Coupon coupon) {
        when(coupons.findActiveByCode(eq(coupon.getCode()), any(LocalDateTime.class)))
            .thenReturn(Optional.of(coupon));
    }

    // ─── validate ────────────────────────────────────────────────────────────

    @Test
    void should_reject_basket_below_minimum_order_amount() {
        Coupon coupon = TestDataFactory.createCoupon("MIN500", DiscountType.FIXED_AMOUNT, new BigDecimal("50"));
        coupon.setMinOrderAmount(new BigDecimal("500.00"));
        stubActive(coupon);

        assertThatThrownBy(() -> service.validate("MIN500", new BigDecimal("499.99"), 1L))
            .isInstanceOf(WarehouseManagementException.class)
            .hasMessageContaining("en az");
    }

    @Test
    void should_accept_basket_exactly_at_minimum_order_amount() {
        Coupon coupon = TestDataFactory.createCoupon("MIN500", DiscountType.FIXED_AMOUNT, new BigDecimal("50"));
        coupon.setMinOrderAmount(new BigDecimal("500.00"));
        stubActive(coupon);

        assertThat(service.validate("MIN500", new BigDecimal("500.00"), null)).isSameAs(coupon);
    }

    @Test
    void should_reject_when_total_usage_limit_is_exhausted() {
        Coupon coupon = TestDataFactory.createCoupon("LIMIT", DiscountType.FIXED_AMOUNT, new BigDecimal("10"));
        coupon.setUsageLimit(100);
        coupon.setUsedCount(100);
        stubActive(coupon);

        assertThatThrownBy(() -> service.validate("LIMIT", new BigDecimal("200"), 1L))
            .isInstanceOf(WarehouseManagementException.class)
            .hasMessageContaining("kullanım hakkı dolmuş");
    }

    @Test
    void should_reject_when_customer_reached_their_own_limit() {
        Coupon coupon = TestDataFactory.createCoupon("ONCE", DiscountType.FIXED_AMOUNT, new BigDecimal("10"));
        coupon.setUsageLimitPerCustomer(1);
        stubActive(coupon);
        when(usages.countByCouponIdAndCustomerId(coupon.getId(), 7L)).thenReturn(1L);

        assertThatThrownBy(() -> service.validate("ONCE", new BigDecimal("200"), 7L))
            .isInstanceOf(WarehouseManagementException.class)
            .hasMessageContaining("kullanabileceğiniz sayıya");
    }

    @Test
    void should_uppercase_and_trim_the_submitted_code() {
        Coupon coupon = TestDataFactory.createCoupon("SAVE10", DiscountType.PERCENTAGE, new BigDecimal("10"));
        stubActive(coupon);

        assertThat(service.validate("  save10 ", new BigDecimal("100"), null)).isSameAs(coupon);
    }

    @Test
    void validateQuietly_should_return_empty_instead_of_throwing() {
        when(coupons.findActiveByCode(eq("GONE"), any(LocalDateTime.class))).thenReturn(Optional.empty());

        assertThat(service.validateQuietly("GONE", new BigDecimal("100"), null)).isEmpty();
    }

    // ─── discount math ───────────────────────────────────────────────────────

    @Test
    void percentage_discount_should_respect_the_max_discount_cap() {
        Coupon coupon = TestDataFactory.createCoupon("P50", DiscountType.PERCENTAGE, new BigDecimal("50"));
        coupon.setMaxDiscountAmount(new BigDecimal("100.00"));

        assertThat(service.calculateDiscount(coupon, new BigDecimal("1000.00")))
            .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void fixed_discount_should_never_exceed_the_basket() {
        Coupon coupon = TestDataFactory.createCoupon("BIG", DiscountType.FIXED_AMOUNT, new BigDecimal("500.00"));

        assertThat(service.calculateDiscount(coupon, new BigDecimal("120.00")))
            .isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    void free_shipping_should_carry_no_line_discount() {
        Coupon coupon = TestDataFactory.createCoupon("SHIP", DiscountType.FREE_SHIPPING, BigDecimal.ONE);

        assertThat(service.calculateDiscount(coupon, new BigDecimal("300.00"))).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(service.isFreeShipping(coupon)).isTrue();
    }

    // ─── redeem / release ────────────────────────────────────────────────────

    @Test
    void redeem_should_increment_usage_and_record_it() {
        Coupon coupon = TestDataFactory.createCoupon("SAVE10", DiscountType.PERCENTAGE, new BigDecimal("10"));
        coupon.setUsedCount(4);
        Customer customer = TestDataFactory.createCustomer();
        Order order = new Order();
        order.setId(55L);
        order.setOrderNumber("ORD1");

        when(usages.existsByOrderId(55L)).thenReturn(false);
        when(coupons.findByIdForUpdate(coupon.getId())).thenReturn(Optional.of(coupon));

        service.redeem(coupon.getId(), customer, order, new BigDecimal("300.00"));

        assertThat(coupon.getUsedCount()).isEqualTo(5);
        verify(coupons).save(coupon);
        verify(usages).save(any(CouponUsage.class));
    }

    @Test
    void redeem_should_be_idempotent_per_order() {
        Order order = new Order();
        order.setId(55L);
        when(usages.existsByOrderId(55L)).thenReturn(true);

        service.redeem(9L, TestDataFactory.createCustomer(), order, new BigDecimal("300.00"));

        verify(coupons, never()).findByIdForUpdate(anyLong());
        verify(usages, never()).save(any(CouponUsage.class));
    }

    @Test
    void redeem_should_refuse_when_the_limit_ran_out_since_validation() {
        Coupon coupon = TestDataFactory.createCoupon("LAST", DiscountType.FIXED_AMOUNT, new BigDecimal("10"));
        coupon.setUsageLimit(1);
        coupon.setUsedCount(1); // another checkout took it in the meantime
        Order order = new Order();
        order.setId(56L);
        order.setOrderNumber("ORD2");

        when(usages.existsByOrderId(56L)).thenReturn(false);
        when(coupons.findByIdForUpdate(coupon.getId())).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.redeem(coupon.getId(), TestDataFactory.createCustomer(), order,
                new BigDecimal("300.00")))
            .isInstanceOf(WarehouseManagementException.class);
        verify(usages, never()).save(any(CouponUsage.class));
    }

    @Test
    void release_should_give_the_use_back() {
        Coupon coupon = TestDataFactory.createCoupon("SAVE10", DiscountType.PERCENTAGE, new BigDecimal("10"));
        coupon.setUsedCount(3);
        CouponUsage usage = new CouponUsage();
        usage.setCoupon(coupon);

        when(usages.findByOrderId(77L)).thenReturn(Optional.of(usage));
        when(coupons.findByIdForUpdate(coupon.getId())).thenReturn(Optional.of(coupon));

        service.release(77L);

        assertThat(coupon.getUsedCount()).isEqualTo(2);
        verify(usages).delete(usage);
    }

    @Test
    void release_should_do_nothing_for_an_order_without_a_coupon() {
        when(usages.findByOrderId(78L)).thenReturn(Optional.empty());

        service.release(78L);

        verify(coupons, never()).save(any(Coupon.class));
    }
}
