package com.warehouse.service.cargo;

import java.math.BigDecimal;

/**
 * The cargo account's balance, and how much we actually know about it.
 *
 * <p>A plain {@code BigDecimal} could not tell three very different situations apart, because all
 * three came back as null: the integration being off, the carrier being unreachable, and the
 * carrier answering perfectly well with no credit figure at all. The last one is the dangerous
 * case — a prepaid account with nothing on it — and it was the one being silently ignored.
 *
 * <p>A rejected credential is separated from an unreachable carrier for the same reason. Both used
 * to surface as "ulaşılamadı ya da token reddedildi", which leaves an admin with two unrelated
 * things to check and no way to tell which one is broken — a wrong token and a blocked network
 * need opposite fixes.
 */
public record CargoBalance(State state, BigDecimal amount, String detail) {

    public enum State {
        /** The carrier reported a figure. */
        OK,
        /** The carrier answered, but reported no credit — usually an account with no balance. */
        NOT_REPORTED,
        /** The carrier answered and refused the credentials: a wrong or expired token. */
        REJECTED,
        /** We never got an answer: DNS, timeout, blocked egress, or an error from the carrier. */
        UNREACHABLE,
        /** The cargo integration is switched off, or the provider has no balance concept. */
        UNSUPPORTED
    }

    public static CargoBalance of(BigDecimal amount) {
        return new CargoBalance(State.OK, amount, null);
    }

    public static CargoBalance notReported() {
        return new CargoBalance(State.NOT_REPORTED, null, null);
    }

    /** @param detail what the carrier said, shown to the admin verbatim */
    public static CargoBalance rejected(String detail) {
        return new CargoBalance(State.REJECTED, null, detail);
    }

    public static CargoBalance unreachable(String detail) {
        return new CargoBalance(State.UNREACHABLE, null, detail);
    }

    public static CargoBalance unreachable() {
        return unreachable(null);
    }

    public static CargoBalance unsupported() {
        return new CargoBalance(State.UNSUPPORTED, null, null);
    }

    /** True when shipments are at risk: no credit reported, or a figure at or below zero. */
    public boolean isDepleted() {
        if (state == State.NOT_REPORTED) return true;
        return state == State.OK && amount != null && amount.signum() <= 0;
    }

    /** True when the problem is ours to fix in settings rather than a passing outage. */
    public boolean isCredentialProblem() {
        return state == State.REJECTED;
    }

    /** What an admin should read on the cargo screen. */
    public String describe() {
        String base = switch (state) {
            case OK -> amount + " TL";
            case NOT_REPORTED -> "Kargo firması bakiye bildirmedi — hesapta yüklü bakiye "
                    + "olmayabilir. Bakiye yüklenmeden kargo gönderisi oluşturulamaz.";
            case REJECTED -> "Kargo firması API token'ını reddetti. Token hatalı, süresi dolmuş "
                    + "ya da bu hesaba ait değil — Ayarlar > Kargo API'den yeniden girin.";
            case UNREACHABLE -> "Kargo firmasına ulaşılamadı — sunucunun internet çıkışı "
                    + "ya da kargonomi_api_base_url ayarı engelliyor olabilir.";
            case UNSUPPORTED -> "Kargo entegrasyonu kapalı.";
        };
        return detail == null || detail.isBlank() ? base : base + " (" + detail + ")";
    }
}
