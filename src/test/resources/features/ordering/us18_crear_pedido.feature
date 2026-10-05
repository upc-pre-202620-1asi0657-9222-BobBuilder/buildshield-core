# language: es
Característica: US18 Crear pedido de materiales
  Como encargado de obra
  quiero pedir materiales a un almacén para mi obra
  para tener lo necesario a tiempo

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
    Y que creé el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Y que asigné a "jorge@andina.pe" a la obra "Torre Norte"

  Escenario: El encargado de obra crea un pedido para su obra
    Dado que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando creo el pedido "P1" para la obra "Torre Norte" al almacén "Almacén Central" con:
      | material | cantidad |
      | CEM-001  | 50       |
      | FIE-012  | 120      |
    Entonces la respuesta tiene estado 201
    Y el pedido "P1" está en estado "Registrado"

  Esquema del escenario: Las cantidades deben ser mayores que cero
    Dado que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando creo el pedido "P1" para la obra "Torre Norte" al almacén "Almacén Central" con:
      | material | cantidad   |
      | CEM-001  | <cantidad> |
    Entonces la operación es rechazada con estado 400

    Ejemplos:
      | cantidad |
      | 0        |
      | -5       |

  Escenario: Un material no se repite en el mismo pedido
    Dado que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando creo el pedido "P1" para la obra "Torre Norte" al almacén "Almacén Central" con:
      | material | cantidad |
      | CEM-001  | 50       |
      | CEM-001  | 10       |
    Entonces la operación es rechazada con estado 400 y código "DUPLICATE_MATERIAL"

  Escenario: Solo se pide para una obra asignada
    Dado que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando creo el pedido "P1" para la obra "Torre Sur" al almacén "Almacén Central" con:
      | material | cantidad |
      | CEM-001  | 50       |
    Entonces la operación es rechazada con estado 404

  Escenario: No se pide a un almacén desactivado
    Dado que desactivé el almacén "Almacén Callao"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando creo el pedido "P1" para la obra "Torre Norte" al almacén "Almacén Callao" con:
      | material | cantidad |
      | CEM-001  | 50       |
    Entonces la operación es rechazada con estado 409 y código "SITE_INACTIVE"

  Escenario: Un encargado de almacén no crea pedidos
    Dado que "rosa@andina.pe" inició sesión con la contraseña "Almacen123"
    Cuando creo el pedido "P1" para la obra "Torre Norte" al almacén "Almacén Central" con:
      | material | cantidad |
      | CEM-001  | 50       |
    Entonces la operación es rechazada con estado 403
