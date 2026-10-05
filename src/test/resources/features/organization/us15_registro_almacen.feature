# language: es
Característica: US15 Registro de almacén o centro de acopio
  Como administrador de la organización
  quiero registrar almacenes y centros de acopio, y desactivarlos cuando dejen de operar
  para despachar materiales desde ellos sin perder su historial

  Antecedentes:
    Dado que la organización "Constructora Andina SAC" con RUC "20123456789" está registrada con el administrador "ana@andina.pe" y contraseña "Segura123"
    Y que "ana@andina.pe" inició sesión con la contraseña "Segura123"

  Escenario: Registro de un almacén
    Cuando registro el almacén "Almacén Central" de tipo "almacén" en "Av. Argentina 2500, Callao"
    Entonces la respuesta tiene estado 201
    Y el almacén "Almacén Central" está activo

  Escenario: Registro de un centro de acopio
    Cuando registro el almacén "Acopio Chosica" de tipo "centro de acopio" en "Carretera Central km 34"
    Entonces la respuesta tiene estado 201

  Escenario: Al desactivar un almacén se conserva con su historial
    Dado que registré el almacén "Almacén Central"
    Y que creé el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Y que asigné a "rosa@andina.pe" al almacén "Almacén Central"
    Cuando desactivo el almacén "Almacén Central"
    Entonces la respuesta tiene estado 200
    Y el almacén "Almacén Central" está inactivo
    Y las asignaciones incluyen la de "rosa@andina.pe" al almacén "Almacén Central"

  Escenario: Un almacén desactivado se puede reactivar
    Dado que registré el almacén "Almacén Central"
    Y que desactivé el almacén "Almacén Central"
    Cuando reactivo el almacén "Almacén Central"
    Entonces la respuesta tiene estado 200
    Y el almacén "Almacén Central" está activo

  Escenario: No se asigna personal a un almacén desactivado
    Dado que registré el almacén "Almacén Central"
    Y que desactivé el almacén "Almacén Central"
    Y que creé el usuario "Rosa Quispe" con correo "rosa@andina.pe", rol "encargado de almacén" y contraseña "Almacen123"
    Cuando asigno a "rosa@andina.pe" al almacén "Almacén Central"
    Entonces la operación es rechazada con estado 409 y código "SITE_INACTIVE"

  Escenario: Un almacén de otra organización no existe para mí
    Dado que registré el almacén "Almacén Central"
    Y que la organización "Otra Constructora SAC" con RUC "20999999991" está registrada con el administrador "luis@otra.pe" y contraseña "Segura123"
    Y que "luis@otra.pe" inició sesión con la contraseña "Segura123"
    Cuando consulto el almacén "Almacén Central"
    Entonces la operación es rechazada con estado 404
