# BuildShield: contexto del proyecto
SaaS multiempresa web de trazabilidad de materiales entre almacén y obra
(pedido -> despacho -> recepción -> cotejo de merma -> discrepancias).

## Arquitectura (decisiones tomadas, no cambiarlas)
- Dos unidades desplegables: (1) Core logístico (buildshield-core), MONOLITO MODULAR con
  módulos iam, organization, inventory, ordering, dispatch, subscription, notification, audit;
  (2) Reception Service (buildshield-reception).
- Librería compartida buildshield-commons: JWT, TenantContext, idempotencia, outbox.
- Core y Recepción se comunican SOLO por eventos (RabbitMQ). Nunca por HTTP.
- Dentro del Core: llamadas en proceso. Despacho + descuento de stock + actualización del
  pedido + registro de auditoría ocurren en UNA transacción local con bloqueo optimista
  (columna version, hasta 3 reintentos).
- Eventos Core <-> Recepción: Transactional Outbox (tabla outbox en la misma transacción,
  OutboxRelay con publisher confirms), entrega at-least-once, consumidores idempotentes
  (tabla processed_messages), cola de mensajes fallidos tras 5 intentos.
  Saga coreografiada solo para la anulación de un despacho en tránsito.
- Multiempresa: tablas compartidas con organization_id en TODA tabla de negocio. Un filtro
  de persistencia agrega la organización del token a cada consulta.
- Auditoría: tabla audit_log de solo anexado (el usuario de la app solo tiene INSERT y SELECT),
  cada fila guarda la huella SHA-256 de la anterior. NO es Event Sourcing.
- Idempotencia en recepción: cabecera Idempotency-Key (UUID del navegador), tabla
  idempotency_keys (24 h), 409 si la operación está en curso, UNIQUE(dispatch_id) en receptions.
- Pagos: PaymentGatewayPort + adaptador, Idempotency-Key, timeout 5 s, circuit breaker
  (Resilience4j), confirmación solo por webhook firmado.
- NO usar: API Gateway propio, MongoDB, CQRS con modelos de lectura, Event Sourcing,
  particionamiento físico, operación sin conexión.

## Stack
Java 21, Spring Boot 3, Maven, PostgreSQL 16 + Flyway, Spring Data JPA, Spring AMQP,
Resilience4j, springdoc-openapi, JUnit 5, AssertJ, Mockito, Testcontainers, ArchUnit,
JaCoCo (>= 80 % en dominio), Cucumber JVM, k6. Web: Angular 18 + TypeScript, ESLint + Prettier.

## Convenciones
- Identificadores en inglés; textos al usuario en español.
- Paquetes pe.buildshield.<unidad>.<modulo>.<capa>, capas interfaces / application / domain /
  infrastructure. El dominio no depende de Spring ni de JPA.
- Puertos y adaptadores solo con proveedores externos (pagos, correo, S3, broker).
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
