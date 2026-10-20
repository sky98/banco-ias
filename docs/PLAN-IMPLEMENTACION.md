# Plan de implementación — Prueba técnica BancoIAS

> Estado: **backend implementado, corregido y con pruebas en verde**. Hallazgos H-01 a H-06
> resueltos o mitigados. Frontend Angular, ADRs y documentación de arquitectura pendientes.

## Contexto

La prueba técnica solicita una solución Full Stack mínima y funcional para usar cupos preaprobados, conservar el resultado de cada solicitud y consultar las solicitudes procesadas, manteniendo consistencia bajo solicitudes repetidas o concurrentes.

Restricciones obligatorias:

- Backend: Java + Spring Boot WebFlux.
- Frontend: Angular.
- Persistencia de solicitudes procesadas.
- Pruebas automatizadas.
- Registro de decisiones técnicas y uso de IA.
- Historial de commits.
- RabbitMQ es opcional.

## El problema en simple

El PDF no pide un CRUD de solicitudes. Pide un pequeño motor de consumo de cupos preaprobados que mantenga la consistencia del saldo.

Ejemplo con los datos semilla:

1. `USR-10` tiene el preaprobado `PRA-1001` con cupo de 1.000.000 y estado `ACTIVE`.
2. Llega `REF-001` solicitando 600.000.
3. El sistema valida monto, existencia, cliente, estado y cupo.
4. Si todo es correcto, aprueba, guarda la solicitud con fecha y hora, y deja el cupo en 400.000.
5. Si la solicitud pidiera 1.200.000, se rechaza y se guarda el rechazo, pero el cupo sigue en 1.000.000.

Los dos casos que hacen difícil el ejercicio son:

### Concurrencia

Con cupo de 1.000.000 llegan dos solicitudes simultáneas de 600.000. Si ambas leen el cupo antes de descontar, ambas se aprueban y se usan 1.200.000. La solución debe serializar o hacer atómica la operación para que solo una sea aprobada y la otra sea rechazada por cupo insuficiente.

### Idempotencia

El cliente envía `REF-001`, el servidor la procesa, pero la respuesta se pierde y el cliente reintenta. El sistema no debe descontar dos veces ni crear otra solicitud: debe devolver el resultado original. Si la misma referencia llega con datos diferentes, no debe sobrescribir la original; debe responder de forma controlada conservando el primer resultado.

En resumen: el núcleo del problema es **consistencia de cupo bajo concurrencia + idempotencia por referencia + persistencia y consulta del resultado**.

## Decisiones ya acordadas

| Tema | Decisión |
|---|---|
| Tipo de solución | Scaffold Bancolombia Clean Architecture aplicado a un dominio nuevo, no réplica de CrediYa |
| Repositorio | Un solo repo con `back/` y `front/` |
| Backend | Un único microservicio desplegable |
| Scaffold | `co.com.bancolombia.cleanArchitecture` versión `4.7.4` (serie 4 estable; misma base moderna que la 5) |
| Java | Java 21, compilado y ejecutado vía Docker (el host solo tiene JDK 17) |
| Spring Boot | `4.1.1` |
| Gradle | `9.8.0` (wrapper generado por el scaffold 4.7.4) |
| Base de datos | PostgreSQL con Docker |
| RabbitMQ | Solo si sobra tiempo, al final; en caso contrario se documenta el descarte |
| Persistencia reactiva | R2DBC PostgreSQL |
| Concurrencia | Actualización condicional atómica sobre el cupo |
| Idempotencia | `requestReference` único + hash del payload |
| Seguridad | Sin autenticación, según el alcance del PDF |

## Objetivo funcional

Implementar RF01–RF07:

- RF01: registrar una solicitud de uso de preaprobado con fecha y hora de procesamiento.
- RF02: validar monto mayor a cero, preaprobado existente, cliente correcto, estado habilitado y cupo suficiente.
- RF03: conservar el resultado de cada solicitud; una rechazada no descuenta cupo.
- RF04: soportar solicitudes simultáneas sin superar el cupo disponible.
- RF05: manejar referencias repetidas de forma idempotente y controlada.
- RF06: consultar por `requestReference` y listar solicitudes recientes.
- RF07: interfaz Angular funcional integrada al backend.

