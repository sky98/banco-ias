package co.com.bancoias.api.mapper;

import co.com.bancoias.api.dto.PreApprovedResponse;
import co.com.bancoias.api.dto.PreApprovedUsageResponse;
import co.com.bancoias.model.preapproved.PreApproved;
import co.com.bancoias.model.preapprovedusage.PreApprovedUsage;

public final class PreApprovedUsageMapper {

    private PreApprovedUsageMapper() {
    }

    public static PreApprovedUsageResponse toResponse(PreApprovedUsage usage, boolean replayed) {
        return new PreApprovedUsageResponse(
                usage.getRequestReference(),
                usage.getPreApprovedId(),
                usage.getCustomerId(),
                usage.getAmount(),
                usage.getStatus().name(),
                usage.getReason() == null ? null : usage.getReason().name(),
                usage.getProcessedAt(),
                usage.getRemainingAmount(),
                replayed);
    }

    public static PreApprovedResponse toResponse(PreApproved preApproved) {
        return new PreApprovedResponse(
                preApproved.getPreApprovedId(),
                preApproved.getCustomerId(),
                preApproved.getStatus().name(),
                preApproved.getAvailableAmount());
    }
}
