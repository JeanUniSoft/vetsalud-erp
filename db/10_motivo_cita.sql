-- =====================================================================
-- VetSalud ERP · Paso 10 (APF3): el motivo de cancelación o inasistencia queda en el historial de la cita
--   · RF-10: cancelar o marcar "No asistió" exige motivo; ahora el trigger lo guarda en cita_estado_hist
--   · La aplicación envía el motivo con set_config('vetsalud.motivo_cita', ..., true) dentro de la transacción
-- =====================================================================
SET ROLE vetsalud_owner;
SET search_path TO vetsalud, public;

CREATE OR REPLACE FUNCTION fn_cita_estado() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    INSERT INTO cita_estado_hist (id_cita, estado_anterior, estado_nuevo, id_usuario)
    VALUES (NEW.id_cita, NULL, NEW.estado, coalesce(fn_usuario_actual(), NEW.id_registrado_por));
    RETURN NEW;
  END IF;

  IF NEW.estado IS DISTINCT FROM OLD.estado THEN
    IF NOT (
         (OLD.estado = 'PENDIENTE'   AND NEW.estado IN ('CONFIRMADA', 'CANCELADA'))
      OR (OLD.estado = 'CONFIRMADA'  AND NEW.estado IN ('EN_ESPERA', 'CANCELADA', 'NO_ASISTIO'))
      OR (OLD.estado = 'EN_ESPERA'   AND NEW.estado IN ('EN_ATENCION', 'CANCELADA'))
      OR (OLD.estado = 'EN_ATENCION' AND NEW.estado = 'ATENDIDA')
    ) THEN
      RAISE EXCEPTION 'Transición de cita no permitida: % -> %', OLD.estado, NEW.estado
        USING ERRCODE = 'check_violation';
    END IF;
    INSERT INTO cita_estado_hist (id_cita, estado_anterior, estado_nuevo, motivo, id_usuario)
    VALUES (NEW.id_cita, OLD.estado, NEW.estado,
            nullif(current_setting('vetsalud.motivo_cita', true), ''), fn_usuario_actual());
  ELSIF (NEW.inicio, NEW.fin) IS DISTINCT FROM (OLD.inicio, OLD.fin)
        AND OLD.estado NOT IN ('PENDIENTE', 'CONFIRMADA') THEN
    RAISE EXCEPTION 'Solo se reprograma una cita pendiente o confirmada'
      USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$;

RESET ROLE;
