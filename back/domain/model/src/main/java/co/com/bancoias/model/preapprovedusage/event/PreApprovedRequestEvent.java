package co.com.bancoias.model.preapprovedusage.event;

import co.com.bancoias.model.preapprovedusage.PreApprovedUsage;
import co.com.bancoias.model.preapprovedusage.UsageProcessingResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class PreApprovedRequestEvent {
    private final String eventId;
    private final EventType eventType;
    private final String requestReference;
    private final String preApprovedId;
    private final String customerId;
    private final BigDecimal amount;
    private final String status;
    private final String reason;
    private final BigDecimal remainingAmount;
    private final Instant occurredAt;

    public static PreApprovedRequestEvent from(UsageProcessingResult result) {
        PreApprovedUsage usage = result.usage();
        EventType eventType = mapEventType(usage.getStatus().name());

        return PreApprovedRequestEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .requestReference(usage.getRequestReference())
                .preApprovedId(usage.getPreApprovedId())
                .customerId(usage.getCustomerId())
                .amount(usage.getAmount())
                .status(usage.getStatus().name())
                .reason(usage.getReason() != null ? usage.getReason().name() : null)
                .remainingAmount(usage.getRemainingAmount())
                .occurredAt(usage.getProcessedAt())
                .build();
    }

    private static EventType mapEventType(String status) {
        Map<String, EventType> mapping = Map.of(
            "APPROVED", EventType.PRE_APPROVED_REQUEST_APPROVED,
            "REJECTED", EventType.PRE_APPROVED_REQUEST_REJECTED
        );
        return mapping.get(status);
    }
}
