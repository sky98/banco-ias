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
        if (amount == null || amount.signum() <= 0) {
            return Optional.of(RejectionReason.INVALID_AMOUNT);
        }
        if (preApproved.isEmpty()) {
            return Optional.of(RejectionReason.PREAPPROVED_NOT_FOUND);
        }

        PreApproved current = preApproved.get();
        if (!current.getCustomerId().equals(customerId)) {
            return Optional.of(RejectionReason.CUSTOMER_MISMATCH);
        }
        if (current.getStatus() != PreApprovedStatus.ACTIVE) {
            return Optional.of(RejectionReason.PREAPPROVED_BLOCKED);
        }
        if (current.getAvailableAmount().compareTo(amount) < 0) {
            return Optional.of(RejectionReason.INSUFFICIENT_QUOTA);
        }
        return Optional.empty();
    }
}
