# BancoIAS — backend

Servicio WebFlux para procesar usos de cupos preaprobados con persistencia R2DBC
PostgreSQL, actualización condicional atómica e idempotencia por `requestReference`.

La documentación general, ejecución con Docker, API y pruebas están en el
[README principal](../README.md).

## Módulos

- `domain/model`: `PreApproved`, `PreApprovedUsage`, política de rechazo, hash del
  payload y puertos.
- `domain/usecase`: procesamiento, consulta por referencia, listado reciente y consulta
  de cupo.
- `infrastructure/driven-adapters/r2dbc-postgresql`: entidades, repositorios,
  `DatabaseClient`, transacción reactiva y adaptadores.
- `infrastructure/entry-points/reactive-web`: handlers funcionales, router, DTOs,
  mapper, manejo de errores y CORS.
- `applications/app-service`: arranque, configuración, `schema.sql` y `data.sql`.

## Comandos

Desde esta carpeta, con Java 21 y Gradle 9.8.0:

```bash
./gradlew test
./gradlew :app-service:bootJar
```

En este equipo el host no tiene JDK 21, por lo que los comandos se ejecutan dentro de
`gradle:9.8.0-jdk21`. El `Makefile` de la raíz ya encapsula ese uso.

## Decisiones relevantes

- El modelo se llama `PreApprovedUsage` y la tabla `preapproved_usage` porque el
  scaffold prohíbe el sufijo `Request` en clases de dominio. La API conserva
  `/api/v1/preapproved-requests`.
- El cupo se descuenta con un `UPDATE ... WHERE available_amount >= :amount RETURNING`,
  sin `SELECT ... FOR UPDATE`.
- La idempotencia se garantiza con `UNIQUE(request_reference)` y el hash SHA-256 del
  payload canónico.
- Los rechazos se persisten con un motivo del catálogo
  `INVALID_AMOUNT`, `PREAPPROVED_NOT_FOUND`, `CUSTOMER_MISMATCH`,
  `PREAPPROVED_BLOCKED`, `INSUFFICIENT_QUOTA`.
