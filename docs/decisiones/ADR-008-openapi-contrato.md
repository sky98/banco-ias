# ADR-008: OpenAPI anotado y contrato estático

## Estado

Aceptado

## Contexto

La primera versión de la API exponía las rutas en OpenAPI, pero sin `requestBody`,
sin esquemas de respuesta y con todas las operaciones documentadas como `200`. Eso
es engañoso para un consumidor y para la evaluación. Se necesita un contrato
verificable y versionado.

## Opciones Consideradas

1. **Documento OpenAPI escrito a mano**. Control total, pero se desactualiza con
   facilidad y no garantiza que coincida con el código.
2. **Solo anotaciones de Springdoc**. El documento se genera desde el código, pero
   nadie valida el resultado y el contrato no queda en el repositorio.
3. **Anotaciones + contrato estático + test**. Las anotaciones son la fuente de
   verdad, el YAML queda versionado y una prueba de integración verifica el
   contenido generado.

## Decisión

Se elige **Opción 3**: anotaciones `@RouterOperation`, `@Operation`, `@RequestBody` y
`@ApiResponse` en el router y `@Schema` en los DTOs, más
`docs/api/openapi.yaml` generado desde `/v3/api-docs.yaml` y un test de contrato.

## Justificación Detallada

El test `documentsOpenApiContract` arranca el contexto, consulta
`/v3/api-docs` y verifica que el `POST` tenga `requestBody`, que las respuestas
`201/400/409` existan y que el parámetro `limit` esté documentado. Así, un cambio de
contrato rompe la suite antes de llegar a la entrega. El YAML estático permite
revisar el contrato sin levantar la aplicación.

## Consecuencias

### Positivas

- El contrato y el código evolucionan juntos.
- El evaluador puede revisar el YAML y Swagger UI.
- Los errores `400/409` dejan de documentarse como `200`.

### Negativas

- Hay que regenerar el YAML cuando cambia la API.
- Springdoc requiere permitir la lectura de classpath en las pruebas con BlockHound.

### Riesgos

- Deriva entre YAML y código. Mitigación: regeneración documentada y test de
  contrato en la suite.

## Referencias

- `RouterRest.java`
- `PreApprovedUsageIntegrationTest.documentsOpenApiContract`
- `docs/api/openapi.yaml`
