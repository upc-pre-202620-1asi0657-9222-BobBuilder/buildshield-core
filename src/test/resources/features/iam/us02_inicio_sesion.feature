# language: es
Característica: US02 Inicio de sesión
  Como usuario de una organización
  quiero iniciar sesión con mi correo y contraseña
  para acceder a las funciones de mi rol

  Antecedentes:
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"

  Escenario: Inicio de sesión con credenciales válidas
    Cuando inicio sesión con el correo "ana@andina.pe" y la contraseña "Segura123"
    Entonces la respuesta tiene estado 200
    Y recibo un token de acceso válido por 15 minutos y un token de renovación

  Escenario: Contraseña incorrecta
    Cuando inicio sesión con el correo "ana@andina.pe" y la contraseña "Incorrecta1"
    Entonces la operación es rechazada con estado 401 y código "INVALID_CREDENTIALS"

  Escenario: Correo no registrado recibe la misma respuesta
    Cuando inicio sesión con el correo "nadie@andina.pe" y la contraseña "Segura123"
    Entonces la operación es rechazada con estado 401 y código "INVALID_CREDENTIALS"

  Escenario: Renovación de la sesión con rotación del token de renovación
    Dado que "ana@andina.pe" inició sesión con la contraseña "Segura123"
    Cuando renuevo la sesión
    Entonces la respuesta tiene estado 200
    Y recibo un token de acceso válido por 15 minutos y un token de renovación
    Y el token de renovación anterior ya no permite renovar la sesión

  Escenario: Un token de renovación vencido no renueva la sesión
    Dado que "ana@andina.pe" inició sesión con la contraseña "Segura123"
    Y pasaron 8 días
    Cuando renuevo la sesión
    Entonces la operación es rechazada con estado 401 y código "INVALID_REFRESH_TOKEN"
