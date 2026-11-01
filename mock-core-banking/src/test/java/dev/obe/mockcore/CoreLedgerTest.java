package dev.obe.mockcore;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoreLedgerTest {

    private final CoreLedger ledger = new CoreLedger(new CoreProperties(Map.of("C-1", new BigDecimal("1000.00"))));

    @Test
    void reservesFromTheLimit() {
        assertThat(ledger.reserveLimit("op-1", "C-1", new BigDecimal("400.00"))).isTrue();

        assertThat(ledger.availableLimit("C-1")).isEqualByComparingTo("600.00");
    }

    @Test
    void refusesMoreThanTheLimit() {
        assertThat(ledger.reserveLimit("op-1", "C-1", new BigDecimal("1000.01"))).isFalse();

        assertThat(ledger.availableLimit("C-1")).isEqualByComparingTo("1000.00");
    }

    @Test
    void repeatedOperationReservesOnce() {
        ledger.reserveLimit("op-1", "C-1", new BigDecimal("400.00"));

        assertThat(ledger.reserveLimit("op-1", "C-1", new BigDecimal("400.00"))).isTrue();

        assertThat(ledger.availableLimit("C-1")).isEqualByComparingTo("600.00");
    }

    @Test
    void repeatedRefusalStaysARefusal() {
        ledger.reserveLimit("op-1", "C-1", new BigDecimal("5000.00"));

        assertThat(ledger.reserveLimit("op-1", "C-1", new BigDecimal("5000.00"))).isFalse();
    }

    @Test
    void unknownCustomerHasNoLimit() {
        assertThat(ledger.reserveLimit("op-1", "C-9", new BigDecimal("1.00"))).isFalse();
    }
}
