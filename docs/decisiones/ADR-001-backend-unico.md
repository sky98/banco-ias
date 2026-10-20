# ADR-001: Backend único para el dominio de cupos preaprobados

## Estado

Aceptado

## Contexto

La prueba define un dominio acotado: consumir cupos preaprobados, conservar el
resultado de cada solicitud y consultar el historial reciente, con consistencia bajo
concurrencia e idempotencia. El alcance estimado es de dos horas, por lo que la
solución debe ser pequeña, ejecutable y fácil de evaluar.

## Opciones Consideradas

1. **Microservicios separados** para solicitudes, cupos y consultas. Aporta
   despliegue independiente, pero agrega red, contratos, observabilidad y datos
   distribuidos que no se justifican para el alcance.
2. **Backend único modular** con capas de dominio, casos de uso, persistencia y API.
   Mantiene límites claros dentro de un solo proceso y un único despliegue.
3. **Función serverless** con base de datos administrada. Reduce operación, pero
   dificulta la demostración local y la transacción atómica entre cupo y solicitud.

## Decisión

Se elige **Opción 2: backend único modular**, desplegado como un solo contenedor.

## Justificación Detallada

El problema tiene un único contexto delimitado y una sola transacción crítica:
descontar cupo y registrar la solicitud. Un backend único permite resolverla con
una transacción local y un `UPDATE` condicional atómico, sin coordinación
distribuida. La modularidad se conserva mediante Clean Architecture: `model`,
`usecase`, `reactive-web` y `r2dbc-postgresql`. Si el dominio creciera, los módulos
ya separan las responsabilidades y podrían extraerse después.

## Consecuencias

### Positivas

- Una sola transacción local para cupo y solicitud.
- Un único `docker compose up --build` para evaluar todo el backend.
- Menos latencia y menos puntos de falla que una arquitectura distribuida.

### Negativas

- El despliegue y el escalamiento son conjuntos para todo el backend.
- No hay aislamiento físico entre consultas y procesamiento.

### Riesgos

- Crecimiento del proceso único. Mitigación: límites por módulo y extracción futura
  si aparecen equipos o volúmenes que lo justifiquen.

## Referencias

- `docs/PLAN-IMPLEMENTACION.md`
- `back/applications/app-service`
- `back/domain/model`, `back/domain/usecase`
