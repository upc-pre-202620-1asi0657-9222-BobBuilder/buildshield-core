package pe.buildshield.core.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Configuración del kernel compartido ({@code buildshield.*}). */
@ConfigurationProperties(prefix = "buildshield")
public class BuildshieldProperties {

    private final Security security = new Security();
    private final Idempotency idempotency = new Idempotency();

    public Security getSecurity() {
        return security;
    }

    public Idempotency getIdempotency() {
        return idempotency;
    }

    public static class Security {

        /** Emisor esperado (claim iss). */
        private String issuer = "buildshield";

        /** Clave pública RSA en PEM para validar tokens. Llega por variable de entorno. */
        private String jwtPublicKey;

        /** Clave privada RSA en PEM para emitir tokens. Solo en la unidad que autentica. */
        private String jwtPrivateKey;

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getJwtPublicKey() {
            return jwtPublicKey;
        }

        public void setJwtPublicKey(String jwtPublicKey) {
            this.jwtPublicKey = jwtPublicKey;
        }

        public String getJwtPrivateKey() {
            return jwtPrivateKey;
        }

        public void setJwtPrivateKey(String jwtPrivateKey) {
            this.jwtPrivateKey = jwtPrivateKey;
        }
    }

    public static class Idempotency {

        /** Rutas (patrón Ant) en las que la cabecera Idempotency-Key es obligatoria. */
        private List<String> requiredPaths = new ArrayList<>();

        public List<String> getRequiredPaths() {
            return requiredPaths;
        }

        public void setRequiredPaths(List<String> requiredPaths) {
            this.requiredPaths = requiredPaths;
        }
    }
}
