package co.com.bancoias.usecase.processpreapprovedrequest;

import co.com.bancoias.model.preapprovedusage.PayloadHasher;
import co.com.bancoias.model.preapprovedusage.UsageCommand;
import co.com.bancoias.model.preapprovedusage.UsageProcessingResult;
import co.com.bancoias.model.preapprovedusage.event.PreApprovedRequestEvent;
import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedRequestEventPublisher;
import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedUsageRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ProcessPreApprovedRequestUseCase {

    private final PreApprovedUsageRepository repository;
    private final PreApprovedRequestEventPublisher eventPublisher;

    public Mono<UsageProcessingResult> execute(ProcessPreApprovedRequestCommand command) {
        String payloadHash = PayloadHasher.hash(
                command.requestReference(),
                command.preApprovedId(),
                command.customerId(),
                command.amount());

        return repository.process(new UsageCommand(
                command.requestReference().trim(),
                command.preApprovedId().trim(),
                command.customerId().trim(),
                command.amount(),
                payloadHash))
                .flatMap(this::publishEventIfNeeded);
    }

    private Mono<UsageProcessingResult> publishEventIfNeeded(UsageProcessingResult result) {
        return Mono.just(result)
                .filter(r -> r.outcome() == UsageProcessingResult.Outcome.CREATED)
                .flatMap(this::publishEvent)
                .switchIfEmpty(Mono.just(result));
    }

    private Mono<UsageProcessingResult> publishEvent(UsageProcessingResult result) {
        PreApprovedRequestEvent event = PreApprovedRequestEvent.from(result);
        return eventPublisher.publish(event)
                .thenReturn(result);
    }
}
