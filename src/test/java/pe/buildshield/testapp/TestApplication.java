package pe.buildshield.testapp;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicación mínima con entidades de prueba para las pruebas de integración del kernel compartido
 * (pe.buildshield.core.shared) contra PostgreSQL real. Está fuera de pe.buildshield.core para que
 * el Core no la escanee.
 */
@SpringBootApplication
public class TestApplication {
}
