package co.com.bancoias.api.dto;

import co.com.bancoias.api.validation.MaxDecimalPlaces;
import co.com.bancoias.api.validation.MaxIntegerDigits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Optional;

@Schema(description = "Solicitud de consumo de cupo preaprobado")
public record CreatePreApprovedUsageRequest(
        @Schema(
                        description = "Referencia única de la solicitud",
                        example = "REF-001",
                        maxLength = 80,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "requestReference: no debe estar vacío")
                @Size(max = 80, message = "requestReference: máximo 80 caracteres")
                String requestReference,
        @Schema(
                        description = "Identificador del preaprobado",
                        example = "PRA-1001",
                        maxLength = 40,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "preApprovedId: no debe estar vacío")
                @Size(max = 40, message = "preApprovedId: máximo 40 caracteres")
                String preApprovedId,
        @Schema(
                        description = "Identificador del cliente",
                        example = "USR-10",
                        maxLength = 40,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank(message = "customerId: no debe estar vacío")
                @Size(max = 40, message = "customerId: máximo 40 caracteres")
                String customerId,
        @Schema(
                        description = "Monto solicitado; máximo 2 decimales y 17 dígitos enteros",
                        example = "600000.00")
                @NotNull(message = "amount: no debe ser nulo")
                @MaxDecimalPlaces(value = 2, message = "amount: máximo 2 decimales")
                @MaxIntegerDigits(value = 17, message = "amount: supera el máximo permitido")
                BigDecimal amount) {

    public CreatePreApprovedUsageRequest {
        requestReference = trim(requestReference);
        preApprovedId = trim(preApprovedId);
        customerId = trim(customerId);
    }

    private static String trim(String value) {
        return Optional.ofNullable(value).map(String::trim).orElse(null);
    }
}
