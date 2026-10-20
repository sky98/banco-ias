package co.com.bancoias.model.preapprovedusage;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

public final class PayloadHasher {

    private PayloadHasher() {
    }

    public static String hash(
            String requestReference, String preApprovedId, String customerId, BigDecimal amount) {
        String canonical = String.join("|",
                normalize(requestReference),
                normalize(preApprovedId),
                normalize(customerId),
                normalize(amount));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String normalize(String value) {
        return Optional.ofNullable(value).map(String::trim).orElse("");
    }

    private static String normalize(BigDecimal value) {
        return Optional.ofNullable(value)
                .map(number -> number.stripTrailingZeros().toPlainString())
                .orElse("");
    }
}
