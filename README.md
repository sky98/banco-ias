# BancoIAS — uso de cupos preaprobados

Backend reactivo para procesar solicitudes de uso de cupos preaprobados, conservar el
resultado de cada solicitud y consultar el historial reciente. El foco está en la
consistencia del cupo bajo concurrencia y en la idempotencia por referencia.

## Stack

- Backend: Java 21, Spring Boot 4.1.1 / WebFlux, R2DBC PostgreSQL, Gradle 9.8.0 (wrapper),
  scaffold Bancolombia Clean Architecture 4.7.4, Springdoc/OpenAPI, Actuator y ArchUnit
- Frontend: Angular 22 (standalone, signals, reactive forms), Vitest, Node 24 en build
- Infraestructura: PostgreSQL 16, Nginx (sirve la SPA y hace proxy de `/api`), Docker Compose
- Pruebas: JUnit 5, Reactor Test, Mockito, Testcontainers y Vitest

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
├── front/                      Angular 22 + Nginx (Dockerfile, nginx.conf)
├── docs/
│   ├── arquitectura.md         vista de contexto, contenedores, capas, datos y secuencias
│   ├── decisiones/             ADR-001 a ADR-008
│   ├── api/openapi.yaml        contrato OpenAPI estático
│   ├── entrega.md              trazabilidad RF01–RF07
│   ├── uso-ia.md               registro del uso de IA
│   ├── PLAN-IMPLEMENTACION.md
│   └── REVISION-BACKEND.md
├── scripts/smoke-test.sh
├── docker-compose.yml
└── Makefile
```

## Ejecución

Requisitos: Docker y Docker Compose.

```bash
make up        # construye y levanta db, back y front
make logs      # sigue los logs
make down      # detiene sin borrar datos
make reset     # borra el volumen y vuelve a sembrar
```

También se puede usar directamente:

```bash
docker compose up --build
docker compose down -v
```

Puertos:

- Interfaz Angular: `http://localhost:8081`
- API: `http://localhost:8080`; Swagger en `http://localhost:8080/swagger-ui.html`
- PostgreSQL: `localhost:5432`

La interfaz permite registrar una solicitud (valores demo `USR-10`, `PRA-1001`,
`REF-001`, `600000`), ver el resultado (estado, motivo, cupo restante, replay o
resultado original en un `409`) y revisar las solicitudes recientes.

Para Docker Compose, copiar `.env.example` a `.env` y ajustar los valores. Variables
principales:

| Variable | Default | Uso |
|---|---|---|
| `FRONT_PORT` | `8081` | Puerto publicado de la interfaz |
| `SERVER_PORT` | `8080` | Puerto HTTP |
| `SPRING_R2DBC_URL` | `r2dbc:postgresql://localhost:5432/bancoias` | Conexión R2DBC |
| `SPRING_R2DBC_USERNAME` | `bancoias` | Usuario de base de datos |
| `SPRING_R2DBC_PASSWORD` | `bancoias` | Contraseña de base de datos |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200,http://localhost:8080,http://localhost:8081` | Orígenes permitidos, separados por coma |
| `SQL_INIT_MODE` | `always` | Modo de inicialización SQL |
| `SQL_INIT_SCHEMA_LOCATIONS` | `classpath:schema.sql` | Script de esquema |
| `SQL_INIT_DATA_LOCATIONS` | `classpath:data.sql` | Script de datos semilla |

## API

```text
POST /api/v1/preapproved-requests
GET  /api/v1/preapproved-requests/{requestReference}
GET  /api/v1/preapproved-requests?limit=20
GET  /api/v1/preapproved/{preApprovedId}
GET  /actuator/health
```

El contrato estático está en `docs/api/openapi.yaml`; se regenera con el backend arriba:

```bash
curl -fsS http://localhost:8080/v3/api-docs.yaml -o docs/api/openapi.yaml
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
- `400`: payload inválido o malformado (campos vacíos, longitudes 80/40/40, más de 2
  decimales o monto fuera de rango); no se persiste.
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
make test         # backend + frontend
make test-back    # solo backend
make test-front   # solo frontend
```

Backend (50 tests), en un contenedor con JDK 21 y Docker disponible para Testcontainers:

- Unitarias de política, hash y casos de uso.
- WebTestClient para los endpoints y validaciones.
- Integración R2DBC con PostgreSQL vía Testcontainers.
- Idempotencia, conflictos, rechazos, concurrencia y contrato OpenAPI.
- ArchUnit para reglas de arquitectura.

Frontend (26 tests), en un contenedor Node 24 con Vitest:

- Servicio HTTP con `HttpTestingController`.
- Página: defaults, envío exitoso, replay, conflicto `409`, validación `400`, tabla y
  formulario inválido.

## Smoke test

Con el backend arriba y la base recién sembrada:

```bash
make smoke
```

El script valida aprobación, replay, rechazo por cupo, rechazo por monto, referencia
demasiado larga (`400`), monto con más de dos decimales (`400`), conflicto de referencia
(`409`) y concurrencia (5 aprobadas y 1 rechazada sobre `PRA-2001`).

## Limitaciones y pendientes

- Sin autenticación ni administración de clientes o preaprobados (fuera del alcance del PDF).
- RabbitMQ no implementado (opcional); decisión documentada en `docs/decisiones/ADR-006-rabbitmq-opcional.md`.
- El esquema se inicializa con `spring.sql.init`; para producción se recomienda Flyway o
  Liquibase (H-05 en `docs/REVISION-BACKEND.md`).
- El smoke test asume la base recién sembrada: ejecutar `make reset` antes de repetirlo.
- Los montos aceptan máximo dos decimales; `100.005` responde `400` (ADR-007).
