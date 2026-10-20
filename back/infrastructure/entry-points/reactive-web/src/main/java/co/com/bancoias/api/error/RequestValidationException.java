package co.com.bancoias.api.error;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class RequestValidationException extends RuntimeException {

    private final transient Set<String> errors;

    public RequestValidationException(String message) {
        this(Set.of(message));
    }

    public RequestValidationException(Set<String> errors) {
        super(String.join("; ", errors));
        this.errors = Collections.unmodifiableSet(new LinkedHashSet<>(errors));
    }

    public Set<String> getErrors() {
        return errors;
    }
}
