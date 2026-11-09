package cl.duoc.pedidos360.adminmq.monitoring.exception;

public class RabbitMqManagementException extends RuntimeException {

    public RabbitMqManagementException(String message) {
        super(message);
    }

    public RabbitMqManagementException(String message, Throwable cause) {
        super(message, cause);
    }
}
