# Trazabilidad de la entrega

Matriz de requisitos del PDF (RF01–RF07, restricciones y criterio de completitud)
con su implementación, prueba y forma de verificación.

## Requisitos funcionales

| ID | Requisito | Implementación | Prueba automatizada | Verificación manual / smoke |
|---|---|---|---|---|
| RF01 | Procesar solicitud y registrar fecha y hora | `POST /api/v1/preapproved-requests`; `processed_at` en `preapproved_usage` | `PreApprovedUsageHandlerTest`, `PreApprovedUsageIntegrationTest` | `make smoke` (aprobación, HTTP 201) |
| RF02 | Validar monto > 0, preaprobado existente, cliente correcto, estado habilitado y cupo suficiente | `PreApprovedUsagePolicy`, `ProcessPreApprovedRequestUseCase`, `UPDATE` condicional | `PreApprovedUsagePolicyTest`, `ProcessPreApprovedRequestUseCaseTest`, pruebas de integración de rechazos | `make smoke` (rechazo por cupo y por monto) |
| RF03 | Conservar el resultado; rechazo sin descuento de cupo | Tabla `preapproved_usage` con `status`, `reason` y `remaining_amount` | Integración: rechazos persistidos sin cambio de cupo | Consulta de cupo tras el smoke (`availableAmount` = 0 en `PRA-2001`) |
| RF04 | Solicitudes simultáneas sin superar el cupo | `UPDATE ... WHERE available_amount >= :amount RETURNING` (ADR-003) | `PreApprovedUsageIntegrationTest.concurrentRequestsNeverOverdraw` | `make smoke`: 6 concurrentes, 5 aprobadas y 1 rechazada |
| RF05 | Referencias repetidas idempotentes; conflicto controlado | `request_reference UNIQUE` + `PayloadHasher` (ADR-004); `200` replay y `409` | Replay, conflicto y `concurrentSameReferenceIsAppliedOnce` | `make smoke` (replay `200`, conflicto `409`); UI con la misma referencia |
| RF06 | Consultar por `requestReference` y listar recientes | `GET /api/v1/preapproved-requests/{requestReference}` y `GET /api/v1/preapproved-requests?limit=` | `PreApprovedUsageHandlerTest`, `ListRecentPreApprovedRequestsUseCaseTest` | `curl` o la tabla de recientes en la UI |
| RF07 | Interfaz Angular para registrar, ver resultado y consultar recientes | `front/` (Angular 22): formulario, tarjeta de resultado y tabla | `preapproved-requests.page.spec.ts` (7), `preapproved-usage.service.spec.ts` (3), `app.spec.ts` (2) | UI en `http://localhost:8081` |

## Restricciones técnicas y operativas

| Restricción | Cumplimiento |
|---|---|
| Backend Java con Spring Boot WebFlux | Java 21, Spring Boot 4.1.1, WebFlux, R2DBC (ADR-001, ADR-005) |
| Frontend Angular | Angular 22 standalone, signals y reactive forms (`front/`) |
| Persistencia de solicitudes procesadas | PostgreSQL 16 con `preapproved_usage` |
| Pruebas automatizadas | 50 tests backend (JUnit/Testcontainers) y 12 tests frontend (Vitest) |
| Instrucciones de ejecución | `README.md`, `make up`, `make test`, `make smoke` |
| Registro de decisiones técnicas | `docs/decisiones/` (ADR-001 a ADR-008) y `docs/arquitectura.md` |
| Registro de uso de IA | `docs/uso-ia.md` |
| Historial de commits | `git log` (commits por fase: backend, correcciones, frontend, documentación) |

## Criterio de completitud

| Elemento | Estado |
|---|---|
| RF01–RF07 verificables desde UI y API | Completo |
| Persistencia requerida | Completo |
| Pruebas automatizadas | Completo: `make test` ejecuta backend y frontend |
| Instrucciones de ejecución | Completo: `README.md` |
| Trazabilidad de decisiones e IA | Completo: `docs/decisiones/`, `docs/uso-ia.md` |
| Limitaciones y pendientes identificados | Completo: sección "Limitaciones y pendientes" de `README.md` |

## Punto opcional: RabbitMQ

No implementado. La decisión, sus alternativas y el diseño futuro (outbox
transaccional) están en [ADR-006](decisiones/ADR-006-rabbitmq-opcional.md). Según el
PDF, su ausencia no afecta la evaluación del alcance obligatorio.

## Cómo verificar la entrega

```bash
make test       # backend (50 tests) + frontend (12 tests)
make reset      # base limpia y stack completo
make smoke      # regresión E2E sobre el backend
# UI: http://localhost:8081
```
