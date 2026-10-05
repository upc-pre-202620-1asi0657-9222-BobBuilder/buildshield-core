# Backend: recuperación, consistencia y auditoría

El Core es un monolito modular con PostgreSQL. IAM, Organización, Inventario y Pedidos ejecutan llamadas síncronas en proceso a fachadas públicas. Auditoría registra el historial de esas operaciones. El kernel compartido contiene mecanismos técnicos, y no depende de los BC.

## Contrato de idempotencia

En `POST /api/v1/stock/entries`, `POST /api/v1/orders` y `POST /api/v1/orders/{id}/approve|reject` es obligatorio enviar `Idempotency-Key` con un UUID completo. Una intención nueva necesita una clave nueva; un reintento conserva la clave, el cuerpo, el tipo de contenido y los parámetros.

La identidad incluye empresa, actor, rol, método, ruta, parámetros y SHA-256 del cuerpo. Los objetos JSON se ordenan por nombre de propiedad; el orden de arrays se conserva. El cuerpo está limitado a 1 MiB. El UUID, empresa o usuario enviados dentro del cuerpo no sustituyen la identidad del JWT.

El bloqueo de PostgreSQL dura la transacción. Negocio, movimientos, auditoría exitosa y respuesta se confirman juntos. Solo se guardan respuestas 2xx. Las operaciones fallidas deshacen sus efectos y permiten reintentar. Se revisan las asignaciones actuales antes de reproducir una respuesta de Inventario o Pedidos.

| Situación | Respuesta |
|---|---|
| Misma operación confirmada, dentro de 24 horas | Estado, cuerpo, Content-Type y Location originales; `Idempotent-Replayed: true` |
| Clave en curso | 409 `IDEMPOTENCY_IN_PROGRESS` y `Retry-After: 1` |
| Misma clave con otra ruta, parámetros o datos | 400 `IDEMPOTENCY_KEY_REUSED` |
| Otra identidad o rol | 403 `IDEMPOTENCY_OWNER_MISMATCH` |
| Se retiró la asignación del actor | 403 `FORBIDDEN` |
| Respuesta de más de 24 horas | 409 `IDEMPOTENCY_RESULT_EXPIRED`, sin repetir el negocio |
| Registro histórico sin actor o huella verificable | 409 `IDEMPOTENCY_LEGACY_RECORD`, sin ejecutar otra vez |
| Falta la clave / UUID inválido | 400 `IDEMPOTENCY_KEY_REQUIRED` / `INVALID_IDEMPOTENCY_KEY` |
| Cuerpo demasiado grande | 413 `REQUEST_TOO_LARGE` |
| Fallo temporal de acceso a la base | 503 `TEMPORARILY_UNAVAILABLE` y `Retry-After: 1` |

La tarea horaria retira el contenido de respuestas viejas, pero conserva su identidad. La marca nunca se libera para volver a aplicar una operación confirmada. Esto cambia el comportamiento anterior, que permitía repetir el efecto después de 24 horas.

Si el celular pierde la respuesta, debe repetir la misma solicitud con la misma clave. Puede hacer tres reintentos espaciados, por ejemplo 1, 2 y 4 segundos, respetando Retry-After. Ante un resultado vencido debe consultar el negocio/historial; no generar automáticamente otra clave. No se implementa operación offline ni reintentos automáticos de transacciones completas en el servidor.

## Consistencia y aislamiento

Stock mantiene su actualización condicionada por versión y cantidad disponible: hasta tres reintentos ante conflicto, sin inventario negativo. El saldo, movimiento y evento de auditoría se guardan en una misma transacción. La fachada StockService se une a la transacción de su llamador.

Aprobar pedidos mantiene el comportamiento existente: Registrado → EnRevision. No reserva ni descuenta stock. Despachos y Recepciones todavía no están implementados.

Las tablas son compartidas entre empresas con organization_id; el esquema separa módulos. Hibernate filtra por empresa y JDBC usa condiciones explícitas. Las relaciones de movimientos–stock y líneas–pedido incluyen la empresa en su FK (relación que la base comprueba), dentro del mismo BC.

Las conexiones esperan 5 segundos, las sentencias 5 segundos y los bloqueos 2 segundos. Se ajustan con DB_CONNECTION_TIMEOUT_MS, DB_STATEMENT_TIMEOUT_MS y DB_LOCK_TIMEOUT_MS. La conexión y el socket del controlador PostgreSQL también tienen límites. Se controlan fallos temporales tanto al validar la revocación del JWT como al ejecutar el negocio.

## Auditoría y permisos de base de datos

AuditTrail es la fachada pública del módulo. Registra vistas públicas del estado confirmado y detalles explícitos del stock: empresa, actor, rol, acción, recurso, fecha, correlación y clave de operación cuando existe. No recibe comandos de contraseña ni entidades IAM con credenciales.

