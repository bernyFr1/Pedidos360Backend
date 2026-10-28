package cl.duoc.pedidos360.notify.controller;

import cl.duoc.pedidos360.notify.dlq.DlqInspector;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/lab/dlq")
public class DlqInspectionController {

    private final DlqInspector dlqInspector;

    public DlqInspectionController(DlqInspector dlqInspector) {
        this.dlqInspector = dlqInspector;
    }

    @PostMapping("/inspeccionar")
    public Map<String, Integer> inspeccionar() {
        return dlqInspector.inspeccionarTodas();
    }
}
