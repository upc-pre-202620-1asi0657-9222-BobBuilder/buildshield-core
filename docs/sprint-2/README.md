# Sprint 2: resumen de buildshield-core

Datos tomados de la corrida local de `mvn -B verify` del 6 de octubre de 2026 (rama `feature/sprint-2-dispatch-reception`, versión `1.1.0-SNAPSHOT`) contra PostgreSQL 18 local con bases temporales externas (`-Dbuildshield.test.external-database=true`): **BUILD SUCCESS**.

## 1. Qué se hizo

| Módulo | Historias y tareas | Endpoints |
|---|---|---|
| inventory | T29 reserva de stock (RF40, RF41, QAS02) | sin endpoints nuevos: la reserva ocurre al aprobar el pedido y se consume al despachar |
| ordering | US21 completa (despachado y recibido reales), `lines[].id` | `/api/v1/orders` (sin rutas nuevas) |
| dispatch | US22, US23 (T27, T28, T30), US24 (T31), US27 (T34), US30 (T33) | `/api/v1/dispatches`, `/{id}`, `/{id}/carrier`, `/{id}/departure-weighing`, `/{id}/depart`, `/{id}/manifest` |
| reception | US35 (T35, T36), US37 (T38) | `/api/v1/receptions`, `/{id}`, `/{id}/lines/{lineId}`, `/{id}/comparison`, `/{id}/confirm` |
| iam | US04 pendiente (T39) | `PATCH /api/v1/users/{id}` |

En total el Core tiene **45 operaciones REST**, todas con `@PreAuthorize` y documentadas en OpenAPI.

### Roles por endpoint nuevo

| Método y ruta | Administrador | Encargado de almacén | Encargado de obra |
|---|---|---|---|
| `POST /api/v1/dispatches` | sí | sí, si está asignado al almacén de origen | no (403) |
| `GET /api/v1/dispatches`, `/{id}`, `/{id}/manifest` | todos | los de sus almacenes | los que van a sus obras |
| `PATCH /{id}/carrier`, `POST /{id}/departure-weighing`, `POST /{id}/depart` | sí | sí, del almacén de origen | no (403) |
| `POST /api/v1/receptions`, `PUT /{id}/lines/{lineId}`, `POST /{id}/confirm` | sí | no (403) | sí, si está asignado a la obra de destino |
| `GET /api/v1/receptions/{id}`, `/{id}/comparison` | sí | sí, del almacén de origen | sí, de la obra de destino |
| `PATCH /api/v1/users/{id}` | sí | no (403) | no (403) |

Un recurso de otra organización o que el usuario no puede ver responde **404**.

### Contrato que usa el frontend

- **Estados del despacho:** `PREPARED` (Preparado), `IN_TRANSIT` (EnTransito), `RECEIVED` (Recibido) y `CANCELLED` (Anulado, reservado para el Sprint 3). El nombre exacto del primer estado es **`PREPARED`**, no `PREPARING`. Cada respuesta trae `status` y `statusLabel`, y `type`/`typeLabel` (`COMPLETE` Completo, `PARTIAL` Parcial).
- **Estados de la recepción:** `IN_PROGRESS` (EnCurso) y `CONFIRMED` (Confirmada).
- **Id de línea del pedido:** `GET /api/v1/orders` y `/orders/{id}` devuelven `lines[].id`; es el `orderLineId` del cuerpo de `POST /api/v1/dispatches`.
- **Despachos por recibir:** `GET /api/v1/dispatches?status=IN_TRANSIT` con el token del encargado de obra devuelve solo los que van a sus obras asignadas.
- **Cotejo:** `GET /api/v1/receptions/{id}/comparison` devuelve `receptionId`, `dispatchId`, `orderId`, `status`, `statusLabel`, `complete`, `withinTolerance` y `lines[]` con `lineId`, `materialId`, `sku`, `materialName`, `unit`, `requested`, `dispatched`, `received`, `difference` (despachado - recibido), `shrinkagePercent`, `tolerancePercent` y `withinTolerance`.
- **Recepción repetida:** abrir una segunda recepción del mismo despacho responde 409 `RECEPTION_ALREADY_EXISTS` con el id de la existente en `details[0].message`.
- **Idempotencia:** `Idempotency-Key` obligatoria en `POST /api/v1/dispatches`, `POST /api/v1/dispatches/{id}/depart` y `POST /api/v1/receptions/{id}/confirm` (además de las del Sprint 1).

