# Cambios por versión

Se utiliza versionado semántico: MAYOR.MENOR.PARCHE. Una corrección compatible incrementa PARCHE; una funcionalidad compatible incrementa MENOR; un cambio incompatible en la API incrementa MAYOR. Las versiones publicadas conservan su tag y contenido originales.

## 1.1.0 — sin publicar

Sprint 2: despachos, recepciones, reservas de stock y gestión completa de usuarios. Detalle y verificación en [docs/sprint-2](docs/sprint-2/README.md).

### Funcionalidades

- **Reservas de stock (RF40, RF41, QAS02).** Aprobar un pedido reserva lo solicitado de cada línea en el almacén de origen (disponible → reservado). Si una línea no alcanza responde 409 `INSUFFICIENT_STOCK` y no cambia nada. La salida del despacho consume la reserva (reservado → salida).
- **Despachos (US22, US23, US24, US27, US30).** `POST/GET /api/v1/dispatches`, `GET /{id}`, `PATCH /{id}/carrier`, `POST /{id}/departure-weighing`, `POST /{id}/depart`, `GET /{id}/manifest`. Varios despachos por pedido sin superar lo pendiente; estados `PREPARED` → `IN_TRANSIT` → `RECEIVED` (patrón State); manifiesto con código único y QR en PNG base64.
- **Recepciones (US35, US37, QAS01, QAS10).** `POST /api/v1/receptions`, `GET /{id}`, `PUT /{id}/lines/{lineId}`, `GET /{id}/comparison`, `POST /{id}/confirm`. Una recepción por despacho; cotejo de solicitado, despachado y recibido con la merma contra la tolerancia; conformidad única que suma el stock de la obra, actualiza el pedido y cierra el despacho.
- **Usuarios (US04).** `PATCH /api/v1/users/{id}` desactiva, reactiva o cambia el rol; revoca las sesiones y los tokens de acceso vigentes dejan de valer. Un usuario desactivado no inicia sesión.
- **Pedidos (US21).** Las líneas exponen su `id`; lo despachado y lo recibido ya son reales.
- Auditoría de reservas, salidas de stock, despachos, pesajes, recepciones, conformidades y cambios de usuarios.

### Configuración y compatibilidad

- Migraciones nuevas V16 (reservas y tipos de movimiento), V17 (despachos) y V18 (recepciones); no se modifican las publicadas.
- `Idempotency-Key` obligatoria también en `POST /api/v1/dispatches`, `POST /api/v1/dispatches/{id}/depart` y `POST /api/v1/receptions/{id}/confirm`.
- Cambio de comportamiento: aprobar un pedido ahora exige stock disponible. Exceder lo pendiente de una línea responde 409 (antes la regla de dominio respondía 400).
- Dependencias nuevas: `com.google.zxing:core` y `javase` 3.5.3 (QR del manifiesto).
- `docs/database/runtime-role.sql` incluye los esquemas `dispatch` y `reception`.
- Colección Postman nueva: `docs/postman/BuildShield-Sprint2.postman_collection.json`.

## 1.0.0 — 2026-10-05

Primera entrega estable del backend BuildShield Core, un monolito modular con PostgreSQL. Incluye IAM, Organización, Inventario, Pedidos y Auditoría. Esta versión define el contrato HTTP documentado en OpenAPI y Postman; no incluye frontend.

### Funcionalidades y garantías

- Registro de empresa y administrador, autenticación JWT, gestión de usuarios y sesiones.
- Obras, almacenes, materiales y asignaciones aislados por empresa.
- Existencias y movimientos; descuentos concurrentes sin saldo negativo y transacción conjunta con el historial.
- Creación, aprobación y rechazo de pedidos, con permisos y control de estados.
- Idempotencia en entradas de inventario y operaciones de pedidos: repetir la misma intención no repite su efecto.
- Historial de negocio por empresa, actor y recurso; endpoint de consulta exclusivo del administrador.
- Protecciones PostgreSQL contra modificación, borrado y vaciado de auditoría y movimientos.
- Esperas de base de datos acotadas y errores temporales controlados.

### Configuración y compatibilidad

- Es obligatorio enviar un UUID en Idempotency-Key al registrar entradas y crear/aprobar/rechazar pedidos.
- Se añaden V13, V14 y V15 sin modificar migraciones anteriores. Los registros antiguos de idempotencia sin identidad verificable se rechazan; las respuestas vencidas no habilitan una nueva ejecución.
- El perfil prod requiere cuentas separadas de ejecución y migración.
- Se retira Resilience4j, que no tenía uso implementado.
- Las comprobaciones automáticas conservan el JAR, su SHA-256 y los reportes; los PRs también comprueban la construcción Docker.

La evidencia anterior y sus condiciones se conservan sin presentarlas como una ejecución nueva. Despachos, Recepciones, pagos, exportación de custodia y validación completa de los QAS de infraestructura siguen pendientes.

Notas completas: [v1.0.0](docs/releases/v1.0.0.md).
