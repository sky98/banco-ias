package co.com.bancoias.usecase.getpreapprovedrequest;

import co.com.bancoias.model.preapprovedusage.PreApprovedUsage;
import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedUsageRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetPreApprovedRequestUseCase {

    private final PreApprovedUsageRepository repository;

    public Mono<PreApprovedUsage> execute(String requestReference) {
        return repository.findByRequestReference(requestReference);
    }
}