## Estructura de entrega

```text
bancoias/
├── README.md
├── Makefile
├── docker-compose.yml
├── .env.example
├── .gitignore
├── docs/
│   ├── arquitectura.md
│   ├── api/openapi.yaml
│   ├── decisiones/
│   │   ├── 0001-backend-unico.md
│   │   ├── 0002-scaffold-clean-architecture.md
│   │   ├── 0003-concurrencia-actualizacion-condicional.md
│   │   ├── 0004-idempotencia-request-reference.md
│   │   ├── 0005-postgresql-r2dbc.md
│   │   └── 0006-rabbitmq-opcional.md
│   └── uso-ia.md
├── back/
│   ├── applications/app-service
│   ├── domain/model
│   ├── domain/usecase
│   ├── infrastructure/driven-adapters/r2dbc-postgresql
│   ├── infrastructure/entry-points/reactive-web
│   └── deployment/Dockerfile
├── front/
└── scripts/smoke-test.sh
```

Un único repositorio Git, sin submódulos ni repos anidados.

## Backend

Base: scaffold Bancolombia `4.7.4`, Java 21, Spring Boot `4.1.1`, Gradle `9.8.0`, WebFlux, R2DBC PostgreSQL, Lombok, Springdoc, Actuator y ArchUnit.

La generación se hará dentro de un contenedor con Gradle 9.8.0 y Java 21 usando las tareas del plugin (`ca` para crear el proyecto y `gep` para el entry point WebFlux). Se mantienen las convenciones de la serie 4 (`build.gradle`, `main.gradle`, módulos por capa).

Módulos:

- `model`: entidades, enums, puertos, excepciones y política de negocio.
- `usecase`: casos de uso de procesamiento, consulta y listado.
- `r2dbc-postgresql`: entidades, repositorios, adaptador transaccional y configuración R2DBC.
- `reactive-web`: routers, handlers, DTOs, mapper, validación, CORS, OpenAPI y manejo global de errores.
- `app-service`: `MainApplication`, `UseCasesConfig`, `ObjectMapperConfig`, `application.yaml`, `schema.sql`, `data.sql` y el arranque Spring Boot. El `TransactionalOperator` y el `DatabaseClient` se configuran en el adaptador R2DBC.

No se incluyen JWT, SQS, SNS, DynamoDB ni Lambdas.

### Modelo de dominio

```text
PreApproved
- preApprovedId: String
- customerId: String
- status: ACTIVE | BLOCKED
- availableAmount: BigDecimal

PreApprovedUsage
- id: Long
- requestReference: String (único)
- preApprovedId: String
- customerId: String
- amount: BigDecimal
- status: APPROVED | REJECTED
- reason: RejectionReason | null
- payloadHash: String
- processedAt: Instant
- remainingAmount: BigDecimal
```

> El modelo se llama `PreApprovedUsage` y la tabla `preapproved_usage` porque el
> scaffold prohíbe el sufijo `Request` en clases de dominio. La API conserva
> `/api/v1/preapproved-requests`.

Catálogo de motivos de rechazo:

- `INVALID_AMOUNT`: monto menor o igual a cero.
- `PREAPPROVED_NOT_FOUND`: el preaprobado no existe.
- `CUSTOMER_MISMATCH`: el preaprobado no pertenece al cliente informado.
- `PREAPPROVED_BLOCKED`: el preaprobado no está habilitado.
- `INSUFFICIENT_QUOTA`: el monto supera el cupo disponible.

El PDF pide conservar “una razón general que permita comprender el resultado”. No exige un catálogo específico ni un texto exacto, pero sí exige que el motivo quede persistido cuando la solicitud sea rechazada. Se guardará el motivo y se devolverá en la API y en la UI. Para solicitudes aprobadas, `reason` será `null`.

### Base de datos

