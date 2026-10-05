# BuildShield: contexto del proyecto
SaaS multiempresa web de trazabilidad de materiales entre almacén y obra
(pedido -> despacho -> recepción -> cotejo de merma -> discrepancias).

## Arquitectura (decisiones tomadas, no cambiarlas)
- UNA sola unidad desplegable: buildshield-core, MONOLITO MODULAR con los módulos iam,
  organization, inventory, ordering, dispatch, reception, subscription, notification, audit.
  La web (buildshield-web) es el cliente. El docker-compose.yml del Core levanta PostgreSQL local.
- Kernel compartido DENTRO del Core (paquete pe.buildshield.core.shared): JWT, TenantContext,
  errores, idempotencia, correlación. No hay librerías compartidas aparte.
- Los módulos se comunican SOLO por llamadas en proceso a la fachada pública del otro módulo.
  Nunca por HTTP entre módulos, nunca accediendo a su dominio, repositorios ni tablas.
- Despacho + descuento de stock + actualización del pedido + registro de auditoría ocurren en
  UNA transacción local con bloqueo optimista (columna version, hasta 3 reintentos).
- Recepción + actualización de lo recibido en el pedido + cotejo de merma + discrepancias +
  registro de auditoría ocurren en UNA transacción local.
- La anulación de un despacho en tránsito es una transacción local (sin saga).
- Multiempresa: tablas compartidas con organization_id en TODA tabla de negocio. Un filtro
  de persistencia agrega la organización del token a cada consulta.
- Auditoría: tabla audit_log de solo anexado (el usuario de la app solo tiene INSERT y SELECT),
  cada fila guarda la huella SHA-256 de la anterior. NO es Event Sourcing.
- Idempotencia en recepción: cabecera Idempotency-Key (UUID del navegador), tabla
  idempotency_keys (24 h), 409 si la operación está en curso, UNIQUE(dispatch_id) en receptions.
- Pagos: PaymentGatewayPort + adaptador, Idempotency-Key, timeout 5 s, circuit breaker
  (Resilience4j), confirmación solo por webhook firmado.
- NO usar: más unidades desplegables ni microservicios, librerías compartidas aparte,
  broker de mensajes, API Gateway propio, MongoDB, CQRS con modelos de lectura,
  Event Sourcing, particionamiento físico, operación sin conexión.

## Stack
Java 21, Spring Boot 3, Maven, PostgreSQL 16 + Flyway, Spring Data JPA,
Resilience4j, springdoc-openapi, JUnit 5, AssertJ, Mockito, Testcontainers, ArchUnit,
JaCoCo (>= 80 % en dominio), Cucumber JVM, k6. Web: Angular 18 + TypeScript, ESLint + Prettier.

## Convenciones
- Identificadores en inglés; textos al usuario en español.
- Paquetes pe.buildshield.core.<modulo>.<capa>, capas interfaces / application / domain /
  infrastructure. El dominio no depende de Spring ni de JPA. Cada módulo expone solo su
  fachada en pe.buildshield.core.<modulo>; ArchUnit hace cumplir los límites.
- Puertos y adaptadores solo con proveedores externos (pagos, correo, S3).
- API /api/v1, recursos en plural y kebab-case. Códigos: 201, 400, 401, 403,
  404 (recurso fuera de la organización), 409 (conflicto o repetición en curso).
- BD: snake_case, tablas en plural, migraciones V<n>__<desc>.sql, un esquema por módulo,
  sin llaves foráneas entre esquemas.
- Commits: Conventional Commits con alcance, ej. feat(ordering): add order approval.
- Ramas GitFlow: main, develop, feature/us18-create-order, release/v1.0.0, hotfix/v1.0.1.
- Pruebas: un .feature por historia (Gherkin en español: Dado/Cuando/Entonces).

## Reglas de trabajo
- Antes de programar, mostrar un plan y esperar aprobación.
- Escribir primero el .feature y las pruebas, luego la implementación.
- Ejecutar `mvn verify` antes de cada commit; no commitear con pruebas rojas.
- Nunca declarar un atributo de calidad como "cumplido" sin una prueba que lo mida.
- No agregar tecnologías ni patrones fuera de esta lista sin preguntar.
