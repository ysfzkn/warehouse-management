package com.warehouse.service.cargo;

import java.math.BigDecimal;

/**
 * The cargo account's balance, and how much we actually know about it.
 *
 * <p>A plain {@code BigDecimal} could not tell three very different situations apart, because all
 * three came back as null: the integration being off, the carrier being unreachable, and the
 * carrier answering perfectly well with no credit figure at all. The last one is the dangerous
 * case — a prepaid account with nothing on it — and it was the one being silently ignored.
 */
public record CargoBalance(State state, BigDecimal amount) {

    public enum State {
        /** The carrier reported a figure. */
        OK,
        /** The carrier answered, but reported no credit — usually an account with no balance. */
        NOT_REPORTED,
        /** We could not ask: network, credentials, or an error response. */
        UNREACHABLE,
        /** The cargo integration is switched off, or the provider has no balance concept. */
        UNSUPPORTED
    }

    public static CargoBalance of(BigDecimal amount) {
        return new CargoBalance(State.OK, amount);
    }

    public static CargoBalance notReported() {
        return new CargoBalance(State.NOT_REPORTED, null);
    }

    public static CargoBalance unreachable() {
        return new CargoBalance(State.UNREACHABLE, null);
    }

    public static CargoBalance unsupported() {
        return new CargoBalance(State.UNSUPPORTED, null);
    }

    /** True when shipments are at risk: no credit reported, or a figure at or below zero. */
    public boolean isDepleted() {
        if (state == State.NOT_REPORTED) return true;
        return state == State.OK && amount != null && amount.signum() <= 0;
    }

    /** What an admin should read on the cargo screen. */
    public String describe() {
        return switch (state) {
            case OK -> amount + " TL";
            case NOT_REPORTED -> "Kargo firması bakiye bildirmedi — hesapta yüklü bakiye "
                    + "olmayabilir. Bakiye yüklenmeden kargo gönderisi oluşturulamaz.";
            case UNREACHABLE -> "Bakiye sorgulanamadı — kargo firmasına ulaşılamıyor "
                    + "ya da API bilgileri hatalı.";
            case UNSUPPORTED -> "Kargo entegrasyonu kapalı.";
        };
    }
}