```sql
preapproved (
  pre_approved_id VARCHAR PRIMARY KEY,
  customer_id VARCHAR NOT NULL,
  status VARCHAR NOT NULL,
  available_amount NUMERIC(19,2) NOT NULL
)

preapproved_usage (
  id BIGSERIAL PRIMARY KEY,
  request_reference VARCHAR UNIQUE NOT NULL,
  pre_approved_id VARCHAR NOT NULL,
  customer_id VARCHAR NOT NULL,
  amount NUMERIC(19,2) NOT NULL,
  status VARCHAR NOT NULL,
  reason VARCHAR,
  payload_hash VARCHAR(64) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL,
  remaining_amount NUMERIC(19,2)
)
```

Semilla idempotente con `ON CONFLICT DO NOTHING`:

| preApprovedId | customerId | status | availableAmount |
|---|---|---|---|
| PRA-1001 | USR-10 | ACTIVE | 1.000.000 |
| PRA-1002 | USR-10 | BLOCKED | 800.000 |
| PRA-2001 | USR-20 | ACTIVE | 2.000.000 |

## Estrategia de concurrencia e idempotencia

Se usará **actualización condicional atómica**. La base de datos hace en una sola sentencia el “verificar cupo y descontar”, sin necesidad de un `SELECT ... FOR UPDATE` explícito.

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

Flujo completo:

```text
1. Validar forma del payload: campos obligatorios y tipos.
   Si está malformado -> 400 y no se persiste.
2. Calcular hash SHA-256 canónico del contenido.
3. Buscar request_reference:
   - existe y mismo hash    -> devolver resultado original (200, replayed).
   - existe y distinto hash -> 409 Conflict + resultado original.
4. Si no existe, abrir transacción reactiva.
5. Si amount <= 0:
   - insertar REJECTED con INVALID_AMOUNT;
   - commit y responder 201.
6. Ejecutar el UPDATE condicional.
7. Si el UPDATE devuelve una fila:
   - insertar APPROVED con remainingAmount;
   - commit y responder 201.
8. Si el UPDATE no devuelve filas:
   - consultar el preaprobado para clasificar el motivo:
     PREAPPROVED_NOT_FOUND, CUSTOMER_MISMATCH,
     PREAPPROVED_BLOCKED o INSUFFICIENT_QUOTA;
   - insertar REJECTED con el motivo y el cupo vigente;
   - commit y responder 201.
9. Si el INSERT falla por referencia duplicada:
   - revertir la transacción completa;
   - volver a leer la solicitud original;
   - mismo hash -> 200 replay; distinto hash -> 409.
```

La transacción se consume con `single()` para esperar el commit antes de responder. Usar
`next()` cancelaba la transacción antes del commit y provocaba rollback.

Puntos clave:

- La condición `available_amount >= :amount` y el descuento ocurren en la misma sentencia. Dos solicitudes simultáneas no pueden descontar sobre el mismo saldo viejo: PostgreSQL reevalúa la condición sobre la versión actualizada de la fila.
- Si el `UPDATE` no afecta filas, se hace una consulta adicional **solo en el camino de rechazo** para obtener el motivo. En el camino aprobado no hay consulta extra.
- El `UNIQUE(request_reference)` es la garantía final de idempotencia.
- Si dos solicitudes con la misma referencia compiten, una inserta y la otra recibe violación de unicidad. La perdedora revierte su transacción, por lo que cualquier descuento que hubiera intentado queda deshecho.
- Una solicitud rechazada se persiste, pero el `UPDATE` no afecta filas, así que no descuenta cupo.
- La misma referencia con el mismo payload devuelve el resultado original.
- La misma referencia con payload distinto devuelve `409` y conserva la original.
- El hash se calcula con valores canónicos: textos sin espacios accidentales y monto normalizado, para que `600000` y `600000.0` produzcan la misma huella.
- Para solicitudes aprobadas, `reason` es `null`. Para rechazadas, se persiste y se devuelve el motivo.

### Respuesta sobre el motivo de rechazo

El PDF (RF03) exige conservar “información suficiente para conocer sus datos principales, si fue autorizada o rechazada, cuándo fue procesada y, cuando corresponda, una razón general que permita comprender el resultado”.

