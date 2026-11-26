package cl.duoc.pedidos360.adminmq.kafka.exception;

public class KafkaAdminException extends RuntimeException {

    public KafkaAdminException(String message) {
        super(message);
    }

    public KafkaAdminException(String message, Throwable cause) {
        super(message, cause);
    }
}
