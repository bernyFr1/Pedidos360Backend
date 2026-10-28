# Guía de RabbitMQ y comunicación asíncrona para Pedidos360

> **Documento vivo.** Esta guía se irá actualizando a medida que avancemos.
> **Estado actual (2026-10-27):** Fases 0, 1, 2, 3, 4 y 5 completadas. Fase 6 validada en local con Oracle real y RabbitMQ. Reglas de negocio aplicadas: máquina de estados y matriz estado→dominio en `orders`/`notify`. Topología operativa: `cmd.direct` para creación y `cmd.topic` con `email.#`, `kitchen.#`, `invoice.#` para cambios de estado/variantes. Fase 7 (despliegue EC2) parcialmente preparada con `infra/apps/docker-compose.yml` y `Dockerfile`.
> **Audiencia:** estudiante de informática que está viendo RabbitMQ por primera vez.
> **Objetivo:** entender qué estamos haciendo, por qué lo hacemos y cómo encaja en la evaluación EP3/EP4.
>
> **Importante:** la sección 18 es la fuente de verdad de la implementación final. Si una sección anterior (conceptual o de planificación) entra en conflicto con la sección 18, prevalece la sección 18.

> **Actualización vigente: topología oficial del caso semestral (EFT).**
> La topología implementada es la del caso `caso_pedidos360_enunciado_caso_semestral.md`:
> **6 colas en total** (3 principales + 3 DLQ) y **3 exchanges**: `cmd.direct`, `cmd.topic` y `cmd.dead.dlx`. `cmd.direct` se usa para la creación del pedido con routing keys base; `cmd.topic` se usa para cambios de estado y variantes operativas con patrones `#`.
> **No se crean colas de retry ni cola de monitoreo.** La inspección de DLQ es bajo demanda vía `POST /api/lab/dlq/inspeccionar` y no vacía las colas. Las secciones de esta guía que mencionan `.retry`, `pedidos360.*` o `monitoreo` quedan como material histórico/de estudio y **no deben implementarse**. La tabla y el flujo vigentes están en la sección 9.4; el detalle final de implementación y comportamiento está en la sección 18.

---

## Índice

