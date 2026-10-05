package pe.buildshield.core.iam.interfaces.rest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pe.buildshield.core.shared.error.ErrorResponse;
import pe.buildshield.core.iam.domain.model.AuthenticationFailedException;

/**
 * 401 para credenciales o tokens de renovación inválidos, con el formato común de error. Va antes
 * del manejador global del kernel compartido, que no tiene un caso 401.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class IamExceptionHandler {

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ErrorResponse> handleAuthenticationFailed(AuthenticationFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(ex.getCode(), ex.getMessage()));
    }
}
