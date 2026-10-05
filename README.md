# buildshield-core

Core logístico de BuildShield: **monolito modular** con los módulos `iam`, `organization`, `inventory`, `ordering`, `dispatch`, `subscription`, `notification` y `audit`. Se comunica con buildshield-reception solo por eventos (RabbitMQ). Usa la librería `buildshield-commons` 0.1.1 (JWT, multiempresa, errores, outbox).

## Requisitos
- Java 21
- Docker (para `buildshield-infra` y las pruebas con Testcontainers)
- `buildshield-commons` 0.1.1 instalada en el repositorio Maven local (`./mvnw install` en ese repositorio)

## Estructura
```
pe.buildshield.core.<modulo>                  fachada pública del módulo
pe.buildshield.core.<modulo>.interfaces       REST, DTO, consumidores
pe.buildshield.core.<modulo>.application      casos de uso
pe.buildshield.core.<modulo>.domain           modelo (sin Spring ni JPA)
pe.buildshield.core.<modulo>.infrastructure   persistencia y adaptadores
```
`ModuleBoundariesTest` (ArchUnit) hace cumplir que un módulo solo use la fachada de otro y que el dominio no dependa de Spring ni JPA.

## Módulo iam (US01–US05)

| Método y ruta | Quién | Descripción |
|---|---|---|
| `POST /api/v1/auth/sign-up` | público | Crea la organización (RUC de 11 dígitos, único) y su administrador (correo único) en una transacción → 201 |
| `POST /api/v1/auth/sign-in` | público | Token de acceso JWT RS256 de 15 min + token de renovación → 200; credenciales inválidas → 401 |
| `POST /api/v1/auth/refresh` | público | Rota el token de renovación (el usado queda revocado) → 200; inválido o vencido → 401 |
| `POST /api/v1/auth/sign-out` | autenticado | Revoca el token de renovación y el token de acceso usado hasta que venza → 204 |
| `POST /api/v1/users` | administrador | Crea un usuario de su organización (ADMINISTRATOR, WAREHOUSE_MANAGER, SITE_MANAGER) → 201 |
| `GET /api/v1/users` | administrador | Usuarios de su organización → 200 |
| `POST /api/v1/auth/password-reset` | público | Envía un enlace válido 30 min, de un solo uso; misma respuesta exista o no el correo → 202 |
| `POST /api/v1/auth/password-reset/confirm` | público | Cambia la contraseña y cierra las sesiones renovables → 204; enlace inválido → 400 |

Detalles de seguridad:
- Contraseñas con bcrypt (costo 12); la política exige entre 8 y 72 caracteres, con letra y número.
- Los tokens de renovación y de recuperación son aleatorios de 256 bits; en la base solo se guarda su SHA-256.
- La autorización por rol está en cada endpoint (`@PreAuthorize`). `RoleAuthorizationTest` verifica la matriz completa y que ningún endpoint quede sin regla.
- Documentación OpenAPI: `http://localhost:8080/swagger-ui.html` (especificación en `/api/v1/api-docs`).
- Correo: el puerto `EmailPort` tiene por ahora solo un adaptador **falso** (`LoggingEmailAdapter`), que escribe el enlace en el log y solo existe en los perfiles `local` y `test`. Con el perfil `prod` la aplicación no arranca hasta que exista un adaptador real.

## Módulo organization (US14–US17)

| Recurso | POST | GET (lista y por id) | PATCH |
|---|---|---|---|
| `/api/v1/worksites` (obras) | administrador | autenticado, según visibilidad | administrador |
| `/api/v1/warehouses` (almacenes y centros de acopio) | administrador | autenticado, según visibilidad | administrador (`active=false` desactiva) |
| `/api/v1/materials` (catálogo) | administrador | todos (los encargados, solo activos) | administrador (el SKU no cambia) |
| `/api/v1/assignments` (asignaciones) | administrador | administrador: todas; encargado: las suyas | administrador (`active=false` termina) |

Reglas:
- **Obra:** la fecha de fin es opcional y nunca anterior a la de inicio (`INVALID_DATE_RANGE`).
- **Almacén:** no se borra, se desactiva y conserva su historial. No se le asigna personal mientras está desactivado (`SITE_INACTIVE`, 409).
- **Material:** SKU único por organización, sin distinguir mayúsculas (`SKU_ALREADY_EXISTS`, 409). Unidad del catálogo. Tolerancia de merma de 0 a 100 %, con hasta 2 decimales.
- **Asignación:** el encargado de obra va a obras y el de almacén a almacenes (`ROLE_NOT_ALLOWED_FOR_SITE`, 400). Hay una sola asignación activa por usuario y lugar. Al terminar queda en el historial.
- **Visibilidad:**
  - el administrador ve todo;
  - el encargado de obra ve sus obras asignadas y los almacenes activos (para pedir material);
  - el encargado de almacén ve sus almacenes asignados y ninguna obra.
- **Todo se filtra por organización.** Un recurso de otra organización, o uno que el usuario no puede ver, responde **404**. `OrganizationIsolationIntegrationTest` lo mide en los cuatro endpoints: 16 de 16 intentos denegados.

Integración con otros módulos:
- `OrganizationContextFacade` es la única puerta para los demás módulos (pedidos, despachos). Devuelve snapshots de obra, almacén y material, e indica si un usuario está asignado a un lugar.
- organization no depende de iam. Pide los datos del usuario por el puerto `StaffDirectory`, que implementa iam.

