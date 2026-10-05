# language: es
@US14
Característica: US14 Registro de obra
  Como administrador de la organización
  quiero registrar las obras con su ubicación y fechas
  para que los pedidos y despachos se dirijan a ellas

  Antecedentes:
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"
    Y que "ana@andina.pe" inició sesión con la contraseña "Segura123"

  Escenario: Registro de una obra con ubicación y fechas
    Cuando registro la obra "Torre Norte" en "Av. Javier Prado 123", "San Isidro", "Lima" del "2026-11-01" al "2027-06-30"
    Entonces la respuesta tiene estado 201
    Y la obra "Torre Norte" figura en "Av. Javier Prado 123", "San Isidro", "Lima" del "2026-11-01" al "2027-06-30"

  Escenario: La fecha de fin no puede ser anterior a la de inicio
    Cuando registro la obra "Torre Norte" en "Av. Javier Prado 123", "San Isidro", "Lima" del "2027-06-30" al "2026-11-01"
    Entonces la operación es rechazada con estado 400 y código "INVALID_DATE_RANGE"

  Escenario: Una obra puede registrarse sin fecha de fin
    Cuando registro la obra "Puente Rímac" en "Jr. Trujillo 500", "Rímac", "Lima" desde el "2026-11-01" sin fecha de fin
    Entonces la respuesta tiene estado 201

  Escenario: Al modificar las fechas se respeta la misma regla
    Dado que registré la obra "Torre Norte" del "2026-11-01" al "2027-06-30"
    Cuando cambio la fecha de fin de la obra "Torre Norte" al "2027-12-31"
    Entonces la respuesta tiene estado 200
    Cuando cambio la fecha de fin de la obra "Torre Norte" al "2026-10-01"
    Entonces la operación es rechazada con estado 400 y código "INVALID_DATE_RANGE"

  Escenario: Una obra de otra organización no existe para mí
    Dado que registré la obra "Torre Norte" del "2026-11-01" al "2027-06-30"
    Y que la organización "Otra Constructora SAC" con RUC "20999999991" está registrada con el administrador "luis@otra.pe" y contraseña "Segura123"
    Y que "luis@otra.pe" inició sesión con la contraseña "Segura123"
    Cuando consulto la obra "Torre Norte"
    Entonces la operación es rechazada con estado 404

  Escenario: Solo el administrador registra obras
    Dado que creé el usuario "Jorge Huamán" con correo "jorge@andina.pe", rol "encargado de obra" y contraseña "Obra12345"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando registro la obra "Torre Sur" en "Av. Arequipa 900", "Lince", "Lima" del "2026-11-01" al "2027-06-30"
    Entonces la operación es rechazada con estado 403
