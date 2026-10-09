# language: es
@US23
Característica: US23 Partición del pedido en varios despachos
  Como encargado del almacén de origen
  quiero atender un pedido en varios despachos
  para enviar lo que hay disponible sin esperar a completarlo

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

  Escenario: Un pedido se atiende en dos despachos
    Cuando despacho del pedido "P1" el despacho "D1" con:
      | material | cantidad |
      | CEM-001  | 20       |
    Entonces la respuesta tiene estado 201
    Y el despacho "D1" es de tipo "Parcial"
    Y el pedido "P1" está en estado "ParcialmenteAtendido"
    Cuando despacho del pedido "P1" el despacho "D2" con:
      | material | cantidad |
      | CEM-001  | 30       |
      | FIE-012  | 120      |
    Entonces la respuesta tiene estado 201
    Y el despacho "D2" es de tipo "Completo"
    Y el pedido "P1" está en estado "Atendido"
    Cuando consulto los despachos del pedido "P1"
    Entonces la lista de despachos contiene exactamente:
      | D1 |
      | D2 |

  Escenario: Lo despachado y lo pendiente se ven por material
    Dado que despaché del pedido "P1" el despacho "D1" con:
      | material | cantidad |
      | CEM-001  | 20       |
    Cuando consulto el pedido "P1"
    Entonces el pedido muestra por material:
      | material | solicitado | despachado | recibido | pendiente |
      | CEM-001  | 50         | 20         | 0        | 30        |
      | FIE-012  | 120        | 0          | 0        | 120       |

  Escenario: Los despachos sucesivos no superan lo pedido
    Dado que despaché del pedido "P1" el despacho "D1" con:
      | material | cantidad |
      | CEM-001  | 40       |
    Cuando despacho del pedido "P1" el despacho "D2" con:
      | material | cantidad |
      | CEM-001  | 11       |
    Entonces la operación es rechazada con estado 409 y código "DISPATCH_EXCEEDS_PENDING"

  Escenario: Al salir cada despacho se consume su parte de la reserva
    Dado que despaché del pedido "P1" el despacho "D1" con:
      | material | cantidad |
      | CEM-001  | 20       |
    Y que el despacho "D1" salió con el transportista "Transportes Rímac SAC"
    Entonces el stock disponible del material "CEM-001" en el almacén "Almacén Central" es "50"
    Y el stock reservado del material "CEM-001" en el almacén "Almacén Central" es "30"
