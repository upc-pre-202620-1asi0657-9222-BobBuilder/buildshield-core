# Evidencia y matriz técnica del backend

Ejecución local del 5 de octubre de 2026, sobre el monolito modular integrado desde origin/main (base 18f031a). Los resultados corresponden a IAM, Organización, Inventario, Pedidos y Auditoría. El informe y sus diagramas no se modificaron.

## Verificación completa

`mvn verify`: **BUILD SUCCESS**, 479 pruebas unitarias y 181 pruebas de integración/aceptación, 0 fallos, 0 errores y 0 omitidas. Incluye HTTP real, PostgreSQL, escenarios Cucumber, ArchUnit y migraciones. Cobertura de líneas en dominio/aplicación: **99,80 %** (985 cubiertas de 987); mínimo existente: 80 %. El número de pruebas no equivale al número de requisitos satisfechos.

Se verificaron registros y respuestas repetidas, claves concurrentes, cuerpos JSON con distinto orden, cambio de cantidad/material/almacén, respuesta perdida tras commit, caducidad y registros antiguos. La inyección de fallos en movimientos o auditoría deshace saldo, movimiento, bitácora y clave. También se comprueban asignaciones actuales, permisos, aislamiento por empresa, decisiones simultáneas de pedidos y protección de UPDATE/DELETE/TRUNCATE.

TDD (prueba que falla antes de implementar): las comprobaciones iniciales de huella, propietario y Location fallaron tres veces; el nuevo endpoint de auditoría devolvía 404; el fallo de la base al comprobar revocación JWT escapaba como excepción. Después de las correcciones, estos casos y la suite completa pasan. El resumen reproducible está en [verification-summary.json](verification-summary.json). Los logs originales están en target/full-verify.log y target/tdd-*.log; sus resúmenes no contienen tokens ni credenciales.

La prueba MigrationUpgradeIT verifica una base vacía y una base con datos en V12, conservando stock, movimientos, empresa, pedido y clave anterior. RuntimeRoleIT usa un rol sin privilegios de propietario para comprobar permisos, DML rechazado y prohibición de alterar triggers. TestDatabase solo limpia bases temporales expresamente reconocidas. Se ejecutó además HealthEndpointIT sin Docker ni configuración externa: BUILD FAILURE, 2 errores, 0 omitidas, con diagnóstico de Docker ausente. Es el resultado esperado de la comprobación negativa; no altera el resultado exitoso de la suite conectada a PostgreSQL.

## Condiciones y carga medida

PostgreSQL 18.2 local en un clúster temporal exclusivo, dos bases independientes para Core/kernel; Java 21.0.10; Maven 3.9.11. El sistema operativo informado por JVM es Windows 10, con 12 procesadores disponibles y heap máximo de 4 261 412 864 bytes. El pool de conexión de pruebas tiene máximo 6 conexiones. Aplicación, PostgreSQL y generador se ejecutaron en la misma máquina; no se dispone de una medición de carga externa ni de red móvil.

50 clientes concurrentes durante 60 segundos, en ciclo cerrado (cada cliente espera la respuesta antes de enviar otra consulta), sin pausa entre solicitudes. Comparten una sesión de administrador. Dataset preparado: una empresa, un almacén, un material, un pedido y una existencia. No son 50 identidades ni una simulación de 500 recepciones.

**99 677 solicitudes, 0 errores.** Datos sin redondear en [backend-load.json](backend-load.json).

| Consulta | Solicitudes | Mediana (ms) | Percentil 95 (ms) | Máximo (ms) |
|---|---:|---:|---:|---:|
| Existencias | 33 223 | 21,60 | 77,56 | 831,42 |
| Pedido | 33 227 | 20,82 | 76,28 | 559,16 |
| Auditoría | 33 227 | 20,86 | 74,81 | 666,05 |

El percentil 95 es el tiempo que no supera el 95 % de solicitudes. Estas mediciones son evidencia de consultas de este backend y dataset, sin acreditar el cotejo de Recepciones, el escalado horizontal ni disponibilidad mensual.

