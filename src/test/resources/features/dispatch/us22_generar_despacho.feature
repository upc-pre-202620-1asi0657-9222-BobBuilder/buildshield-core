# language: es
@US22
Característica: US22 Generar despacho
  Como encargado del almacén de origen
  quiero despachar los materiales de un pedido aprobado
  para que la obra reciba lo que pidió

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

  Escenario: El encargado del almacén despacha todo el pedido
    Cuando despacho del pedido "P1" el despacho "D1" con:
      | material | cantidad |
      | CEM-001  | 50       |
      | FIE-012  | 120      |
    Entonces la respuesta tiene estado 201
    Y el despacho "D1" está en estado "Preparado"
    Y el despacho "D1" es de tipo "Completo"
    Y el pedido "P1" está en estado "Atendido"

  Escenario: No se despacha más de lo pendiente
    Cuando despacho del pedido "P1" el despacho "D1" con:
      | material | cantidad |
      | CEM-001  | 51       |
    Entonces la operación es rechazada con estado 409 y código "DISPATCH_EXCEEDS_PENDING"
    Y el pedido "P1" está en estado "EnRevision"

  Escenario: Solo se despachan pedidos aprobados
    Dado que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Y que creé el pedido "P2" para la obra "Torre Norte" al almacén "Almacén Central" con:
      | material | cantidad |
      | CEM-001  | 10       |
    Y que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Cuando despacho del pedido "P2" el despacho "D2" con:
      | material | cantidad |
      | CEM-001  | 10       |
    Entonces la operación es rechazada con estado 409 y código "ORDER_NOT_DISPATCHABLE"

  Escenario: La cantidad del despacho debe ser mayor que cero
    Cuando despacho del pedido "P1" el despacho "D1" con:
      | material | cantidad |
      | CEM-001  | 0        |
    Entonces la operación es rechazada con estado 400

  Escenario: El encargado de otro almacén no despacha el pedido
    Dado que "maria@andina.pe" inició sesión con la contraseña "Almacen123"
    Cuando despacho del pedido "P1" el despacho "D1" con:
      | material | cantidad |
      | CEM-001  | 10       |
    Entonces la operación es rechazada con estado 404

  Escenario: El encargado de obra no genera despachos
    Dado que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando despacho del pedido "P1" el despacho "D1" con:
      | material | cantidad |
      | CEM-001  | 10       |
    Entonces la operación es rechazada con estado 403
