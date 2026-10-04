# language: es
Característica: US03 Cierre de sesión
  Como usuario autenticado
  quiero cerrar mi sesión
  para que nadie pueda seguir usándola

  Antecedentes:
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"
    Y que "ana@andina.pe" inició sesión con la contraseña "Segura123"

  Escenario: Cierre de sesión exitoso
    Cuando cierro la sesión
    Entonces la respuesta tiene estado 204

  Escenario: Después del cierre el token de renovación no renueva la sesión
    Dado que cerré la sesión
    Cuando renuevo la sesión
    Entonces la operación es rechazada con estado 401 y código "INVALID_REFRESH_TOKEN"

  Escenario: Después del cierre el token de acceso queda revocado
    Dado que cerré la sesión
    Cuando consulto la lista de usuarios
    Entonces la operación es rechazada con estado 401 y código "TOKEN_REVOKED"

  Escenario: No se puede cerrar sesión sin token de acceso
    Cuando cierro la sesión sin token de acceso
    Entonces la operación es rechazada con estado 401
