package co.com.bancoias.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public class MaxIntegerDigitsValidator
        implements ConstraintValidator<MaxIntegerDigits, BigDecimal> {

    private int maxIntegerDigits;

    @Override
    public void initialize(MaxIntegerDigits annotation) {
        this.maxIntegerDigits = annotation.value();
    }

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        return value == null || integerDigits(value) <= maxIntegerDigits;
    }

    private int integerDigits(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.precision() - normalized.scale();
    }
}
