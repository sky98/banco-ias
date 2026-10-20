package co.com.bancoias.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = MaxDecimalPlacesValidator.class)
@Target({
    ElementType.FIELD,
    ElementType.METHOD,
    ElementType.PARAMETER,
    ElementType.ANNOTATION_TYPE,
    ElementType.TYPE_USE,
    ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxDecimalPlaces {

    int value();

    String message() default "máximo {value} decimales";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
