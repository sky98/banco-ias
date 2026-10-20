# Uso de inteligencia artificial

## Herramientas

Se utilizó un agente de codificación basado en LLM (OpenCode) como apoyo durante el
análisis, la implementación, las pruebas y la documentación. Las decisiones finales,
la revisión de los cambios y la ejecución de las pruebas quedaron en el repositorio y
en la terminal.

## Actividades asistidas

| Actividad | Uso de IA | Resultado aprovechado |
|---|---|---|
| Análisis del PDF y planificación | Resumen de requisitos, riesgos y secuencia de commits | Plan de implementación y criterios de aceptación |
| Scaffold del backend | Apoyo para generar y adaptar la estructura Clean Architecture | Módulos `model`, `usecase`, `reactive-web`, `r2dbc-postgresql`, `app-service` |
| Dominio y casos de uso | Borradores de política, hash, comandos y puertos | Reglas RF02–RF05 y pruebas unitarias |
| Persistencia | Borradores de SQL, adaptador y transacción reactiva | `UPDATE` condicional, búsqueda por referencia y detección de duplicados |
| API | Router, handler, DTOs, errores y OpenAPI | Endpoints RF01/RF06 y contrato versionado |
| Frontend Angular | Scaffold, servicio, formulario, resultado y tabla | RF07 integrado al backend |
| Pruebas | Casos de handler, integración, concurrencia, frontend y smoke | 50 tests backend y 12 tests frontend |
| Documentación | Borradores de arquitectura, ADRs y este registro | `docs/arquitectura.md`, `docs/decisiones/`, `docs/entrega.md` |

## Validación de resultados

- Cada cambio de backend se ejecutó con la suite completa en Docker: **50 tests, 0
  fallos**.
- El frontend se ejecutó con Vitest: **12 tests, 0 fallos**.
- Se levantó el stack completo con `make reset` y se verificó:
  - UI en `http://localhost:8081`.
  - Proxy `/api` de Nginx hacia `back:8080`.
  - POST, consulta y listado a través del frontend.
  - `make smoke` con aprobación, replay, rechazos, validaciones `400`, conflicto
    `409` y concurrencia.
- Se revisaron los diffs y se ejecutaron compilaciones de producción antes de cada
  commit.

## Correcciones y descartes

- **H-01 (OpenAPI):** se descartó documentar solo con anotaciones; se agregó el YAML
  estático y un test de contrato para evitar derivas.
- **H-03/H-04 (validación):** se evaluó normalizar el monto a dos decimales, pero se
  descartó porque ocultaba un dato inválido. Se decidió responder `400` para más de
  dos decimales; `100.000` se acepta y `100.005` se rechaza.
- **H-02 (duplicados):** se reemplazó la detección por texto `"duplicate key"` por
  SQLState `23505` en la cadena de causas.
- **BlockHound:** el test de OpenAPI falló inicialmente porque Springdoc lee el
  classpath. Se agregó una allow-list acotada a la lectura de metadatos en pruebas.
- **Pruebas:** se ajustó el número esperado de tests al alcance final (50 backend y
  12 frontend) y se verificó cada suite antes de documentar.
- **Frontend:** se mantuvo una página con formulario, resultado y tabla en lugar de
  dividir en muchos componentes, para conservar el alcance acotado y la claridad.
- **RabbitMQ:** se descartó su implementación y se documentó la decisión en
  `ADR-006`, priorizando el alcance obligatorio.

## Datos sensibles

- Solo se usaron datos ficticios de semilla (`PRA-1001`, `USR-10`, etc.). No se
  procesaron datos personales reales.
- No se incluyeron secretos en el repositorio. `.env` está ignorado y
  `.env.example` solo contiene valores de demostración.
- No se enviaron credenciales ni payloads de producción a la herramienta de IA.
- Los logs de la aplicación no imprimen el payload completo de las solicitudes.

## Limitaciones

La IA puede producir sugerencias incorrectas o desactualizadas. Por eso, todo cambio
se validó con compilación, pruebas reales, smoke test y revisión del diff. Las
decisiones arquitectónicas y sus trade-offs están en `docs/decisiones/` para que
puedan explicarse y defenderse sin depender de la herramienta.
