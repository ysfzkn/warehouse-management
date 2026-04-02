package com.warehouse.service.payment;

/**
 * @deprecated PayTR is now handled by VirtualPosGateway + PayTRProtocol.
 * This stub is kept for backward compatibility only and is not loaded as a Spring bean.
 * Use the admin panel to configure a PayTR gateway via "Ödeme Gateway'leri" page.
 */
// Removed @Component — no longer a Spring bean
// PayTR integration is now in: com.warehouse.service.payment.protocol.PayTRProtocol
@Deprecated
public class PayTRPaymentGateway {
    // Intentionally empty — all PayTR logic moved to PayTRProtocol
}
