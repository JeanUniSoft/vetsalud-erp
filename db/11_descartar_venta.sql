-- =====================================================================
-- VetSalud ERP · Paso 11 (APF3): descarte de ventas pendientes (RF-26, RF-30)
--   · Una venta PENDIENTE abandonada impedía cerrar la caja (fn_cerrar_caja rechaza turnos con pendientes)
--     y no podía borrarse: el rol vetsalud_app no tiene DELETE sobre venta y ANULADA exige correlativo.
--   · Esta función borra SOLO una venta PENDIENTE (sin comprobante ni movimientos de kardex) de un turno
--     ABIERTO, con sus líneas y pagos. Las confirmadas se siguen anulando con fn_anular_venta (RN-09).
--   · SECURITY DEFINER: la app no recibe DELETE sobre venta; tg_aud_venta audita el borrado con el
--     usuario de la sesión (vetsalud.id_usuario).
-- =====================================================================
SET ROLE vetsalud_owner;
SET search_path TO vetsalud, public;

CREATE OR REPLACE FUNCTION fn_descartar_venta(p_id_venta BIGINT)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER SET search_path = vetsalud, public AS $$
DECLARE
  v_venta venta%ROWTYPE;
BEGIN
  SELECT * INTO v_venta FROM venta WHERE id_venta = p_id_venta FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'La venta % no existe', p_id_venta;
  END IF;
  IF v_venta.estado <> 'PENDIENTE' THEN
    RAISE EXCEPTION 'Solo se descarta una venta pendiente: la venta % está %', p_id_venta, v_venta.estado;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM caja_turno WHERE id_turno = v_venta.id_turno AND estado = 'ABIERTA') THEN
    RAISE EXCEPTION 'El turno de caja % de la venta % está cerrado', v_venta.id_turno, p_id_venta;
  END IF;

  DELETE FROM pago WHERE id_venta = p_id_venta;
  DELETE FROM venta_detalle WHERE id_venta = p_id_venta;
  DELETE FROM venta WHERE id_venta = p_id_venta;
END $$;

REVOKE ALL ON FUNCTION fn_descartar_venta(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION fn_descartar_venta(BIGINT) TO vetsalud_app;

RESET ROLE;
