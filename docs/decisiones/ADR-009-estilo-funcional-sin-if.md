# ADR-009: Estilo funcional sin `if` en el código principal

## Estado

Aceptado

## Contexto

El código principal concentraba **23 `if` imperativos**, distribuidos así:

| Capa | `if` | Qué resolvían |
|---|---:|---|
| `PreApprovedUsageHandler` | 11 | Validación de campos y longitudes, escala del monto, `amount` nulo y conflicto de referencia |
| `PreApprovedUsagePolicy` | 5 | Monto inválido, preaprobado inexistente, cliente distinto, estado no activo y cupo insuficiente |
| `PreApprovedUsageR2dbcAdapter` | 4 | `amount` nulo, violación de unicidad, comparación de hash y recorrido de causas |
| `ApiExceptionHandler` | 3 | Cadena de `instanceof` para validación, conflicto y request inválido |

La referencia interna de CrediYa (`pragma`) evita el `if` en el camino feliz y lo
reemplaza por Bean Validation, `Optional`, operadores reactivos y despacho por mapas.
Se quiere aplicar el mismo criterio en BancoIAS.

## Motivación del refactor

El objetivo no era “quitar `if` por moda”, sino mejorar el flujo:

1. **Separar responsabilidades.** La validación del contrato sale del handler y pasa
   a Bean Validation + `ValidadorRequest`. El handler queda como orquestador.
2. **Hacer lineal el camino feliz.** `switchIfEmpty` → `flatMap` → `switch` expresa
   lectura, validación, ejecución y respuesta sin ramas imperativas intermedias.
3. **Expresar la ausencia con operadores.** `switchIfEmpty` y `filter` comunican
   “no hay dato”, “no hay cupo” o “no es una violación de unicidad” mejor que un
   `if` con `return` temprano dentro de una cadena reactiva.
4. **Modelar el resultado.** `Outcome { CREATED, REPLAYED, CONFLICT }` y un `switch`
   exhaustivo eliminan los booleanos `replayed`/`conflict` y obligan a cubrir los
   tres casos.
5. **Extender errores sin tocar el flujo.** Una lista de `ErrorMapping` permite
   agregar un error nuevo sin añadir otro `if instanceof`.
6. **Conservar la legibilidad del dominio.** La cadena de `Optional.or` mantiene el
   orden de prioridad de los rechazos con métodos con nombre de intención.

## Opciones Consideradas

1. **Mantener guard clauses con `if`**. Es legible, pero no cumple el estilo buscado
   y mezcla validación con orquestación.
2. **Reemplazar todo por `switch`**. Elimina algunos `if`, pero no resuelve
   validación, ausencia de valores ni errores reactivos.
3. **Estilo funcional y reactivo**: Bean Validation en el borde, `Optional` en
   dominio, `switchIfEmpty`/`filter` en Reactor, `switch` exhaustivo para resultados
   y despacho por mappings para errores.

## Decisión

Se elige **Opción 3**. El código principal no contiene sentencias `if`:

- Entrada: `@NotBlank`, `@Size`, `@NotNull`, `@MaxDecimalPlaces`,
  `@MaxIntegerDigits` y `ValidadorRequest`.
- Handler: `switchIfEmpty` para el body, `flatMap` para el flujo y `switch`
  exhaustivo sobre `UsageProcessingResult.Outcome`.
- Dominio: cadena de `Optional.or(...)` para los motivos de rechazo.
- Persistencia: `Mono.justOrEmpty`, `filter`, `switchIfEmpty`, ternario y
  `Stream.iterate` para la cadena de causas.
- Errores: lista de `ErrorMapping` con `isInstance`, equivalente al `Map` de
  `GlobalExceptionHandler` de pragma.
- Se conserva `try/catch` en el parseo de `limit`, porque no es una decisión de
  negocio.

## Justificación Detallada

La validación de contrato pertenece al borde HTTP y se expresa mejor con
anotaciones declarativas. La ausencia de datos se expresa con `switchIfEmpty`; el
filtrado de un error de unicidad, con `filter` + `switchIfEmpty`; las reglas de
negocio, con una cadena de `Optional` que conserva el orden de prioridad. El
resultado de procesamiento se modela con un enum, de modo que el `switch` sea
exhaustivo y el compilador detecte un caso faltante. Los `if` de validación,
longitud y errores desaparecen del camino principal.

## Consecuencias

### Positivas

- Menos ramas imperativas y una intención más explícita por operador.
- Validación declarativa y reutilizable.
- El `switch` de resultado obliga a cubrir creado, replay y conflicto.
- El dominio conserva su orden de rechazo sin `if`.

### Negativas

- `Optional.or`, `Stream.iterate` y `switchIfEmpty` pueden ser menos obvios para
  quien no está familiarizado con el estilo.
- Se agregan constraints y un validador propio.
- Los mensajes de error de Bean Validation deben mantenerse alineados con el
  contrato.

### Riesgos

- Perder legibilidad por “functional golf”. Mitigación: métodos pequeños con
  nombres de intención (`invalidAmount`, `customerMismatch`, `blocked`).
- Cambiar mensajes o códigos HTTP. Mitigación: tests de handler, integración,
  smoke test y contrato OpenAPI regenerado.

## Referencias

- `PreApprovedUsageHandler`
- `PreApprovedUsagePolicy`
- `PreApprovedUsageR2dbcAdapter`
- `ValidadorRequest`, `MaxDecimalPlaces`, `MaxIntegerDigits`
- `docs/api/openapi.yaml`
