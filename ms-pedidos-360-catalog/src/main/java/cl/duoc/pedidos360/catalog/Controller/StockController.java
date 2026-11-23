package cl.duoc.pedidos360.catalog.Controller;

import cl.duoc.pedidos360.catalog.Service.StockService;
import cl.duoc.pedidos360.catalog.dto.StockReservationRequest;
import cl.duoc.pedidos360.catalog.dto.StockReservationResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping("/reserve")
    public StockReservationResponse reservar(@Valid @RequestBody StockReservationRequest request) {
        return stockService.reservar(request);
    }

    @PostMapping("/release")
    public StockReservationResponse liberar(@Valid @RequestBody StockReservationRequest request) {
        return stockService.liberar(request);
    }
}