## Matriz Driver → QAS → decisión → táctica/patrón → componente → verificación → resultado

Un driver es una necesidad que guía la arquitectura. Un QAS es un escenario concreto y medible de calidad. Se conserva la referencia QAS del informe, indicando expresamente cuando solo se valida una parte o un mecanismo reutilizable.

| Driver | QAS y medida aplicable | Decisión | Táctica/patrón | Componente | Mecanismo de verificación | Resultado |
|---|---|---|---|---|---|---|
| No repetir una entrada por pérdida de respuesta | Mecanismo relacionado con QAS10: una entrada confirmada conserva un único efecto | Misma clave UUID para la misma intención; resultado y negocio en una transacción | Idempotencia, huella canónica, bloqueo transaccional sin espera | IdempotencyKeyFilter, JdbcIdempotencyStore | BackendGuaranteesIT: repeticiones, concurrencia, socket cerrado después de commit; IdempotencyKeyFilterIT | Verificado para entradas y pedidos. QAS10 de Recepciones pendiente |
| No volver a ejecutar una intención antigua | Mecanismo relacionado con QAS10: 0 ejecuciones al vencer respuesta o encontrar clave legacy | Retirar contenido a las 24 h, conservar marca e identidad | Marca persistente; rechazo seguro | IdempotencyKeyPurger, V13 | BackendGuaranteesIT: reloj +25 h y clave legacy; pruebas unitarias de identidad/huella | Verificado; respuesta vencida/legacy devuelve 409 |
| Impedir saldo negativo bajo concurrencia | Parte de QAS02: 0 saldos negativos en descuentos concurrentes probados | Actualización condicionada por cantidad y versión, hasta 3 reintentos | Bloqueo optimista + CHECK | StockOperations, StockService, repositorio de stock | StockConcurrencyIntegrationTest: 50 repeticiones; BackendGuaranteesIT: rollback por fallos de movimiento y auditoría | Verificado en Inventario. Transacción de Despachos pendiente |
| Una decisión de pedido debe ser consistente | Consistencia local: 1 decisión válida entre aprobar/rechazar simultáneamente | Mantener transición actual; no reservar ni descontar | Estado de dominio, control de versión | OrderService, Order | BackendGuaranteesIT: carrera aprobación/rechazo; OrderServiceTest y OrderStateTest | Verificado: una decisión, un evento; aprobación no descuenta stock |
| Datos de empresa inaccesibles a otra empresa | QAS04 aplicado al Core: accesos ajenos probados denegados y registrados | Tablas compartidas con organization_id; esquemas por módulo | Filtro Hibernate + SQL explícito + FK compuesta | OrganizationTenantResolver, AuditTrail, V15 | OrganizationIsolationIntegrationTest, OrdersStockIsolationIntegrationTest, BackendGuaranteesIT, MigrationUpgradeIT | Verificado para módulos actuales; no acredita módulos futuros |
| Acceso condicionado por rol y asignación | Seguridad: usuarios sin asignación no leen/modifican ni recuperan respuestas | Comprobar JWT/rol y asignación actual antes de ejecutar/repetir | Autorización de métodos y fachada pública | IAM, OrganizationContextFacade, StockReplayAuthorizer, OrderReplayAuthorizer | RoleAuthorizationTest, BackendGuaranteesIT: propietario ajeno, asignación revocada y acceso denegado | Verificado en escenarios ejecutados |
| Reconstruir quién hizo qué | Parte de QAS09: operaciones actuales tienen actor, empresa, recurso, fecha y correlación | Bitácora junto al negocio; denegación en transacción independiente | Audit Store, propagación transaccional | AuditTrail, AccessDenialFilter, servicios de negocio | AuditEndpointIT, BackendGuaranteesIT: historial por recurso, tenant, fallos y ausencia de secretos | Historial del alcance verificado; exportación de custodia <10 s pendiente |
| Conservar el historial sin alteraciones | Parte de QAS05: 0 cambios/borrados/vaciados aceptados en tablas protegidas | Triggers + cuenta de ejecución sin permisos de dueño | Solo anexado, mínimo privilegio | V14, runtime-role.sql, audit.events, stock_movements | BackendGuaranteesIT: UPDATE/DELETE/TRUNCATE; RuntimeRoleIT: permisos y DDL prohibido | Verificado; cadena de huellas y custodia de Despachos pendientes |
| Evitar esperas indefinidas y revelar internos | Soporte a QAS03: respuesta controlada ante fallo temporal; sin acreditar recuperación de infraestructura | Conexión 5 s, sentencia 5 s, bloqueo 2 s; error 503 | Timeouts acotados, clasificación de fallos | application.yml, GlobalExceptionHandler, JwtAuthenticationFilter, IdempotencyKeyFilter | Pruebas unitarias de error temporal en transacción y revocación JWT; revisión de configuración | Configurado y respuesta verificada. Conmutación <60 s y 99,5 % mensual pendientes |
| Consultas útiles bajo carga moderada | Medición complementaria a QAS01/QAS06; no es el escenario de Recepciones | Medir endpoints actuales antes de afirmar rendimiento/escalabilidad | Carga HTTP reproducible, percentiles | BackendLoadIT | 50 clientes/60 s; reporte JSON | 99 677 consultas, 0 errores; p95 entre 74,81 y 77,56 ms en dataset pequeño |
| Evolucionar sin perder datos ni romper BC | Modificabilidad del Core: migración vacía/upgrade y límites pasan | Migraciones nuevas V13–V15; fachadas públicas; kernel sin dominio | Migración incremental, ArchUnit, monolito modular | Flyway, ModuleBoundariesTest, fachadas | MigrationUpgradeIT; mvn verify; migraciones previas intactas | Verificado; no acredita cambio de pasarela QAS08 |
| Complejidad proporcional al equipo | Restricción de entrega modular; cada mecanismo debe proteger un driver | Transacciones locales y fachada mínima de auditoría; retirar dependencia sin uso | Modularidad; mínima complejidad | Core, pom.xml, AuditTrail | ArchUnit, pruebas de consistencia, revisión de uso de Resilience4j | Implementado; independencia de despliegue no se declara necesaria para estos BC |

