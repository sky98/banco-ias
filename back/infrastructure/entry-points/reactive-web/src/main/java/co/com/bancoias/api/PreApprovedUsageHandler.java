package co.com.bancoias.api;

import co.com.bancoias.api.dto.CreatePreApprovedUsageRequest;
import co.com.bancoias.api.dto.PreApprovedUsageResponse;
import co.com.bancoias.api.error.ReferenceConflictException;
import co.com.bancoias.api.error.RequestValidationException;
import co.com.bancoias.api.mapper.PreApprovedUsageMapper;
import co.com.bancoias.usecase.getpreapproved.GetPreApprovedUseCase;
import co.com.bancoias.usecase.getpreapprovedrequest.GetPreApprovedRequestUseCase;
import co.com.bancoias.usecase.listrecentpreapprovedrequests.ListRecentPreApprovedRequestsUseCase;
import co.com.bancoias.usecase.processpreapprovedrequest.ProcessPreApprovedRequestCommand;
import co.com.bancoias.usecase.processpreapprovedrequest.ProcessPreApprovedRequestUseCase;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class PreApprovedUsageHandler {

    private static final int DEFAULT_LIMIT = 20;

    private final ProcessPreApprovedRequestUseCase processUseCase;
    private final GetPreApprovedRequestUseCase getUsageUseCase;
    private final ListRecentPreApprovedRequestsUseCase listUseCase;
    private final GetPreApprovedUseCase getPreApprovedUseCase;

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(CreatePreApprovedUsageRequest.class)
                .switchIfEmpty(Mono.error(new RequestValidationException("El cuerpo es obligatorio")))
                .flatMap(body -> validate(body).thenReturn(body))
                .flatMap(body -> processUseCase.execute(new ProcessPreApprovedRequestCommand(
                        body.requestReference(),
                        body.preApprovedId(),
                        body.customerId(),
                        body.amount())))
                .flatMap(result -> {
                    PreApprovedUsageResponse response =
                            PreApprovedUsageMapper.toResponse(result.usage(), result.replayed());
                    if (result.conflict()) {
                        return Mono.error(new ReferenceConflictException(response));
                    }
                    HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
                    return ServerResponse.status(status)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(response);
                });
    }

    public Mono<ServerResponse> getByReference(ServerRequest request) {
        return getUsageUseCase.execute(request.pathVariable("requestReference"))
                .map(usage -> PreApprovedUsageMapper.toResponse(usage, false))
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> listRecent(ServerRequest request) {
        return Mono.defer(() -> {
            int limit = parseLimit(request);
            return listUseCase.execute(limit)
                    .map(usage -> PreApprovedUsageMapper.toResponse(usage, false))
                    .collectList()
                    .flatMap(usages -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(usages));
        });
    }

    public Mono<ServerResponse> getPreApproved(ServerRequest request) {
        return getPreApprovedUseCase.execute(request.pathVariable("preApprovedId"))
                .map(PreApprovedUsageMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<Void> validate(CreatePreApprovedUsageRequest body) {
        List<String> errors = new ArrayList<>();
        if (isBlank(body.requestReference())) {
            errors.add("requestReference: no debe estar vacío");
        }
        if (isBlank(body.preApprovedId())) {
            errors.add("preApprovedId: no debe estar vacío");
        }
        if (isBlank(body.customerId())) {
            errors.add("customerId: no debe estar vacío");
        }
        if (body.amount() == null) {
            errors.add("amount: no debe ser nulo");
        }
        if (errors.isEmpty()) {
            return Mono.empty();
        }
        return Mono.error(new RequestValidationException(String.join("; ", errors)));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int parseLimit(ServerRequest request) {
        return request.queryParam("limit")
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException exception) {
                        throw new RequestValidationException("limit debe ser un número entero");
                    }
                })
                .orElse(DEFAULT_LIMIT);
    }
}
