#!/bin/sh
set -eu

printf '%s\n' "${RABBITMQ_ERLANG_COOKIE}" > "${HOME}/.erlang.cookie"
chmod 400 "${HOME}/.erlang.cookie"

if rabbitmqctl -n rabbit@rabbitmq-2 cluster_status | grep -q "rabbit@rabbitmq-1"; then
  echo "[init] rabbitmq-2 ya pertenece al clúster."
else
  echo "[init] Uniendo rabbitmq-2 al clúster..."
  rabbitmqctl -n rabbit@rabbitmq-2 stop_app
  rabbitmqctl -n rabbit@rabbitmq-2 reset
  rabbitmqctl -n rabbit@rabbitmq-2 join_cluster rabbit@rabbitmq-1
  rabbitmqctl -n rabbit@rabbitmq-2 start_app
fi

echo "[init] Estado final del clúster:"
rabbitmqctl -n rabbit@rabbitmq-1 cluster_status
