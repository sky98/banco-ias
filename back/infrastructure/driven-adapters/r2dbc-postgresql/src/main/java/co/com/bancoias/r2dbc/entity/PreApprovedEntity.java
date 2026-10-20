package co.com.bancoias.r2dbc.entity;

import java.math.BigDecimal;
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
@Table("preapproved")
public class PreApprovedEntity {
    @Id
    @Column("pre_approved_id")
    private String preApprovedId;

    @Column("customer_id")
    private String customerId;

    private String status;

    @Column("available_amount")
    private BigDecimal availableAmount;
}
