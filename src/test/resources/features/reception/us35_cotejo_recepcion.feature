# language: es
@US35
Característica: US35 Recepción y cotejo en obra
  Como encargado de la obra de destino
  quiero registrar lo que llegó y compararlo con lo pedido y lo despachado
  para detectar la merma y saber si está dentro de la tolerancia

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
      | CEM-001  | 50       |
      | FIE-012  | 120      |

  Escenario: El encargado de obra coteja con la tolerancia de cada material
    Dado que el despacho "D1" salió con el transportista "Transportes Rímac SAC"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Y que abrí la recepción "R1" del despacho "D1"
    Y que registré que en la recepción "R1" llegaron "49" del material "CEM-001"
    Y que registré que en la recepción "R1" llegaron "110" del material "FIE-012"
    Cuando consulto el cotejo de la recepción "R1"
    Entonces el cotejo muestra:
      | material | solicitado | despachado | recibido | diferencia | merma | dentro |
      | CEM-001  | 50         | 50         | 49       | 1          | 2.00  | sí     |
      | FIE-012  | 120        | 120        | 110      | 10         | 8.33  | no     |
    Y el cotejo está completo y "no" dentro de la tolerancia

  Escenario: Lo recibido se puede corregir mientras la recepción está en curso
    Dado que el despacho "D1" salió con el transportista "Transportes Rímac SAC"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Y que abrí la recepción "R1" del despacho "D1"
    Y que registré que en la recepción "R1" llegaron "45" del material "CEM-001"
    Y que registré que en la recepción "R1" llegaron "50" del material "CEM-001"
    Y que registré que en la recepción "R1" llegaron "120" del material "FIE-012"
    Cuando consulto el cotejo de la recepción "R1"
    Entonces el cotejo está completo y "sí" dentro de la tolerancia

  Escenario: Lo recibido no supera lo despachado
    Dado que el despacho "D1" salió con el transportista "Transportes Rímac SAC"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Y que abrí la recepción "R1" del despacho "D1"
    Cuando registro que en la recepción "R1" llegaron "51" del material "CEM-001"
    Entonces la operación es rechazada con estado 400 y código "RECEIVED_EXCEEDS_DISPATCHED"

  Escenario: Hay una sola recepción por despacho
    Dado que el despacho "D1" salió con el transportista "Transportes Rímac SAC"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Y que abrí la recepción "R1" del despacho "D1"
    Cuando abro la recepción "R2" del despacho "D1"
    Entonces la operación es rechazada con estado 409 y código "RECEPTION_ALREADY_EXISTS"

  Escenario: Solo se recibe un despacho en tránsito
    Dado que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando abro la recepción "R1" del despacho "D1"
    Entonces la operación es rechazada con estado 409 y código "DISPATCH_NOT_IN_TRANSIT"

  Escenario: El encargado de obra ve los despachos en tránsito hacia sus obras
    Dado que el despacho "D1" salió con el transportista "Transportes Rímac SAC"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando consulto los despachos en tránsito
    Entonces la lista de despachos contiene exactamente:
      | D1 |
    Dado que "lucia@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando consulto los despachos en tránsito
    Entonces la lista de despachos está vacía

  Escenario: El encargado de otra obra no recibe el despacho
    Dado que el despacho "D1" salió con el transportista "Transportes Rímac SAC"
    Y que "lucia@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando abro la recepción "R1" del despacho "D1"
    Entonces la operación es rechazada con estado 404
