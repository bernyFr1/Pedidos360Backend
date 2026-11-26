package cl.duoc.pedidos360.adminmq.kafka.service;

import cl.duoc.pedidos360.adminmq.exception.ResourceNotFoundException;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaClusterResponse;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaConsumerGroupDetailResponse;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaConsumerGroupOffsetResponse;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaConsumerGroupResponse;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaNodeResponse;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaPartitionResponse;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaTopicConfigRequest;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaTopicRequest;
import cl.duoc.pedidos360.adminmq.kafka.dto.KafkaTopicResponse;
import cl.duoc.pedidos360.adminmq.kafka.exception.KafkaAdminException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.AlterConfigsOptions;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.CreateTopicsOptions;
import org.apache.kafka.clients.admin.DeleteTopicsOptions;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsOptions;
import org.apache.kafka.clients.admin.DescribeTopicsOptions;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions;
import org.apache.kafka.clients.admin.ListConsumerGroupsOptions;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class KafkaAdminService {

    private static final long TIMEOUT_SECONDS = 10;

    private final AdminClient adminClient;

    public KafkaAdminService(AdminClient adminClient) {
        this.adminClient = adminClient;
    }

    public KafkaClusterResponse obtenerCluster() {
        try {
            DescribeClusterResult result = adminClient.describeCluster(
                    new DescribeClusterOptions().timeoutMs(5000));
            String clusterId = result.clusterId().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Node controller = result.controller().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Collection<Node> nodes = result.nodes().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return new KafkaClusterResponse(
                    clusterId,
                    controller == null ? -1 : controller.id(),
                    nodes.stream()
                            .sorted(Comparator.comparingInt(Node::id))
                            .map(node -> new KafkaNodeResponse(node.id(), node.host(), node.port(), node.rack()))
                            .toList());
        } catch (Exception exception) {
            throw error("consultar el clúster", exception);
        }
    }

    public List<KafkaTopicResponse> listarTopicos() {
        try {
            Set<String> names = adminClient.listTopics(
                            new ListTopicsOptions().listInternal(false).timeoutMs(5000))
                    .names()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (names.isEmpty()) {
                return List.of();
            }

            Map<String, TopicDescription> descriptions = adminClient.describeTopics(names)
                    .allTopicNames()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Map<ConfigResource, Config> configs = adminClient.describeConfigs(
                            names.stream()
                                    .map(name -> new ConfigResource(ConfigResource.Type.TOPIC, name))
                                    .toList())
                    .all()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return names.stream()
                    .sorted()
                    .map(name -> mapTopic(
                            descriptions.get(name),
                            configs.get(new ConfigResource(ConfigResource.Type.TOPIC, name))))
                    .toList();
        } catch (Exception exception) {
            throw error("listar los tópicos", exception);
        }
    }

    public KafkaTopicResponse obtenerTopico(String name) {
        try {
            Map<String, TopicDescription> descriptions = adminClient.describeTopics(List.of(name))
                    .allTopicNames()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            TopicDescription description = descriptions.get(name);
            if (description == null) {
                throw new ResourceNotFoundException("Tópico no encontrado: " + name);
            }

            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, name);
            Config config = adminClient.describeConfigs(List.of(resource))
                    .all()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .get(resource);

            return mapTopic(description, config);
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (Exception exception) {
            throw error("describir el tópico " + name, exception);
        }
    }

    public void crearTopico(KafkaTopicRequest request) {
        try {
            NewTopic topic = new NewTopic(
                    request.name(),
                    request.partitions(),
                    (short) request.replicationFactor());

            if (request.configs() != null && !request.configs().isEmpty()) {
                topic.configs(request.configs());
            }

            adminClient.createTopics(
                            List.of(topic),
                            new CreateTopicsOptions().timeoutMs(5000))
                    .all()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw error("crear el tópico " + request.name(), exception);
        }
    }

    public void actualizarConfiguracion(String name, KafkaTopicConfigRequest request) {
        try {
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, name);
            List<AlterConfigOp> operations = request.configs().entrySet().stream()
                    .map(entry -> new AlterConfigOp(
                            new ConfigEntry(entry.getKey(), entry.getValue()),
                            AlterConfigOp.OpType.SET))
                    .toList();

            Map<ConfigResource, Collection<AlterConfigOp>> changes = Map.of(resource, operations);
            adminClient.incrementalAlterConfigs(
                            changes,
                            new AlterConfigsOptions().timeoutMs(5000))
                    .all()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw error("actualizar la configuración del tópico " + name, exception);
        }
    }

    public void eliminarTopico(String name) {
        if (name.startsWith("__")) {
            throw new IllegalArgumentException("No se permite eliminar un tópico interno: " + name);
        }

        try {
            adminClient.deleteTopics(
                            List.of(name),
                            new DeleteTopicsOptions().timeoutMs(5000))
                    .all()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw error("eliminar el tópico " + name, exception);
        }
    }

    public List<KafkaConsumerGroupResponse> listarGrupos() {
        try {
            Collection<ConsumerGroupListing> groups = adminClient.listConsumerGroups(
                            new ListConsumerGroupsOptions().timeoutMs(5000))
                    .all()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return groups.stream()
                    .sorted(Comparator.comparing(ConsumerGroupListing::groupId))
                    .map(group -> new KafkaConsumerGroupResponse(
                            group.groupId(),
                            group.state().map(Enum::name).orElse("UNKNOWN"),
                            group.isSimpleConsumerGroup()))
                    .toList();
        } catch (Exception exception) {
            throw error("listar los consumer groups", exception);
        }
    }

    public KafkaConsumerGroupDetailResponse obtenerGrupo(String groupId) {
        try {
            ConsumerGroupDescription description = adminClient.describeConsumerGroups(
                            List.of(groupId),
                            new DescribeConsumerGroupsOptions().timeoutMs(5000))
                    .all()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .get(groupId);

            if (description == null) {
                throw new ResourceNotFoundException("Consumer group no encontrado: " + groupId);
            }

            Map<TopicPartition, OffsetAndMetadata> committed = adminClient.listConsumerGroupOffsets(
                            groupId,
                            new ListConsumerGroupOffsetsOptions().timeoutMs(5000))
                    .partitionsToOffsetAndMetadata()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Map<TopicPartition, OffsetSpec> latestSpecs = committed.keySet().stream()
                    .collect(Collectors.toMap(topicPartition -> topicPartition, topicPartition -> OffsetSpec.latest()));

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest = latestSpecs.isEmpty()
                    ? Map.of()
                    : adminClient.listOffsets(latestSpecs).all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            List<KafkaConsumerGroupOffsetResponse> offsets = committed.entrySet().stream()
                    .map(entry -> {
                        long current = entry.getValue().offset();
                        ListOffsetsResult.ListOffsetsResultInfo end = latest.get(entry.getKey());
                        long logEnd = end == null ? current : end.offset();
                        return new KafkaConsumerGroupOffsetResponse(
                                entry.getKey().topic(),
                                entry.getKey().partition(),
                                current,
                                logEnd,
                                Math.max(0, logEnd - current));
                    })
                    .sorted(Comparator.comparing(KafkaConsumerGroupOffsetResponse::topic)
                            .thenComparingInt(KafkaConsumerGroupOffsetResponse::partition))
                    .toList();

            return new KafkaConsumerGroupDetailResponse(
                    groupId,
                    description.state().name(),
                    description.members().size(),
                    offsets);
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (Exception exception) {
            throw error("consultar el consumer group " + groupId, exception);
        }
    }

    private KafkaTopicResponse mapTopic(TopicDescription description, Config config) {
        if (description == null) {
            throw new ResourceNotFoundException("Tópico no encontrado");
        }

        List<KafkaPartitionResponse> partitions = description.partitions().stream()
                .map(partition -> new KafkaPartitionResponse(
                        partition.partition(),
                        partition.leader() == null ? -1 : partition.leader().id(),
                        partition.replicas().stream().map(Node::id).toList(),
                        partition.isr().stream().map(Node::id).toList()))
                .toList();

        Map<String, String> configs = new TreeMap<>();
        if (config != null) {
            for (ConfigEntry entry : config.entries()) {
                if (entry.value() != null) {
                    configs.put(entry.name(), entry.value());
                }
            }
        }

        int replicationFactor = partitions.isEmpty() ? 0 : partitions.get(0).replicas().size();

        return new KafkaTopicResponse(
                description.name(),
                description.isInternal(),
                description.partitions().size(),
                replicationFactor,
                configs,
                partitions);
    }

    private RuntimeException error(String action, Exception exception) {
        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new KafkaAdminException("Operación interrumpida al " + action, exception);
        }

        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof TopicExistsException) {
                return new IllegalArgumentException("El tópico ya existe");
            }
            if (cause instanceof UnknownTopicOrPartitionException
                    || cause instanceof GroupIdNotFoundException) {
                return new ResourceNotFoundException("Recurso Kafka no encontrado");
            }
            if (cause instanceof InvalidReplicationFactorException
                    || cause instanceof InvalidPartitionsException) {
                return new IllegalArgumentException(cause.getMessage());
            }
            if (cause instanceof java.util.concurrent.TimeoutException
                    || cause instanceof org.apache.kafka.common.errors.TimeoutException) {
                return new KafkaAdminException("Kafka no respondió a tiempo al " + action, exception);
            }
            cause = cause.getCause();
        }

        return new KafkaAdminException("No fue posible " + action + " en Kafka", exception);
    }
}
