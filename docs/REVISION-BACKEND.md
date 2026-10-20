# Revisión del backend

- **Fecha:** 2026-10-20
- **Alcance:** backend `back/` (Java 21, Spring Boot 4.1.1, WebFlux, R2DBC PostgreSQL)
- **Estado:** correcciones **H-01 a H-07** aplicadas y verificadas
- **Commits de corrección:** `52ecdb7`, `5bc9922`, `3823752`, `a64915c`, `87964ce`; frontend y documentación posteriores

## Veredicto

Implementación sólida y verificable. La funcionalidad principal, la concurrencia y la
idempotencia están cubiertas por pruebas reales y por un smoke test de extremo a extremo.
Los hallazgos bloqueantes **H-01** y **H-03** quedan resueltos. **H-07** también queda
cerrado con el frontend Angular (RF07), los ADR, `docs/arquitectura.md`, `docs/uso-ia.md` y
`docs/entrega.md`. Ver la matriz de trazabilidad en `docs/entrega.md`.

## Evidencia

- **50 tests, 0 fallos, 0 errores, 0 skipped** (`make test`).
- **Smoke test E2E exitoso** (`scripts/smoke-test.sh`):
  aprobación, replay idempotente, rechazo por cupo, rechazo por monto, **referencia
  demasiado larga (`400`)**, **monto con más de 2 decimales (`400`)**, conflicto `409`,
  concurrencia `5 aprobadas / 1 rechazada` y cupo final `0`.
- **OpenAPI** responde `200`; Swagger UI responde `302`; listado responde `200`.
- **`docs/api/openapi.yaml`** generado desde `/v3/api-docs.yaml` con `requestBody`,
  respuestas `200/201/400/409/500` y parámetros documentados.
- **Contenedores detenidos** y volumen de prueba eliminado; `make up` arranca limpio.

### Distribución de pruebas

| Suite | Tests |
|---|---:|
| `PreApprovedUsageHandlerTest` | 16 |
| `PreApprovedUsageIntegrationTest` | 10 |
| `PreApprovedUsagePolicyTest` | 7 |
| `ArchitectureTest` | 6 |
| `ListRecentPreApprovedRequestsUseCaseTest` | 3 |
| `PayloadHasherTest` | 2 |
| `ProcessPreApprovedRequestUseCaseTest` | 2 |
| `PreApprovedUsageR2dbcAdapterTest` | 2 |
| `ObjectMapperConfigTest` + `UseCasesConfigTest` | 2 |
| **Total** | **50** |

Cobertura por módulo (Jacoco, sin reporte agregado):

- `model`: `76.2%` líneas, `100%` ramas.
- `usecase`: `86.7%` líneas, `100%` ramas.
- `reactive-web`: `87.4%` líneas, `81.6%` ramas.
- `r2dbc-postgresql`: `6.7%` líneas y `27.3%` ramas en unitarias; el adaptador se
  ejercita en los 10 tests de integración de `app-service` y Jacoco no agrega cobertura
  entre módulos.

## Arquitectura y diseño

- Clean Architecture respetada: `model`, `usecase`, `reactive-web`, `r2dbc-postgresql`, `app-service`.
- El dominio no depende de frameworks; los casos de uso solo usan puertos.
- `ArchitectureTest` valida 6 reglas y pasa.
- Se usó `PreApprovedUsage` en lugar de sufijos `Request`/`Response` para cumplir la convención del scaffold.
- Sin bloqueo en el event loop: validación manual en el handler, exclusión de métricas de
  repositorios y allow-list de `SecureRandom` más la lectura de classpath de Springdoc
  durante `/v3/api-docs` en pruebas.

## Concurrencia e idempotencia

- Descuento atómico en `PreApprovedUsageR2dbcAdapter.java:90`:
  `UPDATE ... WHERE available_amount >= :amount RETURNING available_amount`.
- Transacción consumida con `.single()` en `PreApprovedUsageR2dbcAdapter.java:68` para esperar
  el commit real; `.next()` cancelaba antes del commit.
- `request_reference` es `UNIQUE` (`schema.sql:10`). Si dos solicitudes compiten, la perdedora
  relee y responde replay o `409`. La violación de unicidad se detecta por SQLState `23505`
  en la cadena de causas (`PreApprovedUsageR2dbcAdapter.java:191`), no por el texto del error.
- Rechazos de negocio persistidos con `reason`; aprobadas con `reason = null`.
- `GET /api/v1/preapproved-requests` ordena por `processed_at DESC, id DESC`.

### Prueba de concurrencia

`PreApprovedUsageIntegrationTest` — `concurrentSameReferenceIsAppliedOnce`:

- 10 solicitudes concurrentes con la misma `requestReference` y el mismo payload.
- Resultado: `1` creada, `9` replay, una sola fila persistida y cupo descontado una vez.
- Confirma que el fallback por violación de unicidad funciona en PostgreSQL/R2DBC.

