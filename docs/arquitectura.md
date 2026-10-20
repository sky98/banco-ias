# Arquitectura — BancoIAS

## Propósito y alcance

BancoIAS procesa solicitudes de uso de cupos preaprobados, conserva el resultado de
cada solicitud y permite consultar el historial reciente. El alcance implementado es
RF01–RF07 del PDF, con backend reactivo, frontend Angular y PostgreSQL. RabbitMQ y
Flyway quedan como mejoras opcionales.

## Vista de contexto

```mermaid
flowchart LR
    U[Usuario] -->|HTTP| F[Frontend Angular]
    F -->|/api| N[Nginx]
    N -->|proxy| B[Backend Spring Boot WebFlux]
    B -->|R2DBC| P[(PostgreSQL 16)]
    O[Otros sistemas] -.->|evento futuro, opcional| B
```

## Contenedores

```mermaid
flowchart TB
    subgraph Compose[Docker Compose]
        FE[front<br/>Angular 22 + Nginx<br/>:8081]
        BE[back<br/>Java 21 + Spring Boot 4.1.1<br/>:8080]
        DB[(db<br/>PostgreSQL 16<br/>:5432)]
    end

    FE -->|/api/*| BE
    BE -->|R2DBC| DB
    DB -->|volumen| VOL[(postgres_data)]
```

El frontend se construye en una etapa Node y se sirve como estático desde Nginx.
Nginx hace proxy de `/api` hacia `back:8080`, por lo que el navegador no necesita
CORS en el despliegue por Compose. En desarrollo, `ng serve` usa
`proxy.conf.json` con el mismo destino.

## Capas del backend

```mermaid
flowchart TB
    API[reactive-web<br/>Router, Handler, DTOs, validación, errores, OpenAPI]
    UC[usecase<br/>Procesar solicitud, consultar, listar]
    MODEL[model<br/>PreApprovedUsage, política, puertos, PayloadHasher]
    ADP[r2dbc-postgresql<br/>Adaptador, entidades, repositorios]
    APP[app-service<br/>MainApplication, configuración, schema.sql, data.sql]

    API --> UC
    UC --> MODEL
    ADP -.->|implementa puertos| MODEL
    APP --> API
    APP --> ADP
```

Reglas:

- `model` no depende de Spring ni de R2DBC.
- `usecase` solo conoce puertos del dominio.
- `reactive-web` traduce HTTP a comandos y comandos a respuestas.
- `r2dbc-postgresql` implementa los puertos con SQL explícito y transacciones
  reactivas.
- `app-service` compone la aplicación y concentra la configuración.

## Modelo de datos

```mermaid
erDiagram
    PREAPPROVED {
        varchar pre_approved_id PK
        varchar customer_id
        varchar status
        numeric available_amount
    }

    PREAPPROVED_USAGE {
        bigint id PK
        varchar request_reference UK
        varchar pre_approved_id FK
        varchar customer_id
        numeric amount
        varchar status
        varchar reason
        varchar payload_hash
        timestamptz processed_at
        numeric remaining_amount
    }

    PREAPPROVED ||--o{ PREAPPROVED_USAGE : "es usado por"
```

`request_reference` es único y `payload_hash` distingue un replay legítimo de un
conflicto. Los montos usan `NUMERIC(19,2)` y el cupo tiene un `CHECK` para no ser
negativo.

## Flujo de procesamiento

```mermaid
sequenceDiagram
    autonumber
    participant C as Cliente Angular
    participant A as API WebFlux
    participant U as Caso de uso
    participant R as Adaptador R2DBC
    participant D as PostgreSQL

    C->>A: POST /api/v1/preapproved-requests
    A->>A: Validar longitudes y escala
    alt Payload inválido
        A-->>C: 400 VALIDATION_ERROR
    else Payload válido
        A->>U: procesar(command)
        U->>R: buscar por requestReference
        alt Referencia existente con mismo hash
            R-->>C: 200 resultado original (replayed=true)
        else Referencia existente con hash distinto
            R-->>C: 409 REFERENCE_CONFLICT (details=original)
        else Referencia nueva
            R->>D: UPDATE preapproved ... WHERE available_amount >= amount RETURNING
            alt Actualización exitosa
                D-->>R: cupo restante
                R->>D: INSERT preapproved_usage APPROVED
                R-->>C: 201 APPROVED
            else Sin cupo suficiente
                R->>D: clasificar motivo y registrar REJECTED
                R-->>C: 201 REJECTED
            end
        end
    end
```

La actualización condicional y el `INSERT` ocurren en la misma transacción. Si dos
solicitudes compiten por la misma referencia, la restricción única decide y la
perdedora relee la fila para responder replay o conflicto.

## Despliegue

```mermaid
flowchart LR
    H[Host :8081] --> FE[front :80]
    FE -->|/api| BE[back :8080]
    BE --> DB[(db :5432)]
    subgraph Compose[docker compose]
        FE
        BE
        DB
    end
```

`back` espera a que `db` esté saludable y `front` espera a que `back` esté
saludable. El healthcheck del backend consulta `/actuator/health`.

## Atributos de calidad

| Atributo | Implementación | Evidencia |
|---|---|---|
| Consistencia de cupo | `UPDATE` condicional atómico + transacción | Test de concurrencia y smoke test |
| Idempotencia | `request_reference` único + hash canónico | Tests de replay/conflicto y concurrencia |
| Reactividad | WebFlux + R2DBC, sin bloqueo en el event loop | BlockHound en pruebas |
| Contrato | OpenAPI anotado + YAML estático | `documentsOpenApiContract` |
| Validación | Longitudes 80/40/40; monto 2 decimales y 17 enteros | Handler tests y smoke test |
| Operación | Compose, healthchecks, variables de entorno | `make up`, `make smoke` |
| Trazabilidad | Commits por fase, ADRs, uso de IA | `git log`, `docs/decisiones/`, `docs/uso-ia.md` |

## Decisiones relacionadas

- [ADR-001](decisiones/ADR-001-backend-unico.md)
- [ADR-002](decisiones/ADR-002-scaffold-clean-architecture.md)
- [ADR-003](decisiones/ADR-003-concurrencia-actualizacion-condicional.md)
- [ADR-004](decisiones/ADR-004-idempotencia-request-reference.md)
- [ADR-005](decisiones/ADR-005-postgresql-r2dbc.md)
- [ADR-006](decisiones/ADR-006-rabbitmq-opcional.md)
- [ADR-007](decisiones/ADR-007-validacion-payload.md)
- [ADR-008](decisiones/ADR-008-openapi-contrato.md)
