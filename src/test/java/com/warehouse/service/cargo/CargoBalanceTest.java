package com.warehouse.service.cargo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Telling "no money" apart from "no answer".
 *
 * <p>Both used to arrive as a null balance, so an account the carrier reported as having no
 * credit — which stops every shipment on a prepaid account — was treated exactly like a carrier
 * that happened to be unreachable, and passed over in silence.
 */
class CargoBalanceTest {

    @Test
    @DisplayName("Bakiye bildirilmemesi tükenmiş sayılır")
    void anUnreportedCreditCountsAsDepleted() {
        assertThat(CargoBalance.notReported().isDepleted()).isTrue();
        assertThat(CargoBalance.notReported().describe()).contains("bakiye");
    }

    @Test
    @DisplayName("Ulaşılamama tükenmişlik değil — bilinmezlik")
    void unreachableIsNotDepleted() {
        assertThat(CargoBalance.unreachable().isDepleted()).isFalse();
        assertThat(CargoBalance.unsupported().isDepleted()).isFalse();
        assertThat(CargoBalance.rejected("HTTP 401").isDepleted()).isFalse();
    }

    /**
     * A refused token and a carrier we cannot reach need opposite fixes — one is a settings
     * change, the other a network problem — so the admin screen must not merge them.
     */
    @Test
    @DisplayName("Reddedilen token ile ulaşılamayan sunucu ayrı durumlar")
    void aRefusedTokenIsNotAnUnreachableCarrier() {
        CargoBalance rejected = CargoBalance.rejected("HTTP 401");
        CargoBalance unreachable = CargoBalance.unreachable("ResourceAccessException");

        assertThat(rejected.isCredentialProblem()).isTrue();
        assertThat(unreachable.isCredentialProblem()).isFalse();

        assertThat(rejected.describe()).contains("token").contains("HTTP 401");
        assertThat(unreachable.describe()).doesNotContain("token");
    }

    @Test
    @DisplayName("Sıfır ve altı bakiye tükenmiştir, pozitif bakiye değildir")
    void zeroOrBelowIsDepleted() {
        assertThat(CargoBalance.of(BigDecimal.ZERO).isDepleted()).isTrue();
        assertThat(CargoBalance.of(new BigDecimal("-5")).isDepleted()).isTrue();
        assertThat(CargoBalance.of(new BigDecimal("0.01")).isDepleted()).isFalse();
    }

    @Test
    @DisplayName("Her durumun admin'e gösterilecek bir açıklaması var")
    void everyStateExplainsItself() {
        for (CargoBalance.State state : CargoBalance.State.values()) {
            CargoBalance balance = state == CargoBalance.State.OK
                    ? CargoBalance.of(new BigDecimal("120"))
                    : new CargoBalance(state, null, null);
            assertThat(balance.describe()).isNotBlank();
        }
    }
}
