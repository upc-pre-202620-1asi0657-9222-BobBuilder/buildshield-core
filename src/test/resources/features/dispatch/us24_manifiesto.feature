# language: es
@US24
Característica: US24 Manifiesto del despacho
  Como encargado del almacén o de la obra
  quiero el manifiesto del despacho con su código y su QR
  para identificar rápido lo que viaja y a dónde va

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
      | FIE-012  | 120      |

  Escenario: El manifiesto tiene el código único y un QR que lo codifica
    Cuando consulto el manifiesto del despacho "D1"
    Entonces el manifiesto tiene el código del despacho "D1" y su QR lo codifica
    Y el manifiesto muestra la obra "Torre Norte", el almacén "Almacén Central" y los materiales:
      | sku     | nombre                  | unidad | cantidad |
      | CEM-001 | Cemento Portland tipo I | BAG    | 30       |
      | FIE-012 | Fierro corrugado 1/2    | UNIT   | 120      |

  Escenario: El manifiesto de un despacho en tránsito incluye transportista y pesaje
    Dado que el despacho "D1" salió con el transportista "Transportes Rímac SAC"
    Cuando consulto el manifiesto del despacho "D1"
    Entonces la respuesta tiene estado 200
    Y el manifiesto muestra el transportista "Transportes Rímac SAC" y el neto "1500" kg

  Escenario: El encargado de la obra de destino ve el manifiesto
    Dado que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando consulto el manifiesto del despacho "D1"
    Entonces el manifiesto tiene el código del despacho "D1" y su QR lo codifica

  Escenario: El encargado de otra obra no ve el manifiesto
    Dado que "lucia@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando consulto el manifiesto del despacho "D1"
    Entonces la operación es rechazada con estado 404
