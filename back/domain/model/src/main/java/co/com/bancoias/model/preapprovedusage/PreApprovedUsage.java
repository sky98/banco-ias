package co.com.bancoias.model.preapprovedusage;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class PreApprovedUsage {
    private final Long id;
    private final String requestReference;
    private final String preApprovedId;
    private final String customerId;
    private final BigDecimal amount;
    private final UsageStatus status;
    private final RejectionReason reason;
    private final String payloadHash;
    private final Instant processedAt;
    private final BigDecimal remainingAmount;
}
