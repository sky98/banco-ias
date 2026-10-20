package co.com.bancoias.api.error;

import co.com.bancoias.api.dto.ApiError;
import java.util.List;
import java.util.function.Function;
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

    private record ErrorMapping(
            Class<? extends Throwable> type,
            Function<Throwable, Mono<ServerResponse>> handler) {
    }

    private final List<ErrorMapping> mappings = List.of(
            new ErrorMapping(RequestValidationException.class, this::handleValidation),
            new ErrorMapping(ReferenceConflictException.class, this::handleConflict),
            new ErrorMapping(ServerWebInputException.class, this::handleInvalidRequest),
            new ErrorMapping(DecodingException.class, this::handleInvalidRequest));

    public Mono<ServerResponse> handle(Throwable error) {
        return mappings.stream()
                .filter(mapping -> mapping.type().isInstance(error))
                .findFirst()
                .map(mapping -> mapping.handler().apply(error))
                .orElseGet(() -> handleUnexpected(error));
    }

    private Mono<ServerResponse> handleValidation(Throwable error) {
        RequestValidationException validation = (RequestValidationException) error;
        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                validation.getMessage(),
                validation.getErrors());
    }

    private Mono<ServerResponse> handleConflict(Throwable error) {
        ReferenceConflictException conflict = (ReferenceConflictException) error;
        return response(
                HttpStatus.CONFLICT,
                "REFERENCE_CONFLICT",
                conflict.getMessage(),
                conflict.getOriginal());
    }

    private Mono<ServerResponse> handleInvalidRequest(Throwable ignored) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "La solicitud no es válida",
                null);
    }

    private Mono<ServerResponse> handleUnexpected(Throwable error) {
        log.error("Unexpected error processing request", error);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Ocurrió un error inesperado",
                null);
    }

    private Mono<ServerResponse> response(
            HttpStatus status, String code, String message, Object details) {
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApiError(code, message, details));
    }
}
