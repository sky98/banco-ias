package co.com.bancoias.model.preapprovedusage;

import co.com.bancoias.model.preapproved.PreApproved;
import co.com.bancoias.model.preapproved.PreApprovedStatus;
import java.math.BigDecimal;
import java.util.Optional;

public final class PreApprovedUsagePolicy {

    private PreApprovedUsagePolicy() {
    }

    public static Optional<RejectionReason> rejectionReason(
            Optional<PreApproved> preApproved, BigDecimal amount, String customerId) {
        return invalidAmount(amount)
                .or(() -> preApprovedMissing(preApproved))
                .or(() -> customerMismatch(preApproved, customerId))
                .or(() -> blocked(preApproved))
                .or(() -> insufficientQuota(preApproved, amount));
    }

    private static Optional<RejectionReason> invalidAmount(BigDecimal amount) {
        return Optional.of(RejectionReason.INVALID_AMOUNT)
                .filter(ignored -> amount == null || amount.signum() <= 0);
    }

    private static Optional<RejectionReason> preApprovedMissing(
            Optional<PreApproved> preApproved) {
        return Optional.of(RejectionReason.PREAPPROVED_NOT_FOUND)
                .filter(ignored -> preApproved.isEmpty());
    }

    private static Optional<RejectionReason> customerMismatch(
            Optional<PreApproved> preApproved, String customerId) {
        return preApproved
                .filter(current -> !current.getCustomerId().equals(customerId))
                .map(current -> RejectionReason.CUSTOMER_MISMATCH);
    }

    private static Optional<RejectionReason> blocked(Optional<PreApproved> preApproved) {
        return preApproved
                .filter(current -> current.getStatus() != PreApprovedStatus.ACTIVE)
                .map(current -> RejectionReason.PREAPPROVED_BLOCKED);
    }

    private static Optional<RejectionReason> insufficientQuota(
            Optional<PreApproved> preApproved, BigDecimal amount) {
        return preApproved
                .filter(current -> current.getAvailableAmount().compareTo(amount) < 0)
                .map(current -> RejectionReason.INSUFFICIENT_QUOTA);
    }
}
