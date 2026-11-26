#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka-1:29092}"

# Los consumer groups no se crean aquí: nacen cuando los consumidores se suscriben.
# Grupos definidos para el proyecto:
#   - report-pedidos-group     -> pedidos-eventos
#   - report-inventario-group  -> inventario-eventos
#   - audit-pedidos-group      -> pedidos-eventos

echo "Esperando a que Kafka esté disponible en ${BOOTSTRAP}..."
until kafka-topics --bootstrap-server "${BOOTSTRAP}" --list >/dev/null 2>&1; do
  sleep 2
done

crear_topico() {
  local nombre="$1"
  shift

  kafka-topics \
    --bootstrap-server "${BOOTSTRAP}" \
    --create \
    --if-not-exists \
    --topic "${nombre}" \
    --partitions 3 \
    --replication-factor 3 \
    "$@"
}

# Historial de pedidos: retención por tiempo, sin compactación.
crear_topico pedidos-eventos \
  --config cleanup.policy=delete \
  --config retention.ms=604800000 \
  --config min.insync.replicas=2

# Estado de inventario: compactado para conservar el último stock por producto,
# con retención por tiempo para los registros antiguos.
crear_topico inventario-eventos \
  --config cleanup.policy=compact,delete \
  --config retention.ms=604800000 \
  --config min.insync.replicas=2 \
  --config min.cleanable.dirty.ratio=0.1

echo "Tópicos configurados:"
for topico in pedidos-eventos inventario-eventos; do
  kafka-topics --bootstrap-server "${BOOTSTRAP}" --describe --topic "${topico}"
done