### Decisiones de diseño

- **Monolito modular.** dispatch y reception son módulos del Core; se hablan por las fachadas `OrderingFacade`, `DispatchFacade`, `StockService` y `OrganizationContextFacade`, en una transacción local. `ModuleBoundariesTest` lo hace cumplir. Las referencias entre esquemas son ids sin llave foránea.
- **Reserva y consumo (QAS02).** UPDATE condicionado por `version` y por la cantidad disponible o reservada, con hasta 3 reintentos; `CHECK` de no negativos en `stock_items` y de consumo ≤ reserva en `stock_reservations`.
- **Sin asignar de más.** Pedido y recepción se guardan forzando su versión (`PESSIMISTIC_FORCE_INCREMENT`): dos despachos simultáneos del mismo pedido o una conformidad y un cambio de línea simultáneos no se pisan; el segundo responde 409.
- **Patrón State** en el despacho (`DispatchState`), igual que el pedido.
- **Puertos con adaptadores locales:** `EvidenceStorage` (`LocalEvidenceStorage`, valida y conserva la URL del ticket, sin AWS) y `ManifestQrCode` (`ZxingManifestQrCode`).
- **Sesiones al desactivar o cambiar el rol:** se revocan los tokens de renovación y el filtro JWT compara el estado y el rol actuales del usuario, así el token de acceso vigente deja de valer de inmediato (401 `TOKEN_REVOKED`).

## 2. Pruebas

| Tipo | Cantidad | Qué cubre |
|---|---|---|
| Unitarias (surefire) | 605 (antes 479) | Dominio y casos de uso de dispatch, reception, reservas y usuarios; ArchUnit; `RoleAuthorizationTest` con 160 casos (52 nuevos: cada endpoint nuevo contra anónimo y los tres roles) |
| Integración (failsafe) | 264 (antes 181) | Incluye los 120 escenarios Cucumber, `DispatchReceptionFlowIT` (18), `StockReservationConcurrencyIntegrationTest` (21) y `OpenApiCompletenessIT` |
| Aceptación (Cucumber, HTTP real) | 120 escenarios, 120 pasan (1875 pasos) | 37 escenarios nuevos del Sprint 2 y 7 ampliados en US04 y US20 |

Atributos de calidad medidos por una prueba:

| Atributo | Prueba | Resultado |
|---|---|---|
| QAS02: dos reservas simultáneas sobre el mismo stock | `StockReservationConcurrencyIntegrationTest`, 20 repeticiones (stock 10, dos reservas de 7) | 20 de 20: gana una sola, disponible 3, reservado 7 |
| QAS02: dos aprobaciones simultáneas por HTTP | `DispatchReceptionFlowIT.simultaneous_approvals...`, 5 repeticiones | 5 de 5: 200 y 409 `INSUFFICIENT_STOCK`, nunca negativo |
| US23: dos despachos simultáneos del mismo pedido | `DispatchReceptionFlowIT.simultaneous_dispatches...`, 5 repeticiones (pendiente 50, dos de 30) | 5 de 5: 201 y 409, despachado 30, un solo despacho |
| QAS10: dos conformidades simultáneas con claves distintas | `DispatchReceptionFlowIT.simultaneous_confirmations...`, 5 repeticiones | 5 de 5: 200 y 409; stock de la obra sumado una vez y un solo evento `RECEPTION_CONFIRMED` |
| QAS10: misma clave | `DispatchReceptionFlowIT.full_flow...` y escenario de US37 | Respuesta idéntica con `Idempotent-Replayed: true`, sin efectos nuevos |
| QAS01: cotejo sin N+1 | `DispatchReceptionFlowIT.comparison_uses_the_same_number_of_queries...` (estadísticas de Hibernate) | Mismas sentencias con 1 o 4 materiales, como máximo 5 |
| Aislamiento entre organizaciones | `DispatchReceptionFlowIT.another_organization_reaches_no_dispatch_or_reception` | 8 de 8 intentos denegados (404) y lista vacía |
| Documentación de la API completa | `OpenApiCompletenessIT` | 45 de 45 operaciones completas |

