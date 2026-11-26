package cl.duoc.pedidos360.audit.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class KafkaEventValidator {

    private final Validator validator;

    public KafkaEventValidator(Validator validator) {
        this.validator = validator;
    }

    public <T> void validar(T evento) {
        Set<ConstraintViolation<T>> violations = validator.validate(evento);
        if (!violations.isEmpty()) {
            String detalle = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("Evento inválido: " + detalle);
        }
    }
}
