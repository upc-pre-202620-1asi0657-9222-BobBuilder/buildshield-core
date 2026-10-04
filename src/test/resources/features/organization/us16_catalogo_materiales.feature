# language: es
@pendiente
Característica: US16 Catálogo de materiales
  Como administrador de la organización
  quiero mantener el catálogo de materiales con su unidad y tolerancia de merma
  para pedir, despachar y cotejar las mermas con criterios comunes

  Antecedentes:
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"
    Y que "ana@andina.pe" inició sesión con la contraseña "Segura123"

  Escenario: Alta de un material
    Cuando registro el material "Cemento Portland tipo I" con SKU "CEM-001", unidad "bolsa" y tolerancia de merma "2.5" %
    Entonces la respuesta tiene estado 201
    Y el catálogo contiene el material "CEM-001" con tolerancia de merma "2.50" %

  Escenario: El SKU es único dentro de la organización
    Dado que registré el material "Cemento Portland tipo I" con SKU "CEM-001", unidad "bolsa" y tolerancia de merma "2.5" %
    Cuando registro el material "Cemento Portland tipo V" con SKU "cem-001", unidad "bolsa" y tolerancia de merma "3" %
    Entonces la operación es rechazada con estado 409 y código "SKU_ALREADY_EXISTS"

  Escenario: Otra organización puede usar el mismo SKU
    Dado que registré el material "Cemento Portland tipo I" con SKU "CEM-001", unidad "bolsa" y tolerancia de merma "2.5" %
    Y que la organización "Otra Constructora SAC" con RUC "20999999991" está registrada con el administrador "luis@otra.pe" y contraseña "Segura123"
    Y que "luis@otra.pe" inició sesión con la contraseña "Segura123"
    Cuando registro el material "Cemento de la otra" con SKU "CEM-001", unidad "bolsa" y tolerancia de merma "1" %
    Entonces la respuesta tiene estado 201

  Esquema del escenario: La tolerancia de merma debe estar entre 0 y 100 %
    Cuando registro el material "Arena gruesa" con SKU "ARE-001", unidad "metro cúbico" y tolerancia de merma "<tolerancia>" %
    Entonces la respuesta tiene estado <estado>

    Ejemplos:
      | tolerancia | estado |
      | -0.01      | 400    |
      | 0          | 201    |
      | 100        | 201    |
      | 100.01     | 400    |

  Escenario: Se puede cambiar la tolerancia de merma
    Dado que registré el material "Fierro corrugado 1/2" con SKU "FIE-012", unidad "unidad" y tolerancia de merma "1" %
    Cuando cambio la tolerancia de merma del material "FIE-012" a "1.5" %
    Entonces la respuesta tiene estado 200
    Y el catálogo contiene el material "FIE-012" con tolerancia de merma "1.50" %

  Escenario: Todos los roles consultan el catálogo
    Dado que registré el material "Cemento Portland tipo I" con SKU "CEM-001", unidad "bolsa" y tolerancia de merma "2.5" %
    Y que creé el usuario "Jorge Huamán" con correo "jorge@andina.pe", rol "encargado de obra" y contraseña "Obra12345"
    Y que "jorge@andina.pe" inició sesión con la contraseña "Obra12345"
    Cuando consulto el catálogo de materiales
    Entonces la respuesta tiene estado 200
    Y el catálogo contiene el material "CEM-001" con tolerancia de merma "2.50" %
