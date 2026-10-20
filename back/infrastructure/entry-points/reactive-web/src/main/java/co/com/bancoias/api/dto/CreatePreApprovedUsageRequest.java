package co.com.bancoias.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Solicitud de consumo de cupo preaprobado")
public record CreatePreApprovedUsageRequest(
        @Schema(
                        description = "Referencia única de la solicitud",
                        example = "REF-001",
                        maxLength = 80,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String requestReference,
        @Schema(
                        description = "Identificador del preaprobado",
                        example = "PRA-1001",
                        maxLength = 40,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String preApprovedId,
        @Schema(
                        description = "Identificador del cliente",
                        example = "USR-10",
                        maxLength = 40,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String customerId,
        @Schema(
                        description = "Monto solicitado; máximo 2 decimales y 17 dígitos enteros",
                        example = "600000.00")
                BigDecimal amount) {
}
