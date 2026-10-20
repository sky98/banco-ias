# Revisión del backend

- **Fecha:** 2026-10-20
- **Alcance:** backend `back/` (Java 21, Spring Boot 4.1.1, WebFlux, R2DBC PostgreSQL)
- **Estado:** implementación completa y verificada; **sin commit** (`master` aún no tiene commits)
- **Decisión:** pausar correcciones y retomar los hallazgos antes de una entrega formal

## Veredicto

Implementación sólida y verificable. La funcionalidad principal, la concurrencia y la
idempotencia están cubiertas por pruebas reales y por un smoke test de extremo a extremo.
No se considera cerrada para entrega formal hasta resolver los hallazgos **H-01** y **H-03**.

## Evidencia

- **41 tests, 0 fallos, 0 errores, 0 skipped** (`make test`).
- **Smoke test E2E exitoso** (`scripts/smoke-test.sh`):
  aprobación, replay idempotente, rechazo por cupo, rechazo por monto, conflicto `409`,
  concurrencia `5 aprobadas / 1 rechazada` y cupo final `0`.
- **OpenAPI** responde `200`; Swagger UI responde `302`; listado responde `200`.
- **Contenedores detenidos** y volumen de prueba eliminado; `make up` arranca limpio.

### Distribución de pruebas

| Suite | Tests |
|---|---:|
| `PreApprovedUsageHandlerTest` | 10 |
| `PreApprovedUsageIntegrationTest` | 9 |
| `PreApprovedUsagePolicyTest` | 7 |
| `ArchitectureTest` | 6 |
| `ListRecentPreApprovedRequestsUseCaseTest` | 3 |
| `PayloadHasherTest` | 2 |
| `ProcessPreApprovedRequestUseCaseTest` | 2 |
| `ObjectMapperConfigTest` + `UseCasesConfigTest` | 2 |
| **Total** | **41** |

Cobertura por módulo (Jacoco, sin reporte agregado):

- `model`: `76.2%` líneas, `100%` ramas.
- `usecase`: `86.7%` líneas, `100%` ramas.
- `reactive-web`: `86.1%` líneas, `75%` ramas.
- `r2dbc-postgresql`: sin reporte propio; comportamiento ejercitado por los 9 tests de integración.

## Arquitectura y diseño

- Clean Architecture respetada: `model`, `usecase`, `reactive-web`, `r2dbc-postgresql`, `app-service`.
- El dominio no depende de frameworks; los casos de uso solo usan puertos.
- `ArchitectureTest` valida 6 reglas y pasa.
- Se usó `PreApprovedUsage` en lugar de sufijos `Request`/`Response` para cumplir la convención del scaffold.
- Sin bloqueo en el event loop: validación manual en el handler, exclusión de métricas de
  repositorios y allow-list de `SecureRandom` solo en pruebas.

## Concurrencia e idempotencia

- Descuento atómico en `PreApprovedUsageR2dbcAdapter.java:87`:
  `UPDATE ... WHERE available_amount >= :amount RETURNING available_amount`.
- Transacción consumida con `.single()` en `PreApprovedUsageR2dbcAdapter.java:66` para esperar
  el commit real; `.next()` cancelaba antes del commit.
- `request_reference` es `UNIQUE` (`schema.sql:10`). Si dos solicitudes compiten, la perdedora
  relee y responde replay o `409`.
- Rechazos de negocio persistidos con `reason`; aprobadas con `reason = null`.
- `GET /api/v1/preapproved-requests` ordena por `processed_at DESC, id DESC`.

### Prueba añadida durante la revisión

`PreApprovedUsageIntegrationTest.java:140` — `concurrentSameReferenceIsAppliedOnce`:

- 10 solicitudes concurrentes con la misma `requestReference` y el mismo payload.
- Resultado: `1` creada, `9` replay, una sola fila persistida y cupo descontado una vez.
- Confirma que el fallback por violación de unicidad funciona en PostgreSQL/R2DBC.

## API

| Método | Ruta | Respuestas |
|---|---|---|
| `POST` | `/api/v1/preapproved-requests` | `201` creada, `200` replay, `409` conflicto, `400` inválida |
| `GET` | `/api/v1/preapproved-requests/{requestReference}` | `200`, `404` |
| `GET` | `/api/v1/preapproved-requests?limit=N` | `200` (límite 1-100) |
| `GET` | `/api/v1/preapproved/{preApprovedId}` | `200`, `404` |

Los rechazos de negocio responden `201` con estado `REJECTED`; el payload malformado responde
`400` sin persistir. `ApiExceptionHandler.java:17` centraliza el mapeo de errores.

## Hallazgos

| ID | Severidad | Ubicación | Hallazgo | Impacto | Corrección propuesta | Estado |
|---|---|---|---|---|---|---|
| **H-01** | **Alta / bloqueante** | `RouterRest.java:19`, `PreApprovedUsageHandler.java` | OpenAPI expone las rutas pero sin `requestBody`, sin esquemas de respuesta y todas como `200` | Documentación engañosa para consumidores y para la entrega | Anotar `@Operation`, `@RequestBody` y `@ApiResponse`, o generar `docs/api/openapi.yaml` estático | Pendiente |
| **H-02** | Media | `PreApprovedUsageR2dbcAdapter.java:190` | Detección de duplicado por texto `"duplicate key"` | Funciona con PostgreSQL, pero es frágil ante cambios de driver/mensaje | Validar SQLState `23505` en la cadena de causas | Pendiente |
| **H-03** | **Alta / bloqueante** | `PreApprovedUsageHandler.java:86`, `schema.sql:2-13` | Sin validación de longitudes (`VARCHAR(40/80)`) | Un valor demasiado largo provoca error de BD y responde `500` en vez de `400` | Validar longitudes en el handler o migrar columnas a `TEXT` | Pendiente |
| **H-04** | Media | `PayloadHasher.java:34`, `schema.sql:13` | La BD redondea a 2 decimales, pero el hash usa la escala original | Un monto como `100.005` se guarda como `100.01`; un replay con `100.01` se trataría como conflicto | Normalizar el monto a 2 decimales antes de hashear y persistir | Pendiente |
| **H-05** | Baja | `application.yaml:18` | `spring.sql.init.mode=always` en todos los entornos | Aceptable para demo; riesgoso para producción | Usar Flyway/Liquibase y desactivar SQL init en producción | Aceptado para demo |
| **H-06** | Baja | `application.yaml:39` | CORS con orígenes fijos | Dificulta despliegues en otros dominios | Externalizar a variable de entorno | Pendiente |
| **H-07** | Media | `docs/` | Faltan frontend Angular, ADRs, `docs/arquitectura.md`, `docs/api/openapi.yaml` y `docs/uso-ia.md` | Entrega incompleta fuera del backend | Abordar en la siguiente fase | Pendiente |

## Recomendación

Antes de una entrega formal:

1. Resolver **H-01** (OpenAPI) y **H-03** (validación de longitudes).
2. Resolver **H-02** y **H-04** por robustez.
3. Reevaluar **H-05** y **H-06** según el entorno de despliegue.
4. Completar **H-07** en la fase de frontend/documentación.

## Comandos de verificación

```bash
# Suite completa (Docker, Java 21)
make test

# Levantar backend + PostgreSQL
make up

# Smoke test E2E
./scripts/smoke-test.sh

# Detener y limpiar volumen
docker compose down -v
```

## Próximo paso

Retomar los hallazgos cuando el usuario lo indique. **No se ha creado ningún commit.**
