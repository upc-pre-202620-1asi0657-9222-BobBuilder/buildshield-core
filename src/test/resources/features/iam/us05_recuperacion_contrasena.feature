# language: es
@US05
Característica: US05 Recuperación de contraseña
  Como usuario que olvidó su contraseña
  quiero recibir un enlace para definir una nueva
  para volver a acceder sin ayuda del administrador

  Antecedentes:
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"

  Escenario: Solicitud con un correo registrado
    Cuando solicito recuperar la contraseña de "ana@andina.pe"
    Entonces la respuesta tiene estado 202
    Y se envió un correo de recuperación a "ana@andina.pe"

  Escenario: Solicitud con un correo no registrado recibe la misma respuesta
    Cuando solicito recuperar la contraseña de "nadie@andina.pe"
    Entonces la respuesta tiene estado 202
    Y no se envió ningún correo de recuperación

  Escenario: Confirmación con un enlace válido
    Dado que "ana@andina.pe" inició sesión con la contraseña "Segura123"
    Y que "ana@andina.pe" solicitó recuperar su contraseña
    Cuando confirmo la recuperación con el enlace recibido y la nueva contraseña "Nueva12345"
    Entonces la respuesta tiene estado 204
    Y "ana@andina.pe" puede iniciar sesión con la contraseña "Nueva12345" como "administrador"
    Y "ana@andina.pe" no puede iniciar sesión con la contraseña "Segura123"
    Y el token de renovación anterior ya no permite renovar la sesión

  Escenario: El enlace vence a los 30 minutos
    Dado que "ana@andina.pe" solicitó recuperar su contraseña
    Y pasaron 31 minutos
    Cuando confirmo la recuperación con el enlace recibido y la nueva contraseña "Nueva12345"
    Entonces la operación es rechazada con estado 400 y código "INVALID_RESET_TOKEN"

  Escenario: El enlace se usa una sola vez
    Dado que "ana@andina.pe" solicitó recuperar su contraseña
    Y que confirmé la recuperación con el enlace recibido y la nueva contraseña "Nueva12345"
    Cuando confirmo la recuperación con el enlace recibido y la nueva contraseña "Otra12345"
    Entonces la operación es rechazada con estado 400 y código "INVALID_RESET_TOKEN"

  Escenario: La nueva contraseña debe ser segura
    Dado que "ana@andina.pe" solicitó recuperar su contraseña
    Cuando confirmo la recuperación con el enlace recibido y la nueva contraseña "123"
    Entonces la operación es rechazada con estado 400
    Y "ana@andina.pe" puede iniciar sesión con la contraseña "Segura123" como "administrador"