1. [La idea central en una frase](#1-la-idea-central-en-una-frase)
2. [Comunicación síncrona vs. asíncrona](#2-comunicación-síncrona-vs-asíncrona)
3. [¿Qué es RabbitMQ?](#3-qué-es-rabbitmq)
4. [Conceptos fundamentales](#4-conceptos-fundamentales)
5. [Tipos de exchange](#5-tipos-de-exchange)
6. [Ciclo de vida de un mensaje](#6-ciclo-de-vida-de-un-mensaje)
7. [Confiabilidad: ACK, NACK, DLQ, TTL y reintentos](#7-confiabilidad-ack-nack-dlq-ttl-y-reintentos)
8. [Clúster de RabbitMQ](#8-clúster-de-rabbitmq)
9. [Arquitectura de Pedidos360](#9-arquitectura-de-pedidos360)
10. [Paso a paso de lo que estamos haciendo](#10-paso-a-paso-de-lo-que-estamos-haciendo)
11. [Mapa con la rúbrica EP3](#11-mapa-con-la-rúbrica-ep3)
12. [Guion de defensa EP4](#12-guion-de-defensa-ep4)
13. [Glosario](#13-glosario)
14. [Comandos útiles](#14-comandos-útiles)
15. [Anti-patrones y cómo los evitamos](#15-anti-patrones-y-cómo-los-evitamos)
16. [Preguntas frecuentes](#16-preguntas-frecuentes)
17. [Próximos pasos](#17-próximos-pasos)
18. [Implementación final, comportamiento y cambios](#18-implementación-final-comportamiento-y-cambios)

---

## 1. La idea central en una frase

**RabbitMQ es un intermediario que recibe mensajes de un servicio y los guarda hasta que otro servicio pueda procesarlos, permitiendo que el primero responda rápido sin esperar al segundo.**

En Pedidos360, cuando se crea o acepta un pedido, el cliente no debería esperar a que se envíe un correo, se imprima un ticket de cocina o se genere una boleta. El pedido se guarda en la base de datos y se responde al usuario; las tareas secundarias se envían como mensajes a RabbitMQ y se procesan después, en paralelo y con posibilidad de reintentos.

---

## 2. Comunicación síncrona vs. asíncrona

### 2.1 La analogía del restaurante

Imagina que entras a un restaurante:

- **Síncrono:** pides tu comida y te quedas parado en la caja esperando a que el cocinero termine. No puedes hacer nada más hasta que te entreguen el plato. Si la cocina se llena, tú sigues esperando.
- **Asíncrono:** pides tu comida, te dan un número de pedido y te sientas. La caja quedó libre para atender a otra persona. La cocina prepara tu plato cuando puede. Si algo falla, el pedido se puede reintentar o derivar a un encargado.

RabbitMQ es el **sistema de pedidos con número**: recibe la orden, la guarda y la entrega a quien corresponda. La caja (el servicio que recibe la petición HTTP) no espera a la cocina.

### 2.2 Qué cambia en Pedidos360

**Antes (síncrono):**

```text
Angular → API Gateway → BFF → orders
                                 ├── guarda pedido en Oracle
                                 ├── llama por HTTP a notificaciones  ← espera
                                 ├── llama por HTTP a cocina          ← espera
                                 └── llama por HTTP a documentos      ← espera
                                 └── recién ahí responde al usuario
```

Problemas:

- El usuario espera por tareas que no necesitan estar en la respuesta.
- Si notificaciones está caído, el pedido puede fallar o responder lento.
- Los servicios quedan acoplados: `orders` necesita conocer las URLs y la disponibilidad de todos.
- Un fallo en un servicio externo puede generar un efecto dominó.

**Después (asíncrono con RabbitMQ):**

```text
Angular → API Gateway → BFF → orders
                                 ├── guarda pedido en Oracle
                                 ├── publica los mensajes de notificación en RabbitMQ
                                 └── responde al usuario (201/202)

RabbitMQ → notificaciones  (envía correo)
         → cocina          (imprime ticket)
         → documentos      (genera boleta)
```

Ventajas:

- La respuesta HTTP es rápida y no depende de tareas secundarias.
- Si un consumidor está caído, el mensaje queda en la cola y se procesa cuando vuelva.
- Podemos reintentar sin volver a ejecutar toda la petición del usuario.
- Los servicios quedan desacoplados: `orders` solo conoce el exchange y la routing key, no las URLs de los consumidores.
- Podemos agregar un nuevo consumidor sin tocar a `orders` (por ejemplo, auditoría o métricas).

Costos:

- El sistema es eventualmente consistente: el pedido se guarda antes de que el correo se envíe.
- Hay que monitorear colas, reintentos y mensajes muertos.
- Los consumidores deben ser idempotentes: un mensaje podría llegar más de una vez.
- La depuración es distinta: ya no hay una sola traza HTTP; hay que seguir el mensaje por logs y por la Management UI.

---

## 3. ¿Qué es RabbitMQ?

### 3.1 Un broker de mensajes

RabbitMQ es un **message broker** (intermediario de mensajes). Implementa el protocolo **AMQP 0-9-1** y permite que aplicaciones escritas en distintos lenguajes se comuniquen mediante mensajes.

Sus responsabilidades principales:

1. **Recibir** mensajes de los productores.
2. **Enrutar** esos mensajes hacia una o más colas según reglas.
3. **Almacenar** los mensajes mientras esperan ser consumidos.
4. **Entregar** los mensajes a los consumidores.
5. **Confirmar** que un mensaje fue procesado (ACK) para poder eliminarlo.
6. **Gestionar fallos**: reintentos, mensajes muertos, expiración, etc.

### 3.2 Qué NO es RabbitMQ

- **No es una base de datos.** Un mensaje confirmado con ACK se elimina. No está pensado para consultar historial.
- **No es un bus de eventos permanente.** Para un log inmutable de eventos (por ejemplo, auditoría histórica) se usa Kafka, que veremos más adelante en el proyecto.
- **No reemplaza a la base de datos.** La transacción del pedido sigue en Oracle; RabbitMQ solo transporta tareas.
- **No es magia.** Si el consumidor tiene un bug, el mensaje se reintenta y termina en una DLQ; alguien tiene que revisarlo.

### 3.3 AMQP en una frase

AMQP define **qué** se envía (mensajes), **dónde** se enrutan (exchanges, queues, bindings) y **cómo** se confirman (ACK/NACK). RabbitMQ es una implementación de ese protocolo.

---

## 4. Conceptos fundamentales

### 4.1 Productor (producer)

Es el servicio que **publica** mensajes. En Pedidos360, `ms-pedidos-360-orders` es el productor principal: cuando se crea o cambia el estado de un pedido, publica eventos.

Un productor:

- Abre una conexión con RabbitMQ.
- Publica un mensaje en un **exchange** con una **routing key**.
- No sabe (ni necesita saber) qué colas van a recibir el mensaje.
- Puede pedir confirmaciones de que RabbitMQ recibió el mensaje.

### 4.2 Consumidor (consumer)

Es el servicio que **escucha** una cola y procesa mensajes. En Pedidos360, `ms-pedidos-360-notify` tiene consumidores para notificaciones, cocina y documentos.

Un consumidor:

- Declara que quiere consumir de una cola.
- Recibe mensajes.
- Procesa el mensaje (enviar correo, imprimir ticket, generar boleta).
- Confirma con **ACK** si todo salió bien.
- Rechaza con **NACK** si algo falló.
- Puede tener un límite de mensajes sin confirmar (**prefetch**).

### 4.3 Exchange

Es el **enrutador**. El productor nunca envía directamente a una cola: envía a un exchange. El exchange decide a qué colas mandar el mensaje según su tipo, la routing key y los bindings.

Analogía: el exchange es el **cartero**; la routing key es la **dirección**; las colas son los **buzones**.

### 4.4 Queue (cola)

Es el **buzón** donde se acumulan los mensajes hasta que un consumidor los procese. Características importantes:

- **Durable:** sobrevive a un reinicio de RabbitMQ si está declarada como durable.
- **No exclusiva:** puede ser consumida por varios consumidores.
- **No auto-delete:** no se borra cuando el último consumidor se desconecta.
- **Argumentos:** por ejemplo, `x-dead-letter-exchange`, `x-message-ttl`, `x-max-length`.

### 4.5 Binding y routing key

Un **binding** es la regla que conecta un exchange con una cola. Se define con una **binding key** (o routing key) y opcionalmente argumentos.

```text
Exchange "cmd.direct"
   ├── binding key "email.send"     → cola q.cmd.email
   ├── binding key "kitchen.ticket" → cola q.cmd.kitchen
   └── binding key "invoice.gen"    → cola q.cmd.invoice
```

La **routing key** es la etiqueta que el productor pone al mensaje. El exchange la compara con las binding keys para decidir el destino.

### 4.6 Conexión y canal

- **Conexión:** un socket TCP entre la aplicación y RabbitMQ. Es costosa de abrir.
- **Canal (channel):** una sesión liviana dentro de una conexión. Varios canales comparten la misma conexión TCP. Spring AMQP gestiona conexiones y canales por nosotros.

### 4.7 Virtual host (vhost)

Es un espacio de nombres aislado dentro de RabbitMQ. Cada vhost tiene sus propios exchanges, colas y permisos. En el proyecto usamos el vhost por defecto (`/`) para simplificar.

### 4.8 Mensaje

Un mensaje tiene:

- **Payload:** el contenido (en nuestro caso, JSON con el `PedidoEventoDTO`).
- **Properties:** metadatos como `contentType`, `deliveryMode`, `messageId`, `timestamp`.
- **Headers:** pares clave/valor personalizados, por ejemplo `x-retry-count` (intentos de reintento) y `x-dominio` (dominio lógico).
- **Routing key:** la etiqueta de enrutamiento.
- **Exchange:** el exchange al que se publicó.

Un mensaje persistente (`deliveryMode=2`) se guarda en disco si la cola es durable. Eso ayuda a sobrevivir reinicios, pero no es una garantía absoluta ante fallos de disco.

### 4.9 Tabla resumen

| Concepto | Qué es | En Pedidos360 |
|---|---|---|
| Broker | Servidor de mensajería | RabbitMQ en Docker/EC2 |
| Productor | Publica mensajes | `ms-pedidos-360-orders` |
| Consumidor | Procesa mensajes | `ms-pedidos-360-notify` |
| Exchange | Enruta mensajes | `cmd.direct`, `cmd.topic` y `cmd.dead.dlx` |
| Queue | Guarda mensajes | `q.cmd.email`, `q.cmd.kitchen`, `q.cmd.invoice` |
| Binding | Regla exchange → cola | direct: `email.send`, `kitchen.ticket`, `invoice.gen`; topic: `email.#`, `kitchen.#`, `invoice.#` |
| Routing key | Etiqueta del mensaje | base: `email.send`, `kitchen.ticket`, `invoice.gen`; variantes: `email.send.high`, `kitchen.ticket.thermal`, `invoice.gen.pdf` |
| ACK | Confirmación de éxito | `basicAck(deliveryTag, false)` |
| NACK | Rechazo | `basicNack(deliveryTag, false, false)` |
| DLX | Exchange de mensajes muertos | `cmd.dead.dlx` |
| DLQ | Cola de mensajes muertos | `q.cmd.<dominio>.dlq` |
| TTL | Tiempo de vida de un mensaje | No se usa en la topología del caso |
| Prefetch | Mensajes sin ACK por consumidor | 1 para fair dispatch |
| Clúster | Varios nodos RabbitMQ | `rabbit@rabbitmq-1` + `rabbit@rabbitmq-2` |

---

## 5. Tipos de exchange

### 5.1 Direct exchange

Enruta por **coincidencia exacta** entre la routing key y la binding key.

```text
routing key "notificaciones" → binding "notificaciones" → cola notificaciones
routing key "cocina"         → binding "cocina"         → cola cocina
routing key "documentos"     → binding "documentos"     → cola documentos
```

Uso en Pedidos360: **comandos operativos**. “Envía una notificación”, “imprime un ticket”, “genera un documento”. Cada mensaje tiene un destino claro.

### 5.2 Topic exchange

Enruta por **patrones** separados por puntos:

- `*` reemplaza **exactamente una palabra**.
- `#` reemplaza **cero o más palabras**.

Ejemplos:

| Binding | Routing key | ¿Coincide? |
|---|---|---|
| `pedido.*` | `pedido.aceptado` | Sí |
| `pedido.*` | `pedido.cancelado` | Sí |
| `pedido.*` | `pedido.estado.aceptado` | No (tres palabras) |
| `pedido.#` | `pedido.estado.aceptado` | Sí |
| `pedido.#` | `pedido` | Sí (cero palabras después del punto) |

Uso en Pedidos360: **variantes operativas y cambios de estado**. `cmd.direct` publica las routing keys base al crear el pedido (`email.send`, `kitchen.ticket`, `invoice.gen`). `cmd.topic` publica variantes cuando el pedido cambia de estado y en las pruebas de laboratorio:

| Binding topic | Routing key | ¿Coincide? |
|---|---|---|
| `email.#` | `email.send` | Sí |
| `email.#` | `email.send.high` | Sí |
| `kitchen.#` | `kitchen.ticket` | Sí |
| `kitchen.#` | `kitchen.ticket.thermal` | Sí |
| `invoice.#` | `invoice.gen` | Sí |
| `invoice.#` | `invoice.gen.pdf` | Sí |

Los eventos de negocio (`OrderCreated`, `OrderAccepted`, etc.) no van por RabbitMQ: van por Kafka en la etapa EFT.

### 5.3 Fanout exchange

Ignora la routing key y copia el mensaje a **todas** las colas enlazadas. Se usa para broadcast. En la semana 08 se usó para notificaciones, auditoría y métricas. En la topología actual de EP3/EP4 priorizamos **direct + topic**; fanout queda como concepto y referencia de laboratorio.

### 5.4 Headers exchange

Enruta según headers en lugar de la routing key. No lo usamos en este proyecto.

### 5.5 Qué usamos y por qué

- **Direct (`cmd.direct`):** para la creación del pedido y los comandos base (`email.send`, `kitchen.ticket`, `invoice.gen`).
- **Topic (`cmd.topic`):** para cambios de estado y variantes operativas (`email.send.high`, `kitchen.ticket.thermal`, `invoice.gen.pdf`) usando patrones `#`.
- **DLX (`cmd.dead.dlx`):** es un direct exchange; los mensajes fallidos se enrutan a la DLQ correspondiente con la routing key principal del dominio (`email.send`, `kitchen.ticket`, `invoice.gen`).
- **Retry:** es un direct exchange; los mensajes que fallan van a una cola de espera con TTL y luego vuelven al exchange principal.

---

## 6. Ciclo de vida de un mensaje

```text
Productor
   │  publica (exchange + routing key + payload)
   ▼
Exchange ── binding ──► Queue
                         │
                         ▼
                     Consumidor
                         │
           ┌─────────────┴─────────────┐
           ▼                           ▼
     procesa OK                   falla (excepción)
           │                           │
     basicAck                    ¿reintentos disponibles?
           │                     ┌─────┴─────┐
           ▼                     ▼           ▼
     RabbitMQ elimina       publica en     basicNack
     el mensaje             cola .retry    (requeue=false)
                                  │           │
                                  ▼           ▼
                            TTL 5s → vuelve   DLX → DLQ
                            a la cola main
```

### 6.1 Publicación

El productor publica en un exchange con una routing key. El exchange evalúa los bindings y decide a qué colas entregar el mensaje.

### 6.2 Enrutamiento

Si ninguna cola coincide, el mensaje se descarta (o se devuelve si `mandatory=true` y hay un callback de returns). Por eso es importante que la topología exista antes de publicar. En Spring, los beans de `Queue`, `Exchange` y `Binding` declaran la topología al arrancar.

### 6.3 Consumo

El consumidor recibe el mensaje. En modo manual, el mensaje queda **unacked** hasta que el consumidor llame a `basicAck` o `basicNack`.

### 6.4 ACK

`basicAck(deliveryTag, false)` le dice a RabbitMQ: “procesé este mensaje, puedes eliminarlo”. Si el consumidor se cae antes del ACK, RabbitMQ vuelve a entregar el mensaje a otro consumidor.

### 6.5 NACK/Reject

`basicNack(deliveryTag, false, false)` le dice: “no pude procesarlo y no lo reencoles”. Si la cola tiene `x-dead-letter-exchange`, el mensaje va a la DLX. Si no, se descarta.

`basicNack(deliveryTag, false, true)` lo reencola. **Peligro:** si el error es permanente, se genera un bucle infinito y se satura el broker. Por eso usamos reintentos controlados y luego `requeue=false`.

### 6.6 DLX y DLQ

- **DLX (Dead Letter Exchange):** exchange al que se envían los mensajes rechazados, expirados o que excedieron un límite.
- **DLQ (Dead Letter Queue):** cola donde quedan esos mensajes para inspección y reproceso.

En Pedidos360 (caso semestral):

```text
q.cmd.email --(NACK requeue=false)--> cmd.dead.dlx
cmd.dead.dlx --(routing key email.send)--> q.cmd.email.dlq
```

Las colas principales se declaran con:

```java
QueueBuilder.durable("q.cmd.email")
    .withArgument("x-dead-letter-exchange", "cmd.dead.dlx")
    .withArgument("x-dead-letter-routing-key", "email.send")
    .build();
```

### 6.7 Reintentos sin colas extra

La topología del caso **no tiene colas de retry**. Para un reintento controlado, el consumidor republica el mensaje en el mismo exchange y routing key con un header `x-retry-count` incrementado, y hace `basicAck` del original. Si se agotan los intentos, `basicNack(requeue=false)` lo envía a `cmd.dead.dlx` y a la DLQ.

```text
Fallo transitorio -> republicar en cmd.direct / email.send con x-retry-count+1
                    -> básicamente vuelve a q.cmd.email
Intentos agotados -> basicNack(requeue=false) -> cmd.dead.dlx -> q.cmd.email.dlq
```

**Por qué no `requeue=true`:** reencolar inmediatamente y sin límite puede generar un bucle infinito. El header `x-retry-count` permite limitar los intentos.

> El patrón clásico de **retry queue con TTL** es válido y se estudia en el laboratorio, pero agrega colas que el caso semestral no contempla. Por eso no se implementa aquí.

### 6.8 Persistencia y durabilidad

- **Cola durable:** sobrevive a un reinicio de RabbitMQ.
- **Mensaje persistente:** `deliveryMode=2`; se guarda en disco.
- **Publisher confirms:** el broker confirma que recibió y persistió el mensaje.
- **Consumer ACK:** el consumidor confirma que lo procesó.

La combinación correcta es: cola durable + mensaje persistente + publisher confirms + ACK manual. No basta con una sola.

### 6.9 Publisher confirms

Cuando publicas, RabbitMQ puede confirmar de forma asíncrona que recibió el mensaje. Si el exchange no existe o el mensaje no se pudo enrutar, hay callbacks de confirmación/return. En Spring:

```properties
spring.rabbitmq.publisher-confirm-type=correlated
spring.rabbitmq.publisher-returns=true
```

Esto no reemplaza una DLQ; es una herramienta de diagnóstico y confiabilidad del lado del productor.

### 6.10 Prefetch

`prefetch` limita cuántos mensajes sin ACK puede tener un consumidor. Con `prefetch=1`, el broker entrega uno y espera el ACK antes de entregar el siguiente. Es justo para repartir carga y fácil de demostrar. En producción se ajusta según el throughput.

### 6.11 Idempotencia

Un mensaje puede entregarse más de una vez (por un reintento, por una caída antes del ACK o por un fallo de red). El consumidor debe poder procesarlo dos veces sin efectos duplicados. Estrategias:

- Guardar el `eventoId` procesado en una tabla y verificar antes de actuar.
- Usar operaciones idempotentes (por ejemplo, `upsert` en lugar de `insert`).
- En el alcance de este curso, dejamos el `eventoId` en los logs y documentamos la limitación; una mejora futura es una tabla de idempotencia o un inbox pattern.

---

## 7. Confiabilidad: ACK, NACK, DLQ, TTL y reintentos

### 7.1 El problema del `requeue=true`

Si un consumidor falla y reencola inmediatamente, RabbitMQ le vuelve a entregar el mismo mensaje. Si el error es permanente (JSON inválido, dato inexistente), se produce un bucle infinito. Eso consume CPU, llena logs y bloquea la cola.

### 7.2 La estrategia de Pedidos360 (según el caso semestral)

La topología oficial tiene **6 colas**: 3 principales + 3 DLQ. **No hay colas de retry**. Para no reencolar infinitamente y no agregar colas extra:

1. El consumidor recibe el mensaje con **ACK manual**.
2. Intenta procesarlo.
3. Si falla:
   - **Error permanente** (JSON inválido, validación): no tiene sentido reintentar. Se envía directo a la DLQ.
   - **Error transitorio** (servicio externo caído, timeout): se reintenta de forma controlada.
4. Para reintentar, el consumidor **republica el mensaje en el mismo exchange y routing key** con el header `x-retry-count` incrementado, y hace `basicAck` del mensaje original. El mensaje vuelve a la misma cola principal, sin colas adicionales.
5. Si se agotan los intentos, `basicNack(requeue=false)` envía el mensaje a `cmd.dead.dlx` y de allí a la DLQ correspondiente.
6. La DLQ se inspecciona desde la Management UI; el mensaje conserva headers y payload para diagnosticar.

> El reintento por republicación es inmediato. En producción se podría agregar un exchange delayed o backoff, pero eso agrega colas/plugins que el caso no contempla.

### 7.3 Tabla de decisión

| Situación | Acción | Resultado |
|---|---|---|
| Procesamiento exitoso | `basicAck` | Mensaje eliminado |
| Error transitorio, intentos < máximo | Republicar en el mismo exchange + `basicAck` del original | Vuelve a la misma cola |
| Error transitorio, intentos = máximo | `basicNack(requeue=false)` | `cmd.dead.dlx` → DLQ |
| JSON inválido / validación | `basicNack(requeue=false)` | `cmd.dead.dlx` → DLQ sin reintentos |
| Fallo al publicar el reintento | `basicNack(requeue=false)` + log | `cmd.dead.dlx` → DLQ (no se pierde silenciosamente) |

### 7.4 Configuración mínima en `application.properties`

```properties
spring.rabbitmq.listener.simple.acknowledge-mode=manual
spring.rabbitmq.listener.simple.prefetch=1
spring.rabbitmq.listener.simple.default-requeue-rejected=false
```

`default-requeue-rejected=false` evita que una excepción no controlada genere un reencolado infinito.

---

## 8. Clúster de RabbitMQ

### 8.1 Qué es un clúster

Un clúster es un conjunto de nodos RabbitMQ que comparten metadatos (usuarios, vhosts, exchanges, bindings, políticas) y se comunican entre sí. Los clientes pueden conectarse a cualquiera de los nodos.

Analogía: dos sucursales de un mismo banco. Comparten el sistema central, pero cada sucursal tiene su propia caja. Si una sucursal se cae, la otra sigue operando para ciertas cosas; para otras, depende de cómo estén replicados los datos.

### 8.2 Nodos, cookie de Erlang y nombres

Cada nodo tiene un nombre único, por ejemplo:

- `rabbit@rabbitmq-1`
- `rabbit@rabbitmq-2`

Para que dos nodos puedan formar un clúster, deben compartir el mismo **Erlang cookie**. Es un secreto que usan para autenticarse entre sí. Si los cookies no coinciden, el `join_cluster` falla.

### 8.3 Qué hace el job de inicialización

El servicio `rabbitmq-cluster-init`:

1. Espera a que ambos nodos estén `healthy`.
2. Escribe el cookie en su propio `HOME` (`/tmp`).
3. Verifica si `rabbit@rabbitmq-2` ya está en el clúster.
4. Si no está:
   - `rabbitmqctl -n rabbit@rabbitmq-2 stop_app`: detiene la aplicación del nodo 2 (no el contenedor).
   - `reset`: limpia metadatos locales del nodo 2. **Solo se ejecuta si el nodo no está en el clúster.**
   - `join_cluster rabbit@rabbitmq-1`: une el nodo 2 al clúster del nodo 1.
   - `start_app`: vuelve a iniciar la aplicación del nodo 2.
5. Imprime `cluster_status` para verificar que ambos nodos están en `Running Nodes`.

Es un **job de una sola vez**, no lógica de la aplicación. Por eso `restart: "no"` y por eso no usamos `Runtime.exec` desde Spring.

### 8.4 Clásicas vs. quorum queues

- **Cola clásica durable:** vive en un nodo. El clúster comparte metadatos, pero la cola no se replica automáticamente. Si el nodo dueño cae, la cola puede quedar no disponible hasta que se recupere.
- **Quorum queue:** usa Raft para replicar la cola en varios nodos. Necesita una mayoría (quórum) para operar. Con 2 nodos, el quórum es 2; si uno cae, la cola no está disponible. Para tolerar la caída de un nodo se recomiendan **3 nodos**.

Para EP3/EP4 la rúbrica pide un clúster de **al menos 2 nodos** y 3 colas + 3 DLQ. Podemos usar colas clásicas durables para la demo y mencionar quorum como mejora. Si el docente pide quorum explícitamente, se puede agregar un tercer nodo y declarar las colas con `.quorum()`. Importante: **un clúster de 2 nodos no es alta disponibilidad real**; es una demostración de clúster. La HA real requiere réplica y quórum.

### 8.5 Nuestro clúster de 2 nodos

- Nodo 1: `rabbitmq-1`, puertos `5672` (AMQP) y `15672` (Management UI).
- Nodo 2: `rabbitmq-2`, puertos `5673` (AMQP) y `15673` (Management UI).
- Red interna: `pedidos360-rabbitmq-net`.
- Volúmenes: `rabbitmq-1-data` y `rabbitmq-2-data`.
- Job de unión: `rabbitmq-cluster-init`.
- Cookie y credenciales: variables de entorno en `.env` (no se commitea).

El puerto `5672` es el que usará la aplicación localmente. El `5673` permite demostrar que el nodo 2 también está activo y acepta conexiones.

---

## 9. Arquitectura de Pedidos360

### 9.1 Estado EV1/EV2 (lo que ya existe)

```text
Angular + MSAL
      │ Bearer JWT
      ▼
AWS API Gateway (JWT Authorizer)
      │
      ▼
ms-pedidos-360-bff (8080)
      ├── ms-pedidos-360-orders (8082) ──► Oracle Autonomous DB
      └── ms-pedidos-360-catalog (8081) ──► Oracle Autonomous DB
```

- **Angular** inicia sesión con Microsoft Entra ID y adjunta el JWT.
- **API Gateway** valida el token en el perímetro.
- **BFF** revalida el JWT y aplica RBAC; delega a los microservicios.
- **orders** y **catalog** persisten en Oracle.
- **notify** ya tiene los 3 consumidores RabbitMQ de EP3/EP4; **report** y **audit** siguen como esqueletos sin lógica de mensajería.

### 9.2 Por qué agregar RabbitMQ

Al crear o aceptar un pedido hay tareas secundarias:

- Notificar al cliente (correo/WebPush).
- Enviar el ticket a cocina.
- Generar la boleta o documento contable.

Hacerlas por HTTP dentro de la misma petición bloquea al usuario y acopla los servicios. RabbitMQ las convierte en tareas asíncronas.

### 9.3 Diagrama objetivo

```text
Angular ──► API Gateway ──► BFF ──► orders ──► Oracle
                                      │
                                      │ publica comandos/eventos
                                      ▼
                         ┌──────────────────────────┐
                         │ RabbitMQ (clúster 2 nodos)│
                         │                          │
                         │ cmd.direct               │
                         │   ├─ email.send ─────────┼──► notify: EmailConsumer
                         │   ├─ kitchen.ticket ─────┼──► notify: KitchenConsumer
                         │   └─ invoice.gen ────────┼──► notify: InvoiceConsumer
                         │                          │
                         │ cmd.topic                │
                         │   ├─ email.# ────────────┼──► notify: EmailConsumer
                         │   ├─ kitchen.# ──────────┼──► notify: KitchenConsumer
                         │   └─ invoice.# ──────────┼──► notify: InvoiceConsumer
                         │                          │
                         │ cmd.dead.dlx + 3 DLQ     │
                         └──────────────────────────┘
```

### 9.4 Topología concreta (la del caso semestral)

**6 colas en total:** 3 principales + 3 DLQ. No hay colas de retry ni cola de monitoreo.

| Exchange | Tipo | Binding key / patrón | Cola |
|---|---|---|---|
| `cmd.direct` | direct | `email.send` | `q.cmd.email` |
| `cmd.direct` | direct | `kitchen.ticket` | `q.cmd.kitchen` |
| `cmd.direct` | direct | `invoice.gen` | `q.cmd.invoice` |
| `cmd.topic` | topic | `email.#` | `q.cmd.email` |
| `cmd.topic` | topic | `kitchen.#` | `q.cmd.kitchen` |
| `cmd.topic` | topic | `invoice.#` | `q.cmd.invoice` |
| `cmd.dead.dlx` | direct | `email.send` | `q.cmd.email.dlq` |
| `cmd.dead.dlx` | direct | `kitchen.ticket` | `q.cmd.kitchen.dlq` |
| `cmd.dead.dlx` | direct | `invoice.gen` | `q.cmd.invoice.dlq` |

Además:

- Cada cola principal tiene `x-dead-letter-exchange=cmd.dead.dlx` y `x-dead-letter-routing-key` igual a su routing key principal (`email.send`, `kitchen.ticket`, `invoice.gen`).
- La DLQ se enlaza a `cmd.dead.dlx` con esa misma routing key.
- `cmd.direct` se usa en la creación del pedido con las routing keys base. `cmd.topic` se usa en cambios de estado y pruebas de variantes. Ambos apuntan a las mismas 3 colas principales; no se agregan colas.
- Las 3 colas principales y sus 3 DLQ son las 6 colas que se muestran en la defensa.

### 9.5 Flujo de creación de pedido

1. El usuario crea un pedido en Angular.
2. Angular llama al API Gateway con el JWT.
3. El Gateway valida el token y reenvía al BFF.
4. El BFF valida el token y llama a `orders`.
5. `orders` guarda el pedido en Oracle dentro de una transacción. El mismo mecanismo se aplica al cambiar estado en `OrderService.cambiarEstado` (por ejemplo `CREADO -> ACEPTADO`).
6. Al confirmar la transacción, `orders` publica **solo los comandos que corresponden al estado** según la matriz, usando el exchange según el modo:
   - `CREADO` → `cmd.direct` con routing keys base:
     - `email.send` → `q.cmd.email`
     - `kitchen.ticket` → `q.cmd.kitchen`
     - `invoice.gen` → `q.cmd.invoice`
   - Cambio de estado → `cmd.topic` con variantes:
     - `email.send.high` → `q.cmd.email`
     - `kitchen.ticket.thermal` → `q.cmd.kitchen`
     - `invoice.gen.pdf` → `q.cmd.invoice`
7. `orders` responde al BFF y al usuario.
8. `notify` consume los mensajes publicados y procesa solo los dominios que correspondan al estado.
9. Si todo va bien, cada consumidor hace `basicAck`.
10. Si algo falla, se aplica el flujo de la sección 9.7: reintento controlado sin colas extra o DLQ.

### 9.6 Flujo con `cmd.topic`: cambios de estado y variantes

1. Al crear un pedido (`CREADO`), `PedidoPersistidoListener` publica en `cmd.direct` con las routing keys base: `email.send`, `kitchen.ticket`, `invoice.gen`.
2. Cuando el pedido cambia de estado (`ACEPTADO`, `EN_PREPARACION`, `DESPACHADO`, `ENTREGADO`, `CANCELADO`), el listener publica en `cmd.topic` con las variantes: `email.send.high`, `kitchen.ticket.thermal`, `invoice.gen.pdf`.
3. Los bindings `email.#`, `kitchen.#` e `invoice.#` coinciden tanto con las keys de 2 palabras como con las variantes de 3 palabras.
4. Las variantes llegan a las mismas colas y son procesadas por los mismos consumidores.
5. Cada consumidor distingue la variante por `receivedRoutingKey` y lo registra en el log:
   - email: `email.send` → estándar; `email.send.high` → alta prioridad.
   - cocina: `kitchen.ticket` → estándar; `kitchen.ticket.thermal` → formato térmico.
   - factura: `invoice.gen` → estándar; `invoice.gen.pdf` → PDF.
6. No se crean colas adicionales: siguen siendo las mismas 6 colas del caso.

### 9.7 Flujo de fallo y DLQ

1. Un consumidor recibe un mensaje.
2. Si el flag de fallo del DTO está activo o el procesamiento lanza una excepción, el consumidor lo detecta.
3. Si el error es transitorio y quedan intentos, el consumidor **republica el mensaje en el mismo exchange (`cmd.direct` o `cmd.topic`) y routing key** con el header `x-retry-count` incrementado y hace `basicAck` del original. No se crean colas de retry: se usan las 6 colas del caso.
4. Si el error es permanente o se agotaron los intentos, `basicNack(requeue=false)` envía el mensaje a `cmd.dead.dlx`.
5. La DLQ correspondiente (`q.cmd.email.dlq`, `q.cmd.kitchen.dlq` o `q.cmd.invoice.dlq`) queda con `Ready: 1`.
6. En la Management UI se puede inspeccionar el payload, los headers (`x-retry-count`, `x-death`, `traceId`, `correlationId`) y el motivo. También se puede ejecutar `POST /api/lab/dlq/inspeccionar` para loguear y reencolar sin vaciar la DLQ.
7. Nota: el reintento por republicación es inmediato. Para producción se podría agregar un exchange delayed o una política de backoff, pero eso agregaría colas/plugins que el caso no contempla.

### 9.8 Contrato del mensaje: `PedidoEventoDTO`

El contrato debe ser común para productor y consumidores. En este repositorio no hay un módulo compartido, así que el record está **duplicado** en ambos servicios, pero con el **mismo FQCN**:

```text
ms-pedidos-360-orders/src/main/java/cl/duoc/pedidos360/common/dto/PedidoEventoDTO.java
ms-pedidos-360-notify/src/main/java/cl/duoc/pedidos360/common/dto/PedidoEventoDTO.java
```

El FQCN `cl.duoc.pedidos360.common.dto.PedidoEventoDTO` es parte del contrato de cable: el productor lo escribe en el header `__TypeId__` y el consumidor lo usa para deserializar. Si cambia el paquete o el nombre de la clase en un solo lado, el consumo falla.

```java
public record PedidoEventoDTO(
        String type,
        String eventId,
        LocalDateTime timestamp,
        String traceId,
        String correlationId,
        Long pedidoId,
        String estado,
        String clienteEmail,
        boolean falloEmail,
        boolean falloCocina,
        boolean falloFactura
) {}
```

Campos:

- `type`: tipo de comando (`EMAIL_SEND`, `KITCHEN_TICKET`, `INVOICE_GEN`, etc.).
- `eventId`: identifica el evento; sirve para trazabilidad e idempotencia futura.
- `timestamp`: cuándo ocurrió.
- `traceId` / `correlationId`: permiten seguir una petición a través de los logs y servicios.
- `pedidoId`: identifica el pedido.
- `estado`: estado del pedido (`CREADO`, `ACEPTADO`, etc.).
- `clienteEmail`: datos para la notificación.
- `falloEmail` / `falloCocina` / `falloFactura`: banderas de **laboratorio** para forzar fallos controlados en la demo. En producción se eliminarían o se aislarían en un perfil de pruebas.

### 9.9 Publicación después del commit

Regla de oro: **no publicar antes de confirmar la transacción de Oracle**.

Si publicamos antes y la transacción falla, enviamos un mensaje de un pedido que no existe (“mensaje fantasma”). Si publicamos después y RabbitMQ está caído, el pedido queda guardado pero la tarea no se encola (problema de doble escritura).

En la implementación real, `OrderService.crearPedido` y `OrderService.cambiarEstado` guardan en Oracle y publican un evento de aplicación dentro de la transacción. Al crear se publica siempre; al cambiar estado se publica solo si el estado realmente cambió:

```java
Order guardado = orderRepository.save(nuevoPedido);
eventPublisher.publishEvent(new PedidoPersistidoEvent(
        guardado.getId(), guardado.getClientId(), guardado.getStatus()));
return guardado;
```

Luego, `PedidoPersistidoListener` escucha ese evento **solo después del commit** y ahí publica los comandos en RabbitMQ:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void alPersistirPedido(PedidoPersistidoEvent event) {
    String modo = "CREADO".equals(event.estado()) ? "direct" : "topic";
    List<String> dominios = pedidoPolicy.dominiosPara(event.estado());
    publisher.publicarComandos(new PedidoComando(
            event.pedidoId(),
            event.clienteEmail(),
            event.estado(),
            false, false, false,
            modo,
            dominios
    ));
}
```

Ventaja: el mensaje solo se publica si la transacción de Oracle se confirmó. Limitaciones importantes:

- Si RabbitMQ está caído justo después del commit, el pedido queda guardado pero el mensaje no se encola. Además, la excepción del listener after-commit puede propagarse a la petición HTTP aunque la transacción ya esté confirmada; no hay rollback de Oracle.
- `@TransactionalEventListener` no se ejecuta si no hay una transacción activa (por defecto `fallbackExecution=false`). Por eso `crearPedido` y `cambiarEstado` deben seguir siendo `@Transactional`.
- Para producción se agrega un **outbox pattern** (tabla de eventos pendientes + reintento), que queda fuera del alcance de esta entrega.

> El endpoint `/api/lab/pedidos/aceptar` **no usa Oracle**: llama directamente a `PedidoEventPublisher` y sirve para probar RabbitMQ sin depender de la base. Acepta `estado` opcional (por defecto `ACEPTADO`) y `exchange` opcional (por defecto `direct`) para probar la matriz y las variantes de `cmd.topic`. El flujo real usa Oracle, la máquina de estados y el listener after-commit: `CREADO` publica en `cmd.direct` y los cambios de estado en `cmd.topic`.

### 9.10 Consumidores por dominio

La rúbrica pide consumidores agrupados por dominio. En `notify`:

```text
notify/
├── config/
│   ├── RabbitProperties.java
│   ├── RabbitTopologyConfig.java
│   └── RabbitConsumerConfig.java
├── controller/
│   └── DlqInspectionController.java
├── dlq/
│   └── DlqInspector.java
├── consumer/
│   ├── EmailConsumer.java
│   ├── KitchenConsumer.java
│   ├── InvoiceConsumer.java
│   ├── FalloPermanenteException.java
│   └── ProcesadorMensajes.java
└── common/dto/
    └── PedidoEventoDTO.java
```

Cada consumidor:

- Escucha solo su cola.
- Recibe `(Message message, Channel channel)` en lugar de un DTO ya convertido.
- Delega el procesamiento a una lógica de dominio.
- Usa `ProcesadorMensajes` para convertir el payload, hacer ACK/NACK y aplicar el reintento controlado, evitando duplicar esa lógica en cada clase.
- Recibe la `routingKey` y distingue la variante: estándar vs alta prioridad, formato térmico o PDF.
- Registra logs con `eventId`, `pedidoId`, `dominio`, `intentos`, `routingKey` y variante.

> **Detalle clave:** la conversión JSON se hace **manualmente** dentro de `ProcesadorMensajes`. Si el método `@RabbitListener` recibiera directamente `PedidoEventoDTO`, Spring convertiría antes de invocar el método y un JSON inválido se trataría como error fatal, deteniendo el contenedor. La sección 18.5.3 explica este comportamiento en detalle.

### 9.11 Microservicio administrador (completado, Fase 5)

`ms-pedidos-360-admin-mq` es un microservicio separado (puerto 8086) que permite crear y eliminar colas, exchanges y bindings en caliente usando `RabbitAdmin`.

Endpoints implementados:

```text
POST   /api/admin/queues
GET    /api/admin/queues/{name}
DELETE /api/admin/queues/{name}
POST   /api/admin/exchanges
DELETE /api/admin/exchanges/{name}
POST   /api/admin/bindings
DELETE /api/admin/bindings?queue=...&exchange=...&routingKey=...
```

Características:

- DTOs con `@NotBlank`, `@Pattern`, `@NotNull`, etc.
- `@Valid` en el controlador.
- `RabbitAdminService` encapsula toda la lógica de RabbitMQ.
- El controlador no conoce `AmqpAdmin` ni detalles de la librería.
- Manejo global de errores: 400 para validación, 404 para recursos inexistentes, 503 si el broker no está disponible.
- Seguridad: JWT de Entra ID y `ROLE_ADMINISTRADOR`.
- Servicio agregado a `infra/apps/docker-compose.yml` con el módulo `ms-pedidos-360-admin-mq`.
- BFF expone `/api/admin/rabbit/**` con `ROLE_ADMINISTRADOR` y reenvía el JWT al admin.
- Tests de servicio, validación, seguridad y JWT firmado; 15 tests en total.
- El BFF lo expone bajo `/api/admin/rabbit/**` para mantener un único punto de entrada y no abrir un puerto nuevo en EC2.

### 9.12 Seguridad

- El API Gateway valida el JWT en el perímetro.
- El BFF y los microservicios revalidan el token (defensa en profundidad).
- El microservicio admin exige `ROLE_ADMINISTRADOR`.
- El BFF exige `ROLE_ADMINISTRADOR` en `/api/admin/**` y reenvía el token al admin (defensa en profundidad).
- Las credenciales de RabbitMQ viven en variables de entorno, nunca en el repositorio.
- La Management UI no debe quedar expuesta a Internet sin restricción; para la demo se usa un túnel SSH o se limita por IP.

---

## 10. Paso a paso de lo que estamos haciendo

> **Nota de actualización:** las fases 2, 3, 4, 5 y 6 ya se implementaron y validaron en local. El texto de cada fase se conserva como registro de lo construido; la sección 18 contiene el detalle final, los ajustes finos de Spring AMQP y las evidencias de la validación.

### Fase 0: baseline y rama

**Qué hicimos:**

1. Revisamos los cambios pendientes del backend.
2. Confirmamos que el diff eliminaba credenciales hardcodeadas de Oracle y las reemplazaba por variables de entorno.
3. Commiteamos un baseline limpio en `main`.
4. Creamos la rama `feature/rabbitmq-ep3-ep4`.
5. Agregamos `.env` y `.vscode/` al `.gitignore`.

**Por qué:**

- Necesitamos un punto de partida estable para distinguir nuestros cambios de RabbitMQ de los cambios previos.
- No queremos mezclar la implementación de la evaluación con deuda técnica anterior.
- No queremos commitear secretos ni archivos de IDE.

**Cómo se verifica:**

```bash
git log --oneline -3
git status --short
```

El árbol debe estar limpio (o solo con archivos nuevos de la fase actual) y la rama debe ser `feature/rabbitmq-ep3-ep4`.

---

### Fase 1: clúster local de RabbitMQ (completada)

**Qué vamos a hacer:**

- Levantar dos nodos RabbitMQ con Docker Compose.
- Unirlos en un clúster.
- Verificar el estado con `rabbitmqctl cluster_status`.
- Abrir la Management UI y ver ambos nodos.

**Archivos:**

```text
infra/rabbitmq/
├── docker-compose.yml
├── join-cluster.sh
└── .env.example
```

#### `docker-compose.yml` línea por línea

- `name: pedidos360-rabbitmq`: nombre del proyecto Compose.
- `rabbitmq-1` / `rabbitmq-2`: dos servicios, cada uno con su contenedor.
- `image: rabbitmq:3.13-management`: imagen oficial con la Management UI incluida.
- `hostname`: nombre que usará Erlang para el nodo.
- `RABBITMQ_NODENAME`: nombre explícito del nodo (`rabbit@rabbitmq-1`, etc.).
- `RABBITMQ_ERLANG_COOKIE`: secreto compartido para que los nodos puedan unirse.
- `RABBITMQ_DEFAULT_USER` / `RABBITMQ_DEFAULT_PASS`: credenciales iniciales de la Management UI y AMQP.
- `ports`: publican AMQP y la UI al host. El nodo 2 usa `5673`/`15673` para no chocar con el nodo 1.
- `volumes`: persisten los datos en volúmenes nombrados.
- `networks`: ambos nodos comparten una red interna; se resuelven por nombre (`rabbitmq-1`, `rabbitmq-2`).
- `healthcheck`: verifica que RabbitMQ esté realmente listo, no solo que el contenedor esté “Up”.
- `depends_on` con `condition: service_healthy`: el nodo 2 espera al nodo 1.
- `rabbitmq-cluster-init`: job de una sola ejecución que une el nodo 2.
- `restart: "no"`: el job no debe reiniciarse.
- `:ro,z` en el volumen del script: `ro` = solo lectura; `z` = etiqueta SELinux compartida, necesaria en Fedora.

#### `join-cluster.sh` línea por línea

- `set -eu`: aborta si un comando falla o si se usa una variable no definida.
- `printf ... > "${HOME}/.erlang.cookie"`: escribe el cookie en un lugar que `rabbitmqctl` pueda leer.
- `chmod 400`: solo el dueño puede leer el cookie.
- `rabbitmqctl -n rabbit@rabbitmq-2 cluster_status | grep ...`: comprueba si el nodo 2 ya está en el clúster. Hace el script idempotente.
- `stop_app`: detiene la aplicación RabbitMQ del nodo 2, no el contenedor.
- `reset`: limpia metadatos locales antes de unirse. Solo se ejecuta si no está en el clúster.
- `join_cluster rabbit@rabbitmq-1`: une el nodo 2 al clúster.
- `start_app`: vuelve a iniciar la aplicación.
- `cluster_status`: imprime el estado final para verificar.

#### `.env.example`

```dotenv
RABBITMQ_DEFAULT_USER=pedidos360
RABBITMQ_DEFAULT_PASS=change-me-local
RABBITMQ_ERLANG_COOKIE=change-me-local-cookie
```

Se copia a `.env` (ignorado por Git) y se cambian los valores. El compose lee `.env` automáticamente.

#### Cómo se verifica

```bash
cd infra/rabbitmq
cp .env.example .env
docker compose up -d
docker compose ps -a
docker compose logs rabbitmq-cluster-init --tail=60
```

Debes ver:

- `rabbitmq-1` y `rabbitmq-2` en `healthy`.
- `rabbitmq-cluster-init` en `Exited (0)`.
- En los logs, `Running Nodes` con ambos nodos.

Management UI:

- Nodo 1: http://localhost:15672
- Nodo 2: http://localhost:15673

#### Errores comunes

| Error | Causa | Solución |
|---|---|---|
| `Permission denied` al leer `join-cluster.sh` | SELinux en Fedora | Usar `:ro,z` en el volumen |
| `ports are already allocated` | Otro RabbitMQ/ servicio usa 5672 o 15672 | Detener el otro servicio o cambiar el puerto del host |
| `join_cluster` falla por cookie | Los nodos no comparten `RABBITMQ_ERLANG_COOKIE` | Usar el mismo valor y recrear los contenedores |
| El nodo 2 no aparece en el clúster | El job corrió antes de que el nodo estuviera listo | Revisar healthchecks y logs del init |
| Cambié usuario/password y no funciona | El volumen conserva los usuarios anteriores | `docker compose down -v` y volver a levantar |

#### Qué aprendiste en esta fase

- Un clúster no es “dos contenedores sueltos”: hay que unirlos explícitamente.
- El cookie de Erlang es el secreto de confianza entre nodos.
- `stop_app` / `start_app` detienen la aplicación RabbitMQ, no el contenedor.
- `reset` borra metadatos; solo se usa en la unión inicial.
- La Management UI permite ver nodos, exchanges, colas y bindings.

---

### Fase 2: topología y configuración centralizada (completada)

**Objetivo:** que Spring Boot declare automáticamente las **6 colas del caso** (3 principales + 3 DLQ) y los **3 exchanges** `cmd.direct`, `cmd.topic` y `cmd.dead.dlx` al arrancar.

**Qué se creará:**

- `RabbitProperties` con `@ConfigurationProperties(prefix = "app.rabbit")` en `orders` y `notify`.
- `RabbitTopologyConfig` con beans de:
  - `DirectExchange` `cmd.direct`.
  - `TopicExchange` `cmd.topic`.
  - `DirectExchange` `cmd.dead.dlx`.
  - 3 colas principales: `q.cmd.email`, `q.cmd.kitchen`, `q.cmd.invoice`.
  - 3 DLQ: `q.cmd.email.dlq`, `q.cmd.kitchen.dlq`, `q.cmd.invoice.dlq`.
  - Bindings direct: `email.send`, `kitchen.ticket`, `invoice.gen`.
  - Bindings topic: `email.#`, `kitchen.#`, `invoice.#`.
  - Bindings DLX: las mismas routing keys principales.
- **No se crean colas de retry ni monitoreo**; topic reutiliza las mismas 3 colas principales.
- Nombres en `application.properties`, no hardcodeados en servicios/controladores.

**Por qué:**

- La rúbrica exige centralización y beans explícitos.
- RabbitMQ es idempotente: declarar la misma topología dos veces no rompe nada.
- Si la topología no existe, los mensajes publicados a un exchange sin bindings se pierden.

**Cómo se verifica:**

- Al arrancar `notify` y `orders`, en la Management UI deben aparecer los exchanges, colas y bindings.
- `rabbitmqctl list_queues name messages consumers` debe mostrar las 3 colas principales y las 3 DLQ.

---

### Fase 3: productor en `orders` + endpoint de laboratorio (completada)

**Objetivo:** publicar mensajes después del commit y demostrar `202 Accepted`.

**Qué se creará:**

- `PedidoEventoDTO` (record).
- `PedidoEventPublisher` con `RabbitTemplate`.
- Integración en `OrderService` con `@TransactionalEventListener(AFTER_COMMIT)`.
- `LabPedidoController` en `/api/lab/pedidos/aceptar` para la demo con flags de fallo.
- Configuración de `JacksonJsonMessageConverter`, mensajes persistentes y publisher confirms.

**Por qué:**

- El endpoint de laboratorio permite demostrar el flujo asíncrono y los fallos controlados sin depender de una orden real.
- `202 Accepted` comunica correctamente que la tarea se aceptó para procesamiento.
- Publicar después del commit evita mensajes fantasma.

**Cómo se verifica:**

- `POST /api/lab/pedidos/aceptar` responde 202.
- En la Management UI, las colas principales reciben mensajes y los consumidores los procesan.

---

### Fase 4: consumidores en `notify` (completada)

**Objetivo:** procesar los 3 dominios con ACK/NACK manual, reintento controlado sin colas extra y DLQ.

**Qué se creará:**

- `EmailConsumer`, `KitchenConsumer`, `InvoiceConsumer`.
- `ProcesadorMensajes` compartido para ACK/NACK y reintento controlado.
- `PedidoEventoDTO` en `notify`.
- Logs con `eventId`, `pedidoId`, `intentos`, `dominio` y `routingKey`.

**Por qué:**

- La rúbrica asigna 20% al manejo de ACK/NACK y errores.
- Separar por dominio facilita el mantenimiento y cumple el indicador de consumidores.
- El procesador compartido evita duplicar la lógica de reintentos.
- No se agregan colas `.retry`: el caso exige exactamente 6 colas (3 principales + 3 DLQ).

**Cómo se verifica:**

- Flujo normal: las colas quedan en 0 y las DLQ en 0.
- Fallo controlado en email: después de los reintentos, `q.cmd.email.dlq` tiene `Ready: 1`.
- Los logs muestran los intentos y el ACK.

---

### Fase 5: microservicio administrador (completada)

**Objetivo:** gestionar colas, exchanges y bindings por REST con validación.

**Qué se construyó:**

- Módulo `ms-pedidos-360-admin-mq` (puerto 8086).
- `RabbitAdminService` con `AmqpAdmin`.
- Controllers REST y DTOs validados.
- Manejo global de errores.
- Seguridad JWT + `ROLE_ADMINISTRADOR`.
- Servicio agregado a `infra/apps/docker-compose.yml`.

**Por qué:**

- La rúbrica exige un microservicio administrador y validación de entrada.
- Encapsular `AmqpAdmin` en un servicio evita que los controladores dependan de RabbitMQ.
- Los DTOs validados evitan crear colas con nombres vacíos o inválidos.

**Cómo se verifica:**

- `POST /api/admin/queues` con nombre válido → 201.
- Nombre vacío o con caracteres inválidos → 400 con mensaje claro.
- `DELETE /api/admin/queues/{name}` → 204.
- La Management UI refleja los cambios.

---

### Fase 6: prueba end-to-end local (validada)

**Objetivo:** demostrar el flujo completo y las evidencias de la rúbrica.

**Casos:**

1. Flujo normal: 3 colas procesan, DLQ en 0.
2. Fallo en email: reintento controlado y `q.cmd.email.dlq` con `Ready: 1`.
3. Fallo en cocina: reintento controlado y `q.cmd.kitchen.dlq` con `Ready: 1`.
4. Fallo en factura: reintento controlado y `q.cmd.invoice.dlq` con `Ready: 1`.
5. Admin API: crear/eliminar cola, exchange y binding.

**Evidencias:**

- Captura de exchanges, colas y DLQ en la Management UI.
- Captura del `202 Accepted`.
- Logs de ACK.
- Captura de la DLQ con `x-retry-count` y el payload.
- Captura de la API admin con 201/400/204.

---

### Fase 7: despliegue en EC2 y defensa EP4 (parcial)

**Objetivo:** replicar el clúster en la nube y preparar la demo.

**Compose de aplicaciones restaurado:** `infra/apps/docker-compose.yml` recupera el compose original de los microservicios y lo adapta a RabbitMQ. Usa un `Dockerfile` raíz con `ARG MODULE` para construir cualquier módulo, y conecta `orders` y `notify` a la red externa `pedidos360-rabbitmq-net`. Los servicios `report`, `audit` y `admin-mq` quedan comentados hasta sus fases correspondientes. Antes de levantarlo, el clúster de RabbitMQ debe estar arriba y debe existir el `.env` de `infra/apps`.

**Pendiente de definir con el docente/entorno:**

- Cómo están corriendo hoy los microservicios en EC2 (jar, Docker, systemd).
- Puertos y security groups.
- Cómo se sirve el frontend Angular.
- Si la Management UI se expondrá por túnel SSH o por IP restringida.

**Guion de defensa:** ver la sección 12.

---

## 11. Mapa con la rúbrica EP3

| Indicador | Peso | Dónde se cumple | Cómo se demuestra |
|---|---:|---|---|
| Configuración centralizada | 12% | `application.properties` + `RabbitProperties` + `RabbitTopologyConfig` | Mostrar que no hay nombres hardcodeados en controladores/servicios |
| Declaración de beans AMQP | 13% | `RabbitTopologyConfig` | Mostrar en la Management UI exchanges, colas y bindings |
| Separación de responsabilidades | 10% | Config en `config/`, consumidores en `consumer/`, publisher en `service/` | Mostrar que los controladores no usan `AmqpAdmin`/`RabbitTemplate` directamente |
| Consumidores por dominio | 15% | `EmailConsumer`, `KitchenConsumer`, `InvoiceConsumer` | Mostrar las clases separadas y sus colas |
| ACK/NACK y errores | 20% | `ProcesadorMensajes` + `basicAck`/`basicNack` + reintento controlado + DLQ | Demostrar flujo normal y DLQ con fallo controlado |
| API REST admin | 13% | `RabbitAdminController` | Probar endpoints con Postman/curl: 201/200/204/400/404/503 |
| Encapsulamiento admin | 10% | `RabbitAdminService` | Mostrar que el controller solo llama métodos de alto nivel |
| Validación de entrada | 7% | `@Valid` en `LabPedidoRequest` y DTOs admin | Enviar nombre vacío/inválido al admin y recibir 400 |

> **Estado de la rúbrica:** configuración centralizada, beans AMQP, separación de responsabilidades, consumidores por dominio, ACK/NACK/errores y **API admin con encapsulamiento/validación** están implementados y validados.

---

## 12. Guion de defensa EP4

Los 11 hitos de la pauta:

1. **API Manager en la nube:** mostrar la instancia de API Gateway en AWS.
2. **Ruteo al backend:** mostrar la ruta `/api/{proxy+}` hacia el BFF.
3. **Frontend a través del API Manager:** abrir DevTools → Network y mostrar que las llamadas van a `execute-api...`, no a `localhost`.
4. **Validación de JWT:** probar sin token (401) y con token inválido (401/403); luego con token válido (200).
5. **Tenant y usuarios IDaaS:** mostrar Microsoft Entra ID, el tenant y los usuarios de prueba.
6. **OAuth 2.0 / OIDC:** mostrar el login desde Angular y el token emitido.
7. **Backend y frontend en la nube:** mostrar los procesos/contenedores activos en EC2.
8. **Clúster RabbitMQ de 2 nodos:** mostrar `docker compose ps` y `rabbitmqctl cluster_status` con ambos nodos.
9. **3 colas + 3 DLQ:** mostrar en la Management UI las 3 colas principales y sus 3 DLQ.
10. **Exchanges direct y topic:** mostrar `cmd.direct` con las routing keys base y `cmd.topic` con `email.#`, `kitchen.#`, `invoice.#`; mostrar también `cmd.dead.dlx`.
11. **Docker Compose en la nube:** mostrar el `docker-compose.yml` de RabbitMQ ejecutándose en EC2.

Además, para la parte técnica:

- Crear un pedido o llamar al endpoint de laboratorio.
- Mostrar el `202 Accepted`.
- Mostrar los mensajes entrando y siendo consumidos.
- Forzar un fallo y mostrar el mensaje en la DLQ.
- Mostrar la matriz estado→dominio y una transición inválida rechazada por `OrderService`.
- Mostrar una publicación `direct` y otra `topic` con sus routing keys base/variante.
- Mostrar `/api/admin/rabbit/queues` a través del BFF con un JWT que tenga `ROLE_ADMINISTRADOR`; crear y borrar cola, exchange y binding.

---

## 13. Glosario

| Término | Definición simple |
|---|---|
| **Broker** | Servidor que recibe, guarda y entrega mensajes. |
| **AMQP** | Protocolo estándar de mensajería que implementa RabbitMQ. |
| **Productor** | Servicio que publica mensajes. |
| **Consumidor** | Servicio que procesa mensajes de una cola. |
| **Exchange** | Enrutador que decide a qué colas va un mensaje. |
| **Queue** | Cola donde se acumulan los mensajes. |
| **Binding** | Regla que conecta un exchange con una cola. |
| **Routing key** | Etiqueta del mensaje que usa el exchange para enrutar. |
| **Vhost** | Espacio de nombres aislado dentro de RabbitMQ. |
| **Connection** | Conexión TCP entre la app y RabbitMQ. |
| **Channel** | Sesión liviana dentro de una conexión. |
| **ACK** | Confirmación de que el mensaje fue procesado. |
| **NACK** | Rechazo del mensaje. |
| **Requeue** | Volver a poner el mensaje en la cola. |
| **DLX** | Exchange de mensajes muertos. |
| **DLQ** | Cola de mensajes muertos. |
| **TTL** | Tiempo de vida de un mensaje o cola. |
| **Prefetch** | Máximo de mensajes sin ACK por consumidor. |
| **Durable** | Cola/exchange que sobrevive a un reinicio. |
| **Persistente** | Mensaje que se guarda en disco. |
| **Publisher confirm** | Confirmación del broker de que recibió el mensaje. |
| **Idempotencia** | Procesar el mismo mensaje dos veces sin efectos duplicados. |
| **Clúster** | Varios nodos RabbitMQ trabajando juntos. |
| **Quorum queue** | Cola replicada con consenso Raft. |
| **Management UI** | Interfaz web para ver y administrar RabbitMQ. |
| **Outbox** | Patrón para guardar eventos en la BD y publicarlos de forma confiable. |

---

## 14. Comandos útiles

```bash
# Levantar el clúster
cd infra/rabbitmq
docker compose up -d

# Ver estado
docker compose ps -a

# Ver logs del job de unión
docker compose logs rabbitmq-cluster-init --tail=100

# Estado del clúster
docker compose exec rabbitmq-1 rabbitmqctl cluster_status

# Listar colas (nombre, mensajes, consumidores)
docker compose exec rabbitmq-1 rabbitmqctl list_queues name messages consumers

# Listar exchanges
docker compose exec rabbitmq-1 rabbitmqctl list_exchanges name type

# Listar bindings
docker compose exec rabbitmq-1 rabbitmqctl list_bindings

# Ver las DLQ
docker compose exec rabbitmq-1 rabbitmqctl list_queues name messages | grep dlq

# Apagar sin borrar datos
docker compose down

# Apagar y borrar volúmenes (¡borra datos!)
docker compose down -v
```

Management UI:

- http://localhost:15672 (nodo 1)
- http://localhost:15673 (nodo 2)

### 14.1 Arranque local de `orders` y `notify`

`notify` no necesita Oracle; `orders` sí. El archivo `infra/apps/.env` está ignorado por Git y debe contener las credenciales reales de Oracle y RabbitMQ.

```bash
# Desde la raíz del repo
set -a
source infra/apps/.env
set +a

# Terminal 1: consumidores
cd ms-pedidos-360-notify
SERVER_PORT=8093 mvn spring-boot:run

# Terminal 2: productor + Oracle
cd ms-pedidos-360-orders
mvn spring-boot:run
```

Verificar que los 3 consumidores están conectados:

```bash
curl -s -u "$RABBITMQ_DEFAULT_USER:$RABBITMQ_DEFAULT_PASS" \
  http://localhost:15672/api/consumers \
  | python3 -c 'import sys,json; [print(c["queue"]["name"], c["ack_required"]) for c in json.load(sys.stdin)]'
```

Verificar el clúster y las colas:

```bash
cd infra/rabbitmq
docker compose exec rabbitmq-1 rabbitmqctl cluster_status
docker compose exec rabbitmq-1 rabbitmqctl list_queues name messages consumers
```

### 14.2 Prueba direct y topic

```bash
# Direct + CREADO: publica las keys base en cmd.direct
curl -i -X POST http://localhost:8082/api/lab/pedidos/aceptar \
  -H 'Content-Type: application/json' \
  -d '{"pedidoId":9701,"clienteEmail":"cliente@example.com","falloEmail":false,"falloCocina":false,"falloFactura":false,"estado":"CREADO","exchange":"direct"}'

# Topic + ACEPTADO: publica email.send.high y kitchen.ticket.thermal en cmd.topic
curl -i -X POST http://localhost:8082/api/lab/pedidos/aceptar \
  -H 'Content-Type: application/json' \
  -d '{"pedidoId":9702,"clienteEmail":"cliente@example.com","falloEmail":false,"falloCocina":false,"falloFactura":false,"estado":"ACEPTADO","exchange":"topic"}'

# Topic + DESPACHADO: publica email.send.high e invoice.gen.pdf en cmd.topic
curl -i -X POST http://localhost:8082/api/lab/pedidos/aceptar \
  -H 'Content-Type: application/json' \
  -d '{"pedidoId":9704,"clienteEmail":"cliente@example.com","falloEmail":false,"falloCocina":false,"falloFactura":false,"estado":"DESPACHADO","exchange":"topic"}'
```

Respuesta esperada: `HTTP/1.1 202` con `correlationId` y `comandos[]` mostrando el `exchange` real y la `routingKey` exacta:

- `direct` + `CREADO` → `cmd.direct|email.send`
- `topic` + `ACEPTADO` → `cmd.topic|email.send.high` y `cmd.topic|kitchen.ticket.thermal`
- `topic` + `DESPACHADO` → `cmd.topic|email.send.high` y `cmd.topic|invoice.gen.pdf`

> `exchange` y `estado` son opcionales en el laboratorio. Si se omiten, se usa `direct` y `ACEPTADO`. La matriz de negocio sigue aplicando: `CREADO` solo email; `ACEPTADO`/`EN_PREPARACION` email + cocina; `DESPACHADO`/`ENTREGADO` email + factura; `CANCELADO` email + cocina + factura.

### 14.3 Forzar fallo y revisar la DLQ

```bash
# Falla simulada de email: se reintenta y luego cae en q.cmd.email.dlq
curl -i -X POST http://localhost:8082/api/lab/pedidos/aceptar \
  -H 'Content-Type: application/json' \
  -d '{"pedidoId":9703,"clienteEmail":"cliente@example.com","falloEmail":true,"falloCocina":false,"falloFactura":false,"estado":"ACEPTADO","exchange":"topic"}'
```

Inspeccionar el mensaje muerto (sin borrarlo):

```bash
curl -s -u "$RABBITMQ_DEFAULT_USER:$RABBITMQ_DEFAULT_PASS" \
  -X POST -H 'Content-Type: application/json' \
  -d '{"count":1,"ackmode":"ack_requeue_true","encoding":"auto","truncate":50000}' \
  'http://localhost:15672/api/queues/%2F/q.cmd.email.dlq/get' \
  | python3 -m json.tool
```

Debes ver `x-retry-count=3`, `x-death` con `reason=rejected` y el payload original.

Inspeccionar y reencolar las DLQ sin vaciarlas:

```bash
curl -i -X POST http://localhost:8093/api/lab/dlq/inspeccionar
```

Respuesta esperada: `HTTP/1.1 200` con un mapa de conteos, por ejemplo `{"q.cmd.email.dlq":1,"q.cmd.kitchen.dlq":0,"q.cmd.invoice.dlq":0}`. Los mensajes se registran en el log de `notify` con `x-death` y `x-retry-count`, y **vuelven a quedar Ready** en la misma DLQ.

### 14.4 Limpiar mensajes de prueba

```bash
for q in q.cmd.email q.cmd.kitchen q.cmd.invoice \
         q.cmd.email.dlq q.cmd.kitchen.dlq q.cmd.invoice.dlq; do
  curl -s -o /dev/null -w "$q %{http_code}\n" \
    -u "$RABBITMQ_DEFAULT_USER:$RABBITMQ_DEFAULT_PASS" \
    -X DELETE "http://localhost:15672/api/queues/%2F/$q/contents"
done
```

> Los contadores de la Management API pueden tardar unos segundos en reflejar el purge. No es un error.

### 14.5 Tests

```bash
cd ms-pedidos-360-notify && mvn clean test
cd ms-pedidos-360-orders && mvn test
```

Los tests de contexto mockean `RabbitAdmin` y, en `orders`, también `JwtDecoder`; en `notify` se desactiva `spring.rabbitmq.listener.simple.auto-startup` para no necesitar un broker real durante el test.

### 14.6 API admin a través del BFF

El BFF expone el admin en `http://localhost:8080/api/admin/rabbit/**` y exige `ROLE_ADMINISTRADOR`. El token se reenvía al microservicio admin, que también valida el JWT.

```bash
TOKEN="<access_token_con_ROLE_ADMINISTRADOR>"

# Crear cola
curl -i -X POST http://localhost:8080/api/admin/rabbit/queues \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"q.demo","durable":true}'

# Obtener cola
curl -i http://localhost:8080/api/admin/rabbit/queues/q.demo \
  -H "Authorization: Bearer $TOKEN"

# Crear exchange
curl -i -X POST http://localhost:8080/api/admin/rabbit/exchanges \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"ex.demo","type":"topic","durable":true}'

# Crear binding
curl -i -X POST http://localhost:8080/api/admin/rabbit/bindings \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"queue":"q.demo","exchange":"ex.demo","routingKey":"demo.#"}'

# Eliminar binding (el # va URL-encoded como %23)
curl -i -X DELETE "http://localhost:8080/api/admin/rabbit/bindings?queue=q.demo&exchange=ex.demo&routingKey=demo.%23" \
  -H "Authorization: Bearer $TOKEN"

# Eliminar cola y exchange
curl -i -X DELETE http://localhost:8080/api/admin/rabbit/queues/q.demo \
  -H "Authorization: Bearer $TOKEN"
curl -i -X DELETE http://localhost:8080/api/admin/rabbit/exchanges/ex.demo \
  -H "Authorization: Bearer $TOKEN"
```

Respuestas esperadas: `201` al crear, `200` al consultar, `204` al eliminar, `400` ante validación inválida, `404` si el recurso no existe y `503` si RabbitMQ no está disponible.

---

## 15. Anti-patrones y cómo los evitamos

| Anti-patrón | Por qué es malo | Qué hacemos |
|---|---|---|
| Nombres hardcodeados en servicios/controladores | Difícil de cambiar y frágil | Nombres en `application.properties` |
| Lógica de Rabbit en controladores | Rompe separación de responsabilidades | Config en `config/`, servicio admin dedicado |
| `requeue=true` para todo | Bucle infinito y CPU al 100% | Reintento controlado con header y NACK `requeue=false` hacia DLQ |
| Publicar antes del commit | Mensajes de pedidos inexistentes | `@TransactionalEventListener(AFTER_COMMIT)` |
| No usar ACK manual | Pérdida o duplicación de mensajes | `acknowledge-mode: manual` |
| No configurar DLQ | Mensajes fallidos se pierden | DLX + DLQ por dominio |
| No usar prefetch | Un consumidor acapara todos los mensajes | `prefetch=1` |
| Exponer la Management UI a Internet | Riesgo de seguridad | Túnel SSH o IP restringida |
| Commitear `.env` | Filtración de credenciales | `.env` en `.gitignore`, `.env.example` versionado |
| Unir el clúster desde Spring con `Runtime.exec` | Mezcla infraestructura con aplicación | Job de inicialización en Compose |
| Usar 2 nodos quorum y esperar HA | Con 2 nodos, el quórum es 2; si uno cae, no hay servicio | Documentar la limitación; 3 nodos para HA real |
| No ser idempotente | Duplicados en reintentos | `eventId`/`correlationId` + logs; futuro inbox/outbox |

---

## 16. Preguntas frecuentes

**¿Por qué RabbitMQ y no Kafka?**
RabbitMQ es un broker de **tareas/comandos**: el mensaje se entrega y se elimina con el ACK. Kafka es un **log de eventos** persistente, pensado para streaming, múltiples consumidores y retención. El caso semestral usa RabbitMQ para comandos (`email`, `kitchen`, `invoice`) y Kafka para eventos de negocio y auditoría. En esta etapa trabajamos solo la parte RabbitMQ.

**¿Por qué no hacerlo todo por HTTP?**
Porque el usuario esperaría tareas secundarias, los servicios quedarían acoplados y un fallo externo afectaría la respuesta. RabbitMQ desacopla y permite reintentos controlados.

**¿Por qué 3 colas y no una?**
Porque cada dominio tiene su propio ritmo, sus errores y su DLQ. Si cocina falla, no queremos bloquear email ni factura.

**¿Por qué se usan direct y topic?**
Porque tienen propósitos distintos: `cmd.direct` publica las routing keys base al crear el pedido (`email.send`, `kitchen.ticket`, `invoice.gen`), y `cmd.topic` publica variantes operativas en los cambios de estado y pruebas (`email.send.high`, `kitchen.ticket.thermal`, `invoice.gen.pdf`). Los patrones `email.#`, `kitchen.#`, `invoice.#` coinciden tanto con las keys base como con las variantes.

**¿Qué reglas de negocio se aplican?**
`PedidoPolicy` define las transiciones permitidas y la matriz estado→dominio. `CREADO` solo notifica email; `ACEPTADO` y `EN_PREPARACION` notifican email + cocina; `DESPACHADO` y `ENTREGADO` notifican email + factura; `CANCELADO` notifica email + cocina + factura. Las transiciones inválidas se rechazan y los consumidores validan que el estado sea aplicable a su dominio. Ver sección 18.6.1 y 18.6.2.

**¿Cómo se reintentan los mensajes si no hay colas de retry?**
El consumidor republica el mensaje en el mismo exchange y routing key con un header `x-retry-count` incrementado, y hace ACK del original. Si se agotan los intentos, `basicNack(requeue=false)` lo envía a `cmd.dead.dlx` y a la DLQ. Así se mantienen exactamente las 6 colas del caso.

**¿Por qué los consumidores reciben `Message` y no directamente `PedidoEventoDTO`?**
Porque en Spring AMQP 4.1 un error de conversión en el adapter del listener se considera **fatal** y puede detener el contenedor. Si el método recibiera `PedidoEventoDTO`, un JSON inválido se convertiría antes de entrar al método y el contenedor se detendría. Para controlar ese caso, el método recibe `Message` + `Channel`, el factory usa `SimpleMessageConverter` (no deserializa el body) y `ProcesadorMensajes` convierte manualmente con un `JacksonJsonMessageConverter` que confía en `cl.duoc.pedidos360.common.dto`. Así un JSON inválido se NACK-ea a la DLQ y el listener sigue vivo. Ver sección 18.5.3.

**¿Qué pasa si el JSON es inválido?**
`ProcesadorMensajes` captura la excepción de conversión, hace `basicNack(deliveryTag, false, false)` y el mensaje va a la DLQ correspondiente **sin reintentos**. No se detiene el contenedor ni se reencola infinitamente.

**¿Se usa topic exchange?**
Sí. `cmd.topic` está declarado y se usa para cambios de estado y variantes. Sus bindings son `email.#`, `kitchen.#` e `invoice.#`; las routing keys variantes son `email.send.high`, `kitchen.ticket.thermal` e `invoice.gen.pdf`. No se crean colas nuevas: las variantes llegan a las mismas 3 colas principales.

**¿Cómo se inspeccionan las DLQ sin vaciarlas?**
Se llama `POST /api/lab/dlq/inspeccionar` en `notify`. El componente `DlqInspector` lee la cantidad inicial de mensajes de cada DLQ, registra en el log `queue`, `exchange`, `routingKey`, `messageId`, `x-retry-count`, `x-death` y payload, y reencola cada mensaje en la misma DLQ usando el exchange por defecto (`""`) con routing key igual al nombre de la cola. Así la evidencia sigue visible en Management UI. Para no perder mensajes, hace `basicAck` solo después de `basicPublish`; si falla el publish, hace `basicNack(requeue=true)`.

**¿Qué pasa si RabbitMQ está caído cuando se publica?**
El pedido ya quedó guardado en Oracle, pero el mensaje no se encola. Con publisher confirms y logs detectamos el problema. Una solución de producción es el patrón outbox, que deja el evento en la base y lo publica un proceso confiable. No es parte del alcance actual.

**¿Qué pasa si un consumidor se cae antes del ACK?**
RabbitMQ vuelve a entregar el mensaje a otro consumidor o al mismo cuando vuelva. Por eso el consumidor debe ser idempotente.

**¿Cuántas colas se usan?**
Exactamente **6**: `q.cmd.email`, `q.cmd.kitchen`, `q.cmd.invoice` y sus 3 DLQ (`q.cmd.email.dlq`, `q.cmd.kitchen.dlq`, `q.cmd.invoice.dlq`). No hay cola de monitoreo ni colas de retry.

**¿Necesitamos quorum queues para EP3/EP4?**
La rúbrica pide un clúster de al menos 2 nodos y 3 colas + 3 DLQ. No exige quorum. Con 2 nodos, quorum no da tolerancia a fallos (el quórum es 2). Para HA real se usan 3 nodos. Podemos mencionar quorum como mejora y, si el docente lo pide, agregar un tercer nodo.

**¿Necesitamos Prometheus/Grafana para EP3/EP4?**
No. La rúbrica de EP3/EP4 se centra en RabbitMQ, el clúster, las colas, la DLQ, el admin y el ACK/NACK. Prometheus/Grafana aparece en la guía 6 y en la evaluación de observabilidad posterior. Es un plus, no un requisito de esta entrega.

---

## 17. Próximos pasos

1. **Validar el admin con un token real de Entra ID** en el entorno cloud; la ruta BFF y el JWT firmado local ya están probados.
2. **Fase 7:** desplegar RabbitMQ + `orders` + `notify` en EC2 usando `infra/apps/docker-compose.yml` y el `Dockerfile` raíz; verificar red externa `pedidos360-rabbitmq-net` y wallet de Oracle.
3. **Confiabilidad de producción:** evaluar outbox pattern para la publicación after-commit y una tabla de idempotencia (o inbox pattern) para los consumidores.
4. **Seguridad:** quitar o proteger `/api/lab/**` antes de producción; hoy está `permitAll` solo para la demo.
5. **Reintentos:** si se requiere backoff, agregar un exchange delayed o TTL sin romper la topología de 6 colas; documentar el cambio.
6. **Evidencias:** guardar capturas de Management UI, logs de ACK/reintento/DLQ y respuestas `202` para la defensa.
7. **Commit:** revisar `git status`, no commitear `.env` y subir los cambios de la rama `feature/rabbitmq-ep3-ep4`.

> **Recordatorio:** no avanzar a la siguiente fase sin haber verificado la anterior. La deuda técnica más cara en mensajería es no entender qué pasa con un mensaje cuando falla.

---

## 18. Implementación final, comportamiento y cambios

> **Esta sección es la fuente de verdad del estado actual.** Describe el código que está en la rama `feature/rabbitmq-ep3-ep4` y la validación realizada el 2026-10-27 con Oracle real y el clúster local de RabbitMQ.

### 18.1 Estado de las fases

| Fase | Estado | Evidencia |
|---|---|---|
| 0. Baseline y rama | Completada | Rama `feature/rabbitmq-ep3-ep4`, `.env` ignorado |
| 1. Clúster RabbitMQ 2 nodos | Completada | `rabbitmq-1` + `rabbitmq-2`, Management UI 15672/15673 |
| 2. Topología centralizada | Completada | `cmd.direct` + `cmd.topic` + `cmd.dead.dlx`, 3 colas + 3 DLQ en `orders` y `notify` |
| 3. Productor `orders` | Completada | `PedidoEventPublisher`, publisher confirms, `/api/lab/pedidos/aceptar` |
| 4. Consumidores `notify` | Completada | 3 consumidores, ACK/NACK manual, reintento con `x-retry-count`, DLQ |
| 5. Admin REST | Completada | `ms-pedidos-360-admin-mq` en `:8086`; BFF `/api/admin/rabbit/**`; 15 tests admin + 5 tests BFF; compose habilitado |
| 6. E2E local | Validada | Oracle + RabbitMQ + `orders` + `notify`, flujos normal/fallo/DLQ |
| 7. EC2 | Parcial | `infra/apps/docker-compose.yml`, `Dockerfile`, `.dockerignore` preparados |

### 18.2 Mapa de componentes

```text
ms-pedidos-360-orders
├── common/dto/PedidoEventoDTO.java        contrato de cable (mismo FQCN que notify)
├── config/
│   ├── RabbitProperties.java              app.rabbit.* tipado (direct + topic + dlx)
│   ├── RabbitTopologyConfig.java          cmd.direct + cmd.topic + cmd.dead.dlx, colas, bindings, RabbitAdmin
│   ├── RabbitPublisherConfig.java         Jackson converter + returns callback
│   └── SecurityConfig.java                /api/lab/** permitAll (solo laboratorio)
├── controller/LabPedidoController.java    POST /api/lab/pedidos/aceptar
├── domain/
│   ├── EstadoPedido.java                  estados válidos
│   └── PedidoPolicy.java                  transiciones + matriz estado→dominio
├── dto/                                   PedidoComando, PublicacionComando, etc.
├── messaging/
│   ├── PedidoEventPublisher.java          publica direct/topic con routing keys base/variante
│   ├── PedidoPersistidoEvent.java         evento de aplicación
│   └── PedidoPersistidoListener.java      publica AFTER_COMMIT con modo direct/topic y matriz
└── service/OrderService.java              persiste en Oracle, valida transiciones y publica evento

ms-pedidos-360-notify
├── common/dto/PedidoEventoDTO.java        mismo contrato
├── config/
│   ├── RabbitProperties.java              app.rabbit.* tipado (direct + topic + dlx)
│   ├── RabbitTopologyConfig.java          misma topología direct+topic (idempotente)
│   └── RabbitConsumerConfig.java          converter confiable + factory con SimpleMessageConverter
├── controller/
│   └── DlqInspectionController.java       POST /api/lab/dlq/inspeccionar
├── dlq/
│   └── DlqInspector.java                  lee, loguea y reencola DLQ sin vaciarlas
├── consumer/
│   ├── EmailConsumer.java                 q.cmd.email; estándar vs alta prioridad
│   ├── KitchenConsumer.java               q.cmd.kitchen; estándar vs térmico
│   ├── InvoiceConsumer.java               q.cmd.invoice; estándar vs PDF
│   ├── FalloPermanenteException.java       error no reintentable
│   └── ProcesadorMensajes.java             conversión manual, ACK/NACK, reintento, DLQ, routingKey
└── application.properties                 ACK manual, prefetch 1, max-intentos 3

ms-pedidos-360-bff
├── client/RabbitAdminClient.java           proxy hacia admin-mq
└── controller/AdminRabbitController.java   /api/admin/rabbit/** con ROLE_ADMINISTRADOR

ms-pedidos-360-admin-mq
├── config/
│   ├── RabbitAdminConfig.java             RabbitAdmin explícito
│   └── SecurityConfig.java                JWT + ROLE_ADMINISTRADOR
├── controller/
│   ├── RabbitAdminController.java          CRUD de colas, exchanges y bindings
│   └── AdminExceptionHandler.java          400/404/503/500
├── dto/                                   QueueRequest, ExchangeRequest, BindingRequest, QueueResponse
├── exception/ResourceNotFoundException.java
├── service/RabbitAdminService.java        encapsula RabbitAdmin
└── application.properties                 puerto 8086, Rabbit y JWT
```

> **Detalle de Spring Boot 4.1:** `RabbitAdmin` no se expone automáticamente por su tipo concreto. Por eso ambos servicios lo declaran como bean en `RabbitTopologyConfig` y ejecutan `rabbitAdmin.initialize()` en un `ApplicationRunner`; así la topología se declara al arrancar y los tests pueden mockear `RabbitAdmin` sin un broker real.

> **Inspector de DLQ:** `DlqInspector` usa `RabbitTemplate.execute` + `basicGet` manual, `basicPublish` al exchange por defecto (`""`) con routing key igual al nombre de la cola, y `basicAck` **solo después** de publicar. Si el publish falla, hace `basicNack(requeue=true)`. Así el mensaje vuelve a la misma DLQ y sigue visible en Management UI.

> **Reglas de negocio:** `PedidoPolicy` centraliza las transiciones y la matriz estado→dominio. `PedidoPersistidoListener` la usa para decidir qué dominios publicar; `LabPedidoController` permite simular un estado; y cada consumidor valida que el estado sea aplicable a su dominio.

### 18.3 Flujo normal de comandos (direct y topic)

1. El cliente llama `POST /api/lab/pedidos/aceptar` con `exchange` opcional (por defecto `direct`) o se crea/actualiza un pedido real mediante `OrderService`.
2. `LabPedidoController` arma un `PedidoComando` y llama a `PedidoEventPublisher.publicarComandos`.
3. El publisher genera un `correlationId`, un `traceId` y un `eventId` por dominio. Recorre los dominios indicados en `PedidoComando.dominios` (los que definió `PedidoPolicy`).
4. Para cada dominio construye un `PedidoEventoDTO` y resuelve exchange/routing key según el modo: `direct` usa las keys base (`email.send`, `kitchen.ticket`, `invoice.gen`); `topic` usa las variantes (`email.send.high`, `kitchen.ticket.thermal`, `invoice.gen.pdf`).
5. Publica con `RabbitTemplate.convertAndSend` y `CorrelationData(eventId)`. El mensaje se marca:
   - `deliveryMode=PERSISTENT`
   - `messageId=eventId`
   - header `x-retry-count=0`
   - header `x-dominio=<email|kitchen|invoice>`
   - header `__TypeId__=cl.duoc.pedidos360.common.dto.PedidoEventoDTO` (lo agrega el converter)
6. Espera hasta 3 segundos la confirmación correlacionada. Si el broker responde `ack`, continúa; si responde `nack` o hay timeout, lanza excepción y la petición HTTP no responde 202.
7. RabbitMQ enruta por coincidencia exacta en `cmd.direct` o por patrón `email.#`/`kitchen.#`/`invoice.#` en `cmd.topic`. El mensaje queda en la cola principal correspondiente.
8. El consumidor recibe con `prefetch=1` y ACK manual. `ProcesadorMensajes` lee headers, convierte el body a `PedidoEventoDTO` con el converter confiable y ejecuta la lógica del dominio.
9. Si todo sale bien, registra el log de dominio y hace `basicAck(deliveryTag, false)`. RabbitMQ elimina el mensaje.
10. La respuesta HTTP del endpoint de laboratorio es `202 Accepted` con `correlationId` y la lista de comandos publicados.

> El flujo real `OrderService.crearPedido` y `OrderService.cambiarEstado` publica `PedidoPersistidoEvent`; `PedidoPersistidoListener` lo captura en `AFTER_COMMIT` y publica **solo los dominios que corresponden al estado** según la matriz de negocio.

> **Matriz aplicada:** `CREADO` → email; `ACEPTADO` → email + cocina; `EN_PREPARACION` → email + cocina; `DESPACHADO` → email + factura; `ENTREGADO` → email + factura; `CANCELADO` → email + cocina + factura. La máquina de estados solo permite avanzar por la secuencia y cancelar antes del despacho.

### 18.4 Topic: coincidencias y uso

`cmd.topic` se declara con bindings `email.#`, `kitchen.#` e `invoice.#`. El patrón `#` coincide con **cero o más palabras**, por lo que cubre tanto las routing keys base como las variantes:

| Dominio | Binding | Routing keys | Cola |
|---|---|---|---|
| email | `email.#` | `email.send`, `email.send.high` | `q.cmd.email` |
| kitchen | `kitchen.#` | `kitchen.ticket`, `kitchen.ticket.thermal` | `q.cmd.kitchen` |
| invoice | `invoice.#` | `invoice.gen`, `invoice.gen.pdf` | `q.cmd.invoice` |

Uso:

- `cmd.direct` + `CREADO` → keys base (`email.send`, `kitchen.ticket`, `invoice.gen`).
- `cmd.topic` + cambio de estado/prueba → variantes (`email.send.high`, `kitchen.ticket.thermal`, `invoice.gen.pdf`).
- Las variantes llegan a las mismas 3 colas principales; no se crean colas adicionales.
- Los consumidores distinguen la variante por `receivedRoutingKey` y la registran en el log.

### 18.5 Fallos, reintentos, DLQ y conversión manual

#### 18.5.1 Clasificación de errores

| Tipo | Ejemplo | Acción |
|---|---|---|
| Éxito | Email/ticket/factura simulados | `basicAck`; mensaje eliminado |
| Transitorio | `falloEmail`, `falloCocina`, `falloFactura`, timeout de servicio | Reintento controlado con `x-retry-count`; al agotar, DLQ |
| Permanente | `clienteEmail` vacío, `pedidoId` nulo, dato inválido de negocio | `FalloPermanenteException` → `basicNack(requeue=false)` → DLQ sin reintentos |
| Conversión | JSON inválido, `__TypeId__` no confiable, body vacío | `basicNack(requeue=false)` → DLQ sin reintentos |
| Fallo al republicar | Broker rechaza/timeout el reintento | `basicNack(requeue=false)` → DLQ (no se pierde silenciosamente) |

#### 18.5.2 Máquina de estados del reintento

`app.rabbit.max-intentos=3`. El header `x-retry-count` ausente se interpreta como `0`.

| Valor leído | ¿Quedan intentos? | Acción | Nuevo header |
|---:|---|---|---:|
| 0 | Sí | Republica y ACK del original | 1 |
| 1 | Sí | Republica y ACK del original | 2 |
| 2 | Sí | Republica y ACK del original | 3 |
| 3 | No (`3 >= 3`) | `basicNack(requeue=false)` → DLX → DLQ | 3 (se conserva) |

Por lo tanto hay **4 procesamientos posibles**: el inicial (0) + 3 reintentos (1, 2, 3). El mensaje que llega a la DLQ conserva `x-retry-count=3`. No es que “solo se procese 3 veces”; son 3 reintentos además del intento inicial.

#### 18.5.3 Por qué la conversión es manual (detalle Spring AMQP 4.1)

Este es el punto más delicado del diseño y conviene entenderlo para la defensa:

- Si el método `@RabbitListener` recibe directamente `PedidoEventoDTO`, Spring usa el `MessageConverter` del listener para convertir **antes** de invocar el método.
- En Spring AMQP 4.1, una `MessageConversionException` se considera **fatal** por el `DefaultExceptionStrategy` del contenedor. El `SimpleMessageListenerContainer` tiene su propio `exceptionStrategy` (campo `final`, sin setter público) y detiene el consumidor con `FatalListenerExecutionException`.
- Configurar solo un `ErrorHandler` en el factory **no alcanza**: el error handler se invoca, pero luego el `mainLoop` del contenedor evalúa su propio `exceptionStrategy` (heredado de `AbstractMessageListenerContainer`, `final` y sin setter público) y termina parando el listener.
- Solución implementada:
  1. El método del consumidor recibe `(Message message, Channel channel)`, no el DTO.
  2. El factory se declara con `SimpleMessageConverter`, de modo que el adapter del listener **no deserializa** el body.
  3. `ProcesadorMensajes` llama manualmente a `jsonMessageConverter.fromMessage(message)`.
  4. Ese `JacksonJsonMessageConverter` se construye con el paquete confiable: `new JacksonJsonMessageConverter("cl.duoc.pedidos360.common.dto")`.
  5. Si la conversión falla, se captura la excepción y se hace `basicNack(requeue=false)`; el contenedor sigue vivo.

Fragmento relevante de `RabbitConsumerConfig`:

```java
@Bean
public JacksonJsonMessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter("cl.duoc.pedidos360.common.dto");
}

@Bean
public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        SimpleRabbitListenerContainerFactoryConfigurer configurer,
        ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connectionFactory);   // hereda ACK manual, prefetch, etc.
    factory.setMessageConverter(new SimpleMessageConverter());
    return factory;
}
```

Fragmento relevante de `ProcesadorMensajes`:

```java
PedidoEventoDTO evento;
try {
    evento = (PedidoEventoDTO) jsonMessageConverter.fromMessage(message);
    if (evento == null) {
        throw new MessageConversionException("Mensaje sin contenido");
    }
} catch (Exception exception) {
    registrarDeadLetter(message, dominio, intento, null, "conversión: " + exception.getMessage());
    channel.basicNack(deliveryTag, false, false);
    return;
}
```

> Si en el futuro se vuelve a recibir el DTO directamente en el listener, hay que tener presente que un JSON inválido puede detener el contenedor. La decisión de recibir `Message` es deliberada.

#### 18.5.4 DLQ y headers

Las colas principales se declaran con:

```java
QueueBuilder.durable("q.cmd.email")
    .withArgument("x-dead-letter-exchange", "cmd.dead.dlx")
    .withArgument("x-dead-letter-routing-key", "email.send")
    .build();
```

Cuando se hace `basicNack(requeue=false)`, RabbitMQ agrega `x-death` (con `reason=rejected`, exchange y queue originales) y conserva `x-retry-count`, `__TypeId__`, `traceId`, `correlationId`, `messageId` y el payload. La DLQ no tiene consumidor automático: queda para inspección, purga o reproceso manual.

> En un fallo originado en `cmd.topic`, `x-death` conserva `exchange=cmd.topic` y la routing key variante (`email.send.high`, `kitchen.ticket.thermal` o `invoice.gen.pdf`). El sobre en la DLQ usa la routing key canónica (`email.send`, etc.) porque el DLX está configurado con la key base; la variante queda en `x-death`.

> **Inspección sin vaciar:** `DlqInspector` lee la cantidad inicial de mensajes de cada DLQ, registra en el log `queue`, `exchange`, `routingKey`, `messageId`, `x-retry-count`, `x-death` y payload, y reencola cada mensaje en la misma DLQ con `basicPublish` al exchange por defecto (`""`) y routing key igual al nombre de la cola. Hace `basicAck` solo después del publish; si el publish falla, hace `basicNack(requeue=true)`. El endpoint es `POST /api/lab/dlq/inspeccionar` y devuelve un mapa de conteos. Los mensajes siguen visibles en Management UI.

> Si se republica un mensaje de la DLQ **tal cual** en el exchange principal, conserva `x-retry-count=3`; al consumirlo de nuevo, el procesador lo considerará agotado y lo devolverá a la DLQ inmediatamente. Para reintentarlo de verdad hay que quitar/reiniciar ese header o publicar una copia con `x-retry-count=0`.

### 18.6 Publicación confiable y after-commit

`PedidoEventPublisher`:

- Usa `RabbitTemplate` con `publisher-confirm-type=correlated`.
- Espera `CorrelationData.getFuture().get(3, TimeUnit.SECONDS)`.
- Si `confirm.isAck()` es falso, lanza `IllegalStateException` con el motivo.
- Si hay timeout o interrupción, lanza `IllegalStateException`.
- `RabbitPublisherConfig` registra un `RabbitTemplateCustomizer` con `mandatory=true` y un `ReturnsCallback` que loguea mensajes no enrutables.
- Los mensajes son persistentes y llevan `messageId=eventId`.
- El exchange se selecciona por `PedidoComando.exchange`: `direct` → `cmd.direct` con keys base; `topic` → `cmd.topic` con variantes.
- `PublicacionComando` refleja el exchange real y la routing key exacta utilizada.

> **La publicación de los comandos de un estado no es atómica.** Se hace un `convertAndSend` por dominio y se espera su confirmación. Si falla el segundo dominio, el primero ya quedó encolado y los posteriores no se publican. No hay transacción distribuida ni rollback de RabbitMQ. Un reintento del cliente puede duplicar los comandos ya publicados; de ahí la importancia de la idempotencia y, en producción, del outbox.

Caminos de publicación:

| Camino | Persistencia | Publicación | Uso |
|---|---|---|---|
| `/api/lab/pedidos/aceptar` | No toca Oracle | `direct` o `topic` según `exchange` del request | Demostración de RabbitMQ |
| Creación real de pedido | `OrderService.crearPedido` guarda en Oracle | `PedidoPersistidoEvent` + `AFTER_COMMIT` → `cmd.direct` | Keys base |
| Cambio de estado | `OrderService.cambiarEstado` guarda en Oracle | `PedidoPersistidoEvent` + `AFTER_COMMIT` → `cmd.topic` | Variantes |

> El flujo real usa `cmd.direct` con keys base al crear (`CREADO`) y `cmd.topic` con variantes al cambiar de estado. El endpoint de laboratorio acepta `estado` y `exchange` opcionales (por defecto `ACEPTADO` y `direct`).

`OrderService` publica el evento de aplicación después del `save`, dentro de la transacción:

```java
Order guardado = orderRepository.save(nuevoPedido);
eventPublisher.publishEvent(new PedidoPersistidoEvent(
        guardado.getId(), guardado.getClientId(), guardado.getStatus()));
return guardado;
```

El cambio de estado pasa por la máquina de estados:

```java
EstadoPedido nuevo = pedidoPolicy.parse(nuevoEstado);
Order pedido = obtenerPorId(id);
EstadoPedido actual = pedidoPolicy.parse(pedido.getStatus());

if (nuevo == actual) {
    return pedido;
}

pedidoPolicy.validarTransicion(actual, nuevo);
pedido.setStatus(nuevo.name());
Order guardado = orderRepository.save(pedido);
eventPublisher.publishEvent(new PedidoPersistidoEvent(
        guardado.getId(), guardado.getClientId(), guardado.getStatus()));
return guardado;
```

`PedidoPersistidoListener` publica los comandos **solo si la transacción se confirmó**:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void alPersistirPedido(PedidoPersistidoEvent event) {
    String modo = "CREADO".equals(event.estado()) ? "direct" : "topic";
    List<String> dominios = pedidoPolicy.dominiosPara(event.estado());
    publisher.publicarComandos(new PedidoComando(
            event.pedidoId(), event.clienteEmail(), event.estado(),
            false, false, false, modo, dominios));
}
```

Limitación de doble escritura: si Oracle confirma y RabbitMQ falla justo después, el pedido queda guardado pero el mensaje no se encola. La excepción del listener after-commit puede propagarse a la petición HTTP aunque la transacción ya esté confirmada. La solución de producción es **outbox pattern** (tabla de eventos pendientes + publicador confiable). No está implementado.

> **Observación de negocio 1:** el flujo real pasa `guardado.getClientId()` al campo `clienteEmail` de `PedidoPersistidoEvent` porque la entidad `Order` actual no tiene correo. El consumidor de email recibirá un `clientId`, no un correo real. El endpoint de laboratorio sí envía un email. Si se requiere correo real, agregar el campo a `Order` o resolverlo desde el servicio de clientes.

#### 18.6.1 Máquina de estados aplicada

`OrderService.cambiarEstado` delega en `PedidoPolicy`:

```text
CREADO          -> ACEPTADO | CANCELADO
ACEPTADO        -> EN_PREPARACION | CANCELADO
EN_PREPARACION  -> DESPACHADO | CANCELADO
DESPACHADO      -> ENTREGADO
ENTREGADO       -> (final)
CANCELADO       -> (final)
```

Reglas:

- Transición no permitida → `IllegalStateException`.
- Estado desconocido o vacío → `IllegalArgumentException`.
- Mismo estado → no-op, no guarda y no publica evento.
- `ENTREGADO` y `CANCELADO` son finales.
- Cancelar solo se permite antes de `DESPACHADO`.

#### 18.6.2 Matriz estado → dominio

| Estado | Email | Cocina | Factura |
|---|---|---|---|
| `CREADO` | Sí | No | No |
| `ACEPTADO` | Sí | Sí | No |
| `EN_PREPARACION` | Sí | Sí | No |
| `DESPACHADO` | Sí | No | Sí |
| `ENTREGADO` | Sí | No | Sí |
| `CANCELADO` | Sí | Sí | Sí |

Implementación:

- `PedidoPolicy` concentra transiciones y dominios.
- `PedidoPersistidoListener` llama `pedidoPolicy.dominiosPara(event.estado())` y arma `PedidoComando` solo con esos dominios.
- `PedidoEventPublisher` publica únicamente los dominios indicados en el comando, en el exchange indicado (`cmd.direct` o `cmd.topic`).
- `LabPedidoController` acepta `estado` y `exchange` opcionales (por defecto `ACEPTADO` y `direct`) y aplica la misma matriz.
- Los consumidores distinguen la variante por `receivedRoutingKey`: estándar, alta prioridad, térmico o PDF.
- Cada consumidor valida el estado:
  - email: todos los estados del flujo.
  - cocina: `ACEPTADO`, `EN_PREPARACION`, `CANCELADO`.
  - factura: `DESPACHADO`, `ENTREGADO`, `CANCELADO`.
- Si un consumidor recibe un estado que no le corresponde, lanza `FalloPermanenteException` y el mensaje va a la DLQ sin reintentos.

> **Observación:** al crear un pedido (`CREADO`) ahora solo se publica email por `cmd.direct`. Los cambios de estado publican por `cmd.topic` las variantes de los dominios que correspondan. Ya no se envían los 3 dominios para cualquier evento.

> **Siguiente mejora de negocio:** la matriz indica qué dominio recibe cada estado, pero los adaptadores reales deben validar si existe un ticket o factura previa antes de anular. Para eso conviene agregar `estadoAnterior`, una clave de idempotencia y el estado de la notificación. Además, el `routingKey` variante debe conservarse en la trazabilidad de la notificación.

### 18.7 Garantías, idempotencia y límites

- **Garantía:** at-least-once. Un mensaje puede procesarse más de una vez si el ACK falla después de procesar, si el reintento se publica y el ACK del original falla, o por una caída antes del ACK.
- **Idempotencia:** el código registra `eventId`/`correlationId`, pero **no deduplica**. No hay tabla de idempotencia ni inbox. Si el mismo `eventId` se procesa dos veces, el efecto simulado se repite. Mejora futura: tabla de idempotencia o `upsert` con clave única.
- **Reintento inmediato:** no hay TTL ni backoff entre reintentos. Los 3 reintentos ocurren casi instantáneamente. Para producción se puede agregar un exchange delayed o una cola con TTL, pero eso agrega infraestructura.
- **DLQ sin consumidor automático:** no hay reproceso automático. La inspección es bajo demanda con `POST /api/lab/dlq/inspeccionar`; el inspector reencola en la misma DLQ sin vaciarla.
- **Modo direct/topic:** `CREADO` publica en `cmd.direct` con keys base; los cambios de estado publican en `cmd.topic` con variantes. Cada comando se publica una sola vez, en un solo exchange.
- **Patrones topic:** `email.#`, `kitchen.#`, `invoice.#` cubren las keys base y las variantes de 3 palabras.
- **Publicación no atómica:** los comandos del estado se publican en secuencia; si uno falla, los anteriores quedan encolados y los posteriores no se publican. No hay rollback de RabbitMQ. Un reintento del cliente puede duplicar los ya publicados.
- **Cambio de estado:** `cambiarEstado` publica el evento solo si el estado cambió; si se repite el mismo estado, guarda pero no notifica.
- **Flags de laboratorio:** `falloEmail`, `falloCocina`, `falloFactura` existen para forzar fallos en la demo. En producción se eliminan o se aíslan en un perfil de pruebas.
- **Seguridad:** `/api/lab/**` está `permitAll`; debe quitarse o protegerse antes de producción.
- **Configuración de listener en `orders`:** se copió la configuración de ACK/prefetch aunque `orders` no consume; es inocua y mantiene simetría, pero no es necesaria para el productor.

### 18.8 Configuración final

`ms-pedidos-360-notify/src/main/resources/application.properties`:

```properties
spring.rabbitmq.addresses=${RABBITMQ_ADDRESSES:localhost:5672,localhost:5673}
spring.rabbitmq.username=${RABBITMQ_DEFAULT_USER:pedidos360}
spring.rabbitmq.password=${RABBITMQ_DEFAULT_PASS:pedidos360}

spring.rabbitmq.listener.simple.acknowledge-mode=manual
spring.rabbitmq.listener.simple.prefetch=1
spring.rabbitmq.listener.simple.default-requeue-rejected=false
spring.rabbitmq.publisher-confirm-type=correlated
spring.rabbitmq.publisher-returns=true
spring.rabbitmq.template.mandatory=true

app.rabbit.max-intentos=3
app.rabbit.dominios=email,kitchen,invoice
app.rabbit.exchange.direct=cmd.direct
app.rabbit.exchange.topic=cmd.topic
app.rabbit.exchange.dlx=cmd.dead.dlx
app.rabbit.queues.email=q.cmd.email
app.rabbit.queues.kitchen=q.cmd.kitchen
app.rabbit.queues.invoice=q.cmd.invoice
app.rabbit.queues.email-dlq=q.cmd.email.dlq
app.rabbit.queues.kitchen-dlq=q.cmd.kitchen.dlq
app.rabbit.queues.invoice-dlq=q.cmd.invoice.dlq
app.rabbit.routing-keys.email=email.send
app.rabbit.routing-keys.kitchen=kitchen.ticket
app.rabbit.routing-keys.invoice=invoice.gen
app.rabbit.topic-patterns.email=email.#
app.rabbit.topic-patterns.kitchen=kitchen.#
app.rabbit.topic-patterns.invoice=invoice.#
```

`ms-pedidos-360-orders` agrega además:

```properties
server.port=${SERVER_PORT:8082}
spring.datasource.url=${ORACLE_JDBC_URL:jdbc:oracle:thin:@gcn5ldacwb15oubf_tp}
spring.datasource.hikari.data-source-properties.oracle.net.tns_admin=${ORACLE_TNS_ADMIN:/opt/pedidos360/wallet}
spring.datasource.hikari.data-source-properties.oracle.net.wallet_location=${ORACLE_WALLET_LOCATION:(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=/opt/pedidos360/wallet)))}
spring.datasource.username=${ORACLE_USERNAME}
spring.datasource.password=${ORACLE_PASSWORD}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.database-platform=org.hibernate.dialect.OracleDialect
```

Variables de entorno para ejecución local (archivo `infra/apps/.env`, ignorado por Git):

```dotenv
ORACLE_JDBC_URL=jdbc:oracle:thin:@gcn5ldacwb15oubf_tp
ORACLE_USERNAME=...
ORACLE_PASSWORD=...
ORACLE_TNS_ADMIN='/ruta/a/Wallet_pedidos360db'
ORACLE_WALLET_LOCATION='(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=/ruta/a/Wallet_pedidos360db)))'
ORACLE_WALLET_PATH='/ruta/a/Wallet_pedidos360db'
RABBITMQ_ADDRESSES=localhost:5672,localhost:5673
RABBITMQ_DEFAULT_USER=...
RABBITMQ_DEFAULT_PASS=...
```

Endpoint de inspección (sin configuración adicional; `notify` no tiene capa de seguridad en esta entrega):

```bash
curl -i -X POST http://localhost:8093/api/lab/dlq/inspeccionar
```

### 18.9 Validación realizada (2026-10-27)

| Prueba | Resultado |
|---|---|
| `orders` arranca con Oracle | `HikariPool-1 - Start completed`, `T4CConnection`; Tomcat 8082 |
| `notify` arranca | Tomcat 8093; 3 consumidores con `ack_required=true` |
| Topología direct+topic | `cmd.direct`, `cmd.topic` y `cmd.dead.dlx`; 6 colas; bindings topic `email.#`, `kitchen.#`, `invoice.#` |
| Direct + `CREADO` | `202`; `cmd.direct|email.send`; ACK |
| Topic + `ACEPTADO` | `202`; `cmd.topic|email.send.high` y `cmd.topic|kitchen.ticket.thermal`; ACK de ambos |
| Topic + `DESPACHADO` | `202`; `cmd.topic|email.send.high` y `cmd.topic|invoice.gen.pdf`; ACK de ambos |
| Direct + `ACEPTADO` | `202`; `cmd.direct|email.send` y `cmd.direct|kitchen.ticket`; ACK de ambos |
| Comportamiento de consumidores | Email: estándar/alta prioridad; cocina: estándar/térmico; factura: estándar/PDF |
| `POST /api/lab/pedidos/aceptar` con estado inválido | `400` con `{"error":"estado: Estado no válido"}` |
| Fallo topic `falloEmail=true` en `ACEPTADO` | Reintentos 1,2,3 con `routingKey=email.send.high`; DLQ con `x-death.exchange=cmd.topic`, `routing-keys=[email.send.high]`, `x-retry-count=3`; cocina ACK |
| JSON inválido | NACK conversión a DLQ; el listener **no** se detuvo |
| `POST /api/lab/dlq/inspeccionar` | `200` con conteos; log con `x-death`/`x-retry-count`; mensaje reencolado y DLQ sigue en `Ready: 1` |
| Admin directo sin token | `401` |
| Admin directo con JWT firmado sin rol | `403` |
| Admin directo con JWT firmado y rol admin | `200/201` |
| BFF `/api/admin/rabbit/**` sin token | `401` |
| BFF `/api/admin/rabbit/**` sin rol admin | `403` |
| BFF `/api/admin/rabbit/**` con rol admin | `200/201` |
| Máquina de estados | Tests: transiciones válidas, inválidas y mismo estado sin publicación |
| Tests unitarios | `admin-mq`: 15; `bff`: 5; `orders`: 21; `notify`: 1; todos en 0 fallos |
| Limpieza | Colas principales en 0; se dejó 1 mensaje en `q.cmd.email.dlq` como evidencia para la defensa |

> El test de JWT firmado usa una clave RSA local para validar firma y roles; la validación con un token real de Entra ID queda para la prueba en nube de EP4.

Evidencia de logs:

```text
Email simulado de confirmación de pedido [ESTÁNDAR]: pedidoId=10001 ... routingKey=email.send
Email simulado de pedido aceptado [ALTA PRIORIDAD]: pedidoId=10002 ... routingKey=email.send.high
Ticket de cocina generado [TÉRMICO]: pedidoId=10002 ... routingKey=kitchen.ticket.thermal
Email simulado de pedido despachado [ALTA PRIORIDAD]: pedidoId=10003 ... routingKey=email.send.high
Factura simulada borrador generado [PDF]: pedidoId=10003 ... routingKey=invoice.gen.pdf
Email simulado de pedido aceptado [ESTÁNDAR]: pedidoId=10004 ... routingKey=email.send
Ticket de cocina generado [ESTÁNDAR]: pedidoId=10004 ... routingKey=kitchen.ticket
Reintento publicado dominio=email ... pedidoId=10005 intento=1 routingKey=email.send.high
Reintento publicado dominio=email ... pedidoId=10005 intento=2 routingKey=email.send.high
Reintento publicado dominio=email ... pedidoId=10005 intento=3 routingKey=email.send.high
NACK -> DLQ queue=q.cmd.email.dlq exchange=cmd.dead.dlx ... pedidoId=10005 intento=3 routingKey=email.send.high ...
DLQ inspección ... x-retry-count=3 x-death=[{exchange=cmd.topic, routing-keys=[email.send.high], queue=q.cmd.email}] ...
```

### 18.10 Informe de cambios

#### `ms-pedidos-360-orders`

| Archivo | Cambio | Para qué |
|---|---|---|
| `common/dto/PedidoEventoDTO.java` | Nuevo | Contrato de mensaje, mismo FQCN que notify |
| `config/RabbitProperties.java` | Nuevo | Tipar `app.rabbit.*`; `direct`, `topic`, `dlx`, `routingKeys` y `topicPatterns` |
| `config/RabbitTopologyConfig.java` | Nuevo | `cmd.direct` + `cmd.topic` + `cmd.dead.dlx`, 3 colas, 3 DLQ, bindings exactos y `#`; `RabbitAdmin` |
| `config/RabbitPublisherConfig.java` | Nuevo | `JacksonJsonMessageConverter`; `mandatory` + callback de returns |
| `domain/EstadoPedido.java` | Nuevo | Estados válidos del flujo |
| `domain/PedidoPolicy.java` | Nuevo | Máquina de estados + matriz estado→dominio |
| `config/SecurityConfig.java` | Modificado | Permite `/api/lab/**` para la demo |
| `controller/OrderExceptionHandler.java` | Modificado | Devuelve 400 ante errores de validación de DTOs |
| `controller/LabPedidoController.java` | Nuevo | `POST /api/lab/pedidos/aceptar`, responde 202; `estado` y `exchange` opcionales; aplica matriz |
| `dto/PedidoComando.java` | Nuevo | Comando interno para el publisher; incluye `exchange` y `dominios` |
| `dto/PublicacionComando.java` | Nuevo | Detalle de cada publicación en la respuesta |
| `dto/ResultadoPublicacion.java` | Nuevo | `correlationId` + lista de comandos |
| `dto/LabPedidoRequest.java` | Nuevo | Request validado del endpoint de laboratorio; `estado` y `exchange` opcionales |
| `dto/LabPedidoResponse.java` | Nuevo | Response 202 |
| `messaging/PedidoEventPublisher.java` | Nuevo | Publica en `cmd.direct` o `cmd.topic`; keys base o variantes; espera confirms |
| `messaging/PedidoPersistidoEvent.java` | Nuevo | Evento de aplicación tras persistir |
| `messaging/PedidoPersistidoListener.java` | Nuevo | Publica en `AFTER_COMMIT`; `CREADO` → direct, cambios → topic; aplica matriz |
| `service/OrderService.java` | Modificado | Valida transiciones con `PedidoPolicy` y publica al crear/cambiar estado |
| `application.properties` | Modificado | Rabbit direct/topic/dlx, topic patterns `#`, Oracle, publisher confirms |
| Tests de contexto, `OrderServiceTest`, `PedidoPolicyTest`, `PedidoPersistidoListenerTest`, `PedidoEventPublisherTest` | Nuevos/Modificados | Cubren transiciones, matriz y routing keys direct/topic |

#### `ms-pedidos-360-notify`

| Archivo | Cambio | Para qué |
|---|---|---|
| `common/dto/PedidoEventoDTO.java` | Nuevo | Mismo contrato que orders |
| `config/RabbitProperties.java` | Modificado | `direct`, `topic`, `dlx` y `topicPatterns` |
| `config/RabbitTopologyConfig.java` | Modificado | Direct+topic; `TopicExchange` y bindings `email.#`, `kitchen.#`, `invoice.#` |
| `config/RabbitConsumerConfig.java` | Nuevo | Converter confiable + factory con `SimpleMessageConverter` |
| `controller/DlqInspectionController.java` | Nuevo | `POST /api/lab/dlq/inspeccionar` |
| `dlq/DlqInspector.java` | Nuevo | Lee, loguea y reencola DLQ sin vaciarlas |
| `consumer/EmailConsumer.java` | Nuevo | Consume `q.cmd.email`; estándar vs alta prioridad |
| `consumer/KitchenConsumer.java` | Nuevo | Consume `q.cmd.kitchen`; estándar vs térmico |
| `consumer/InvoiceConsumer.java` | Nuevo | Consume `q.cmd.invoice`; estándar vs PDF |
| `consumer/FalloPermanenteException.java` | Nuevo | Distingue error permanente de transitorio |
| `consumer/ProcesadorMensajes.java` | Nuevo/Modificado | Conversión manual, ACK/NACK, reintento, DLQ; pasa `routingKey` al consumidor y la loguea |
| `application.properties` | Modificado | ACK manual, prefetch 1, max-intentos 3, publisher confirms y topic patterns `#` |
| `MsPedidos360NotifyApplicationTests` | Modificado | Mock `RabbitAdmin`, listeners sin auto-startup |

#### `ms-pedidos-360-bff`

| Archivo | Cambio | Para qué |
|---|---|---|
| `client/RabbitAdminClient.java` | Nuevo | Proxy `RestClient` hacia admin-mq; reenvía el JWT |
| `controller/AdminRabbitController.java` | Nuevo | Expone `/api/admin/rabbit/**` con `ROLE_ADMINISTRADOR` |
| `config/security/SecurityConfig.java` | Modificado | `/api/admin/**` exige `ROLE_ADMINISTRADOR` |
| `application.properties` | Modificado | `services.admin-mq.url` |
| `AdminRabbitControllerSecurityTest` | Nuevo | 4 tests: 401/403/200/201 |
| `infra/apps/docker-compose.yml` | Modificado | `SERVICES_ADMIN_MQ_URL` y `depends_on` admin-mq |

#### `ms-pedidos-360-admin-mq`

| Archivo | Cambio | Para qué |
|---|---|---|
| `MsPedidos360AdminMqApplication.java` | Nuevo | Aplicación Spring Boot en `:8086` |
| `config/RabbitAdminConfig.java` | Nuevo | `RabbitAdmin` explícito |
| `config/SecurityConfig.java` | Nuevo | JWT + `ROLE_ADMINISTRADOR` |
| `controller/RabbitAdminController.java` | Nuevo | CRUD de colas, exchanges y bindings |
| `controller/AdminExceptionHandler.java` | Nuevo | 400/404/503/500 |
| `service/RabbitAdminService.java` | Nuevo | Encapsula `RabbitAdmin` |
| `dto/QueueRequest.java`, `ExchangeRequest.java`, `BindingRequest.java`, `QueueResponse.java` | Nuevos | Request/response validados |
| `exception/ResourceNotFoundException.java` | Nuevo | 404 de recursos |
| `application.properties` | Nuevo | Puerto 8086, RabbitMQ y JWT |
| Tests de contexto, servicio, seguridad y JWT firmado | Nuevos | 15 tests, 0 fallos |
| `infra/apps/docker-compose.yml` | Modificado | Servicio admin habilitado |

#### Infraestructura y documentación

| Archivo | Cambio | Para qué |
|---|---|---|
| `infra/apps/docker-compose.yml` | Nuevo | Compose de aplicaciones; `orders` y `notify` en la red de RabbitMQ |
| `infra/apps/.env.example` | Nuevo | Plantilla de variables (sin secretos) |
| `infra/apps/.env` | Creado local, ignorado | Credenciales reales de Oracle/RabbitMQ |
| `Dockerfile` | Nuevo | Build genérico por `ARG MODULE` |
| `.dockerignore` | Nuevo | Excluye `target`, `.git`, `.env`, docs, etc. |
| `GUIA_RABBITMQ_PEDIDOS360.md` | Actualizado | Estado final, comportamiento, comandos y este informe |
| `infra/rabbitmq` | Ya existente/commit `f78c328` | Clúster de 2 nodos y topología de notify |
| RabbitMQ | Topología operativa | Se restauró `cmd.topic` con bindings `email.#`, `kitchen.#`, `invoice.#`; se mantienen 6 colas |

### 18.11 Diagnóstico rápido

| Síntoma | Causa probable | Solución |
|---|---|---|
| `Could not resolve placeholder 'app.rabbit.queues.email'` | Un `application.properties` viejo en `target/test-classes` tapa el principal | `mvn clean test` o borrar `target/test-classes/application.properties` |
| `class ... is not in the trusted packages` | Converter sin paquete confiable | `new JacksonJsonMessageConverter("cl.duoc.pedidos360.common.dto")` |
| El listener se detiene con `Fatal message conversion error` | El adapter convirtió antes de invocar; error fatal de Spring AMQP 4.1 | Recibir `Message`, usar `SimpleMessageConverter` en el factory y convertir manualmente |
| Las variantes topic no llegan | Falta `cmd.topic`, el binding `#` o se publicó por `cmd.direct` | Verificar exchange y binding en Management UI; usar `exchange=topic` en laboratorio |
| No se ve la variante en consumidores | El log no incluye `receivedRoutingKey` | Revisar que `ProcesadorMensajes` pase la routingKey al consumidor |
| La inspección DLQ vacía la cola | Se usó un `receive` con ACK automático | Usar `DlqInspector`: `basicGet` con ACK manual y `basicPublish` antes del `basicAck` |
| `POST /api/lab/dlq/inspeccionar` devuelve 0 | No hay mensajes o las estadísticas aún no se refrescan | Verificar `Ready` en Management UI y reintentar; el inspector usa `queueDeclarePassive` |
| La DLQ no baja de 1 después del purge | Estadísticas de Management API con retraso | Esperar 3-5 s y refrescar |
| Se esperaba factura en `ACEPTADO` y no llega | La matriz de negocio no factura en ese estado | Usar `DESPACHADO` o `ENTREGADO` para probar factura |
| Se esperaba `email.send.high` y llega `email.send` | Se publicó en `direct` en vez de `topic` | Usar `exchange=topic` en laboratorio o un cambio de estado en el flujo real |
| Transición `CREADO -> ENTREGADO` rechazada | La máquina de estados exige pasar por `ACEPTADO` y `EN_PREPARACION` | Respetar la secuencia o ajustar `PedidoPolicy` si el negocio lo permite |
| `orders` falla al arrancar | Wallet/TNS o credenciales Oracle | Revisar `ORACLE_TNS_ADMIN`, `ORACLE_WALLET_LOCATION`, `ORACLE_USERNAME/PASSWORD` |
| `mvn test` intenta conectar a Rabbit | Contexto Spring levanta listeners/admin | Mockear `RabbitAdmin` y `auto-startup=false` en notify |
| Se reencola un mensaje de DLQ y vuelve a DLQ | Conserva `x-retry-count=3` | Quitar/reiniciar el header antes de reprocesar |
| `/api/lab/**` responde 401 en otro entorno | Seguridad no permite la ruta | Revisar `SecurityConfig`; en producción no debería estar abierta |

### 18.12 Resumen para la defensa

- **Topología:** 2 exchanges de comando (`cmd.direct` y `cmd.topic`) + 1 DLX (`cmd.dead.dlx`), 3 colas principales + 3 DLQ; sin colas de retry.
- **Bindings topic:** `email.#`, `kitchen.#`, `invoice.#`; cubren keys base y variantes.
- **Reglas de negocio:** `PedidoPolicy` define la máquina de estados y la matriz estado→dominio; `OrderService` valida las transiciones.
- **Productor:** al crear (`CREADO`) publica en `cmd.direct` con keys base; al cambiar estado publica en `cmd.topic` con variantes. Solo publica los dominios de la matriz y espera publisher confirms.
- **Consumidores:** `notify` separa email/kitchen/invoice, valida el estado y distingue la variante por `routingKey`: estándar/alta prioridad, estándar/térmico, estándar/PDF.
- **Reintentos:** republicación al mismo exchange/routing key con `x-retry-count+1`; 3 reintentos; luego DLQ.
- **Errores permanentes:** NACK directo a DLQ, sin reintentos.
- **JSON inválido:** NACK directo a DLQ, sin detener el listener (conversión manual).
- **After-commit:** el flujo real publica al crear (direct) y al cambiar estado (topic), solo después de confirmar Oracle; el endpoint lab permite elegir `direct`/`topic`.
- **DLQ:** `DlqInspector` + `POST /api/lab/dlq/inspeccionar` leen, loguean `x-death`/`x-retry-count` y reencolan en la misma DLQ sin vaciarla. En fallos topic, `x-death` conserva `exchange=cmd.topic` y la routing key variante.
- **Admin:** `ms-pedidos-360-admin-mq` en `:8086` expone CRUD de colas, exchanges y bindings con `RabbitAdminService`, validación, 400/404/503 y `ROLE_ADMINISTRADOR`. El BFF lo expone en `/api/admin/rabbit/**` y reenvía el JWT.
- **Limitaciones:** at-least-once sin idempotencia, reintento inmediato sin backoff, sin outbox, DLQ sin reproceso automático (solo inspección manual), `/api/lab/**` abierto para la demo.
