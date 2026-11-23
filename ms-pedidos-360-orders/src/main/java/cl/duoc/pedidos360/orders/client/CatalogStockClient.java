package cl.duoc.pedidos360.orders.client;

import cl.duoc.pedidos360.orders.dto.StockItemRequest;
import cl.duoc.pedidos360.orders.dto.StockReservationRequest;
import cl.duoc.pedidos360.orders.dto.StockReservationResponse;
import cl.duoc.pedidos360.orders.entity.OrderItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Comunicación síncrona con catalog para reservar/liberar stock.
 * Se propaga el JWT del usuario para que catalog aplique su seguridad.
 */
@Component
public class CatalogStockClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CatalogStockClient(
            @Value("${services.catalog.url}") String catalogUrl,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(catalogUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    public StockReservationResponse reservar(Long orderId, List<OrderItem> items, String accessToken) {
        return ejecutar("/api/catalog/stock/reserve", orderId, items, accessToken);
    }

    public StockReservationResponse liberar(Long orderId, List<OrderItem> items, String accessToken) {
        return ejecutar("/api/catalog/stock/release", orderId, items, accessToken);
    }

    private StockReservationResponse ejecutar(
            String path, Long orderId, List<OrderItem> items, String accessToken) {
        StockReservationRequest request = new StockReservationRequest(
                orderId,
                items.stream()
                        .map(item -> new StockItemRequest(item.getProductId(), item.getQuantity()))
                        .toList());

        try {
            StockReservationResponse response = restClient.post()
                    .uri(path)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .body(request)
                    .retrieve()
                    .body(StockReservationResponse.class);

            if (response == null) {
                throw new IllegalStateException("El catálogo no devolvió respuesta al actualizar el stock");
            }

            return response;
        } catch (RestClientResponseException exception) {
            String message = extraerMensaje(exception);

            if (exception.getStatusCode().value() == 409) {
                throw new IllegalStateException(message, exception);
            }
            if (exception.getStatusCode().value() == 404
                    || exception.getStatusCode().value() == 400) {
                throw new IllegalArgumentException(message, exception);
            }

            throw new CatalogUnavailableException(
                    "No se pudo actualizar el stock: " + message, exception);
        } catch (RestClientException exception) {
            throw new CatalogUnavailableException(
                    "No fue posible comunicarse con el catálogo para actualizar el stock", exception);
        }
    }

    private String extraerMensaje(RestClientResponseException exception) {
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsString());
            String message = body.path("error").asText("");
            if (!message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // Se usa el mensaje genérico si el cuerpo no es JSON.
        }

        return "HTTP " + exception.getStatusCode().value() + " del catálogo";
    }
}
