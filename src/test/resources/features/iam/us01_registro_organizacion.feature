# language: es
Característica: US01 Registro de organización
  Como representante de una empresa constructora
  quiero registrar mi organización con su administrador
  para empezar a usar BuildShield

  Escenario: Registro exitoso de la organización y su administrador
    Cuando registro la organización "Constructora Andina SAC" con RUC "20123456789", administrador "Ana Torres", correo "ana@andina.pe" y contraseña "Segura123"
    Entonces la respuesta tiene estado 201
    Y existe la organización con RUC "20123456789"
    Y "ana@andina.pe" puede iniciar sesión con la contraseña "Segura123" como "administrador"

  Escenario: El RUC debe tener 11 dígitos
    Cuando registro la organización "Constructora Andina SAC" con RUC "2012345", administrador "Ana Torres", correo "ana@andina.pe" y contraseña "Segura123"
    Entonces la operación es rechazada con estado 400
    Y no existe ninguna organización con RUC "2012345"

  Escenario: El RUC no puede estar registrado
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"
    Cuando registro la organización "Otra Constructora SAC" con RUC "20123456789", administrador "Luis Rojas", correo "luis@otra.pe" y contraseña "Segura123"
    Entonces la operación es rechazada con estado 409 y código "RUC_ALREADY_REGISTERED"

  Escenario: El correo del administrador no puede estar registrado y no se crea la organización
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"
    Cuando registro la organización "Otra Constructora SAC" con RUC "20999999991", administrador "Ana Torres", correo "ana@andina.pe" y contraseña "Segura123"
    Entonces la operación es rechazada con estado 409 y código "EMAIL_ALREADY_REGISTERED"
    Y no existe ninguna organización con RUC "20999999991"

  Escenario: La contraseña debe ser segura
    Cuando registro la organización "Constructora Andina SAC" con RUC "20123456789", administrador "Ana Torres", correo "ana@andina.pe" y contraseña "corta"
    Entonces la operación es rechazada con estado 400
    Y no existe ninguna organización con RUC "20123456789"
