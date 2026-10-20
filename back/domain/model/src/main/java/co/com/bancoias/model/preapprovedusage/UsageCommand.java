package co.com.bancoias.model.preapprovedusage;

import java.math.BigDecimal;

public record UsageCommand(
        String requestReference,
        String preApprovedId,
        String customerId,
        BigDecimal amount,
        String payloadHash) {
}
