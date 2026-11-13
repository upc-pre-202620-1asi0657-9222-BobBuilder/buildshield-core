# language: es
@US27
Característica: US27 Pesaje de salida
  Como encargado del almacén de origen
  quiero registrar el peso bruto y la tara del vehículo al salir
  para tener evidencia de lo que salió del almacén

  Antecedentes:
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"
    Y que "ana@andina.pe" inició sesión con la contraseña "Segura123"
    Y que registré la obra "Torre Norte" del "2026-11-01" al "2027-06-30"
    Y que registré la obra "Torre Sur" del "2026-11-01" al "2027-06-30"
    Y que registré el almacén "Almacén Central"
    Y que registré el almacén "Almacén Callao"
    Y que registré el material "Cemento Portland tipo I" con SKU "CEM-001", unidad "bolsa" y tolerancia de merma "2.5" %
    Y que registré el material "Fierro corrugado 1/2" con SKU "FIE-012", unidad "unidad" y tolerancia de merma "1" %
    Y que creé el usuario "Jorge Huamán" con correo "jorge@andina.pe", rol "encargado de obra" y contraseña "Obra12345"
    Y que creé el usuario "Lucía Paz" con correo "lucia@andina.pe", rol "encargado de obra" y contraseña "Obra12345"
    Y que creé el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Y que creé el usuario "María Ríos" con correo "maria@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Y que asigné a "jorge@andina.pe" a la obra "Torre Norte"
    Y que asigné a "lucia@andina.pe" a la obra "Torre Sur"
    Y que asigné a "rosa@andina.pe" al almacén "Almacén Central"
    Y que asigné a "maria@andina.pe" al almacén "Almacén Callao"
    Y que el almacén "Almacén Central" tiene "100" del material "CEM-001"
    Y que el almacén "Almacén Central" tiene "200" del material "FIE-012"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Y que creé el pedido "P1" para la obra "Torre Norte" al almacén "Almacén Central" con:
      | material | cantidad |
      | CEM-001  | 50       |
      | FIE-012  | 120      |
    Y que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Y que aprobé el pedido "P1"
    Y que despaché del pedido "P1" el despacho "D1" con:
      | material | cantidad |
      | CEM-001  | 30       |

  Escenario: Se registra el pesaje y se calcula el neto
    Cuando registro el pesaje de salida del despacho "D1" con bruto "2550.5" kg y tara "1050.5" kg
    Entonces la respuesta tiene estado 200
    Y el pesaje de salida del despacho "D1" tiene neto "1500" kg

  Escenario: La tara debe ser menor que el peso bruto
    Cuando registro el pesaje de salida del despacho "D1" con bruto "1000" kg y tara "1000" kg
    Entonces la operación es rechazada con estado 400 y código "INVALID_WEIGHING"

  Escenario: Hay un solo pesaje de salida por despacho
    Dado que registré el pesaje de salida del despacho "D1" con bruto "2550.5" kg y tara "1050.5" kg
    Cuando registro el pesaje de salida del despacho "D1" con bruto "2600" kg y tara "1000" kg
    Entonces la operación es rechazada con estado 409 y código "DEPARTURE_WEIGHING_ALREADY_RECORDED"

  Escenario: No sale un despacho sin pesaje
    Dado que registré el transportista "Transportes Rímac SAC" con documento "20555666777" y placa "ABC-123" en el despacho "D1"
    Cuando hago salir el despacho "D1"
    Entonces la operación es rechazada con estado 409 y código "DEPARTURE_WEIGHING_REQUIRED"
    Y el despacho "D1" está en estado "Preparado"

  Escenario: Un despacho que ya salió no se vuelve a pesar
    Dado que el despacho "D1" salió con el transportista "Transportes Rímac SAC"
    Cuando registro el pesaje de salida del despacho "D1" con bruto "2600" kg y tara "1000" kg
    Entonces la operación es rechazada con estado 409 y código "INVALID_DISPATCH_TRANSITION"
