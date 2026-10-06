# language: es
@US04
Característica: US04 Gestión de usuarios y roles
  Como administrador de la organización
  quiero crear usuarios con su rol, cambiarlo y desactivarlos
  para que cada persona acceda solo a lo que le corresponde

  Antecedentes:
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"
    Y que "ana@andina.pe" inició sesión con la contraseña "Segura123"

  Escenario: El administrador crea un encargado de almacén
    Cuando creo el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Entonces la respuesta tiene estado 201
    Y "rosa@andina.pe" puede iniciar sesión con la contraseña "Almacen123" como "encargado de almacén"

  Escenario: El administrador crea un encargado de obra
    Cuando creo el usuario "Jorge Huamán" con correo "jorge@andina.pe", rol "encargado de obra" y contraseña "Obra12345"
    Entonces la respuesta tiene estado 201
    Y "jorge@andina.pe" puede iniciar sesión con la contraseña "Obra12345" como "encargado de obra"

  Escenario: El correo del usuario no puede estar registrado
    Dado que creé el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Cuando creo el usuario "Rosa Q." con correo "rosa@andina.pe", rol "encargado de obra" y contraseña "Obra12345"
    Entonces la operación es rechazada con estado 409 y código "EMAIL_ALREADY_REGISTERED"

  Escenario: El rol debe ser válido
    Cuando creo el usuario "Pedro Díaz" con correo "pedro@andina.pe", rol "gerente" y contraseña "Gerente123"
    Entonces la operación es rechazada con estado 400

  Escenario: Un encargado no puede crear usuarios
    Dado que creé el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Y que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Cuando creo el usuario "Pedro Díaz" con correo "pedro@andina.pe", rol "encargado de obra" y contraseña "Obra12345"
    Entonces la operación es rechazada con estado 403

  Escenario: Un encargado no puede ver la lista de usuarios
    Dado que creé el usuario "Jorge Huamán" con correo "jorge@andina.pe", rol "encargado de obra" y contraseña "Obra12345"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando consulto la lista de usuarios
    Entonces la operación es rechazada con estado 403

  Escenario: El administrador solo ve los usuarios de su organización
    Dado que creé el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Y que la organización "Otra Constructora SAC" con RUC "20999999991" está registrada con el administrador "luis@otra.pe" y contraseña "Segura123"
    Y que "ana@andina.pe" inició sesión con la contraseña "Segura123"
    Cuando consulto la lista de usuarios
    Entonces la respuesta tiene estado 200
    Y la lista de usuarios contiene exactamente:
      | ana@andina.pe  |
      | rosa@andina.pe |

  Escenario: El administrador desactiva a un usuario y este ya no inicia sesión
    Dado que creé el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Y que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Y que "ana@andina.pe" inició sesión con la contraseña "Segura123"
    Cuando desactivo al usuario "rosa@andina.pe"
    Entonces la respuesta tiene estado 200
    Y el usuario "rosa@andina.pe" figura inactivo
    Y "rosa@andina.pe" no puede iniciar sesión con la contraseña "Almacen123"
    Y la sesión que tenía "rosa@andina.pe" ya no sirve

  Escenario: Un usuario reactivado vuelve a iniciar sesión
    Dado que creé el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Y que desactivé al usuario "rosa@andina.pe"
    Cuando reactivo al usuario "rosa@andina.pe"
    Entonces la respuesta tiene estado 200
    Y "rosa@andina.pe" puede iniciar sesión con la contraseña "Almacen123" como "encargado de almacén"

  Escenario: El administrador cambia el rol y la sesión anterior deja de valer
    Dado que creé el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Y que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Y que "ana@andina.pe" inició sesión con la contraseña "Segura123"
    Cuando cambio el rol de "rosa@andina.pe" a "encargado de obra"
    Entonces la respuesta tiene estado 200
    Y la sesión que tenía "rosa@andina.pe" ya no sirve
    Y "rosa@andina.pe" puede iniciar sesión con la contraseña "Almacen123" como "encargado de obra"

  Escenario: El administrador no se desactiva a sí mismo
    Cuando desactivo al usuario "ana@andina.pe"
    Entonces la operación es rechazada con estado 409 y código "CANNOT_CHANGE_OWN_ACCOUNT"

  Escenario: Un encargado no cambia usuarios
    Dado que creé el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Y que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Cuando desactivo al usuario "rosa@andina.pe"
    Entonces la operación es rechazada con estado 403
