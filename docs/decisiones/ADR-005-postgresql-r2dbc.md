# ADR-005: PostgreSQL con R2DBC para persistencia reactiva

## Estado

Aceptado

## Contexto

El backend es WebFlux y necesita persistir cupos y solicitudes sin bloquear el event
loop. La operación crítica requiere una transacción y una actualización condicional
atómica.

## Opciones Consideradas

1. **JPA/Hibernate sobre JDBC**. Maduro y productivo, pero bloqueante por diseño;
   requeriría aislar las llamadas en otro scheduler.
2. **R2DBC con PostgreSQL**. Acceso no bloqueante, control explícito del SQL y
   soporte de transacciones reactivas.
3. **MongoDB**. Flexible, pero el problema exige consistencia transaccional fuerte
   sobre un saldo y una restricción única.

## Decisión

Se elige **Opción 2: PostgreSQL 16 + R2DBC**, con `DatabaseClient` y
`TransactionalOperator`.

## Justificación Detallada

PostgreSQL ofrece la transacción local, el `UPDATE ... RETURNING` y la restricción
`UNIQUE` que sostienen la concurrencia y la idempotencia. R2DBC mantiene el flujo
reactivo sin bloquear el event loop. Se usa SQL explícito para la operación crítica y
consultas simples para las lecturas; no se necesita un ORM para este alcance.

## Consecuencias

### Positivas

- Flujo completamente no bloqueante.
- Control preciso de la sentencia atómica y de los índices.
- Testcontainers permite probar contra un PostgreSQL real.

### Negativas

- Más SQL manual y mapeo explícito que con JPA.
- Sin carga perezosa ni gestión automática de entidades.

### Riesgos

- Errores de mapeo o de transacción. Mitigación: pruebas de integración con
  PostgreSQL real, incluyendo concurrencia y rollback.

## Referencias

- `back/infrastructure/driven-adapters/r2dbc-postgresql`
- `back/applications/app-service/src/main/resources/schema.sql`
- `PreApprovedUsageIntegrationTest` con Testcontainers
