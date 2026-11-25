package cl.duoc.pedidos360.catalog.Service;

import cl.duoc.pedidos360.catalog.Entity.Product;
import cl.duoc.pedidos360.catalog.Repository.ProductRepository;
import cl.duoc.pedidos360.catalog.dto.StockChangeItem;
import cl.duoc.pedidos360.catalog.dto.StockItemRequest;
import cl.duoc.pedidos360.catalog.dto.StockReservationRequest;
import cl.duoc.pedidos360.catalog.dto.StockReservationResponse;
import cl.duoc.pedidos360.catalog.event.StockChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reserva y liberación atómica de stock.
 *
 * Los productos se bloquean con PESSIMISTIC_WRITE en orden ascendente de id
 * para evitar condiciones de carrera y deadlocks entre pedidos concurrentes.
 */
@Service
public class StockService {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    public StockService(ProductRepository productRepository, ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public StockReservationResponse reservar(StockReservationRequest request) {
        List<StockChangeItem> cambios = new ArrayList<>();

        for (Map.Entry<Long, Integer> item : normalizar(request.items()).entrySet()) {
            Product producto = productRepository.findByIdForUpdate(item.getKey())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Producto no encontrado: " + item.getKey()));

            int anterior = producto.getStock() == null ? 0 : producto.getStock();
            int cantidad = item.getValue();

            if (anterior < cantidad) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Stock insuficiente para " + producto.getName()
                                + ". Disponible: " + anterior + ", solicitado: " + cantidad);
            }

            int nuevo = anterior - cantidad;
            producto.setStock(nuevo);
            productRepository.save(producto);
            cambios.add(new StockChangeItem(
                    producto.getId(), cantidad, anterior, nuevo, -cantidad, producto.getPrice()));
        }

        publicarEvento(request.orderId(), "RESERVA", cambios);
        return new StockReservationResponse(request.orderId(), "RESERVA", cambios);
    }

    @Transactional
    public StockReservationResponse liberar(StockReservationRequest request) {
        List<StockChangeItem> cambios = new ArrayList<>();

        for (Map.Entry<Long, Integer> item : normalizar(request.items()).entrySet()) {
            Product producto = productRepository.findByIdForUpdate(item.getKey())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Producto no encontrado: " + item.getKey()));

            int anterior = producto.getStock() == null ? 0 : producto.getStock();
            int cantidad = item.getValue();
            int nuevo = anterior + cantidad;

            producto.setStock(nuevo);
            productRepository.save(producto);
            cambios.add(new StockChangeItem(
                    producto.getId(), cantidad, anterior, nuevo, cantidad, producto.getPrice()));
        }

        publicarEvento(request.orderId(), "LIBERACION", cambios);
        return new StockReservationResponse(request.orderId(), "LIBERACION", cambios);
    }

    private Map<Long, Integer> normalizar(List<StockItemRequest> items) {
        TreeMap<Long, Integer> acumulado = new TreeMap<>();

        for (StockItemRequest item : items) {
            acumulado.merge(item.productId(), item.quantity(), Integer::sum);
        }

        return acumulado;
    }

    private void publicarEvento(Long orderId, String operation, List<StockChangeItem> cambios) {
        eventPublisher.publishEvent(new StockChangedEvent(
                orderId,
                operation,
                List.copyOf(cambios),
                Instant.now()));
    }
}