## Módulo inventory

| Método y ruta | Quién | Descripción |
|---|---|---|
| `POST /api/v1/stock/entries` | administrador o encargado asignado al almacén | Entrada de material (carga de existencias) → 201 con el saldo |
| `GET /api/v1/stock?warehouseId=` | autenticado | Administrador: todos los almacenes; encargado de almacén: los suyos; encargado de obra: los activos |

**Fachada pública `StockService`**, para otros módulos (despachos):
- `add(almacén, material, cantidad)`: suma stock.
- `deduct(almacén, material, cantidad)`: descuenta con un UPDATE condicionado por `version` y por la cantidad disponible.
  - Si otra operación cambió el ítem, reintenta hasta 3 veces.
  - Si no alcanza, lanza `InsufficientStockException` (409) **sin efectos**.
  - Se une a la transacción de quien llama.
- Cada operación registra un movimiento en `inventory.stock_movements`.
- La restricción `CHECK (available_qty >= 0)` es la última defensa de QAS02.
- `StockConcurrencyIntegrationTest` lo mide: dos hilos descuentan sin stock para ambos, 50 repeticiones; gana uno solo y el saldo nunca queda negativo.

## Módulo ordering (US18, US20, US21)

| Método y ruta | Quién | Descripción |
|---|---|---|
| `POST /api/v1/orders` | encargado de obra | Pedido para una obra asignada a un almacén activo → 201, Registrado |
| `GET /api/v1/orders`, `/{id}` | autenticado | Administrador: todos; encargado de obra: los de sus obras; encargado de almacén: los de sus almacenes |
| `POST /api/v1/orders/{id}/approve` | administrador o encargado del almacén de origen | Registrado → EnRevision |
| `POST /api/v1/orders/{id}/reject` | administrador o encargado del almacén de origen | Registrado → Cancelado; `reason` obligatorio |

- **Líneas:** cada una guarda solicitado, despachado, cancelado y recibido por separado. **Pendiente = solicitado − despachado − cancelado.** Las cantidades son mayores que cero y no hay materiales repetidos.
- **Estados (patrón State):** Registrado → EnRevision → ParcialmenteAtendido → Atendido → Cerrado, y Cancelado. Una transición no permitida responde 409 `INVALID_ORDER_TRANSITION`.
- **Pendiente para otros módulos:**
  - "recibido" vale 0 hasta que Recepción envíe sus eventos;
  - los despachos (`registerDispatch`) y el cierre los usará el módulo dispatch.

## Ejecutar en local
```bash
# 1. Levantar PostgreSQL y RabbitMQ (ver ../buildshield-infra)
# 2. Generar un par de claves RSA para firmar los JWT (una sola vez, fuera del repositorio)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out ~/.buildshield/jwt-private.pem
openssl rsa -in ~/.buildshield/jwt-private.pem -pubout -out ~/.buildshield/jwt-public.pem
export BUILDSHIELD_JWT_PRIVATE_KEY="$(cat ~/.buildshield/jwt-private.pem)"
export BUILDSHIELD_JWT_PUBLIC_KEY="$(cat ~/.buildshield/jwt-public.pem)"
# 3. Exportar las variables de ../buildshield-infra/.env y arrancar con el perfil local
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
Salud: `GET http://localhost:8080/actuator/health`

| Variable | Perfil | Descripción |
|---|---|---|
| `CORE_DB_USER`, `CORE_DB_PASSWORD` | local | Credenciales de `core_db` |
| `CORE_DB_HOST`, `CORE_DB_PORT`, `CORE_DB_NAME` | local | Opcionales (localhost:5432/core_db) |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | prod | Conexión a PostgreSQL |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | local / prod | Broker |
| `BUILDSHIELD_JWT_PUBLIC_KEY`, `BUILDSHIELD_JWT_PRIVATE_KEY` | local / prod | Claves RSA en PEM para firmar y validar los JWT |
| `BUILDSHIELD_PASSWORD_RESET_URL` | local (opcional) / prod | Página de la web donde se define la nueva contraseña |

## Pruebas
```bash
./mvnw verify
```
Ejecuta:
- pruebas unitarias de dominio y aplicación, y de arquitectura (ArchUnit);
- pruebas de la capa web con la seguridad real (`RoleAuthorizationTest`, `JwtAuthenticationFilterTest`);
- pruebas de integración (`*IT`) con PostgreSQL y RabbitMQ de Testcontainers;
- los escenarios de aceptación de `src/test/resources/features` con Cucumber por HTTP real contra el Core en un puerto aleatorio (`CucumberIT`; reporte en `target/cucumber-report.html` y `target/cucumber.json`). Cada `.feature` lleva la etiqueta de su historia (`@US01` … `@US21`);
- el control de cobertura de JaCoCo (≥ 80 % de líneas en `domain` y `application`; reporte en `target/site/jacoco/index.html`).

El perfil `test` usa un par de claves RSA **solo de prueba** (`src/test/resources/application-test.yml`), que no se incluye en el jar.

## Documentación
- Resumen del Sprint 1, pruebas, cobertura y criterios por historia: [docs/sprint-1](docs/sprint-1/README.md).
- Colección Postman con el flujo del sprint: [docs/postman](docs/postman).

## Ramas
GitFlow: `main`, `develop`, `feature/*`, `release/*`, `hotfix/*`. Commits con Conventional Commits.
