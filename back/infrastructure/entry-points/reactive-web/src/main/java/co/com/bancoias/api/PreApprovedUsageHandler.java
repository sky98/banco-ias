package co.com.bancoias.api;

import co.com.bancoias.api.dto.CreatePreApprovedUsageRequest;
import co.com.bancoias.api.dto.PreApprovedUsageResponse;
import co.com.bancoias.api.error.ReferenceConflictException;
import co.com.bancoias.api.error.RequestValidationException;
import co.com.bancoias.api.mapper.PreApprovedUsageMapper;
import co.com.bancoias.api.validation.ValidadorRequest;
import co.com.bancoias.model.preapprovedusage.UsageProcessingResult;
import co.com.bancoias.usecase.getpreapproved.GetPreApprovedUseCase;
import co.com.bancoias.usecase.getpreapprovedrequest.GetPreApprovedRequestUseCase;
import co.com.bancoias.usecase.listrecentpreapprovedrequests.ListRecentPreApprovedRequestsUseCase;
import co.com.bancoias.usecase.processpreapprovedrequest.ProcessPreApprovedRequestCommand;
import co.com.bancoias.usecase.processpreapprovedrequest.ProcessPreApprovedRequestUseCase;
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
    private final ValidadorRequest validator;

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(CreatePreApprovedUsageRequest.class)
                .switchIfEmpty(Mono.error(new RequestValidationException("El cuerpo es obligatorio")))
                .flatMap(validator::validar)
                .flatMap(this::process)
                .flatMap(this::toResponse);
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

    private Mono<UsageProcessingResult> process(CreatePreApprovedUsageRequest body) {
        return processUseCase.execute(new ProcessPreApprovedRequestCommand(
                body.requestReference(),
                body.preApprovedId(),
                body.customerId(),
                body.amount()));
    }

    private Mono<ServerResponse> toResponse(UsageProcessingResult result) {
        PreApprovedUsageResponse response =
                PreApprovedUsageMapper.toResponse(result.usage(), result.replayed());

        return switch (result.outcome()) {
            case CONFLICT -> Mono.error(new ReferenceConflictException(response));
            case REPLAYED -> json(HttpStatus.OK, response);
            case CREATED -> json(HttpStatus.CREATED, response);
        };
    }

    private Mono<ServerResponse> json(HttpStatus status, PreApprovedUsageResponse response) {
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(response);
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
