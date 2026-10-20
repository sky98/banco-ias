package co.com.bancoias.model.preapprovedusage;

public record UsageProcessingResult(
        PreApprovedUsage usage,
        boolean replayed,
        boolean conflict) {

    public static UsageProcessingResult created(PreApprovedUsage usage) {
        return new UsageProcessingResult(usage, false, false);
    }

    public static UsageProcessingResult replayed(PreApprovedUsage usage) {
        return new UsageProcessingResult(usage, true, false);
    }

    public static UsageProcessingResult conflict(PreApprovedUsage usage) {
        return new UsageProcessingResult(usage, false, true);
    }
}
