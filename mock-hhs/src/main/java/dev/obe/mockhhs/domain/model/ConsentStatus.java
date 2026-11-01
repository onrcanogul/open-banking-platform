package dev.obe.mockhhs.domain.model;

public enum ConsentStatus {
    YETKI_BEKLENIYOR("B"),
    YETKILENDIRILDI("Y"),
    YETKI_IPTAL("I");

    private final String code;

    ConsentStatus(String code) {
        this.code = code;
    }

    /** Value of TR.OHVPS.DataCode.RizaDurumu. */
    public String code() {
        return code;
    }
}