## API

| Método | Ruta | Respuestas |
|---|---|---|
| `POST` | `/api/v1/preapproved-requests` | `201` creada, `200` replay, `409` conflicto, `400` inválida, `500` inesperado |
| `GET` | `/api/v1/preapproved-requests/{requestReference}` | `200`, `404` |
| `GET` | `/api/v1/preapproved-requests?limit=N` | `200` (límite 1-100), `400` límite inválido |
| `GET` | `/api/v1/preapproved/{preApprovedId}` | `200`, `404` |

Los rechazos de negocio responden `201` con estado `REJECTED`; el payload malformado responde
`400` sin persistir. `ApiExceptionHandler.java:17` centraliza el mapeo de errores.

Reglas de validación del payload:

- `requestReference`: no vacío y máximo 80 caracteres después de `trim()`.
- `preApprovedId` y `customerId`: no vacíos y máximo 40 caracteres después de `trim()`.
- `amount`: máximo 2 decimales y 17 dígitos enteros. `100.000` se acepta; `100.005` responde
  `400` (no se normaliza silenciosamente).
- `amount <= 0` con escala válida se persiste como `201 REJECTED` con `INVALID_AMOUNT`.

## Hallazgos

| ID | Severidad | Ubicación | Hallazgo | Impacto | Corrección aplicada | Estado |
|---|---|---|---|---|---|---|
| **H-01** | **Alta / bloqueante** | `RouterRest.java`, DTOs | OpenAPI exponía las rutas sin `requestBody`, sin esquemas de respuesta y todas como `200` | Documentación engañosa para consumidores y para la entrega | `@Operation`, `@RequestBody`, `@Parameter` y `@ApiResponse`; esquemas en DTOs; `docs/api/openapi.yaml`; test de contrato | **Resuelto** |
| **H-02** | Media | `PreApprovedUsageR2dbcAdapter.java:191` | Detección de duplicado por texto `"duplicate key"` | Frágil ante cambios de driver/mensaje | Validar SQLState `23505` en la cadena de causas; test unitario | **Resuelto** |
| **H-03** | **Alta / bloqueante** | `PreApprovedUsageHandler.java` | Sin validación de longitudes (`VARCHAR(40/80)`) | Un valor demasiado largo provocaba error de BD y `500` en vez de `400` | Límites 80/40/40 después de `trim()`; tests y smoke test | **Resuelto** |
| **H-04** | Media | `PayloadHasher.java`, `schema.sql:13` | La BD redondea a 2 decimales, pero el hash usaba la escala original | Un monto como `100.005` se guardaba como `100.01`; un replay con `100.01` se trataba como conflicto | Rechazar con `400` montos con más de 2 decimales; `100.000` se acepta; test de escala y overflow | **Resuelto** |
| **H-05** | Baja | `application.yaml` | `spring.sql.init.mode=always` en todos los entornos | Aceptable para demo; riesgoso para producción | Externalizar `SQL_INIT_MODE`, `SQL_INIT_SCHEMA_LOCATIONS` y `SQL_INIT_DATA_LOCATIONS`; Flyway/Liquibase queda como mejora para producción | **Mitigado** |
| **H-06** | Baja | `application.yaml` | CORS con orígenes fijos | Dificultaba despliegues en otros dominios | Externalizar `CORS_ALLOWED_ORIGINS` en `application.yaml`, Compose y `.env.example` | **Resuelto** |
| **H-07** | Media | `docs/`, `front/` | Faltaban frontend Angular, ADRs, `docs/arquitectura.md`, `docs/uso-ia.md` y OpenAPI estático | Entrega incompleta fuera del backend | Frontend Angular 22 (RF07), ADR-001 a ADR-008, `docs/arquitectura.md`, `docs/uso-ia.md`, `docs/entrega.md` y `docs/api/openapi.yaml` | **Resuelto** |

## Recomendación

Antes de una entrega formal:

1. Evaluar Flyway o Liquibase para reemplazar `spring.sql.init` en producción (H-05).
2. Mantener RabbitMQ como opcional; su descarte está en ADR-006.

## Comandos de verificación

```bash
# Suite completa (Docker, Java 21)
make test

# Levantar backend + PostgreSQL
make up

# Smoke test E2E
./scripts/smoke-test.sh

# Generar OpenAPI estático con el backend arriba
curl -fsS http://localhost:8080/v3/api-docs.yaml -o docs/api/openapi.yaml

# Detener y limpiar volumen
docker compose down -v
```

## Próximo paso

Los hallazgos H-01 a H-07 quedan resueltos o mitigados y verificados con pruebas reales.
Solo quedan mejoras opcionales (Flyway/Liquibase y RabbitMQ).
