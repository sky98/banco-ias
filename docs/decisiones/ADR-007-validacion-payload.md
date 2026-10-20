# ADR-007: Validación estricta de longitudes y escala del monto

## Estado

Aceptado

## Contexto

Las columnas usan `VARCHAR(40/80)` y `NUMERIC(19,2)`. Un valor demasiado largo o con
más de dos decimales puede provocar un error de base de datos (`500`) o un redondeo
silencioso que rompe la idempotencia. La API debe responder de forma controlada.

## Opciones Consideradas

1. **Normalizar el monto** a dos decimales antes de hashear y persistir. Evita el
   error, pero cambia el valor solicitado sin avisar y puede ocultar un dato inválido.
2. **Rechazar con `400`** los payloads que no cumplen longitudes o escala, y dejar
   los montos menores o iguales a cero como rechazo de negocio (`201 REJECTED`).
3. **Migrar las columnas a `TEXT`** y aceptar cualquier longitud. Evita el límite,
   pero no resuelve la escala ni la calidad del dato.

## Decisión

Se elige **Opción 2**:

- `requestReference`: máximo 80 caracteres después de `trim()`.
- `preApprovedId` y `customerId`: máximo 40 caracteres después de `trim()`.
- `amount`: máximo 2 decimales y 17 dígitos enteros.
- `amount <= 0` con escala válida se persiste como `201 REJECTED` con
  `INVALID_AMOUNT`.

## Justificación Detallada

Rechazar el dato inválido es explícito y conserva la trazabilidad: el cliente sabe
que `100.005` no fue aceptado y no se genera un hash distinto al de `100.01`.
`100.000` sí se acepta porque, tras `stripTrailingZeros()`, equivale a `100`. La
separación entre error de contrato (`400`) y regla de negocio (`201 REJECTED`) evita
confundir un payload mal formado con una solicitud válida pero no autorizada.

## Consecuencias

### Positivas

- No hay redondeos silenciosos ni errores `500` por longitud.
- El hash y la persistencia reciben montos con la misma escala.
- Los mensajes de error indican el campo y el límite.

### Negativas

- El cliente debe enviar montos con máximo dos decimales.
- Se agregan constraints custom (`@MaxDecimalPlaces`, `@MaxIntegerDigits`) y un
  `ValidadorRequest`; no hay validación manual en el handler.

### Riesgos

- Duplicar reglas entre API y base de datos. Mitigación: pruebas de handler,
  integración y smoke test con los casos límite.

## Referencias

- `CreatePreApprovedUsageRequest`
- `ValidadorRequest`, `MaxDecimalPlaces`, `MaxIntegerDigits`
- `PreApprovedUsageHandlerTest`
- `docs/REVISION-BACKEND.md` (H-03/H-04)
