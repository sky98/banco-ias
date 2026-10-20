package co.com.bancoias.r2dbc.adapter;

import co.com.bancoias.model.preapproved.PreApproved;
import co.com.bancoias.model.preapproved.PreApprovedStatus;
import co.com.bancoias.model.preapprovedusage.PreApprovedUsage;
import co.com.bancoias.model.preapprovedusage.PreApprovedUsagePolicy;
import co.com.bancoias.model.preapprovedusage.RejectionReason;
import co.com.bancoias.model.preapprovedusage.UsageCommand;
import co.com.bancoias.model.preapprovedusage.UsageProcessingResult;
import co.com.bancoias.model.preapprovedusage.UsageStatus;
import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedUsageRepository;
import co.com.bancoias.r2dbc.entity.PreApprovedEntity;
import co.com.bancoias.r2dbc.entity.PreApprovedUsageEntity;
import co.com.bancoias.r2dbc.repository.PreApprovedR2dbcRepository;
import co.com.bancoias.r2dbc.repository.PreApprovedUsageR2dbcRepository;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class PreApprovedUsageR2dbcAdapter implements PreApprovedUsageRepository {

    private static final String SELECT_USAGE = """
            SELECT id, request_reference, pre_approved_id, customer_id, amount, status, reason,
                   payload_hash, processed_at, remaining_amount
            FROM preapproved_usage
            """;
    private static final String PG_UNIQUE_VIOLATION = "23505";

    private final DatabaseClient databaseClient;
    private final PreApprovedUsageR2dbcRepository usageRepository;
    private final PreApprovedR2dbcRepository preApprovedRepository;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<UsageProcessingResult> process(UsageCommand command) {
        return findByRequestReference(command.requestReference())
                .map(existing -> resultForExisting(existing, command))
                .switchIfEmpty(Mono.defer(() -> processNew(command)));
    }

    @Override
    public Mono<PreApprovedUsage> findByRequestReference(String requestReference) {
        return usageRepository.findByRequestReference(requestReference).map(this::toDomain);
    }

    @Override
    public Flux<PreApprovedUsage> findRecent(int limit) {
        return databaseClient.sql(SELECT_USAGE + " ORDER BY processed_at DESC, id DESC LIMIT :limit")
                .bind("limit", limit)
                .map((row, metadata) -> mapUsage(row))
                .all();
    }

    private Mono<UsageProcessingResult> processNew(UsageCommand command) {
        return transactionalOperator.execute(status -> processInTransaction(command))
                .single()
                .onErrorResume(DataIntegrityViolationException.class, error -> {
                    if (!isUniqueViolation(error)) {
                        return Mono.error(error);
                    }
                    return findByRequestReference(command.requestReference())
                            .map(existing -> resultForExisting(existing, command));
                });
    }

    private Mono<UsageProcessingResult> processInTransaction(UsageCommand command) {
        if (command.amount() == null) {
            return reject(command);
        }
        return decrementIfAvailable(command)
                .flatMap(remaining -> saveUsage(command, UsageStatus.APPROVED, null, remaining)
                        .map(UsageProcessingResult::created))
                .switchIfEmpty(Mono.defer(() -> reject(command)));
    }

    private Mono<BigDecimal> decrementIfAvailable(UsageCommand command) {
        return databaseClient.sql("""
                        UPDATE preapproved
                        SET available_amount = available_amount - :amount
                        WHERE pre_approved_id = :preApprovedId
                          AND customer_id = :customerId
                          AND status = 'ACTIVE'
                          AND :amount > 0
                          AND available_amount >= :amount
                        RETURNING available_amount
                        """)
                .bind("amount", command.amount())
                .bind("preApprovedId", command.preApprovedId())
                .bind("customerId", command.customerId())
                .map((row, metadata) -> row.get("available_amount", BigDecimal.class))
                .one();
    }

    private Mono<UsageProcessingResult> reject(UsageCommand command) {
        return preApprovedRepository.findById(command.preApprovedId())
                .map(this::toDomain)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(preApproved -> {
                    RejectionReason reason = PreApprovedUsagePolicy.rejectionReason(
                                    preApproved, command.amount(), command.customerId())
                            .orElse(RejectionReason.INSUFFICIENT_QUOTA);
                    BigDecimal remaining = preApproved
                            .map(PreApproved::getAvailableAmount)
                            .orElse(null);
                    return saveUsage(command, UsageStatus.REJECTED, reason, remaining)
                            .map(UsageProcessingResult::created);
                });
    }

    private Mono<PreApprovedUsage> saveUsage(
            UsageCommand command,
            UsageStatus status,
            RejectionReason reason,
            BigDecimal remainingAmount) {
        PreApprovedUsageEntity entity = PreApprovedUsageEntity.builder()
                .requestReference(command.requestReference())
                .preApprovedId(command.preApprovedId())
                .customerId(command.customerId())
                .amount(command.amount())
                .status(status.name())
                .reason(reason == null ? null : reason.name())
                .payloadHash(command.payloadHash())
                .processedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .remainingAmount(remainingAmount)
                .build();

        return usageRepository.save(entity).map(this::toDomain);
    }

    private UsageProcessingResult resultForExisting(PreApprovedUsage existing, UsageCommand command) {
        if (existing.getPayloadHash().equals(command.payloadHash())) {
            return UsageProcessingResult.replayed(existing);
        }
        return UsageProcessingResult.conflict(existing);
    }

    private PreApproved toDomain(PreApprovedEntity entity) {
        return PreApproved.builder()
                .preApprovedId(entity.getPreApprovedId())
                .customerId(entity.getCustomerId())
                .status(PreApprovedStatus.valueOf(entity.getStatus()))
                .availableAmount(entity.getAvailableAmount())
                .build();
    }

    private PreApprovedUsage toDomain(PreApprovedUsageEntity entity) {
        return PreApprovedUsage.builder()
                .id(entity.getId())
                .requestReference(entity.getRequestReference())
                .preApprovedId(entity.getPreApprovedId())
                .customerId(entity.getCustomerId())
                .amount(entity.getAmount())
                .status(UsageStatus.valueOf(entity.getStatus()))
                .reason(entity.getReason() == null ? null : RejectionReason.valueOf(entity.getReason()))
                .payloadHash(entity.getPayloadHash())
                .processedAt(entity.getProcessedAt() == null ? null : entity.getProcessedAt().toInstant())
                .remainingAmount(entity.getRemainingAmount())
                .build();
    }

    private PreApprovedUsage mapUsage(Row row) {
        String reason = row.get("reason", String.class);
        OffsetDateTime processedAt = row.get("processed_at", OffsetDateTime.class);
        return PreApprovedUsage.builder()
                .id(row.get("id", Long.class))
                .requestReference(row.get("request_reference", String.class))
                .preApprovedId(row.get("pre_approved_id", String.class))
                .customerId(row.get("customer_id", String.class))
                .amount(row.get("amount", BigDecimal.class))
                .status(UsageStatus.valueOf(row.get("status", String.class)))
                .reason(reason == null ? null : RejectionReason.valueOf(reason))
                .payloadHash(row.get("payload_hash", String.class))
                .processedAt(processedAt == null ? null : processedAt.toInstant())
                .remainingAmount(row.get("remaining_amount", BigDecimal.class))
                .build();
    }

    static boolean isUniqueViolation(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof R2dbcException r2dbcException
                    && PG_UNIQUE_VIOLATION.equals(r2dbcException.getSqlState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
