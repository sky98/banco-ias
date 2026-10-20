package co.com.bancoias.api.error;

import co.com.bancoias.api.dto.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ApiExceptionHandler {

    public Mono<ServerResponse> handle(Throwable error) {
        if (error instanceof RequestValidationException validation) {
            return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", validation.getMessage(), null);
        }
        if (error instanceof ReferenceConflictException conflict) {
            return response(HttpStatus.CONFLICT, "REFERENCE_CONFLICT", conflict.getMessage(), conflict.getOriginal());
        }
        if (error instanceof ServerWebInputException || error instanceof DecodingException) {
            return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "La solicitud no es válida", null);
        }
        log.error("Unexpected error processing request", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ocurrió un error inesperado", null);
    }

    private Mono<ServerResponse> response(HttpStatus status, String code, String message, Object details) {
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApiError(code, message, details));
    }
}