Interpretación adoptada:

- Sí se debe persistir y devolver una razón para las solicitudes rechazadas.
- No se exige un catálogo específico ni un detalle técnico exacto; “razón general” es suficiente.
- Se usará un catálogo corto y claro porque mejora la trazabilidad y la UI.
- Un payload malformado o con campos faltantes no se considera una solicitud procesada: se responde `400` y no se persiste.
- Un monto menor o igual a cero sí se considera una solicitud procesada y se persiste como `REJECTED` con `INVALID_AMOUNT`, para no perder el resultado.

## API propuesta

```text
POST /api/v1/preapproved-requests
GET  /api/v1/preapproved-requests/{requestReference}
GET  /api/v1/preapproved-requests?limit=20
GET  /api/v1/preapproved/{preApprovedId}
GET  /actuator/health
```

Request:

```json
{
  "requestReference": "REF-001",
  "preApprovedId": "PRA-1001",
  "customerId": "USR-10",
  "amount": 600000
}
```

Response:

```json
{
  "requestReference": "REF-001",
  "preApprovedId": "PRA-1001",
  "customerId": "USR-10",
  "amount": 600000,
  "status": "APPROVED",
  "reason": null,
  "processedAt": "2026-10-19T23:30:00Z",
  "remainingAmount": 400000,
  "replayed": false
}
```

Códigos HTTP:

- `201`: solicitud nueva procesada, aprobada o rechazada.
- `200`: replay idempotente.
- `409`: misma referencia con payload diferente.
- `400`: validación (campos obligatorios, longitudes, escala del monto o límite inválido).
- `404`: consulta no encontrada.
- `500`: error inesperado.

## Frontend Angular

- Angular standalone, signals, reactive forms y `HttpClient`.
- Estructura `core/` y `features/preapproved-requests/`.
- Formulario con valores iniciales de demo: `USR-10`, `PRA-1001`, `REF-001`, `600000`.
- Tarjeta de resultado con estado, motivo, fecha y cupo restante.
- Tabla de solicitudes recientes.
- Manejo visible de `409` mostrando el resultado original.
- `proxy.conf.json` para desarrollo.
- Docker multi-stage: Node build + Nginx con proxy `/api` hacia `back:8080`.
- Sin diseño avanzado.

## Docker y ejecución

`docker-compose.yml`:

- `db`: PostgreSQL 16, healthcheck y volumen persistente.
- `back`: build con Temurin 21, puerto `8080`, depende de `db`.
- `front`: Nginx, puerto `8081`, proxy `/api`.
- RabbitMQ solo si se implementa el opcional.

Comandos previstos:

```bash
docker compose up --build
make test
docker compose down -v
make smoke
```

Toda compilación/ejecución Java se hace dentro de contenedores con JDK 21. El
`Dockerfile` del backend es multi-stage: compila con `gradle:9.8.0-jdk21` y ejecuta con
`eclipse-temurin:21-jre-alpine` como usuario no root.

Variables de entorno del backend (defaults en `application.yaml` y `.env.example`):

| Variable | Default | Uso |
|---|---|---|
| `SERVER_PORT` | `8080` | Puerto HTTP |
| `SPRING_R2DBC_URL` | `r2dbc:postgresql://localhost:5432/bancoias` | Conexión R2DBC |
| `SPRING_R2DBC_USERNAME` | `bancoias` | Usuario de base de datos |
| `SPRING_R2DBC_PASSWORD` | `bancoias` | Contraseña de base de datos |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200,http://localhost:8080,http://localhost:8081` | Orígenes permitidos, separados por coma |
| `SQL_INIT_MODE` | `always` | Modo de inicialización SQL |
| `SQL_INIT_SCHEMA_LOCATIONS` | `classpath:schema.sql` | Script de esquema |
| `SQL_INIT_DATA_LOCATIONS` | `classpath:data.sql` | Script de datos semilla |

### Makefile

Se incluirá un `Makefile` con objetivos de uso diario. `make` sin argumentos mostrará la ayuda.

