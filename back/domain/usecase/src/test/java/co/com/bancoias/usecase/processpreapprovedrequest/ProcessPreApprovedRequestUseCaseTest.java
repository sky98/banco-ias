package co.com.bancoias.usecase.processpreapprovedrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.bancoias.model.preapprovedusage.PayloadHasher;
import co.com.bancoias.model.preapprovedusage.PreApprovedUsage;
import co.com.bancoias.model.preapprovedusage.RejectionReason;
import co.com.bancoias.model.preapprovedusage.UsageCommand;
import co.com.bancoias.model.preapprovedusage.UsageProcessingResult;
import co.com.bancoias.model.preapprovedusage.UsageStatus;
import co.com.bancoias.model.preapprovedusage.event.EventType;
import co.com.bancoias.model.preapprovedusage.event.PreApprovedRequestEvent;
import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedRequestEventPublisher;
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

    @Mock
    private PreApprovedRequestEventPublisher eventPublisher;

    @InjectMocks
    private ProcessPreApprovedRequestUseCase useCase;

    @Test
    void delegatesWithNormalizedCommandAndPayloadHash() {
        PreApprovedUsage usage = usage();
        UsageProcessingResult result = UsageProcessingResult.created(usage);
        when(repository.process(any(UsageCommand.class))).thenReturn(Mono.just(result));
        when(eventPublisher.publish(any(PreApprovedRequestEvent.class))).thenReturn(Mono.empty());

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
        
        verify(eventPublisher).publish(any(PreApprovedRequestEvent.class));
    }

    @Test
    void publishesEventWhenRequestCreated() {
        PreApprovedUsage usage = usage();
        UsageProcessingResult result = UsageProcessingResult.created(usage);
        when(repository.process(any(UsageCommand.class))).thenReturn(Mono.just(result));
        when(eventPublisher.publish(any(PreApprovedRequestEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new ProcessPreApprovedRequestCommand(
                        "REF-1", "PRA-1", "USR-10", BigDecimal.ONE)))
                .expectNext(result)
                .verifyComplete();

        ArgumentCaptor<PreApprovedRequestEvent> eventCaptor = ArgumentCaptor.forClass(PreApprovedRequestEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        PreApprovedRequestEvent event = eventCaptor.getValue();

        assertThat(event.getRequestReference()).isEqualTo("REF-1");
        assertThat(event.getPreApprovedId()).isEqualTo("PRA-1");
        assertThat(event.getCustomerId()).isEqualTo("USR-10");
        assertThat(event.getAmount()).isEqualByComparingTo("100.00");
        assertThat(event.getStatus()).isEqualTo("APPROVED");
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    void publishesEventWhenRequestRejected() {
        PreApprovedUsage rejected = rejectedUsage();
        UsageProcessingResult result = UsageProcessingResult.created(rejected);
        when(repository.process(any(UsageCommand.class))).thenReturn(Mono.just(result));
        when(eventPublisher.publish(any(PreApprovedRequestEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new ProcessPreApprovedRequestCommand(
                        "REF-2", "PRA-1", "USR-10", BigDecimal.ONE)))
                .expectNext(result)
                .verifyComplete();

        ArgumentCaptor<PreApprovedRequestEvent> eventCaptor = ArgumentCaptor.forClass(PreApprovedRequestEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        PreApprovedRequestEvent event = eventCaptor.getValue();

        assertThat(event.getStatus()).isEqualTo("REJECTED");
        assertThat(event.getReason()).isEqualTo("INSUFFICIENT_QUOTA");
        assertThat(event.getEventType()).isEqualTo(
                EventType.PRE_APPROVED_REQUEST_REJECTED);
    }

    @Test
    void doesNotPublishEventWhenReplayed() {
        PreApprovedUsage usage = usage();
        UsageProcessingResult result = UsageProcessingResult.replayed(usage);
        when(repository.process(any(UsageCommand.class))).thenReturn(Mono.just(result));

        StepVerifier.create(useCase.execute(new ProcessPreApprovedRequestCommand(
                        "REF-1", "PRA-1", "USR-10", BigDecimal.ONE)))
                .expectNext(result)
                .verifyComplete();

        verify(eventPublisher, never()).publish(any(PreApprovedRequestEvent.class));
    }

    @Test
    void doesNotPublishEventWhenConflict() {
        PreApprovedUsage usage = usage();
        UsageProcessingResult result = UsageProcessingResult.conflict(usage);
        when(repository.process(any(UsageCommand.class))).thenReturn(Mono.just(result));

        StepVerifier.create(useCase.execute(new ProcessPreApprovedRequestCommand(
                        "REF-1", "PRA-1", "USR-10", BigDecimal.ONE)))
                .expectNext(result)
                .verifyComplete();

        verify(eventPublisher, never()).publish(any(PreApprovedRequestEvent.class));
    }

    @Test
    void propagatesRepositoryError() {
        IllegalArgumentException error = new IllegalArgumentException("boom");
        when(repository.process(any(UsageCommand.class))).thenReturn(Mono.error(error));

        StepVerifier.create(useCase.execute(new ProcessPreApprovedRequestCommand(
                        "REF-1", "PRA-1", "USR-10", BigDecimal.ONE)))
                .expectErrorMatches(throwable -> throwable == error)
                .verify();
        
        verify(eventPublisher, never()).publish(any(PreApprovedRequestEvent.class));
    }

    private PreApprovedUsage rejectedUsage() {
        return PreApprovedUsage.builder()
                .id(2L)
                .requestReference("REF-2")
                .preApprovedId("PRA-1")
                .customerId("USR-10")
                .amount(new BigDecimal("100.00"))
                .status(UsageStatus.REJECTED)
                .reason(RejectionReason.INSUFFICIENT_QUOTA)
                .payloadHash("hash")
                .processedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .remainingAmount(new BigDecimal("50.00"))
                .build();
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
