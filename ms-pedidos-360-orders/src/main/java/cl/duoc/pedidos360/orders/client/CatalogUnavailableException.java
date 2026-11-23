package cl.duoc.pedidos360.orders.client;

/**
 * Se lanza cuando catalog no está disponible o rechaza la operación por un
 * motivo distinto a falta de stock o producto inexistente.
 */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
