package co.com.bancoias.model.preapprovedusage;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.bancoias.model.preapproved.PreApproved;
import co.com.bancoias.model.preapproved.PreApprovedStatus;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PreApprovedUsagePolicyTest {

    private static final String CUSTOMER = "USR-10";

    @Test
    void rejectsNullAmount() {
        assertThat(PreApprovedUsagePolicy.rejectionReason(active("100"), null, CUSTOMER))
                .contains(RejectionReason.INVALID_AMOUNT);
    }

    @Test
    void rejectsZeroAndNegativeAmount() {
        assertThat(PreApprovedUsagePolicy.rejectionReason(active("100"), BigDecimal.ZERO, CUSTOMER))
                .contains(RejectionReason.INVALID_AMOUNT);
        assertThat(PreApprovedUsagePolicy.rejectionReason(active("100"), new BigDecimal("-1"), CUSTOMER))
                .contains(RejectionReason.INVALID_AMOUNT);
    }

    @Test
    void rejectsMissingPreApproved() {
        assertThat(PreApprovedUsagePolicy.rejectionReason(Optional.empty(), BigDecimal.ONE, CUSTOMER))
                .contains(RejectionReason.PREAPPROVED_NOT_FOUND);
    }

    @Test
    void rejectsCustomerMismatch() {
        assertThat(PreApprovedUsagePolicy.rejectionReason(active("100"), BigDecimal.ONE, "OTHER"))
                .contains(RejectionReason.CUSTOMER_MISMATCH);
    }

    @Test
    void rejectsBlockedPreApproved() {
        Optional<PreApproved> blocked = Optional.of(PreApproved.builder()
                .preApprovedId("PRA-1")
                .customerId(CUSTOMER)
                .status(PreApprovedStatus.BLOCKED)
                .availableAmount(new BigDecimal("100"))
                .build());

        assertThat(PreApprovedUsagePolicy.rejectionReason(blocked, BigDecimal.ONE, CUSTOMER))
                .contains(RejectionReason.PREAPPROVED_BLOCKED);
    }

    @Test
    void rejectsInsufficientQuota() {
        assertThat(PreApprovedUsagePolicy.rejectionReason(active("100"), new BigDecimal("100.01"), CUSTOMER))
                .contains(RejectionReason.INSUFFICIENT_QUOTA);
    }

    @Test
    void approvesWhenAmountEqualsAvailable() {
        assertThat(PreApprovedUsagePolicy.rejectionReason(active("100"), new BigDecimal("100"), CUSTOMER))
                .isEmpty();
    }

    private Optional<PreApproved> active(String amount) {
        return Optional.of(PreApproved.builder()
                .preApprovedId("PRA-1")
                .customerId(CUSTOMER)
                .status(PreApprovedStatus.ACTIVE)
                .availableAmount(new BigDecimal(amount))
                .build());
    }
}
