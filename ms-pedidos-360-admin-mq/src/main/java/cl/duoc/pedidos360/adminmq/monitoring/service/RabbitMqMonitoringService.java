package cl.duoc.pedidos360.adminmq.monitoring.service;

import cl.duoc.pedidos360.adminmq.exception.ResourceNotFoundException;
import cl.duoc.pedidos360.adminmq.monitoring.dto.DlqMessageResponse;
import cl.duoc.pedidos360.adminmq.monitoring.dto.RabbitMqOverviewResponse;
import cl.duoc.pedidos360.adminmq.monitoring.dto.RabbitQueueMetricsResponse;
import cl.duoc.pedidos360.adminmq.monitoring.dto.RequeueRequest;
import cl.duoc.pedidos360.adminmq.monitoring.dto.RequeueResponse;
import cl.duoc.pedidos360.adminmq.monitoring.exception.RabbitMqManagementException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RabbitMqMonitoringService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> XDEATH_TYPE = new TypeReference<>() {
    };

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String vhost;
    private final List<String> queueNames;
    private final List<String> dlqNames;
    private final int dlqLimit;
    private final int requeueSearchLimit;

    public RabbitMqMonitoringService(
            RestClient rabbitMqManagementRestClient,
            ObjectMapper objectMapper,
            @Value("${app.rabbitmq.management.vhost:/}") String vhost,
            @Value("${app.rabbitmq.monitoring.queues:q.cmd.email,q.cmd.kitchen,q.cmd.invoice}") String queues,
            @Value("${app.rabbitmq.monitoring.dlq-queues:q.cmd.email.dlq,q.cmd.kitchen.dlq,q.cmd.invoice.dlq}") String dlqQueues,
            @Value("${app.rabbitmq.monitoring.dlq-limit:20}") int dlqLimit,
            @Value("${app.rabbitmq.monitoring.requeue-search-limit:50}") int requeueSearchLimit) {
        this.restClient = rabbitMqManagementRestClient;
        this.objectMapper = objectMapper;
        this.vhost = vhost;
        this.queueNames = parseList(queues);
        this.dlqNames = parseList(dlqQueues);
        this.dlqLimit = dlqLimit;
        this.requeueSearchLimit = requeueSearchLimit;
    }

    public RabbitMqOverviewResponse obtenerOverview() {
        try {
            JsonNode overview = getJson("/overview");
            JsonNode node = firstNode();
            List<String> alarms = new ArrayList<>(obtenerAlarmas());

            if (node != null) {
                if (node.path("mem_alarm").asBoolean(false)) {
                    alarms.add("Alarma de memoria activa");
                }
                if (node.path("disk_free_alarm").asBoolean(false)) {
                    alarms.add("Alarma de disco activa");
                }
            }

            JsonNode totals = overview.path("queue_totals");
            JsonNode stats = overview.path("message_stats");
            JsonNode objects = overview.path("object_totals");

            return new RabbitMqOverviewResponse(
                    alarms.isEmpty() ? "UP" : "DOWN",
                    overview.path("rabbitmq_version").asText("—"),
                    node != null ? node.path("name").asText("—") : "—",
                    objects.path("queues").asLong(0),
                    objects.path("consumers").asLong(0),
                    totals.path("messages_ready").asLong(0),
                    totals.path("messages_unacknowledged").asLong(0),
                    rate(stats, "publish"),
                    rate(stats, "ack"),
                    rate(stats, "deliver_get"),
                    longAt(node, "mem_used"),
                    longAt(node, "mem_limit"),
                    longAt(node, "disk_free"),
                    longAt(node, "disk_free_limit"),
                    contarEnDlq(),
                    List.copyOf(alarms),
                    Instant.now());
        } catch (RabbitMqManagementException exception) {
            return RabbitMqOverviewResponse.down(exception.getMessage());
        }
    }

    public List<RabbitQueueMetricsResponse> obtenerColas() {
        JsonNode queues = getJson("/queues");
        Map<String, JsonNode> byName = new LinkedHashMap<>();

        if (queues != null && queues.isArray()) {
            for (JsonNode queue : queues) {
                byName.put(queue.path("name").asText(), queue);
            }
        }

        return queueNames.stream()
                .map(name -> mapQueue(name, byName.get(name)))
                .toList();
    }

    public List<DlqMessageResponse> obtenerMensajesDlq(String queue, Integer limit) {
        List<String> targets = (queue == null || queue.isBlank())
                ? dlqNames
                : List.of(validarDlq(queue));

        int count = limit == null ? dlqLimit : Math.max(1, Math.min(limit, 100));
        List<DlqMessageResponse> messages = new ArrayList<>();

        for (String target : targets) {
            JsonNode found = obtenerMensajes(target, count, "ack_requeue_true");
            for (JsonNode message : found) {
                messages.add(mapDlq(target, message));
            }
        }

        return messages;
    }

    /**
     * Recupera un mensaje de la DLQ y lo vuelve a publicar en su exchange/routing key original.
     * Si la referencia no coincide, el mensaje se devuelve a la DLQ y se continúa buscando.
     */
    public RequeueResponse reencolar(RequeueRequest request) {
        String dlq = validarDlq(request.queue());
        String expectedReference = request.reference().trim();

        for (int intento = 0; intento < requeueSearchLimit; intento++) {
            JsonNode messages = obtenerMensajes(dlq, 1, "ack_requeue_false");
            if (messages.isEmpty()) {
                break;
            }

            JsonNode message = messages.get(0);
            String currentReference = referencia(dlq, message);

            if (!currentReference.equals(expectedReference)) {
                devolverADlq(dlq, message);
                continue;
            }

            Destino destino = destinoOriginal(message);
            try {
                publicar(destino.exchange(), destino.routingKey(), message, true);
            } catch (RuntimeException exception) {
                devolverADlq(dlq, message);
                throw new RabbitMqManagementException(
                        "No se pudo reencolar el mensaje; se devolvió a la DLQ " + dlq, exception);
            }

            return new RequeueResponse(
                    true,
                    dlq,
                    expectedReference,
                    destino.exchange(),
                    destino.routingKey(),
                    preview(message.path("payload").asText("")));
        }

        throw new ResourceNotFoundException(
                "No se encontró el mensaje solicitado en la DLQ " + dlq + " (puede haber sido reintentado)");
    }

    private RabbitQueueMetricsResponse mapQueue(String name, JsonNode queue) {
        if (queue == null) {
            return RabbitQueueMetricsResponse.empty(name);
        }

        JsonNode stats = queue.path("message_stats");
        return new RabbitQueueMetricsResponse(
                name,
                queue.path("messages_ready").asLong(0),
                queue.path("messages_unacknowledged").asLong(0),
                queue.path("consumers").asInt(0),
                rate(stats, "incoming"),
                rate(stats, "deliver_get"));
    }

    private DlqMessageResponse mapDlq(String queue, JsonNode message) {
        JsonNode properties = message.path("properties");
        JsonNode headersNode = properties.path("headers");
        JsonNode xDeath = headersNode.path("x-death");
        JsonNode first = xDeath.isArray() && !xDeath.isEmpty() ? xDeath.get(0) : null;

        Map<String, Object> headers = headersNode.isObject()
                ? objectMapper.convertValue(headersNode, MAP_TYPE)
                : Map.of();
        List<Map<String, Object>> xDeathList = xDeath.isArray()
                ? objectMapper.convertValue(xDeath, XDEATH_TYPE)
                : List.of();

        return new DlqMessageResponse(
                queue,
                referencia(queue, message),
                properties.path("message_id").asText(""),
                first != null ? first.path("exchange").asText("") : message.path("exchange").asText(""),
                routingKeyOriginal(first, message),
                first != null ? first.path("reason").asText("") : "",
                first != null ? first.path("count").asLong(0) : 0,
                first != null ? first.path("time").asText("") : "",
                message.path("payload").asText(""),
                message.path("payload_encoding").asText("string"),
                message.path("redelivered").asBoolean(false),
                headers,
                xDeathList);
    }

    private String routingKeyOriginal(JsonNode first, JsonNode message) {
        if (first != null && first.path("routing-keys").isArray() && !first.path("routing-keys").isEmpty()) {
            return first.path("routing-keys").get(0).asText("");
        }
        return message.path("routing_key").asText("");
    }

    private Destino destinoOriginal(JsonNode message) {
        JsonNode xDeath = message.path("properties").path("headers").path("x-death");
        if (!xDeath.isArray() || xDeath.isEmpty()) {
            throw new IllegalArgumentException(
                    "El mensaje no tiene cabecera x-death; no se puede determinar su destino original");
        }

        JsonNode first = xDeath.get(0);
        String exchange = first.path("exchange").asText("");
        String routingKey = routingKeyOriginal(first, message);

        if (exchange.isBlank()) {
            exchange = "amq.default";
            routingKey = first.path("queue").asText(routingKey);
        }

        return new Destino(exchange, routingKey);
    }

    private void devolverADlq(String dlq, JsonNode message) {
        String exchange = message.path("exchange").asText("");
        String routingKey = message.path("routing_key").asText("");

        if (exchange.isBlank()) {
            exchange = "amq.default";
            routingKey = dlq;
        }

        publicar(exchange, routingKey, message, false);
    }

    private void publicar(String exchange, String routingKey, JsonNode message, boolean limpiarTrazabilidad) {
        JsonNode propertiesNode = message.path("properties");
        Map<String, Object> properties = propertiesNode.isObject()
                ? objectMapper.convertValue(propertiesNode, MAP_TYPE)
                : new LinkedHashMap<>();

        Map<String, Object> headers = new LinkedHashMap<>();
        JsonNode headersNode = propertiesNode.path("headers");
        if (headersNode.isObject()) {
            headers.putAll(objectMapper.convertValue(headersNode, MAP_TYPE));
        }

        if (limpiarTrazabilidad) {
            headers.remove("x-death");
            headers.put("x-retry-count", 0);
            headers.put("x-requeued-at", Instant.now().toString());
        }

        if (headersNode.isObject() || limpiarTrazabilidad) {
            properties.put("headers", headers);
        }
        properties.remove("expiration");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("properties", properties);
        body.put("routing_key", routingKey);
        body.put("payload", message.path("payload").asText(""));
        body.put("payload_encoding", message.path("payload_encoding").asText("string"));

        try {
            JsonNode response = restClient.post()
                    .uri("/exchanges/{vhost}/{exchange}/publish", vhost, exchange)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response != null && !response.path("routed").asBoolean(true)) {
                throw new RabbitMqManagementException(
                        "RabbitMQ no pudo rutear el mensaje hacia " + exchange + " (" + routingKey + ")");
            }
        } catch (RestClientResponseException exception) {
            throw new RabbitMqManagementException(
                    "RabbitMQ rechazó la publicación hacia " + exchange + ": " + exception.getResponseBodyAsString(),
                    exception);
        } catch (RestClientException exception) {
            throw new RabbitMqManagementException(
                    "No fue posible publicar el mensaje en RabbitMQ", exception);
        }
    }

    private JsonNode obtenerMensajes(String queue, int count, String ackmode) {
        try {
            JsonNode response = restClient.post()
                    .uri("/queues/{vhost}/{queue}/get", vhost, queue)
                    .body(Map.of("count", count, "ackmode", ackmode, "encoding", "auto"))
                    .retrieve()
                    .body(JsonNode.class);

            return response != null && response.isArray()
                    ? response
                    : objectMapper.createArrayNode();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return objectMapper.createArrayNode();
            }
            throw new RabbitMqManagementException(
                    "No fue posible leer mensajes de la cola " + queue, exception);
        } catch (RestClientException exception) {
            throw new RabbitMqManagementException(
                    "No fue posible leer mensajes de la cola " + queue, exception);
        }
    }

    private long contarEnDlq() {
        try {
            JsonNode queues = getJson("/queues");
            long total = 0;
            if (queues != null && queues.isArray()) {
                for (JsonNode queue : queues) {
                    if (dlqNames.contains(queue.path("name").asText())) {
                        total += queue.path("messages").asLong(0);
                    }
                }
            }
            return total;
        } catch (RabbitMqManagementException exception) {
            return 0;
        }
    }

    private List<String> obtenerAlarmas() {
        try {
            JsonNode health = restClient.get()
                    .uri("/health/checks/alarms")
                    .retrieve()
                    .body(JsonNode.class);

            if (health == null || "ok".equalsIgnoreCase(health.path("status").asText(""))) {
                return List.of();
            }

            return List.of(health.path("reason").asText("Alarma activa en RabbitMQ"));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return List.of();
            }
            return List.of("Health check de alarmas: HTTP " + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            return List.of("No se pudo consultar el health check de alarmas");
        }
    }

    private JsonNode firstNode() {
        try {
            JsonNode nodes = getJson("/nodes");
            return nodes != null && nodes.isArray() && !nodes.isEmpty() ? nodes.get(0) : null;
        } catch (RabbitMqManagementException exception) {
            return null;
        }
    }

    private JsonNode getJson(String path) {
        try {
            JsonNode response = restClient.get().uri(path).retrieve().body(JsonNode.class);
            return response != null ? response : objectMapper.createObjectNode();
        } catch (RestClientResponseException exception) {
            throw new RabbitMqManagementException(
                    "RabbitMQ Management respondió HTTP " + exception.getStatusCode().value()
                            + " al consultar " + path,
                    exception);
        } catch (RestClientException exception) {
            throw new RabbitMqManagementException(
                    "No fue posible consultar RabbitMQ Management en " + path, exception);
        }
    }

    private String validarDlq(String queue) {
        String nombre = queue == null ? "" : queue.trim();
        if (!dlqNames.contains(nombre)) {
            throw new IllegalArgumentException("La cola indicada no es una DLQ monitoreada: " + nombre);
        }
        return nombre;
    }

    private String referencia(String queue, JsonNode message) {
        String messageId = message.path("properties").path("message_id").asText("");
        if (!messageId.isBlank()) {
            return messageId;
        }

        JsonNode xDeath = message.path("properties").path("headers").path("x-death");
        String failedAt = xDeath.isArray() && !xDeath.isEmpty()
                ? xDeath.get(0).path("time").asText("")
                : "";

        String payload = message.path("payload").asText("");
        String fingerprint = queue + "|" + payload + "|" + failedAt;
        return "ref-" + sha256(fingerprint).substring(0, 16);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(Objects.hash(value));
        }
    }

    private double rate(JsonNode stats, String key) {
        if (stats == null) {
            return 0;
        }

        JsonNode details = stats.path(key + "_details");
        return details.isObject() ? details.path("rate").asDouble(0) : 0;
    }

    private long longAt(JsonNode node, String field) {
        return node != null ? node.path(field).asLong(0) : 0;
    }

    private String preview(String payload) {
        if (payload == null) {
            return "";
        }
        return payload.length() <= 300 ? payload : payload.substring(0, 300) + "…";
    }

    private List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return List.copyOf(items);
    }

    private record Destino(String exchange, String routingKey) {
    }
}
