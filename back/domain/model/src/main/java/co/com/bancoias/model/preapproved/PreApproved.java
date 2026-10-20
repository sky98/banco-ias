package co.com.bancoias.model.preapproved;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class PreApproved {
    private final String preApprovedId;
    private final String customerId;
    private final PreApprovedStatus status;
    private final BigDecimal availableAmount;
}
