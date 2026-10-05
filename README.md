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
- los escenarios de aceptación de `src/test/resources/features` con Cucumber (`CucumberIT`; reporte en `target/cucumber-report.html`);
- el control de cobertura de JaCoCo (≥ 80 % de líneas en `domain` y `application`; reporte en `target/site/jacoco/index.html`).

El perfil `test` usa un par de claves RSA **solo de prueba** (`src/test/resources/application-test.yml`), que no se incluye en el jar.

## Ramas
GitFlow: `main`, `develop`, `feature/*`, `release/*`, `hotfix/*`. Commits con Conventional Commits.
