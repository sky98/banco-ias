package co.com.bancoias.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Cupo preaprobado de un cliente")
public record PreApprovedResponse(
        @Schema(description = "Identificador del preaprobado", example = "PRA-1001")
                String preApprovedId,
        @Schema(description = "Identificador del cliente", example = "USR-10")
                String customerId,
        @Schema(description = "Estado del preaprobado", example = "ACTIVE")
                String status,
        @Schema(description = "Cupo disponible", example = "400000.00")
                BigDecimal availableAmount) {
}
