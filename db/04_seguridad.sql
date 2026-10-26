-- =====================================================================
-- VetSalud ERP · Paso 4: permisos por rol de BD (privilegio mínimo)
-- =====================================================================
SET search_path TO vetsalud, public;

-- Aplicación Java: puede leer y escribir, pero no borrar historia ni auditoría
GRANT USAGE ON SCHEMA vetsalud TO vetsalud_app, vetsalud_reportes;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA vetsalud TO vetsalud_app;
GRANT DELETE ON venta_detalle, pago, rol_permiso TO vetsalud_app;          -- solo borradores
REVOKE UPDATE ON auditoria, acceso_log, movimiento_inventario,
                 cita_estado_hist, adenda FROM vetsalud_app;                 -- solo inserción
-- El stock solo lo mueve el kardex: se quita UPDATE de la tabla y se da por columna
REVOKE UPDATE ON lote FROM vetsalud_app;
GRANT UPDATE (codigo_lote, fecha_vencimiento, costo_unitario) ON lote TO vetsalud_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA vetsalud TO vetsalud_app;

-- El trigger del kardex actualiza lote.stock_actual con permisos del dueño
ALTER FUNCTION fn_kardex_aplicar() SECURITY DEFINER SET search_path = vetsalud, public;

-- Reportes: solo vistas, nunca tablas con datos personales completos (RNF-15)
GRANT SELECT ON v_historia_clinica, v_kardex, v_alerta_inventario, v_ventas_diarias
  TO vetsalud_reportes;

RESET ROLE;
