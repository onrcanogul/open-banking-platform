package dev.obe.disbursement.domain.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record DisbursementDetails(
        String partnerCode,
        String customerNo,
        String accountNo,
        BigDecimal amount,
        String currency) {

    /** A fingerprint of the request. The amount is normalized so 100 and 100.00 count as equal. */
    public String hash() {
        String content = String.join("|", partnerCode, customerNo, accountNo,
                amount.stripTrailingZeros().toPlainString(), currency);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is missing from this Java runtime", ex);
        }
    }
}
