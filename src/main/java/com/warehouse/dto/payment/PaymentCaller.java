package com.warehouse.dto.payment;

/**
 * Who is asking to initialise a payment, and with what proof.
 *
 * <p>Payment initialisation has to stay reachable without a session, because guests
 * check out without an account. That made it the weakest link: it accepted a bare
 * {@code orderId} and did nothing to establish that the caller had anything to do with
 * that order. This type makes the proof an explicit, required argument, so a future
 * caller cannot forget to supply one.</p>
 *
 * @param customerId  the authenticated customer, when there is one
 * @param accessToken the one-time token handed back by checkout, for guests
 */
public record PaymentCaller(Long customerId, String accessToken) {

    public static PaymentCaller of(Long customerId, String accessToken) {
        return new PaymentCaller(customerId, accessToken);
    }
}