| Comando | Acción |
|---|---|
| `make up` | Construye y levanta `db`, `back` y `front`. |
| `make down` | Detiene y elimina contenedores, conservando el volumen de datos. |
| `make reload` | `down` + `up --build`: recrea las aplicaciones sin borrar la base. |
| `make reset` | `down -v` + `up --build`: borra la base y vuelve a sembrar. |
| `make restart` | Reinicia servicios sin reconstruir imágenes. |
| `make logs` | Sigue los logs de todos los servicios. |
| `make ps` | Muestra el estado de los contenedores. |
| `make test` | Ejecuta pruebas de backend y frontend. |
| `make test-back` | Ejecuta solo pruebas del backend. |
| `make test-front` | Ejecuta solo pruebas del frontend. |
| `make build` | Compila backend y frontend. |
| `make smoke` | Ejecuta el script de humo contra la API. |
| `make db` | Levanta solo PostgreSQL. |
| `make help` | Muestra la ayuda. |

Regla de seguridad: `make down` no borra datos; solo `make reset` elimina el volumen y vuelve a sembrar. Todos los objetivos serán `.PHONY` y reutilizarán `docker compose`.

## Pruebas

Backend (**50 tests, 0 fallos**):

- Unitarias de la política y casos de uso para todas las reglas.
- Integración R2DBC con Testcontainers PostgreSQL.
- Concurrencia: solicitudes simultáneas sobre el mismo preaprobado; la actualización condicional nunca deja el cupo negativo y solo aprueba hasta agotar el saldo.
- Idempotencia: misma referencia con mismo y distinto payload.
- Rechazos: cada solicitud rechazada persiste su `reason` y no modifica el cupo.
- `WebTestClient` para endpoints.
- ArchUnit para reglas de capas.

Notas de implementación de pruebas:

- Se excluye `DataRepositoryMetricsAutoConfiguration` porque su listener usa un lock
  bloqueante y BlockHound lo detecta en flujos reactivos.
- La integración permite bloqueo dentro de `SecureRandom.nextBytes` porque el driver
  PostgreSQL lee `/dev/urandom` al abrir conexiones SCRAM; es una lectura no bloqueante
  del sistema operativo, no una operación de negocio.
- El test de contrato OpenAPI permite la lectura de classpath de Springdoc
  (`RandomAccessFile.readBytes` y `ClassReader.readStream`) porque genera el documento
  desde los metadatos de las clases, no por tráfico de negocio.

Frontend:

- Servicio HTTP con `HttpTestingController`.
- Componente de formulario/resultado.
- `npm run build` y test runner de Angular.

Smoke test:

```text
REF-001 600000 -> APPROVED, remaining 400000
replay REF-001 -> mismo resultado, cupo intacto
REF-002 500000 -> REJECTED, reason INSUFFICIENT_QUOTA
REF-003 amount 0 -> REJECTED, reason INVALID_AMOUNT
referencia de 81 caracteres -> 400 (VALIDATION_ERROR)
amount 100.005 -> 400 (VALIDATION_ERROR)
REF-004 distinto payload sobre REF-001 -> 409
6 solicitudes concurrentes de 400000 sobre PRA-2001 -> 5 aprobadas, 1 rechazada
```

## Documentación y trazabilidad

- `README.md` con prerequisitos, ejecución, API, pruebas y limitaciones.
- `docs/arquitectura.md` con diagramas Mermaid de capas, modelo y secuencias.
- ADRs con contexto, alternativas, decisión, trade-offs y validación.
- `docs/uso-ia.md` con actividades asistidas, resultados usados, validación, correcciones y manejo de datos.
- `docs/api/openapi.yaml` generado desde `/v3/api-docs.yaml`.
- Historial de commits por fase.

## Orden de ejecución y commits propuestos