Verificación manual en local (perfil `local`, PostgreSQL en el puerto 5433, jar `buildshield-core-1.1.0-SNAPSHOT.jar`):
- `newman` con `BuildShield-Sprint2.postman_collection.json`: **49 de 49 peticiones y 88 de 88 aserciones**, dos corridas seguidas contra la misma base.
- `newman` con la colección del Sprint 1 sobre la misma versión: 22 de 22 peticiones y 32 de 32 aserciones (la aprobación ahora reserva y sigue pasando porque la colección carga 500 antes).

## 3. Cobertura JaCoCo (líneas)

Unitarias e integración juntas. La regla del build exige al menos 80 % en `domain` + `application`.

| Módulo | domain | application | interfaces | infrastructure |
|---|---|---|---|---|
| iam | 100,0 % (127/127) | 98,9 % (186/188) | 100,0 % (42/42) | 90,3 % (93/103) |
| inventory | 100,0 % (46/46) | 100,0 % (105/105) | 100,0 % (9/9) | 100,0 % (69/69) |
| ordering | 100,0 % (184/184) | 100,0 % (69/69) | 100,0 % (21/21) | 96,5 % (55/57) |
| dispatch | 100,0 % (161/161) | 100,0 % (121/121) | 100,0 % (32/32) | 88,9 % (80/90) |
| reception | 100,0 % (89/89) | 100,0 % (82/82) | 100,0 % (19/19) | 98,0 % (50/51) |

- **domain + application: 99,87 % (1559/1561).**
- Total del Core: 97,7 % (2957/3026).

## 4. Cobertura de criterios por historia

| Historia | Escenarios | Pasan | Estado | Qué falta |
|---|---|---|---|---|
| US02 Inicio de sesión | 5 + US04 | todos | Cubierta | "Un usuario inactivo no inicia sesión" ya se prueba por HTTP en US04 |
| US04 Gestión de usuarios y roles | 12 | 12 | Cubierta | Al cambiar el rol, las asignaciones previas no se terminan solas (ver pendientes) |
| US20 Aprobar o rechazar | 8 | 8 | Cubierta | Reserva al aprobar y rechazo sin stock |
| US21 Consultar estado | 3 + 1 en US37 + 1 en US23 | todos | Cubierta | Despachado y recibido reales |
| US22 Generar despacho | 6 | 6 | Cubierta | |
| US23 Partición del pedido | 4 | 4 | Cubierta | |
| US24 Manifiesto | 4 | 4 | Cubierta | El QR se decodifica en la prueba y coincide con el código |
| US27 Pesaje de salida | 5 | 5 | Cubierta | Foto del ticket: solo la URL (adaptador local) |
| US30 Transportista | 5 | 5 | Cubierta | |
| US35 Recepción y cotejo | 7 | 7 | Cubierta | Pesaje de llegada y discrepancias: Sprint 3 |
| US37 Conformidad | 6 | 6 | Cubierta | |

## 5. Pendientes que pasan al Sprint 3

- Anulación de despachos (`CANCELLED`) y liberación de reservas (movimiento RELEASE ya admitido por la tabla).
- Pesaje de llegada, discrepancias (módulo `discrepancy`) y notificaciones.
- Cerrar el pedido (`Atendido` → `Cerrado`) cuando todo lo despachado tenga recepción confirmada.
- Adaptador de Amazon S3 para la evidencia y adaptador real de correo.
- Al cambiar el rol de un usuario, terminar sus asignaciones del rol anterior (hoy se conservan y el administrador debe terminarlas).
- Consulta del stock por obra (hoy lo recibido se guarda en `inventory.stock_items` con la obra como ubicación, pero `GET /api/v1/stock` solo lista almacenes).

## 6. Cómo ejecutar

```bash
# Todas las pruebas, la cobertura y los escenarios (con Docker)
./mvnw clean verify

# Sin Docker, con bases temporales externas
mvn -B verify -Dbuildshield.test.external-database=true \
  -Dbuildshield.test.core-url=jdbc:postgresql://127.0.0.1:5433/buildshield_core_it \
  -Dbuildshield.test.kernel-url=jdbc:postgresql://127.0.0.1:5433/buildshield_kernel_it \
  -Dbuildshield.test.database-user=buildshield_core
```

- Reporte Cucumber: `target/cucumber-report.html`. Filtrar por historia con su etiqueta (`@US22`, `@US37`, …).
- Swagger: con el Core levantado, `http://localhost:8080/swagger-ui.html`.
- Postman:

```bash
npx newman run docs/postman/BuildShield-Sprint2.postman_collection.json -e docs/postman/BuildShield-local.postman_environment.json
```
