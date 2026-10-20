package co.com.bancoias.api.dto;

import java.math.BigDecimal;

public record PreApprovedResponse(
        String preApprovedId,
        String customerId,
        String status,
        BigDecimal availableAmount) {
}
