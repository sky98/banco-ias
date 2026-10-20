# ADR-004: Idempotencia por requestReference y hash del payload

## Estado

Aceptado

## Contexto

Una misma `requestReference` puede reenviarse porque se perdió la respuesta. El
sistema no debe descontar dos veces ni crear otra utilización, y debe conservar el
resultado original. Si la referencia llega con datos distintos, el conflicto debe
manejarse de forma controlada.

## Opciones Consideradas

1. **Tabla de claves de idempotencia** separada, con estado y respuesta almacenada.
   Flexible, pero agrega una entidad y coordinación extra.
2. **Referencia única en la tabla de usos + hash canónico del payload**. La base
   impone la unicidad y el hash distingue replay de conflicto.
3. **Token de idempotencia generado por el cliente**. Traslada la responsabilidad al
   consumidor y no cubre referencias repetidas sin token.

## Decisión

Se elige **Opción 2**: `request_reference UNIQUE` más SHA-256 del payload canónico.

## Justificación Detallada

Antes de procesar, el adaptador busca la referencia. Si existe y el hash coincide,
devuelve el resultado almacenado con `200` y `replayed=true`. Si el hash difiere,
responde `409` con el resultado original en `details`. Si dos solicitudes compiten,
la restricción única decide; la perdedora relee la fila y aplica la misma regla. El
hash normaliza espacios y el monto con `stripTrailingZeros()`, de modo que
`100.000` y `100.00` representan el mismo payload.

## Consecuencias

### Positivas

- La base de datos es la fuente de verdad de la unicidad.
- Replay y conflicto tienen respuestas distintas y explícitas.
- No se duplica el descuento ni la fila.

### Negativas

- El hash debe mantenerse estable ante cambios de contrato.
- El cliente debe enviar una referencia única por operación.

### Riesgos

- Colisiones SHA-256. Mitigación: riesgo despreciable para el alcance y comparación
  adicional de la fila existente.

## Referencias

- `back/domain/model/.../PayloadHasher.java`
- `PreApprovedUsageIntegrationTest` (replay, conflicto y concurrencia)
- `docs/api/openapi.yaml` (respuestas `200` y `409`)
