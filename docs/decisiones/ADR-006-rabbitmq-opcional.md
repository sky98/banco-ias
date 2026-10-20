# ADR-006: RabbitMQ fuera del alcance obligatorio

## Estado

Aceptado

## Contexto

El PDF ofrece un punto opcional: publicar un evento cuando una solicitud es
autorizada para que otros sistemas reaccionen. El alcance obligatorio (RF01–RF07)
no depende de mensajería y el esfuerzo estimado es limitado.

## Opciones Consideradas

1. **Implementar RabbitMQ ahora** con publicación directa y un exchange/routing key
   básico. Demuestra integración, pero agrega infraestructura, manejo de fallos,
   reintentos y pruebas de contrato fuera del alcance obligatorio.
2. **Implementar un outbox transaccional** para publicar eventos de forma confiable.
   Es la opción correcta para producción, pero exige tabla outbox, relay y
   idempotencia del consumidor.
3. **Documentar el descarte** y dejar el diseño preparado para una fase posterior.

## Decisión

Se elige **Opción 3: documentar el descarte** y priorizar el alcance obligatorio.

## Justificación Detallada

El punto opcional no afecta la evaluación del alcance obligatorio. Publicar un
evento sin outbox o sin política de reintentos daría una falsa sensación de
confiabilidad. La decisión correcta para una futura iteración es un patrón outbox
transaccional con un relay y consumidores idempotentes, no una publicación directa
después del commit.

## Consecuencias

### Positivas

- Se concentra el esfuerzo en RF01–RF07 y en la calidad de la entrega.
- No se introduce infraestructura sin pruebas de fallo.

### Negativas

- No hay demostración de mensajería en esta entrega.

### Riesgos

- Si se requiere el opcional, habrá que diseñar outbox y contratos. Mitigación:
  el evento de negocio queda acotado a la solicitud autorizada y el adaptador de
  persistencia ya es el punto natural para un outbox futuro.

## Referencias

- `docs/PLAN-IMPLEMENTACION.md` (punto opcional)
- PDF de la prueba técnica, sección 9
