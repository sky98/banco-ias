# ADR-002: Scaffold Bancolombia Clean Architecture 4.7.4

## Estado

Aceptado

## Contexto

La prueba pide buenas prácticas, patrones de diseño y una arquitectura explicable,
pero no impone una estructura. El equipo ya cuenta con el scaffold Bancolombia y se
busca una base moderna compatible con Java 21, Spring Boot 4.1.1 y WebFlux.

## Opciones Consideradas

1. **Proyecto Spring Boot creado manualmente** con paquetes por capa. Máximo control,
   pero exige construir desde cero la separación de módulos y las reglas de
   dependencia.
2. **Scaffold Bancolombia Clean Architecture 4.7.4**. Genera módulos por capa,
   convenciones del banco y reglas ArchUnit; requiere adaptar el dominio nuevo.
3. **Arquitectura hexagonal implementada a mano**. Clara, pero con más decisiones
   de estructura y menor reutilización del conocimiento existente.

## Decisión

Se elige **Opción 2: scaffold 4.7.4**, aplicado a un dominio nuevo y no a una copia
de otro producto.

## Justificación Detallada

El scaffold aporta la separación `model` / `usecase` / `entry-points` /
`driven-adapters` / `app-service`, además de pruebas ArchUnit que evitan
dependencias invertidas. Se eligió la serie 4.7.4 por estabilidad y compatibilidad
con Spring Boot 4.1.1; la serie 5.0.2 se evaluó, pero no era necesaria para el
alcance. El scaffold impone convenciones útiles y deja el foco en las reglas de
negocio y la consistencia.

## Consecuencias

### Positivas

- Límites de capas verificados con ArchUnit.
- Estructura reconocible para el evaluador y para el equipo Bancolombia.
- Menos código de infraestructura propio.

### Negativas

- Más módulos y archivos que un proyecto mínimo.
- Algunas convenciones del scaffold no aplican al dominio nuevo y deben adaptarse.

### Riesgos

- Complejidad accidental. Mitigación: mantener cada módulo con una responsabilidad
  concreta y no agregar capas vacías.

## Referencias

- `docs/PLAN-IMPLEMENTACION.md`
- `back/architectureTest` / `ArchitectureTest`
- `back/main.gradle`
