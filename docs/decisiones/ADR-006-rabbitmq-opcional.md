# ADR-006: RabbitMQ para publicación de eventos

## Estado

Implementado

## Contexto

El PDF ofrece un punto opcional: publicar un evento cuando una solicitud es
autorizada para que otros sistemas reaccionen. El alcance obligatorio (RF01–RF07)
no depende de mensajería, pero se decidió implementarlo como valor agregado.

## Opciones Consideradas

1. **Implementar RabbitMQ ahora** con publicación directa y un exchange/routing key
   básico. Demuestra integración, pero agrega infraestructura, manejo de fallos,
   reintentos y pruebas de contrato fuera del alcance obligatorio.
2. **Implementar un outbox transaccional** para publicar eventos de forma confiable.
   Es la opción correcta para producción, pero exige tabla outbox, relay y
   idempotencia del consumidor.
3. **Documentar el descarte** y dejar el diseño preparado para una fase posterior.

## Decisión

Se elige **Opción 1: implementar RabbitMQ** con publicación directa de eventos
después del commit exitoso de la transacción.

## Justificación Detallada

La implementación de RabbitMQ agrega valor demostrativo al proyecto. Se publican
dos tipos de eventos:
- `PRE_APPROVED_REQUEST_APPROVED`: cuando una solicitud es aprobada
- `PRE_APPROVED_REQUEST_REJECTED`: cuando una solicitud es rechazada

Los eventos NO se publican para:
- Replays (solicitudes idempotentes)
- Conflictos (referencia duplicada con datos diferentes)

Esto asegura que los eventos representen cambios reales en el estado del sistema.

### Características de la implementación:

1. **Publicación después del commit**: Los eventos se publican solo después de que
   la transacción se haya confirmado exitosamente.
2. **Tolerancia a fallos**: Si RabbitMQ no está disponible, el evento se registra
   en logs pero no falla la operación principal (fallback a NoOp).
3. **Exchange tipo Topic**: Permite routing flexible por tipo de evento.
4. **Serialización JSON**: Los eventos se serializan como JSON para interoperabilidad.

### Configuración:

- Exchange: `preapproved-requests-exchange` (topic)
- Queue: `preapproved-requests-queue`
- Routing keys:
  - `preapproved.request.approved`
  - `preapproved.request.rejected`

## Consecuencias

### Positivas

- Demuestra integración con sistemas de mensajería.
- Permite notificar a otros sistemas sobre cambios en el estado de solicitudes.
- Arquitectura desacoplada mediante eventos.
- Los consumidores pueden procesar eventos de forma asíncrona.

### Negativas

- Requiere infraestructura adicional (RabbitMQ).
- Sin outbox pattern, hay riesgo de pérdida de eventos si falla la publicación.
- Los consumidores deben ser idempotentes.

### Riesgos

- **Pérdida de eventos**: Si RabbitMQ falla después del commit pero antes de la
  publicación, el evento se pierde. Mitigación futura: implementar outbox pattern.
- **Duplicación de eventos**: Si la publicación falla parcialmente, podría haber
  duplicación. Mitigación: usar eventId único en cada evento.

## Implementación

### Módulos creados:

- `back/infrastructure/driven-adapters/rabbitmq-sender/`: Adapter para RabbitMQ
- `back/domain/model/.../event/`: Definición de eventos (EventType, PreApprovedRequestEvent)
- `back/domain/model/.../gateways/PreApprovedRequestEventPublisher.java`: Gateway

### Flujo:

1. `ProcessPreApprovedRequestUseCase` procesa la solicitud
2. Si el resultado es `CREATED` (no replay ni conflict), publica el evento
3. El evento se publica a RabbitMQ con routing key según el tipo
4. Si falla, se registra en logs pero no falla la operación

### Configuración:

- Variable `RABBITMQ_ENABLED` para habilitar/deshabilitar en diferentes ambientes
- En tests se usa `NoOpPreApprovedRequestEventPublisherAdapter`

## Referencias

- `docs/PLAN-IMPLEMENTACION.md` (punto opcional)
- PDF de la prueba técnica, sección 9
- Commit: `9c051e8` - Implementación de RabbitMQ