Se auditan organizaciones, usuarios, obras, almacenes, materiales, asignaciones, movimientos de inventario y decisiones de pedidos. El alta pública usa el actor técnico SYSTEM_USER_ID porque todavía no existe una sesión autenticada.

Las respuestas 403/404 de usuarios autenticados se registran en una transacción independiente, asociadas al solicitante, método, ruta y recurso identificable. Si falla este registro, la denegación se conserva y se registra el fallo técnico sin secretos. No se revela información de la empresa ajena.

`GET /api/v1/audit/events` exige administrador. Acepta resourceType, resourceId, page desde 0 y size entre 1 y 100 (50 por defecto). Ordena por fecha e identificador descendentes y siempre filtra por la empresa del JWT.

PostgreSQL rechaza UPDATE, DELETE y TRUNCATE sobre audit.events e inventory.stock_movements. No existen endpoints para modificarlos. La limpieza privilegiada de fixtures comprueba que la conexión pertenece a una base temporal de pruebas y restaura los triggers en la misma transacción.

Separar dueño/migrador y cuenta de ejecución:

1. Crear una cuenta de ejecución que no sea superusuario, dueña de tablas ni creadora de roles.
2. Aplicar migraciones con la cuenta propietaria.
3. Aplicar `psql ... -v app_role=buildshield_app -f docs/database/runtime-role.sql` como propietario.
4. En producción, configurar DB_USER/DB_PASSWORD para ejecución y DB_MIGRATION_USER/DB_MIGRATION_PASSWORD para Flyway. Los dos usuarios deben ser distintos.
5. En local, CORE_DB_MIGRATION_USER/CORE_DB_MIGRATION_PASSWORD son opcionales; la compatibilidad conserva las credenciales anteriores si se omiten. Los triggers también protegen ese entorno.

Es un Audit Store (historial complementario al estado actual). No es Event Sourcing: el estado operativo sigue en sus tablas. La cadena de huellas y exportación de custodia asociadas a Despachos siguen pendientes.

## Migraciones y mínima complejidad

V13 conserva la identidad de operaciones y marca las respuestas retiradas; no inventa identidad para registros antiguos. V14 crea la bitácora y las protecciones de solo anexado. V15 agrega relaciones compuestas por empresa. No se modifica ninguna migración publicada ni se corrigen datos históricos inválidos silenciosamente.

No se incorpora broker, Saga ni Outbox: las operaciones actuales se resuelven con transacciones locales. Resilience4j se retiró porque no tenía un uso implementado. Auditoría utiliza una fachada pequeña; no exige replicar todas las capas hexagonales.

## Verificación reproducible

Java 21. Con Docker disponible, `./mvnw -B verify` usa PostgreSQL 16 de Testcontainers. Sin Docker, configurar explícitamente dos bases temporales locales independientes: buildshield_core_it y buildshield_kernel_it. Una ejecución completa no omite pruebas por falta de Docker.

En Windows, `scripts/verify-backend.ps1 -PostgresBin "C:\Program Files\PostgreSQL\18\bin" -RunLoad` crea un clúster temporal exclusivo en TEMP, ejecuta verify y la carga, y detiene el clúster. Acepta MavenCommand/MavenRepository si se usa una instalación o caché específica. Sin PostgresBin utiliza Testcontainers. Debe ejecutarse con permisos suficientes para iniciar PostgreSQL.

Alternativa externa manual:
```text
mvn -B verify
  -Dbuildshield.test.external-database=true
  -Dbuildshield.test.core-url=jdbc:postgresql://127.0.0.1:PUERTO/buildshield_core_it
  -Dbuildshield.test.kernel-url=jdbc:postgresql://127.0.0.1:PUERTO/buildshield_kernel_it
  -Dbuildshield.test.database-user=USUARIO_DE_PRUEBAS
  -Dbuildshield.test.database-password=CLAVE_DE_PRUEBAS
```
Los argumentos pertenecen a una sola invocación. Las bases deben ser exclusivas y desechables; las pruebas limpian sus fixtures. La prueba de migraciones crea y elimina sus propias bases; el usuario de pruebas necesita ese permiso y permisos de dueño para la limpieza, distintos de los de ejecución productiva.

Carga opt-in:
```text
mvn -B -Pload-tests test-compile failsafe:integration-test failsafe:verify
```
Agregar los mismos argumentos de conexión externa si corresponde. Defaults: 50 clientes simultáneos durante 60 segundos, consultas GET de stock, pedido y auditoría, una sesión de administrador compartida. Genera target/verification/backend-load.json. Este conjunto pequeño no representa 500 recepciones, una base productiva grande ni disponibilidad mensual.

La colección Postman conserva una clave por solicitud protegida en variables de colección. Para repetir una operación conserve la variable y los datos; para una intención nueva borre la variable indicada en la descripción de la solicitud.

Los resultados medidos y la matriz Driver–QAS se encuentran en docs/verification/README.md. CI verifica en pushes a develop/main y en PRs, usando PostgreSQL 16 de Testcontainers y publicando los reportes.
