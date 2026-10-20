package co.com.bancoias.usecase.processpreapprovedrequest;

import java.math.BigDecimal;

public record ProcessPreApprovedRequestCommand(
        String requestReference,
        String preApprovedId,
        String customerId,
        BigDecimal amount) {
}
