package co.com.bancoias.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PreApprovedUsageResponse(
        String requestReference,
        String preApprovedId,
        String customerId,
        BigDecimal amount,
        String status,
        String reason,
        Instant processedAt,
        BigDecimal remainingAmount,
        boolean replayed) {
}
