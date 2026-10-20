package co.com.bancoias.model.preapprovedusage;

public record UsageProcessingResult(PreApprovedUsage usage, Outcome outcome) {

    public enum Outcome {
        CREATED,
        REPLAYED,
        CONFLICT
    }

    public static UsageProcessingResult created(PreApprovedUsage usage) {
        return new UsageProcessingResult(usage, Outcome.CREATED);
    }

    public static UsageProcessingResult replayed(PreApprovedUsage usage) {
        return new UsageProcessingResult(usage, Outcome.REPLAYED);
    }

    public static UsageProcessingResult conflict(PreApprovedUsage usage) {
        return new UsageProcessingResult(usage, Outcome.CONFLICT);
    }

    public boolean replayed() {
        return outcome == Outcome.REPLAYED;
    }

    public boolean conflict() {
        return outcome == Outcome.CONFLICT;
    }
}
