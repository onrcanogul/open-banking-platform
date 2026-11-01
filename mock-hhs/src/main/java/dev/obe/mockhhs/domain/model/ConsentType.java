package dev.obe.mockhhs.domain.model;

import dev.obe.contract.event.ResourceType;

public enum ConsentType {
    PAYMENT(ResourceType.ODEME_EMRI_RIZASI, "O"),
    ACCOUNT_INFORMATION(ResourceType.HESAP_BILGISI_RIZASI, "H");

    private final ResourceType resourceType;
    private final String code;

    ConsentType(ResourceType resourceType, String code) {
        this.resourceType = resourceType;
        this.code = code;
    }

    public ResourceType resourceType() {
        return resourceType;
    }

    /** Value of TR.OHVPS.DataCode.RizaTip. */
    public String code() {
        return code;
    }
}
