package cl.duoc.pedidos360.bff.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestControllerAdvice
public class BffExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BffExceptionHandler.class);

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<Object> downstreamError(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status >= 400 && status < 500) {
            String responseBody = exception.getResponseBodyAsString();
            Object body = responseBody.isBlank()
                    ? Map.of("error", "Solicitud rechazada por el servicio downstream")
                    : responseBody;
            return ResponseEntity.status(status).body(body);
        }
        log.error("Error HTTP {} recibido desde un microservicio downstream", status, exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Error del servicio de pedidos"));
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<Map<String, String>> downstreamUnavailable(RestClientException exception) {
        log.error("No fue posible comunicarse con un microservicio downstream", exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Servicio downstream no disponible"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpectedError(Exception exception) {
        log.error("Error inesperado procesando una solicitud del BFF", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error interno del BFF"));
    }
}
