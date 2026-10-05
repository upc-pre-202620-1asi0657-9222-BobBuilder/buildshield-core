/**
 * Configuración del kernel compartido (reloj, multiempresa, auditoría JPA, web, JWT e idempotencia).
 * Se registra como autoconfiguración del propio Core (META-INF/spring) para que sus condiciones
 * se evalúen después de las clases del Core y las pruebas por capas puedan importarla.
 */
package pe.buildshield.core.shared.config;
