package co.com.bancoias.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error de la API")
public record ApiError(
        @Schema(description = "Código de error", example = "VALIDATION_ERROR")
                String code,
        @Schema(description = "Mensaje descriptivo", example = "amount: máximo 2 decimales")
                String message,
        @Schema(description = "Detalle adicional; en un 409 contiene el resultado original")
                Object details) {
}
