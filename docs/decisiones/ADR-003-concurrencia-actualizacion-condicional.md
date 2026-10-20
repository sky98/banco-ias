# ADR-003: Concurrencia con actualización condicional atómica

## Estado

Aceptado

## Contexto

Dos solicitudes pueden llegar casi al mismo tiempo sobre el mismo preaprobado. Si
ambas leen el cupo antes de descontar, las dos podrían aprobarse y superar el saldo.
La solución debe preservar el cupo sin introducir bloqueos bloqueantes en WebFlux.

## Opciones Consideradas

1. **Bloqueo pesimista** (`SELECT ... FOR UPDATE`) dentro de una transacción. Es
   correcto, pero mantiene una conexión y un lock durante más tiempo y requiere
   adaptar el flujo reactivo.
2. **Bloqueo optimista** con versión en la fila. Correcto, pero agrega una columna,
   reintentos y manejo de conflictos de versión.
3. **Actualización condicional atómica**: descontar solo si el saldo alcanza, en una
   sola sentencia SQL con `RETURNING`.

## Decisión

Se elige **Opción 3**, con una sentencia `UPDATE` condicional y `RETURNING`.

## Justificación Detallada

La condición `available_amount >= :amount` y el descuento ocurren en la misma
sentencia, por lo que PostgreSQL serializa la escritura y ninguna solicitud puede
dejar el cupo negativo. La transacción se consume con `.single()` para esperar el
commit real; con `.next()` se cancelaba antes del commit. El caso de cupo
insuficiente se clasifica después con una consulta adicional, solo cuando la
actualización no afectó filas.

## Consecuencias

### Positivas

- Sin locks explícitos ni reintentos de versión.
- El cupo nunca queda negativo.
- Una sola sentencia para la operación crítica.

### Negativas

- La clasificación del rechazo requiere una consulta adicional.
- La lógica de concurrencia vive en SQL y debe documentarse bien.

### Riesgos

- Diferencias de comportamiento entre motores. Mitigación: PostgreSQL es el motor
  definido y la prueba de concurrencia se ejecuta con Testcontainers.

## Referencias

- `back/infrastructure/driven-adapters/r2dbc-postgresql/.../PreApprovedUsageR2dbcAdapter.java`
- `PreApprovedUsageIntegrationTest.concurrentSameReferenceIsAppliedOnce`
- `scripts/smoke-test.sh` (6 solicitudes concurrentes)
