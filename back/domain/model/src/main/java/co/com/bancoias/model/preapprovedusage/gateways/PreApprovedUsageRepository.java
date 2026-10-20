package co.com.bancoias.model.preapprovedusage.gateways;

import co.com.bancoias.model.preapprovedusage.PreApprovedUsage;
import co.com.bancoias.model.preapprovedusage.UsageCommand;
import co.com.bancoias.model.preapprovedusage.UsageProcessingResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PreApprovedUsageRepository {
    Mono<UsageProcessingResult> process(UsageCommand command);

    Mono<PreApprovedUsage> findByRequestReference(String requestReference);

    Flux<PreApprovedUsage> findRecent(int limit);
}
