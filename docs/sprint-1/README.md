# Sprint 1: resumen de buildshield-core

> **Nota posterior al cierre:** después de este sprint la arquitectura pasó a ser un solo monolito modular. buildshield-commons se integró al Core como `pe.buildshield.core.shared`, Recepción pasó a ser el módulo `reception` del Core y se retiró RabbitMQ. Los números de este documento corresponden a la corrida del cierre, antes de ese cambio.

Datos tomados de la corrida local de `./mvnw clean verify` del 4 de octubre de 2026 (rama `feature/sprint-1-closure`): **BUILD SUCCESS**.

## 1. Qué se hizo

| Módulo | Historias | Endpoints |
|---|---|---|
| iam | US01 registro de organización, US02 inicio de sesión, US03 cierre de sesión, US04 usuarios y roles, US05 recuperación de contraseña | `/api/v1/auth/*`, `/api/v1/users` |
| organization | US14 obras, US15 almacenes y centros de acopio, US16 catálogo de materiales, US17 asignación de personal | `/api/v1/worksites`, `/api/v1/warehouses`, `/api/v1/materials`, `/api/v1/assignments` |
| inventory | Carga y consulta de existencias (soporte para pedidos y despachos; no es una historia del backlog) | `/api/v1/stock/entries`, `/api/v1/stock` |
| ordering | US18 crear pedido, US20 aprobar o rechazar, US21 consultar estado | `/api/v1/orders`, `/{id}`, `/{id}/approve`, `/{id}/reject` |

En total, **31 operaciones REST**, todas con `@PreAuthorize` y documentadas en OpenAPI.

Decisiones de diseño aplicadas:
- **Monolito modular.** Cada módulo expone solo su fachada; `ModuleBoundariesTest` (ArchUnit) lo hace cumplir, junto con que el dominio no dependa de Spring ni de JPA. Un esquema de base de datos por módulo, sin llaves foráneas entre esquemas.
- **Multiempresa.** Toda tabla de negocio tiene `organization_id`, filtrado con `@TenantId` según el token. Un recurso de otra organización responde 404.
- **Seguridad.** JWT RS256 de 15 minutos, tokens de renovación rotados y guardados como SHA-256, lista de revocación del token de acceso al cerrar sesión. Una cadena de seguridad aparte para los endpoints públicos.
- **Pedidos.** Patrón State: Registrado → EnRevision → ParcialmenteAtendido → Atendido → Cerrado, y Cancelado. Por línea: pendiente = solicitado − despachado − cancelado.
- **Stock.** Descuento con UPDATE condicionado por `version` y cantidad disponible, hasta 3 reintentos; `CHECK (available_qty >= 0)` como última defensa.
- **Visibilidad por asignación.** El administrador ve todo. El encargado de obra ve sus obras y los almacenes activos. El encargado de almacén ve sus almacenes.

Entregables del cierre del sprint:
- Los escenarios Cucumber corren por **HTTP real** contra el Core en un puerto aleatorio (`RANDOM_PORT` + `RestClient`), con PostgreSQL y RabbitMQ de Testcontainers.
- Cada `.feature` lleva el id de su historia en el nombre y la etiqueta `@USxx`. El de existencias se renombró `soporte_carga_existencias.feature` y lleva `@soporte`.
- Documentación OpenAPI completa en `/swagger-ui.html`: descripción, parámetros con descripción y ejemplo, y ejemplo de respuesta en cada operación. La exige `OpenApiCompletenessIT`.
- Colección Postman en [`docs/postman`](../postman).

## 2. Pruebas

| Tipo | Cantidad | Qué cubre |
|---|---|---|
| Unitarias (surefire) | 390 | Dominio y casos de uso, arquitectura (ArchUnit), capa web con la seguridad real (`RoleAuthorizationTest`: 104 casos de la matriz de roles, más la regla de que ningún endpoint quede sin `@PreAuthorize`) |
| Integración (failsafe) | 148 | Persistencia, aislamiento, concurrencia y OpenAPI con PostgreSQL y RabbitMQ reales; incluye los 76 escenarios Cucumber |
| Aceptación (Cucumber, HTTP real) | 76 escenarios, 76 pasan | 72 de las 12 historias + 4 de soporte |

Atributos de calidad **medidos** por una prueba:

| Atributo | Prueba | Resultado |
|---|---|---|
| QAS02: el stock nunca queda negativo con descuentos concurrentes | `StockConcurrencyIntegrationTest`: dos hilos descuentan sin stock para ambos, 50 repeticiones | 50 de 50: gana uno solo y el saldo nunca es negativo |
| Aislamiento entre organizaciones (organization) | `OrganizationIsolationIntegrationTest` | 16 de 16 intentos de acceso cruzado denegados (404) |
| Aislamiento entre organizaciones (orders y stock) | `OrdersStockIsolationIntegrationTest` | 11 de 11 intentos denegados |
| Documentación de la API completa | `OpenApiCompletenessIT` | 31 de 31 operaciones completas |

