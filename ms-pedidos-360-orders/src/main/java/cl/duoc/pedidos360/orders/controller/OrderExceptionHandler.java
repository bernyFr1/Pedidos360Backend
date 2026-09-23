package cl.duoc.pedidos360.orders.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class OrderExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, String>> authenticationError(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Token inválido o ausente"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> accessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "El usuario no tiene permisos para este recurso"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> databaseError(DataAccessException exception) {
        log.error("Error de base de datos al procesar /api/orders", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "No fue posible consultar los pedidos"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpectedError(Exception exception) {
        log.error("Error inesperado al procesar /api/orders", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error interno del servicio de pedidos"));
    }
}