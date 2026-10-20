# BancoIAS — uso de cupos preaprobados

Backend reactivo para procesar solicitudes de uso de cupos preaprobados, conservar el
resultado de cada solicitud y consultar el historial reciente. El foco está en la
consistencia del cupo bajo concurrencia y en la idempotencia por referencia.

## Stack

- Java 21
- Spring Boot 4.1.1 / WebFlux
- R2DBC PostgreSQL
- Gradle 9.8.0 (wrapper)
- Scaffold Bancolombia Clean Architecture 4.7.4
- PostgreSQL 16
- Springdoc/OpenAPI, Actuator y ArchUnit
- JUnit 5, Reactor Test, Mockito y Testcontainers

## Estructura

```text
bancoias/
├── back/
│   ├── applications/app-service
│   ├── domain/model
│   ├── domain/usecase
│   ├── infrastructure/driven-adapters/r2dbc-postgresql
│   ├── infrastructure/entry-points/reactive-web
│   └── deployment/Dockerfile
├── docs/PLAN-IMPLEMENTACION.md
├── scripts/smoke-test.sh
├── docker-compose.yml
└── Makefile
```

El frontend Angular está pendiente; por ahora el alcance implementado es el backend.

## Ejecución

Requisitos: Docker y Docker Compose.

```bash
make up        # construye y levanta db + back
make logs      # sigue los logs
make down      # detiene sin borrar datos
make reset     # borra el volumen y vuelve a sembrar
```

También se puede usar directamente:

```bash
docker compose up --build
docker compose down -v
```

La API queda en `http://localhost:8080` y Swagger en
`http://localhost:8080/swagger-ui.html`.

## API

```text
POST /api/v1/preapproved-requests
GET  /api/v1/preapproved-requests/{requestReference}
GET  /api/v1/preapproved-requests?limit=20
GET  /api/v1/preapproved/{preApprovedId}
GET  /actuator/health
```

Ejemplo:

```bash
curl -X POST http://localhost:8080/api/v1/preapproved-requests \
  -H 'Content-Type: application/json' \
  -d '{
    "requestReference": "REF-001",
    "preApprovedId": "PRA-1001",
    "customerId": "USR-10",
    "amount": 600000
  }'
```

Respuesta:

```json
{
  "requestReference": "REF-001",
  "preApprovedId": "PRA-1001",
  "customerId": "USR-10",
  "amount": 600000.00,
  "status": "APPROVED",
  "reason": null,
  "processedAt": "2026-10-20T05:00:00Z",
  "remainingAmount": 400000.00,
  "replayed": false
}
```

Códigos:

- `201`: solicitud nueva procesada (aprobada o rechazada).
- `200`: replay idempotente con el resultado original.
- `409`: misma referencia con payload diferente.
- `400`: payload inválido o malformado; no se persiste.
- `404`: consulta no encontrada.
- `500`: error inesperado.

## Consistencia e idempotencia

- El descuento y la verificación de cupo se hacen en una sola sentencia:

```sql
UPDATE preapproved
SET available_amount = available_amount - :amount
WHERE pre_approved_id = :preApprovedId
  AND customer_id = :customerId
  AND status = 'ACTIVE'
  AND :amount > 0
  AND available_amount >= :amount
RETURNING available_amount;
```

- `request_reference` es único. El hash SHA-256 del payload canónico distingue un
  replay legítimo de un conflicto.
- Si dos solicitudes con la misma referencia compiten, la restricción única decide;
  la perdedora revierte la transacción y devuelve el resultado original o un `409`.
- Una solicitud rechazada se persiste con su motivo y no descuenta cupo.
- Los montos menores o iguales a cero se persisten como `REJECTED` con
  `INVALID_AMOUNT`. Un payload malformado responde `400` y no se persiste.

## Datos semilla

| preApprovedId | customerId | status | availableAmount |
|---|---|---|---|
| PRA-1001 | USR-10 | ACTIVE | 1.000.000 |
| PRA-1002 | USR-10 | BLOCKED | 800.000 |
| PRA-2001 | USR-20 | ACTIVE | 2.000.000 |

## Pruebas

```bash
make test
```

El comando ejecuta todo el backend en un contenedor con JDK 21 y Docker disponible para
Testcontainers. Incluye:

- Unitarias de política, hash y casos de uso.
- WebTestClient para los endpoints.
- Integración R2DBC con PostgreSQL vía Testcontainers.
- Idempotencia, conflictos, rechazos y concurrencia.
- ArchUnit para reglas de arquitectura.

## Smoke test

Con el backend arriba y la base recién sembrada:

```bash
make smoke
```

El script valida aprobación, replay, rechazo por cupo, rechazo por monto, conflicto de
referencia y concurrencia (5 aprobadas y 1 rechazada sobre `PRA-2001`).

## Pendientes

- Frontend Angular (RF07).
- ADRs y documentación de arquitectura.
- RabbitMQ opcional.
