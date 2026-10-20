package co.com.bancoias.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Resultado del procesamiento de una solicitud")
public record PreApprovedUsageResponse(
        @Schema(description = "Referencia única de la solicitud", example = "REF-001")
                String requestReference,
        @Schema(description = "Identificador del preaprobado", example = "PRA-1001")
                String preApprovedId,
        @Schema(description = "Identificador del cliente", example = "USR-10")
                String customerId,
        @Schema(description = "Monto procesado", example = "600000.00")
                BigDecimal amount,
        @Schema(description = "Estado de la solicitud", example = "APPROVED")
                String status,
        @Schema(description = "Motivo del rechazo; nulo si fue aprobada", example = "INSUFFICIENT_QUOTA")
                String reason,
        @Schema(description = "Fecha de procesamiento", example = "2026-10-19T23:30:00Z")
                Instant processedAt,
        @Schema(description = "Cupo restante después del procesamiento", example = "400000.00")
                BigDecimal remainingAmount,
        @Schema(description = "Indica si la solicitud es un replay idempotente", example = "false")
                boolean replayed) {
}
