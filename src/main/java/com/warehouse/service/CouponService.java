package com.warehouse.service;

import com.warehouse.entity.Coupon;
import com.warehouse.entity.CouponUsage;
import com.warehouse.entity.Customer;
import com.warehouse.entity.Order;
import com.warehouse.enums.DiscountType;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.repository.CouponRepository;
import com.warehouse.repository.CouponUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * The single place coupon rules are enforced.
 *
 * <p>The rules configured on {@link Coupon} — minimum order amount, total usage limit and
 * per-customer limit — used to be write-only: nothing read them and {@code usedCount} was never
 * incremented, so any active coupon worked on any basket, unlimited times. Everything that
 * applies or redeems a coupon now goes through here.</p>
 *
 * <p>Validation runs twice on purpose: once when the customer applies the code (immediate
 * feedback) and again when the order is created, because the basket total, the coupon's window
 * and the remaining usage can all change in between.</p>
 */
@Service
public class CouponService {

    private static final Logger logger = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository coupons;
    private final CouponUsageRepository usages;

    public CouponService(CouponRepository coupons, CouponUsageRepository usages) {
        this.coupons = coupons;
        this.usages = usages;
    }

    /**
     * Full rule check. Throws with a customer-facing Turkish reason when the coupon does not
     * qualify. {@code customerId} may be null for a guest basket — the per-customer limit is
     * then only enforced at redemption, once the guest has a customer record.
     */
    @Transactional(readOnly = true)
    public Coupon validate(String code, BigDecimal subtotal, Long customerId) {
        if (code == null || code.isBlank()) {
            throw invalid("Kupon kodu boş olamaz.");
        }
        Coupon coupon = coupons.findActiveByCode(code.trim().toUpperCase(Locale.ROOT), LocalDateTime.now())
            .orElseThrow(() -> invalid("Geçersiz veya süresi dolmuş kupon kodu."));
        checkRules(coupon, subtotal, customerId);
        return coupon;
    }

    /**
     * Same rules, but returns empty instead of throwing. Used when rendering a basket that
     * already carries a code: a coupon that has since expired should quietly stop applying
     * rather than making the basket unreadable.
     */
    @Transactional(readOnly = true)
    public Optional<Coupon> validateQuietly(String code, BigDecimal subtotal, Long customerId) {
        try {
            return Optional.of(validate(code, subtotal, customerId));
        } catch (WarehouseManagementException e) {
            return Optional.empty();
        }
    }

    private void checkRules(Coupon coupon, BigDecimal subtotal, Long customerId) {
        BigDecimal basket = subtotal == null ? BigDecimal.ZERO : subtotal;

        if (coupon.getMinOrderAmount() != null && basket.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw invalid("Bu kupon en az " + format(coupon.getMinOrderAmount())
                + " tutarındaki siparişlerde geçerli. Sepet tutarınız: " + format(basket) + ".");
        }
        if (coupon.getUsageLimit() != null && used(coupon) >= coupon.getUsageLimit()) {
            throw invalid("Bu kuponun kullanım hakkı dolmuş.");
        }
        if (customerId != null && coupon.getUsageLimitPerCustomer() != null) {
            long mine = usages.countByCouponIdAndCustomerId(coupon.getId(), customerId);
            if (mine >= coupon.getUsageLimitPerCustomer()) {
                throw invalid("Bu kuponu kullanabileceğiniz sayıya ulaştınız.");
            }
        }
    }

    /**
     * Discount in TRY for this coupon on this subtotal. Never exceeds the subtotal, so an
     * over-generous fixed-amount coupon cannot produce a negative order total.
     * FREE_SHIPPING carries no line discount — the caller zeroes the shipping cost instead.
     */
    public BigDecimal calculateDiscount(Coupon coupon, BigDecimal subtotal) {
        if (coupon == null || subtotal == null || subtotal.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal discount = switch (coupon.getDiscountType()) {
            case PERCENTAGE -> {
                BigDecimal raw = subtotal.multiply(nullSafe(coupon.getDiscountValue()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                yield coupon.getMaxDiscountAmount() != null && raw.compareTo(coupon.getMaxDiscountAmount()) > 0
                    ? coupon.getMaxDiscountAmount() : raw;
            }
            case FIXED_AMOUNT -> nullSafe(coupon.getDiscountValue());
            case FREE_SHIPPING -> BigDecimal.ZERO;
        };
        if (discount.signum() < 0) return BigDecimal.ZERO;
        return discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isFreeShipping(Coupon coupon) {
        return coupon != null && coupon.getDiscountType() == DiscountType.FREE_SHIPPING;
    }

    /**
     * Records the redemption: increments {@code usedCount} under a row lock and writes a
     * {@link CouponUsage} row. Re-checks the limits inside the lock so two concurrent
     * checkouts cannot both consume the last remaining use.
     *
     * <p>Called after the order exists. Idempotent per order, so a retried payment callback
     * does not double-count.</p>
     */
    @Transactional
    public void redeem(Long couponId, Customer customer, Order order, BigDecimal subtotal) {
        if (couponId == null || order == null || order.getId() == null) return;
        if (usages.existsByOrderId(order.getId())) {
            logger.debug("Coupon already redeemed for order {}", order.getOrderNumber());
            return;
        }
        Coupon locked = coupons.findByIdForUpdate(couponId)
            .orElseThrow(() -> invalid("Kupon bulunamadı."));

        // Re-check under the lock — the window between validation and redemption is exactly
        // where an over-issue would slip through.
        checkRules(locked, subtotal, customer != null ? customer.getId() : null);

        locked.setUsedCount(used(locked) + 1);
        coupons.save(locked);

        CouponUsage usage = new CouponUsage();
        usage.setCoupon(locked);
        usage.setCustomer(customer);
        usage.setOrder(order);
        usages.save(usage);
        logger.info("Coupon {} redeemed on order {} ({} / {} used)",
            locked.getCode(), order.getOrderNumber(), locked.getUsedCount(), locked.getUsageLimit());
    }

    /**
     * Gives the use back when the order it belonged to is cancelled or times out — mirroring
     * how the reserved stock is released. Without this, a failed payment would permanently
     * burn one of a limited coupon's uses.
     */
    @Transactional
    public void release(Long orderId) {
        if (orderId == null) return;
        usages.findByOrderId(orderId).ifPresent(usage -> {
            Coupon coupon = coupons.findByIdForUpdate(usage.getCoupon().getId()).orElse(null);
            if (coupon != null) {
                coupon.setUsedCount(Math.max(0, used(coupon) - 1));
                coupons.save(coupon);
            }
            usages.delete(usage);
            logger.info("Coupon usage released for cancelled order {}", orderId);
        });
    }

    private static int used(Coupon coupon) {
        return coupon.getUsedCount() == null ? 0 : coupon.getUsedCount();
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String format(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " TL";
    }

    private static WarehouseManagementException invalid(String message) {
        return new WarehouseManagementException(ErrorCode.VALIDATION_ERROR, message);
    }
}