Verificación manual en local:
- Colección Postman con `newman` contra el Core local (perfil `local`, `buildshield-infra`): **21 de 21 peticiones y 32 de 32 aserciones**. Se corrió dos veces seguidas contra la misma base, sin conflictos.
- Swagger UI revisado en el navegador: las operaciones muestran descripción, ejemplos de parámetros y ejemplos de respuesta.

## 3. Cobertura JaCoCo (líneas)

Unitarias e integración juntas (un solo `jacoco.exec`). La regla del build exige al menos 80 % en `domain` + `application`.

| Módulo | domain | application | interfaces | infrastructure |
|---|---|---|---|---|
| iam | 100,0 % (119/119) | 98,8 % (163/165) | 100,0 % (39/39) | 90,8 % (89/98) |
| organization | 100,0 % (224/224) | 100,0 % (158/158) | 100,0 % (62/62) | 96,2 % (127/132) |
| inventory | 100,0 % (26/26) | 100,0 % (58/58) | 100,0 % (9/9) | 100,0 % (36/36) |
| ordering | 100,0 % (165/165) | 100,0 % (53/53) | 100,0 % (21/21) | 96,4 % (53/55) |

- **domain + application: 99,8 % (966/968)**, por encima del mínimo de 80 %.
- Total del Core: 98,5 % (1481/1504).

Reporte completo: `target/site/jacoco/index.html`.

## 4. Cobertura de criterios por historia

No hay un backlog con criterios formales en el repositorio. Los criterios son los que se escribieron como escenarios a partir de la descripción de cada historia, un escenario por criterio. Esta tabla dice cuáles se prueban de punta a punta por HTTP y cuáles no.

| Historia | Escenarios | Pasan | Estado | Qué falta |
|---|---|---|---|---|
| US01 Registro de organización | 5 | 5 | Cubierta | — |
| US02 Inicio de sesión | 5 | 5 | **Parcial** | "Un usuario inactivo no inicia sesión" solo se prueba en una prueba unitaria: todavía no hay endpoint para desactivar usuarios |
| US03 Cierre de sesión | 4 | 4 | Cubierta | — |
| US04 Gestión de usuarios y roles | 7 | 7 | Cubierta | Alcance actual: crear y listar. Cambiar el rol o desactivar usuarios no está implementado |
| US05 Recuperación de contraseña | 6 | 6 | Cubierta | El correo usa el adaptador falso (`LoggingEmailAdapter`); falta un adaptador real para producción |
| US14 Registro de obra | 6 | 6 | Cubierta | — |
| US15 Registro de almacén | 6 | 6 | Cubierta | — |
| US16 Catálogo de materiales | 6 (9 ejemplos) | 9 | Cubierta | — |
| US17 Asignación de personal | 7 | 7 | Cubierta | — |
| US18 Crear pedido | 7 (8 ejemplos) | 8 | Cubierta | Se agregó en este cierre el escenario del material retirado del catálogo (409 `MATERIAL_INACTIVE`) |
| US20 Aprobar o rechazar pedido | 6 | 6 | Cubierta | — |
| US21 Consultar estado del pedido | 3 | 3 | **Parcial** | "Recibido por material" solo se prueba con valor 0, porque depende de los eventos del Reception Service. Lo despachado también vale 0 hasta que exista el módulo dispatch |

Resumen: **10 historias cubiertas y 2 parciales** (US02 y US21), con 76 de 76 escenarios en verde.

## 5. Pendientes que pasan al Sprint 2

- Desactivar usuarios y cambiar su rol (completa US02 y amplía US04).
- Módulo dispatch y eventos de Recepción, para que "despachado" y "recibido" de US21 tengan valores reales.
- Adaptador de correo real para US05.
- Publicar `buildshield-commons` 0.1.1 en un repositorio Maven accesible al CI. Hoy solo está en el repositorio local.

## 6. Cómo ejecutar

```bash
# Todas las pruebas, la cobertura y los escenarios (requiere Docker)
./mvnw clean verify
```

- Reporte Cucumber: `target/cucumber-report.html` (JSON en `target/cucumber.json`).
- Cada `.feature` lleva la etiqueta de su historia (`@US18`, por ejemplo) para filtrar en el IDE.
- Swagger: con el Core levantado en local (ver el README del repositorio), abrir `http://localhost:8080/swagger-ui.html`.
- Postman: importar los dos archivos de `docs/postman`, elegir el environment *BuildShield local* y ejecutar la colección en orden. O, por línea de comandos:

```bash
npx newman run docs/postman/BuildShield-Sprint1.postman_collection.json -e docs/postman/BuildShield-local.postman_environment.json
```
