package co.com.bancoias.usecase.processpreapprovedrequest;

import co.com.bancoias.model.preapprovedusage.PayloadHasher;
import co.com.bancoias.model.preapprovedusage.UsageCommand;
import co.com.bancoias.model.preapprovedusage.UsageProcessingResult;
import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedUsageRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ProcessPreApprovedRequestUseCase {

    private final PreApprovedUsageRepository repository;

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
                payloadHash));
    }
}
