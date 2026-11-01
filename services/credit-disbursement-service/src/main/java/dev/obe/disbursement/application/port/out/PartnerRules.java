package dev.obe.disbursement.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;

public interface PartnerRules {

    /** Returns the most a partner may disburse at once, empty for an unknown partner. */
    Optional<BigDecimal> maxAmount(String partnerCode);
}
