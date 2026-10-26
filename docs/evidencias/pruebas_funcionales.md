# Pruebas funcionales por pantalla (25/10/2026)

Ejecutadas sobre el servidor local con la base recién instalada (`db/instalar.sql`), mediante el recorrido automatizado
`recorrido_capturas.py` (Playwright + Chrome) y solicitudes `curl`. Las capturas están en [`docs/capturas`](../capturas).

| Pantalla | Escenario | Resultado esperado | Resultado |
|---|---|---|---|
| P01 Acceso y recuperación | ltorres ingresa con una clave incorrecta | Mensaje genérico «Usuario o contraseña incorrectos» e intento registrado | Aprobado |
| P01 Acceso y recuperación | jhuaman falla 5 veces e intenta con la clave correcta | Cuenta bloqueada; el administrador la desbloquea en P12 y vuelve a ingresar | Aprobado |
| P01 Acceso y recuperación | POST de un formulario sin token CSRF | Respuesta 403 sin ejecutar la acción | Aprobado |
| P02 Panel principal | rparedes abre el panel | KPIs del día, agenda, ventas de 7 días, alertas e inventario por atender | Aprobado |
| P02 Panel principal | asalazar (veterinaria) abre el panel | «Mi agenda de hoy» solo con sus citas | Aprobado |
| P03 Clientes y mascotas | Registrar un cliente con el DNI 45678912 ya existente | «Ya existe un cliente con DNI 45678912 (RN-01)» sin duplicar | Aprobado |
| P03 Clientes y mascotas | Buscar «Gonzales» y abrir la ficha | Ficha de la cliente con sus mascotas Rocky y Misha | Aprobado |
| P04 Citas y llegada | Programar una cita a las 09:45 con el Dr. Mendoza, que tiene otra de 09:30 a 09:50 | Rechazo «ya tiene una cita de 09:30 a 09:50 (Chispa)» (RN-03) | Aprobado |
| P04 Citas y llegada | Programar la misma cita a las 10:30 | Cita N.° 12 registrada y resaltada en la agenda | Aprobado |
| P04 Citas y llegada | Registrar la llegada de Bruno (cita confirmada) | Estado «En espera» y transición en el historial | Aprobado |
| P05 Historia clínica | Firmar la consulta de Simba sin diagnóstico principal | Rechazo «No se puede firmar sin un diagnóstico principal (RN-04)» | Aprobado |
| P05 Historia clínica | Registrar signos vitales y diagnóstico principal | Datos guardados en la consulta abierta | Aprobado |
| P05 Historia clínica | Recepción intenta abrir /historia | Acceso denegado (403) | Aprobado |
| P06 Procedimientos y receta | Registrar la vacuna triple felina como procedimiento | «Procedimiento registrado con la tarifa vigente» (S/ 75.00) | Aprobado |
| P06 Procedimientos y receta | Firmar la consulta de Simba con control al 25/11/2026 | Consulta firmada, cita «Atendida» y recordatorio para el 24/11/2026 09:00 | Aprobado |
| P07 Inventario por lotes | Sugerir lote para 5 unidades de Drontal Plus (hay un lote vencido) | Sugiere DRP-1102 (vigente, vence 02/07/2027) y no el vencido DRP-0801 | Aprobado |
| P07 Inventario por lotes | Ajuste de salida sin evidencia | Rechazo: el ajuste exige motivo y evidencia (RF-25) | Aprobado |
| P07 Inventario por lotes | Consultar el kardex de Drontal Plus | Movimientos con saldo por lote y conciliación «Cuadra» | Aprobado |
| P08 Venta y pago | Cobrar la atención de Simba y agregar Pro Plan 3 kg | Líneas: vacuna (S/ 75.00) y producto del lote PPL-3301 | Aprobado |
| P08 Venta y pago | Aplicar S/ 20 de descuento sin credenciales de administrador | Rechazo por superar el límite del cajero (RN-08) | Aprobado |
| P08 Venta y pago | Pagar S/ 300 en efectivo y confirmar | Se registra el saldo, vuelto S/ 106.00 y comprobante T001-00000005 | Aprobado |
| P09 Cierre de caja | Cerrar el turno declarando S/ 120 cuando se esperan S/ 539 | Rechazo: registrar una observación que explique la diferencia de S/ 419 (RN-10) | Aprobado |
| P09 Cierre de caja | Abrir un segundo turno para el mismo cajero | Rechazo: ya tiene un turno abierto (prueba unitaria y restricción de BD) | Aprobado |
| P10 Reportes | Exportar el reporte de ventas a CSV y a Excel | Archivos descargados con título, emisión, usuario y filtros | Aprobado |
| P10 Reportes | Descargar el libro Excel S10 de inventario | Archivo .xlsx generado con Apache POI | Aprobado |
| P10 Reportes | Recepción intenta abrir /reportes | Acceso denegado (403) | Aprobado |
| P11 Portal del cliente | mgonzales abre la ficha de su mascota Rocky | Ficha visible (200) | Aprobado |
| P11 Portal del cliente | mgonzales cambia el id de la URL a una mascota ajena (Simba) | Acceso denegado (403) | Aprobado |
| P11 Portal del cliente | El cliente intenta abrir /clientes | Acceso denegado (403) | Aprobado |
| P12 Usuarios y auditoría | Desbloquear la cuenta de jhuaman bloqueada en P01 | Cuenta activa; jhuaman vuelve a ingresar | Aprobado |
| P12 Usuarios y auditoría | Recepción intenta abrir /admin/usuarios | Acceso denegado (403) | Aprobado |
| P12 Usuarios y auditoría | Abrir «Ver cambios» de un registro de auditoría | Valores antes y después del cambio | Aprobado |
