# language: es
Característica: US21 Consultar el estado de un pedido
  Como encargado de obra
  quiero ver en qué estado está mi pedido y cuánto falta por material
  para planificar los trabajos de la obra

  Antecedentes:
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"
    Y que "ana@andina.pe" inició sesión con la contraseña "Segura123"
    Y que registré la obra "Torre Norte" del "2026-11-01" al "2027-06-30"
    Y que registré la obra "Torre Sur" del "2026-11-01" al "2027-06-30"
    Y que registré el almacén "Almacén Central"
    Y que registré el material "Cemento Portland tipo I" con SKU "CEM-001", unidad "bolsa" y tolerancia de merma "2.5" %
    Y que registré el material "Fierro corrugado 1/2" con SKU "FIE-012", unidad "unidad" y tolerancia de merma "1" %
    Y que creé el usuario "Jorge Huamán" con correo "jorge@andina.pe", rol "encargado de obra" y contraseña "Obra12345"
    Y que creé el usuario "Lucía Paz" con correo "lucia@andina.pe", rol "encargado de obra" y contraseña "Obra12345"
    Y que asigné a "jorge@andina.pe" a la obra "Torre Norte"
    Y que asigné a "lucia@andina.pe" a la obra "Torre Sur"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Y que creé el pedido "P1" para la obra "Torre Norte" al almacén "Almacén Central" con:
      | material | cantidad |
      | CEM-001  | 50       |
      | FIE-012  | 120      |

  Escenario: El encargado consulta el estado y las cantidades por material
    Cuando consulto el pedido "P1"
    Entonces la respuesta tiene estado 200
    Y el pedido "P1" está en estado "Registrado"
    Y el pedido muestra por material:
      | material | solicitado | despachado | recibido | pendiente |
      | CEM-001  | 50         | 0          | 0        | 50        |
      | FIE-012  | 120        | 0          | 0        | 120       |

  Escenario: Cada encargado de obra ve solo los pedidos de sus obras
    Dado que "lucia@andina.pe" inició sesión con la contraseña "Obra12345"
    Y que creé el pedido "P2" para la obra "Torre Sur" al almacén "Almacén Central" con:
      | material | cantidad |
      | CEM-001  | 10       |
    Cuando consulto los pedidos
    Entonces la lista de pedidos contiene exactamente:
      | P2 |
    Cuando consulto el pedido "P1"
    Entonces la operación es rechazada con estado 404

  Escenario: Un pedido de otra organización no existe para mí
    Dado que la organización "Otra Constructora SAC" con RUC "20999999991" está registrada con el administrador "luis@otra.pe" y contraseña "Segura123"
    Y que "luis@otra.pe" inició sesión con la contraseña "Segura123"
    Cuando consulto el pedido "P1"
    Entonces la operación es rechazada con estado 404
