package dev.obe.disbursement.infrastructure.config;

import dev.obe.disbursement.application.port.out.PartnerRules;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ConfiguredPartnerRules implements PartnerRules {

    private final PartnerProperties properties;

    ConfiguredPartnerRules(PartnerProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<BigDecimal> maxAmount(String partnerCode) {
        return Optional.ofNullable(properties.partners().get(partnerCode)).map(PartnerProperties.Partner::maxAmount);
    }
}
