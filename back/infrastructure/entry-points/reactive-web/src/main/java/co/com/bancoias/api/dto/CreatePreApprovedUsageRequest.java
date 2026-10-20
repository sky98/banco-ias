package co.com.bancoias.api.dto;

import java.math.BigDecimal;

public record CreatePreApprovedUsageRequest(
        String requestReference,
        String preApprovedId,
        String customerId,
        BigDecimal amount) {
}
