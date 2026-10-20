package co.com.bancoias.api.validation;

import co.com.bancoias.api.error.RequestValidationException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.Validator;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ValidadorRequest {

    private final Validator validator;

    public <T> Mono<T> validar(T dto) {
        return Mono.fromCallable(() -> validarDto(dto))
                .flatMap(errors -> errors.hasErrors()
                        ? Mono.<T>error(validationException(errors))
                        : Mono.just(dto));
    }

    private BeanPropertyBindingResult validarDto(Object dto) {
        BeanPropertyBindingResult errors =
                new BeanPropertyBindingResult(dto, dto.getClass().getName());
        validator.validate(dto, errors);
        return errors;
    }

    private RequestValidationException validationException(
            BeanPropertyBindingResult errors) {
        Set<String> messages = errors.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new RequestValidationException(messages);
    }
}