La configuración de tiempos de espera no sustituye una prueba de caída real de infraestructura. Tampoco una prueba finita demuestra el 100 % de todos los accesos futuros. La evidencia corresponde a los escenarios ejecutados.

## Tradeoff y pendientes

Si se elimina idempotencia se puede repetir una entrada tras perder la respuesta. Sin transacción conjunta puede cambiar el saldo sin movimiento/bitácora. Sin control optimista/CHECK puede competir el stock. Sin filtro de empresa/FK compuesta pueden mezclarse datos. Sin fachada de auditoría y protecciones no hay un historial conservado. Si se elimina un broker o una Saga de estos flujos actuales no se pierde ningún driver implementado: la transacción local basta y no se añadieron.

QAS01 (cotejo), QAS03 (conmutación/disponibilidad mensual), QAS05 (cadena de huellas de custodia), QAS06 (500 recepciones y escalado), QAS07/QAS08 (pagos), QAS09 (exportación de despacho) y QAS10 (Recepciones sin duplicar incluso con otra clave por despacho) siguen pendientes en su alcance completo. QAS02 también requiere el flujo de Despachos para validar el escenario descrito en el informe.

Instrucciones y contratos: [backend-hardening.md](../backend-hardening.md). Lanzador Windows: [verify-backend.ps1](../../scripts/verify-backend.ps1). Su sintaxis se validó; en esta ejecución los comandos Maven se invocaron directamente sobre el clúster temporal. Los comandos permiten repetir la prueba con Docker/PostgreSQL 16 o con PostgreSQL temporal local. CI fue configurado para publicar reportes, pero estos resultados son de la ejecución local.
