package co.com.bancoias.usecase.processpreapprovedrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.bancoias.model.preapprovedusage.PayloadHasher;
import co.com.bancoias.model.preapprovedusage.PreApprovedUsage;
import co.com.bancoias.model.preapprovedusage.UsageCommand;
import co.com.bancoias.model.preapprovedusage.UsageProcessingResult;
import co.com.bancoias.model.preapprovedusage.UsageStatus;
import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedUsageRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProcessPreApprovedRequestUseCaseTest {

    @Mock
    private PreApprovedUsageRepository repository;

    @InjectMocks
    private ProcessPreApprovedRequestUseCase useCase;

    @Test
    void delegatesWithNormalizedCommandAndPayloadHash() {
        PreApprovedUsage usage = usage();
        UsageProcessingResult result = UsageProcessingResult.created(usage);
        when(repository.process(any(UsageCommand.class))).thenReturn(Mono.just(result));

        StepVerifier.create(useCase.execute(new ProcessPreApprovedRequestCommand(
                        " REF-1 ", " PRA-1 ", " USR-10 ", new BigDecimal("100.00"))))
                .expectNext(result)
                .verifyComplete();

        ArgumentCaptor<UsageCommand> captor = ArgumentCaptor.forClass(UsageCommand.class);
        verify(repository).process(captor.capture());
        UsageCommand command = captor.getValue();

        assertThat(command.requestReference()).isEqualTo("REF-1");
        assertThat(command.preApprovedId()).isEqualTo("PRA-1");
        assertThat(command.customerId()).isEqualTo("USR-10");
        assertThat(command.amount()).isEqualByComparingTo("100.00");
        assertThat(command.payloadHash()).isEqualTo(
                PayloadHasher.hash("REF-1", "PRA-1", "USR-10", new BigDecimal("100.00")));
    }

    @Test
    void propagatesRepositoryError() {
        IllegalArgumentException error = new IllegalArgumentException("boom");
        when(repository.process(any(UsageCommand.class))).thenReturn(Mono.error(error));

        StepVerifier.create(useCase.execute(new ProcessPreApprovedRequestCommand(
                        "REF-1", "PRA-1", "USR-10", BigDecimal.ONE)))
                .expectErrorMatches(throwable -> throwable == error)
                .verify();
    }

    private PreApprovedUsage usage() {
        return PreApprovedUsage.builder()
                .id(1L)
                .requestReference("REF-1")
                .preApprovedId("PRA-1")
                .customerId("USR-10")
                .amount(new BigDecimal("100.00"))
                .status(UsageStatus.APPROVED)
                .payloadHash("hash")
                .processedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .remainingAmount(new BigDecimal("900.00"))
                .build();
    }
}
