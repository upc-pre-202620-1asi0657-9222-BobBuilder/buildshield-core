package pe.buildshield.core.dispatch.infrastructure.evidence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pe.buildshield.core.dispatch.application.EvidenceStorage;
import pe.buildshield.core.shared.error.ErrorDetail;
import pe.buildshield.core.shared.error.ValidationException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador local de {@link EvidenceStorage}: no sube archivos ni llama a AWS. Acepta una URL http(s)
 * de la foto ya subida por el cliente (por ejemplo, a la URL firmada que entregará el adaptador S3) y la
 * conserva tal cual. Cuando existan credenciales se reemplaza por el adaptador de Amazon S3.
 */
@Component
class LocalEvidenceStorage implements EvidenceStorage {

    static final int MAX_LENGTH = 500;
    private static final Logger log = LoggerFactory.getLogger(LocalEvidenceStorage.class);

    @Override
    public String registerTicketPhoto(UUID dispatchId, String ticketPhotoUrl) {
        if (ticketPhotoUrl == null || ticketPhotoUrl.isBlank()) {
            return null;
        }
        String url = ticketPhotoUrl.trim();
        if (url.length() > MAX_LENGTH || !isHttp(url)) {
            throw new ValidationException("INVALID_EVIDENCE_URL", "La foto del ticket debe ser una URL http o https",
                    List.of(new ErrorDetail("ticketPhotoUrl", "URL http(s), máximo 500 caracteres")));
        }
        log.debug("Evidencia del despacho {} registrada localmente", dispatchId);
        return url;
    }

    private static boolean isHttp(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (URISyntaxException ex) {
            return false;
        }
    }
}
