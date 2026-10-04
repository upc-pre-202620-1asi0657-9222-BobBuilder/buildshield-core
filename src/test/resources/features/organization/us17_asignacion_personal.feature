# language: es
Característica: US17 Asignación de personal a obras y almacenes
  Como administrador de la organización
  quiero asignar a cada encargado a sus obras o almacenes
  para que cada usuario vea y opere solo lo suyo

  Antecedentes:
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"
    Y que "ana@andina.pe" inició sesión con la contraseña "Segura123"
    Y que registré la obra "Torre Norte" del "2026-11-01" al "2027-06-30"
    Y que registré la obra "Torre Sur" del "2026-11-01" al "2027-06-30"
    Y que registré el almacén "Almacén Central"
    Y que registré el almacén "Almacén Callao"
    Y que creé el usuario "Jorge Huamán" con correo "jorge@andina.pe", rol "encargado de obra" y contraseña "Obra12345"
    Y que creé el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"

  Escenario: Asignación de encargados a una obra y a un almacén
    Cuando asigno a "jorge@andina.pe" a la obra "Torre Norte"
    Entonces la respuesta tiene estado 201
    Cuando asigno a "rosa@andina.pe" al almacén "Almacén Central"
    Entonces la respuesta tiene estado 201

  Escenario: El rol debe corresponder al tipo de lugar
    Cuando asigno a "jorge@andina.pe" al almacén "Almacén Central"
    Entonces la operación es rechazada con estado 400 y código "ROLE_NOT_ALLOWED_FOR_SITE"

  Escenario: No se repite una asignación activa
    Dado que asigné a "jorge@andina.pe" a la obra "Torre Norte"
    Cuando asigno a "jorge@andina.pe" a la obra "Torre Norte"
    Entonces la operación es rechazada con estado 409 y código "ASSIGNMENT_ALREADY_ACTIVE"

  Escenario: El encargado de obra ve solo sus obras
    Dado que asigné a "jorge@andina.pe" a la obra "Torre Norte"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando consulto las obras
    Entonces la lista contiene exactamente:
      | Torre Norte |
    Cuando consulto la obra "Torre Sur"
    Entonces la operación es rechazada con estado 404

  Escenario: El encargado de almacén ve solo sus almacenes y ninguna obra
    Dado que asigné a "rosa@andina.pe" al almacén "Almacén Central"
    Y que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Cuando consulto los almacenes
    Entonces la lista contiene exactamente:
      | Almacén Central |
    Cuando consulto las obras
    Entonces la lista está vacía

  Escenario: El encargado de obra ve los almacenes activos para pedir materiales
    Dado que desactivé el almacén "Almacén Callao"
    Y que asigné a "jorge@andina.pe" a la obra "Torre Norte"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando consulto los almacenes
    Entonces la lista contiene exactamente:
      | Almacén Central |

  Escenario: Al terminar una asignación el encargado deja de ver la obra y queda el historial
    Dado que asigné a "jorge@andina.pe" a la obra "Torre Norte"
    Cuando termino la asignación de "jorge@andina.pe" a la obra "Torre Norte"
    Entonces la respuesta tiene estado 200
    Y las asignaciones incluyen la de "jorge@andina.pe" a la obra "Torre Norte" ya terminada
    Dado que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando consulto las obras
    Entonces la lista está vacía
