package co.com.bancoias.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public class MaxDecimalPlacesValidator
        implements ConstraintValidator<MaxDecimalPlaces, BigDecimal> {

    private int maxDecimalPlaces;

    @Override
    public void initialize(MaxDecimalPlaces annotation) {
        this.maxDecimalPlaces = annotation.value();
    }

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        return value == null || value.stripTrailingZeros().scale() <= maxDecimalPlaces;
    }
}
