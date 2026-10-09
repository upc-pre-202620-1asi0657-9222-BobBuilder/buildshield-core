package pe.buildshield.core.dispatch.application;

import java.util.UUID;

/**
 * Puerto de la evidencia de un despacho (foto del ticket de balanza). En la arquitectura objetivo la
 * implementa un adaptador de Amazon S3; en los perfiles local y test, un adaptador local que solo valida
 * y conserva la referencia. El dominio guarda únicamente la URL devuelta.
 */
public interface EvidenceStorage {

    /**
     * Registra la evidencia del pesaje de salida y devuelve la URL con la que se consultará.
     *
     * @throws pe.buildshield.core.shared.error.ValidationException si la referencia no es válida
     */
    String registerTicketPhoto(UUID dispatchId, String ticketPhotoUrl);
}
