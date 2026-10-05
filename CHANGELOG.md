# Cambios por versión

Se utiliza versionado semántico: MAYOR.MENOR.PARCHE. Una corrección compatible incrementa PARCHE; una funcionalidad compatible incrementa MENOR; un cambio incompatible en la API incrementa MAYOR. Las versiones publicadas conservan su tag y contenido originales.

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
