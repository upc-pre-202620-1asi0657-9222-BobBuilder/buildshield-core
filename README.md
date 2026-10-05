# buildshield-core

Core logístico de BuildShield: **monolito modular** con los módulos `iam`, `organization`, `inventory`, `ordering`, `dispatch`, `subscription`, `notification` y `audit`. Se comunica con buildshield-reception solo por eventos (RabbitMQ).

## Requisitos
- Java 21
- Docker (para `buildshield-infra` y las pruebas con Testcontainers)

## Estructura
```
pe.buildshield.core.<modulo>                  fachada pública del módulo
pe.buildshield.core.<modulo>.interfaces       REST, DTO, consumidores
pe.buildshield.core.<modulo>.application      casos de uso
pe.buildshield.core.<modulo>.domain           modelo (sin Spring ni JPA)
pe.buildshield.core.<modulo>.infrastructure   persistencia y adaptadores
```
`ModuleBoundariesTest` (ArchUnit) hace cumplir que un módulo solo use la fachada de otro y que el dominio no dependa de Spring ni JPA.

## Ejecutar en local
```bash
# 1. Levantar PostgreSQL y RabbitMQ (ver ../buildshield-infra)
# 2. Exportar las variables de .env y arrancar con el perfil local
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
Salud: `GET http://localhost:8080/actuator/health`

| Variable | Perfil | Descripción |
|---|---|---|
| `CORE_DB_USER`, `CORE_DB_PASSWORD` | local | Credenciales de `core_db` |
| `CORE_DB_HOST`, `CORE_DB_PORT`, `CORE_DB_NAME` | local | Opcionales (localhost:5432/core_db) |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | prod | Conexión a PostgreSQL |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | local / prod | Broker |

## Pruebas
```bash
./mvnw verify
```
Ejecuta las pruebas unitarias y de arquitectura, las de integración (`*IT`, con Testcontainers; se omiten si no hay Docker) y el control de cobertura de JaCoCo (≥ 80 % de líneas en `domain` y `application`). Reporte: `target/site/jacoco/index.html`.

## Ramas
GitFlow: `main`, `develop`, `feature/*`, `release/*`, `hotfix/*`. Commits con Conventional Commits.
