package co.com.bancoias.api.error;

import co.com.bancoias.api.dto.PreApprovedUsageResponse;

public class ReferenceConflictException extends RuntimeException {

    private final transient PreApprovedUsageResponse original;

    public ReferenceConflictException(PreApprovedUsageResponse original) {
        super("La referencia ya fue procesada con un payload diferente");
        this.original = original;
    }

    public PreApprovedUsageResponse getOriginal() {
        return original;
    }
}
