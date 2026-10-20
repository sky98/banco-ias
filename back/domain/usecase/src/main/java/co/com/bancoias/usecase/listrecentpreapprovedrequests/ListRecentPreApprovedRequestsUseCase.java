package co.com.bancoias.usecase.listrecentpreapprovedrequests;

import co.com.bancoias.model.preapprovedusage.PreApprovedUsage;
import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedUsageRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class ListRecentPreApprovedRequestsUseCase {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final PreApprovedUsageRepository repository;

    public Flux<PreApprovedUsage> execute(int limit) {
        int effectiveLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return repository.findRecent(effectiveLimit);
    }
}
