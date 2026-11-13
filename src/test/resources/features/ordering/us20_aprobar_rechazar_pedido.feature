# language: es
@US20
Característica: US20 Aprobar o rechazar pedido
  Como encargado del almacén de origen o administrador
  quiero aprobar o rechazar los pedidos de las obras
  para atender solo lo que corresponde

  Antecedentes:
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"
    Y que "ana@andina.pe" inició sesión con la contraseña "Segura123"
    Y que registré la obra "Torre Norte" del "2026-11-01" al "2027-06-30"
    Y que registré el almacén "Almacén Central"
    Y que registré el almacén "Almacén Callao"
    Y que registré el material "Cemento Portland tipo I" con SKU "CEM-001", unidad "bolsa" y tolerancia de merma "2.5" %
    Y que creé el usuario "Jorge Huamán" con correo "jorge@andina.pe", rol "encargado de obra" y contraseña "Obra12345"
    Y que creé el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Y que creé el usuario "María Ríos" con correo "maria@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Y que asigné a "jorge@andina.pe" a la obra "Torre Norte"
    Y que asigné a "rosa@andina.pe" al almacén "Almacén Central"
    Y que asigné a "maria@andina.pe" al almacén "Almacén Callao"
    Y que el almacén "Almacén Central" tiene "100" del material "CEM-001"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Y que creé el pedido "P1" para la obra "Torre Norte" al almacén "Almacén Central" con:
      | material | cantidad |
      | CEM-001  | 50       |

  Escenario: El encargado del almacén de origen aprueba el pedido
    Dado que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Cuando apruebo el pedido "P1"
    Entonces la respuesta tiene estado 200
    Y el pedido "P1" está en estado "EnRevision"

  Escenario: El administrador aprueba el pedido
    Dado que "ana@andina.pe" inició sesión con la contraseña "Segura123"
    Cuando apruebo el pedido "P1"
    Entonces la respuesta tiene estado 200
    Y el pedido "P1" está en estado "EnRevision"

  Escenario: Rechazo con motivo
    Dado que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Cuando rechazo el pedido "P1" con el motivo "No hay transporte disponible esta semana"
    Entonces la respuesta tiene estado 200
    Y el pedido "P1" está en estado "Cancelado"
    Y el motivo de rechazo del pedido "P1" es "No hay transporte disponible esta semana"

  Escenario: El motivo es obligatorio al rechazar
    Dado que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Cuando rechazo el pedido "P1" sin motivo
    Entonces la operación es rechazada con estado 400 y código "REJECTION_REASON_REQUIRED"
    Y el pedido "P1" está en estado "Registrado"

  Escenario: No se aprueba un pedido cancelado
    Dado que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Y que rechacé el pedido "P1" con el motivo "Duplicado"
    Cuando apruebo el pedido "P1"
    Entonces la operación es rechazada con estado 409 y código "INVALID_ORDER_TRANSITION"

  Escenario: El encargado de otro almacén no ve ni aprueba el pedido
    Dado que "maria@andina.pe" inició sesión con la contraseña "Almacen123"
    Cuando apruebo el pedido "P1"
    Entonces la operación es rechazada con estado 404

  Escenario: Al aprobar se reserva lo solicitado en el almacén de origen
    Dado que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Cuando apruebo el pedido "P1"
    Entonces la respuesta tiene estado 200
    Y el stock disponible del material "CEM-001" en el almacén "Almacén Central" es "50"
    Y el stock reservado del material "CEM-001" en el almacén "Almacén Central" es "50"

  Escenario: No se aprueba un pedido sin stock suficiente y nada cambia
    Dado que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Y que creé el pedido "P2" para la obra "Torre Norte" al almacén "Almacén Central" con:
      | material | cantidad |
      | CEM-001  | 60       |
    Y que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Y que aprobé el pedido "P1"
    Cuando apruebo el pedido "P2"
    Entonces la operación es rechazada con estado 409 y código "INSUFFICIENT_STOCK"
    Y el pedido "P2" está en estado "Registrado"
    Y el stock disponible del material "CEM-001" en el almacén "Almacén Central" es "50"
    Y el stock reservado del material "CEM-001" en el almacén "Almacén Central" es "50"
