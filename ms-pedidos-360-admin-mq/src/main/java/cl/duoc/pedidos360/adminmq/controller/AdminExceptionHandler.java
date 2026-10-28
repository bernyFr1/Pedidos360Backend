package cl.duoc.pedidos360.adminmq.controller;

import cl.duoc.pedidos360.adminmq.exception.ResourceNotFoundException;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ShutdownSignalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class AdminExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validationError(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Solicitud inválida");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(AmqpException.class)
    ResponseEntity<Map<String, String>> brokerError(AmqpException exception) {
        log.error("Error de RabbitMQ en /api/admin", exception);
        if (esRecursoInexistente(exception)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Recurso de RabbitMQ no encontrado"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "RabbitMQ no disponible o rechazó la operación"));
    }

    private boolean esRecursoInexistente(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ShutdownSignalException shutdown
                    && shutdown.getReason() instanceof AMQP.Channel.Close close
                    && close.getReplyCode() == 404) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpectedError(Exception exception) {
        log.error("Error inesperado en /api/admin", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error interno del servicio administrador"));
    }
}
