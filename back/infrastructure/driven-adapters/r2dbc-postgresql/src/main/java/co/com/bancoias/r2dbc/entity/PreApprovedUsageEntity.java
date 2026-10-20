package co.com.bancoias.r2dbc.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("preapproved_usage")
public class PreApprovedUsageEntity {
    @Id
    private Long id;

    @Column("request_reference")
    private String requestReference;

    @Column("pre_approved_id")
    private String preApprovedId;

    @Column("customer_id")
    private String customerId;

    private BigDecimal amount;

    private String status;

    private String reason;

    @Column("payload_hash")
    private String payloadHash;

    @Column("processed_at")
    private OffsetDateTime processedAt;

    @Column("remaining_amount")
    private BigDecimal remainingAmount;
}
