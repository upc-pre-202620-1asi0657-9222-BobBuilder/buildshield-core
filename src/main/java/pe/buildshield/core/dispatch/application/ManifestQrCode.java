package pe.buildshield.core.dispatch.application;

/**
 * Puerto que dibuja el QR del manifiesto (US24). El QR solo es un acceso rápido: codifica el código del
 * manifiesto, con el que la obra encuentra el despacho en tránsito.
 */
public interface ManifestQrCode {

    /** Imagen PNG del QR que codifica {@code content}. */
    byte[] png(String content);
}
