package cl.duoc.pedidos360.catalog.Service;

import cl.duoc.pedidos360.catalog.Entity.Product;
import cl.duoc.pedidos360.catalog.Repository.ProductRepository;
import cl.duoc.pedidos360.catalog.dto.StockChangeItem;
import cl.duoc.pedidos360.catalog.dto.StockItemRequest;
import cl.duoc.pedidos360.catalog.dto.StockReservationRequest;
import cl.duoc.pedidos360.catalog.dto.StockReservationResponse;
import cl.duoc.pedidos360.catalog.event.StockChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private StockService stockService;

    @Test
    void reservarDescuentaStockYPublicaEvento() {
        Product producto = producto(1L, "Cupcake", 5);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(producto));
        when(productRepository.save(producto)).thenReturn(producto);

        StockReservationResponse respuesta = stockService.reservar(
                new StockReservationRequest(10L, List.of(new StockItemRequest(1L, 2))));

        assertEquals(3, producto.getStock());
        assertEquals("RESERVA", respuesta.operation());
        assertEquals(1, respuesta.items().size());

        StockChangeItem cambio = respuesta.items().get(0);
        assertEquals(5, cambio.previousStock());
        assertEquals(3, cambio.newStock());
        assertEquals(-2, cambio.delta());

        verify(eventPublisher).publishEvent(any(StockChangedEvent.class));
    }

    @Test
    void reservarSinStockDevuelveConflictoYNoGuarda() {
        Product producto = producto(2L, "Cupcake", 1);
        when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(producto));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> stockService.reservar(
                        new StockReservationRequest(11L, List.of(new StockItemRequest(2L, 2)))));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void liberarSumaStockYPublicaEvento() {
        Product producto = producto(3L, "Cupcake", 3);
        when(productRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(producto));
        when(productRepository.save(producto)).thenReturn(producto);

        StockReservationResponse respuesta = stockService.liberar(
                new StockReservationRequest(12L, List.of(new StockItemRequest(3L, 2))));

        assertEquals(5, producto.getStock());
        assertEquals("LIBERACION", respuesta.operation());
        assertEquals(2, respuesta.items().get(0).delta());
        verify(eventPublisher).publishEvent(any(StockChangedEvent.class));
    }

    @Test
    void normalizaProductosDuplicadosEnUnaSolaOperacion() {
        Product producto = producto(4L, "Cupcake", 5);
        when(productRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(producto));
        when(productRepository.save(producto)).thenReturn(producto);

        StockReservationResponse respuesta = stockService.reservar(new StockReservationRequest(
                13L,
                List.of(new StockItemRequest(4L, 1), new StockItemRequest(4L, 2))));

        assertEquals(2, producto.getStock());
        assertEquals(1, respuesta.items().size());
        assertEquals(3, respuesta.items().get(0).quantity());
    }

    private Product producto(Long id, String name, int stock) {
        Product producto = new Product();
        producto.setId(id);
        producto.setName(name);
        producto.setDescription("Producto de prueba");
        producto.setPrice(new BigDecimal("10.00"));
        producto.setStock(stock);
        return producto;
    }
}