```text
1. chore: crear umbrella, generar scaffold 4.7.4 del back y front Angular
2. feat(back): modelo de dominio, política y casos de uso
3. feat(back): persistencia PostgreSQL, schema, seed y actualización condicional atómica
4. feat(back): API REST, validación, errores y OpenAPI
5. test(back): unitarias, integración, concurrencia e idempotencia
6. feat(front): formulario, resultado y listado reciente
7. test(front): pruebas de servicio y componente
8. docs: arquitectura, ADRs, uso de IA y runbook
9. feat(back): RabbitMQ opcional (solo si el alcance obligatorio está cerrado)
```

La secuencia ejecutada quedó en 7 commits de implementación del backend más 5 commits de
correcciones y documentación; el detalle está en `git log`.

## Criterios de aceptación

- `docker compose up --build` levanta db, back y front.
- RF01–RF07 verificables desde la UI y por API.
- Solicitudes aprobadas y rechazadas persistidas.
- Cupo consistente bajo concurrencia.
- Referencias repetidas no duplican uso ni descuento.
- Tests backend y frontend ejecutables.
- README, ADRs, uso de IA y commits presentes.

## Riesgos y límites

- No hay JDK 21 local; se mitiga con Docker.
- El host tiene Gradle 8.5; el scaffold 4.7.4 genera wrapper 9.8.0, por lo que la generación y compilación se harán en contenedor.
- La última versión del plugin es 5.0.2, pero se elige 4.7.4 por estabilidad y porque comparte Spring Boot 4.1.1. Si 4.7.4 presentara algún problema, se evalúa migrar a 5.0.2.
- La actualización condicional requiere una consulta adicional solo cuando la solicitud se rechaza, para clasificar el motivo.
- Testcontainers y Angular requieren descargar dependencias la primera vez.
- RabbitMQ puede exceder las 2 horas; se implementa solo al final o se documenta su descarte.
- No se implementan autenticación, administración de preaprobados ni paginación avanzada, conforme al PDF.

## Estado actual y pendientes

Backend terminado:

- Scaffold 4.7.4 con Java 21 y Spring Boot 4.1.1.
- Dominio, casos de uso, adaptador R2DBC y API funcional.
- `schema.sql`, `data.sql` y `application.yaml`.
- Dockerfile multi-stage, `docker-compose.yml`, `Makefile`, README y smoke test.
- Pruebas unitarias, de endpoints, integración con Testcontainers, idempotencia,
  conflictos, rechazos y concurrencia (**50 tests**).
- Hallazgos **H-01 a H-06** corregidos: OpenAPI completo, validación de longitudes y
  escala, detección de duplicados por SQLState `23505`, SQL init y CORS externalizados.
- `docs/api/openapi.yaml` generado y smoke test ampliado con casos `400`.

Pendiente:

- Frontend Angular (RF07).
- `docs/arquitectura.md`, ADRs y `docs/uso-ia.md`.
- RabbitMQ opcional.
- Flyway/Liquibase para producción (H-05).

### Ajustes ya incorporados

- Nombre del proyecto: `bancoias`.
- Scaffold actualizado a `4.7.4`, Spring Boot `4.1.1` y Gradle `9.8.0`.
- Sección de explicación del problema del PDF.
- Estrategia de concurrencia cambiada de bloqueo pesimista a actualización condicional atómica.
- Catálogo de motivos de rechazo y decisión de persistir la razón general que exige RF03.
- Makefile con comandos `up`, `down`, `reload`, `reset`, `test`, `smoke`, entre otros.
- OpenAPI anotado en router y DTOs, con test de contrato y `docs/api/openapi.yaml`.
- Validación de longitudes (80/40/40) y de `amount` (2 decimales, 17 dígitos enteros).
- Duplicados detectados por SQLState `23505`, no por texto del driver.
- SQL init y CORS externalizados por variables de entorno.
- Suite backend ampliada a 50 tests y smoke test con casos `400`.
- Modelo renombrado a `PreApprovedUsage` para cumplir la regla del scaffold.
- Transacción reactiva consumida con `single()` para garantizar el commit.
- Validación manual en el handler para no bloquear el event loop.
- Exclusión de métricas de repositorios y allow-list de `SecureRandom` en pruebas para
  BlockHound.
- Corrección del bug de Pitest del scaffold cuando `--mutation=false`.
