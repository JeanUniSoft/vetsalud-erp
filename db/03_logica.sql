-- =====================================================================
-- VetSalud ERP · Paso 3: reglas de negocio dentro de la BD
-- Triggers y funciones que garantizan RN-03 a RN-12 aunque la app falle.
-- La app Java indica quién opera con:  SET LOCAL vetsalud.id_usuario = '5';
-- =====================================================================
SET search_path TO vetsalud, public;

-- Usuario de la aplicación que ejecuta la transacción (NULL si no se indicó)
CREATE FUNCTION fn_usuario_actual() RETURNS INT
LANGUAGE sql STABLE AS $$
  SELECT nullif(current_setting('vetsalud.id_usuario', true), '')::INT
$$;

-- ---------------------------------------------------------------------
-- 3.1 Auditoría genérica (RF-04). Nunca guarda la clave (RNF-16).
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_auditar() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_antes   JSONB := CASE WHEN TG_OP <> 'INSERT' THEN to_jsonb(OLD) - 'clave_hash' END;
  v_despues JSONB := CASE WHEN TG_OP <> 'DELETE' THEN to_jsonb(NEW) - 'clave_hash' END;
BEGIN
  INSERT INTO auditoria (id_usuario, tabla, id_registro, operacion, valores_antes, valores_despues)
  VALUES (fn_usuario_actual(), TG_TABLE_NAME,
          (coalesce(v_despues, v_antes) ->> TG_ARGV[0])::BIGINT,
          TG_OP, v_antes, v_despues);
  RETURN NULL;
END $$;

CREATE TRIGGER tg_aud_usuario  AFTER INSERT OR UPDATE OR DELETE ON usuario  FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_usuario');
CREATE TRIGGER tg_aud_cliente  AFTER INSERT OR UPDATE OR DELETE ON cliente  FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_cliente');
CREATE TRIGGER tg_aud_mascota  AFTER INSERT OR UPDATE OR DELETE ON mascota  FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_mascota');
CREATE TRIGGER tg_aud_consulta AFTER INSERT OR UPDATE OR DELETE ON consulta FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_consulta');
CREATE TRIGGER tg_aud_producto AFTER INSERT OR UPDATE OR DELETE ON producto FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_producto');
CREATE TRIGGER tg_aud_venta    AFTER INSERT OR UPDATE OR DELETE ON venta    FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_venta');
CREATE TRIGGER tg_aud_turno    AFTER INSERT OR UPDATE OR DELETE ON caja_turno FOR EACH ROW EXECUTE FUNCTION fn_auditar('id_turno');

-- Tablas de solo inserción: auditoría, kardex e historiales no se editan ni borran (RNF-06)
CREATE FUNCTION fn_solo_insercion() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'La tabla % es de solo inserción: no se permite %', TG_TABLE_NAME, TG_OP
    USING ERRCODE = 'integrity_constraint_violation';
END $$;

CREATE TRIGGER tg_inmutable_auditoria BEFORE UPDATE OR DELETE ON auditoria
  FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();
CREATE TRIGGER tg_inmutable_kardex BEFORE UPDATE OR DELETE ON movimiento_inventario
  FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();
CREATE TRIGGER tg_inmutable_cita_hist BEFORE UPDATE OR DELETE ON cita_estado_hist
  FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();
CREATE TRIGGER tg_inmutable_adenda BEFORE UPDATE OR DELETE ON adenda
  FOR EACH ROW EXECUTE FUNCTION fn_solo_insercion();

-- ---------------------------------------------------------------------
-- 3.2 Citas: transiciones válidas de estado e historial (RF-10, RF-12)
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_cita_estado() RETURNS trigger
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
    INSERT INTO cita_estado_hist (id_cita, estado_anterior, estado_nuevo, id_usuario)
    VALUES (NEW.id_cita, OLD.estado, NEW.estado, fn_usuario_actual());
  ELSIF (NEW.inicio, NEW.fin) IS DISTINCT FROM (OLD.inicio, OLD.fin)
        AND OLD.estado NOT IN ('PENDIENTE', 'CONFIRMADA') THEN
    RAISE EXCEPTION 'Solo se reprograma una cita pendiente o confirmada'
      USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER tg_cita_estado AFTER INSERT ON cita
  FOR EACH ROW EXECUTE FUNCTION fn_cita_estado();
CREATE TRIGGER tg_cita_estado_upd BEFORE UPDATE ON cita
  FOR EACH ROW EXECUTE FUNCTION fn_cita_estado();

-- ---------------------------------------------------------------------
-- 3.3 Consulta: firma solo por veterinario, luego inmutable (RN-04, RN-05)
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_consulta_proteger() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.estado = 'FIRMADA' THEN
    RAISE EXCEPTION 'La consulta % está firmada; registre una adenda', OLD.id_consulta
      USING ERRCODE = 'integrity_constraint_violation';
  END IF;
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;

  IF NEW.estado = 'FIRMADA' THEN
    IF NOT EXISTS (SELECT 1 FROM usuario u JOIN rol r USING (id_rol)
                   WHERE u.id_usuario = NEW.id_veterinario AND r.nombre = 'VETERINARIO' AND u.activo) THEN
      RAISE EXCEPTION 'Solo un médico veterinario activo puede firmar la consulta'
        USING ERRCODE = 'check_violation';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM consulta_diagnostico
                   WHERE id_consulta = NEW.id_consulta AND es_principal) THEN
      RAISE EXCEPTION 'No se puede firmar sin diagnóstico principal'
        USING ERRCODE = 'check_violation';
    END IF;
    NEW.firmada_en := now();
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER tg_consulta_proteger BEFORE UPDATE OR DELETE ON consulta
  FOR EACH ROW EXECUTE FUNCTION fn_consulta_proteger();

-- Los detalles de una consulta firmada tampoco se modifican
CREATE FUNCTION fn_detalle_consulta_proteger() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_fila        JSONB := to_jsonb(coalesce(NEW, OLD));
  v_id_consulta BIGINT;
BEGIN
  IF TG_TABLE_NAME = 'receta_detalle' THEN
    SELECT id_consulta INTO v_id_consulta FROM receta WHERE id_receta = (v_fila ->> 'id_receta')::BIGINT;
  ELSE
    v_id_consulta := (v_fila ->> 'id_consulta')::BIGINT;
  END IF;

  IF EXISTS (SELECT 1 FROM consulta WHERE id_consulta = v_id_consulta AND estado = 'FIRMADA') THEN
    RAISE EXCEPTION 'La consulta % está firmada; no se puede modificar %', v_id_consulta, TG_TABLE_NAME
      USING ERRCODE = 'integrity_constraint_violation';
  END IF;
  RETURN coalesce(NEW, OLD);
END $$;

CREATE TRIGGER tg_diag_proteger BEFORE INSERT OR UPDATE OR DELETE ON consulta_diagnostico
  FOR EACH ROW EXECUTE FUNCTION fn_detalle_consulta_proteger();
CREATE TRIGGER tg_proc_proteger BEFORE INSERT OR UPDATE OR DELETE ON consulta_procedimiento
  FOR EACH ROW EXECUTE FUNCTION fn_detalle_consulta_proteger();
CREATE TRIGGER tg_receta_proteger BEFORE INSERT OR UPDATE OR DELETE ON receta
  FOR EACH ROW EXECUTE FUNCTION fn_detalle_consulta_proteger();
CREATE TRIGGER tg_receta_det_proteger BEFORE INSERT OR UPDATE OR DELETE ON receta_detalle
  FOR EACH ROW EXECUTE FUNCTION fn_detalle_consulta_proteger();

-- ---------------------------------------------------------------------
-- 3.4 Kardex: cada movimiento actualiza el lote (RN-06, RF-22)
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_kardex_aplicar() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_lote  lote%ROWTYPE;
  v_delta NUMERIC(12,2);
BEGIN
  -- FOR UPDATE bloquea el lote: dos cajas no pueden vender la última unidad a la vez
  SELECT * INTO v_lote FROM lote WHERE id_lote = NEW.id_lote FOR UPDATE;

  v_delta := CASE WHEN NEW.tipo IN ('INGRESO', 'AJUSTE_ENTRADA', 'DEVOLUCION')
                  THEN NEW.cantidad ELSE -NEW.cantidad END;

  IF NEW.tipo = 'VENTA' AND v_lote.fecha_vencimiento < CURRENT_DATE THEN
    RAISE EXCEPTION 'El lote % venció el %; no se puede vender', v_lote.codigo_lote,
      to_char(v_lote.fecha_vencimiento, 'DD/MM/YYYY') USING ERRCODE = 'check_violation';
  END IF;
  IF v_lote.stock_actual + v_delta < 0 THEN
    RAISE EXCEPTION 'Stock insuficiente en lote %: disponible %, solicitado %',
      v_lote.codigo_lote, v_lote.stock_actual, NEW.cantidad USING ERRCODE = 'check_violation';
  END IF;

  UPDATE lote SET stock_actual = stock_actual + v_delta WHERE id_lote = NEW.id_lote;
  NEW.saldo_lote := v_lote.stock_actual + v_delta;
  RETURN NEW;
END $$;

CREATE TRIGGER tg_kardex_aplicar BEFORE INSERT ON movimiento_inventario
  FOR EACH ROW EXECUTE FUNCTION fn_kardex_aplicar();

-- RN-07 (FEFO): lote vigente con vencimiento más próximo que cubra la cantidad
CREATE FUNCTION fn_lote_sugerido(p_id_producto INT, p_cantidad NUMERIC)
RETURNS INT
LANGUAGE sql STABLE AS $$
  SELECT id_lote
  FROM lote
  WHERE id_producto = p_id_producto
    AND stock_actual >= p_cantidad
    AND (fecha_vencimiento IS NULL OR fecha_vencimiento >= CURRENT_DATE)
  ORDER BY fecha_vencimiento NULLS LAST, id_lote
  LIMIT 1
$$;

-- ---------------------------------------------------------------------
-- 3.5 Venta: confirmar, anular y cerrar caja como transacciones únicas (RNF-08)
-- ---------------------------------------------------------------------
CREATE FUNCTION fn_confirmar_venta(p_id_venta BIGINT,
                                   p_descuento NUMERIC DEFAULT 0,
                                   p_id_autoriza INT DEFAULT NULL)
RETURNS TEXT
LANGUAGE plpgsql AS $$
DECLARE
  v_venta     venta%ROWTYPE;
  v_cajero    INT;
  v_estado_t  VARCHAR;
  v_subtotal  NUMERIC(12,2);
  v_pagado    NUMERIC(12,2);
  v_numero    INT;
  r           RECORD;
BEGIN
  SELECT * INTO v_venta FROM venta WHERE id_venta = p_id_venta FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'La venta % no existe', p_id_venta;
  END IF;
  IF v_venta.estado <> 'PENDIENTE' THEN
    RAISE EXCEPTION 'La venta % ya está %', p_id_venta, v_venta.estado;
  END IF;

  SELECT id_cajero, estado INTO v_cajero, v_estado_t FROM caja_turno WHERE id_turno = v_venta.id_turno;
  IF v_estado_t <> 'ABIERTA' THEN
    RAISE EXCEPTION 'El turno de caja % está cerrado', v_venta.id_turno;
  END IF;

  SELECT coalesce(sum(importe), 0) INTO v_subtotal FROM venta_detalle WHERE id_venta = p_id_venta;
  IF v_subtotal = 0 THEN
    RAISE EXCEPTION 'La venta % no tiene detalle', p_id_venta;
  END IF;

  -- RN-12: la suma de pagos debe ser exactamente el total
  SELECT coalesce(sum(monto), 0) INTO v_pagado FROM pago WHERE id_venta = p_id_venta;
  IF v_pagado <> v_subtotal - p_descuento THEN
    RAISE EXCEPTION 'Pagos (S/ %) no coinciden con el total (S/ %)', v_pagado, v_subtotal - p_descuento
      USING ERRCODE = 'check_violation';
  END IF;

  -- RN-11: correlativo sin duplicados (la fila de la serie queda bloqueada)
  UPDATE serie_comprobante SET ultimo_numero = ultimo_numero + 1
  WHERE serie = v_venta.serie
  RETURNING ultimo_numero INTO v_numero;

  -- Salida de inventario por cada producto (el trigger valida stock y vencimiento)
  FOR r IN SELECT id_detalle, id_lote, cantidad FROM venta_detalle
           WHERE id_venta = p_id_venta AND id_lote IS NOT NULL LOOP
    INSERT INTO movimiento_inventario (id_lote, tipo, cantidad, id_venta_detalle, id_usuario)
    VALUES (r.id_lote, 'VENTA', r.cantidad, r.id_detalle, v_cajero);
  END LOOP;

  -- ck_venta_descuento valida RN-08 (descuento > 10 % exige autorizador)
  UPDATE venta
  SET subtotal = v_subtotal, descuento = p_descuento, total = v_subtotal - p_descuento,
      id_autoriza_descuento = p_id_autoriza, numero = v_numero,
      estado = 'CONFIRMADA', fecha_hora = now()
  WHERE id_venta = p_id_venta;

  RETURN v_venta.serie || '-' || lpad(v_numero::TEXT, 8, '0');
END $$;

-- RN-09: anular no borra; genera movimientos inversos y conserva el correlativo
CREATE FUNCTION fn_anular_venta(p_id_venta BIGINT, p_motivo VARCHAR)
RETURNS VOID
LANGUAGE plpgsql AS $$
DECLARE
  v_venta  venta%ROWTYPE;
  v_cajero INT;
  r        RECORD;
BEGIN
  SELECT * INTO v_venta FROM venta WHERE id_venta = p_id_venta FOR UPDATE;
  IF v_venta.estado IS DISTINCT FROM 'CONFIRMADA' THEN
    RAISE EXCEPTION 'Solo se anula una venta confirmada';
  END IF;
  SELECT id_cajero INTO v_cajero FROM caja_turno
  WHERE id_turno = v_venta.id_turno AND estado = 'ABIERTA';
  IF NOT FOUND THEN
    RAISE EXCEPTION 'El turno de la venta ya cerró; registre una devolución en el turno actual';
  END IF;

  FOR r IN SELECT id_detalle, id_lote, cantidad FROM venta_detalle
           WHERE id_venta = p_id_venta AND id_lote IS NOT NULL LOOP
    INSERT INTO movimiento_inventario (id_lote, tipo, cantidad, id_venta_detalle, id_usuario, motivo)
    VALUES (r.id_lote, 'DEVOLUCION', r.cantidad, r.id_detalle,
            coalesce(fn_usuario_actual(), v_cajero), 'Anulación: ' || p_motivo);
  END LOOP;

  UPDATE venta SET estado = 'ANULADA', motivo_anulacion = p_motivo, anulada_en = now()
  WHERE id_venta = p_id_venta;
END $$;

-- UC-12 / RN-10: cierre de caja conciliado
CREATE FUNCTION fn_cerrar_caja(p_id_turno INT, p_efectivo_contado NUMERIC, p_observacion VARCHAR DEFAULT NULL)
RETURNS NUMERIC
LANGUAGE plpgsql AS $$
DECLARE
  v_turno    caja_turno%ROWTYPE;
  v_esperado NUMERIC(12,2);
BEGIN
  SELECT * INTO v_turno FROM caja_turno WHERE id_turno = p_id_turno FOR UPDATE;
  IF v_turno.estado IS DISTINCT FROM 'ABIERTA' THEN
    RAISE EXCEPTION 'El turno % no está abierto', p_id_turno;
  END IF;
  IF EXISTS (SELECT 1 FROM venta WHERE id_turno = p_id_turno AND estado = 'PENDIENTE') THEN
    RAISE EXCEPTION 'Hay ventas pendientes en el turno %; no se puede cerrar', p_id_turno;
  END IF;

  SELECT v_turno.monto_inicial + coalesce(sum(p.monto), 0) INTO v_esperado
  FROM venta v JOIN pago p USING (id_venta)
  WHERE v.id_turno = p_id_turno AND v.estado = 'CONFIRMADA' AND p.medio = 'EFECTIVO';

  -- ck_turno_diferencia_justificada exige observación si no cuadra
  UPDATE caja_turno
  SET efectivo_esperado = v_esperado, efectivo_contado = p_efectivo_contado,
      observacion = p_observacion, cierre = now(), estado = 'CERRADA'
  WHERE id_turno = p_id_turno;

  RETURN p_efectivo_contado - v_esperado;
END $$;

-- ---------------------------------------------------------------------
-- 3.6 Vistas para pantallas y reportes (RF-13, RF-22, RF-24, RF-32)
-- ---------------------------------------------------------------------
CREATE VIEW v_historia_clinica AS
SELECT c.id_mascota, m.nombre AS mascota, c.id_consulta, c.fecha_hora,
       u.nombres || ' ' || u.apellidos AS veterinario,
       c.motivo, d.descripcion AS diagnostico_principal, c.indicaciones,
       c.estado, c.firmada_en,
       (SELECT count(*) FROM adenda a WHERE a.id_consulta = c.id_consulta) AS adendas
FROM consulta c
JOIN mascota m ON m.id_mascota = c.id_mascota
JOIN usuario u ON u.id_usuario = c.id_veterinario
LEFT JOIN consulta_diagnostico d ON d.id_consulta = c.id_consulta AND d.es_principal;

CREATE VIEW v_kardex AS
SELECT mi.id_movimiento, p.codigo, p.nombre AS producto, l.codigo_lote, l.fecha_vencimiento,
       mi.fecha_hora, mi.tipo,
       CASE WHEN mi.tipo IN ('INGRESO', 'AJUSTE_ENTRADA', 'DEVOLUCION') THEN mi.cantidad END AS entrada,
       CASE WHEN mi.tipo IN ('VENTA', 'AJUSTE_SALIDA') THEN mi.cantidad END AS salida,
       mi.saldo_lote, mi.motivo
FROM movimiento_inventario mi
JOIN lote l ON l.id_lote = mi.id_lote
JOIN producto p ON p.id_producto = l.id_producto;

CREATE VIEW v_alerta_inventario AS
SELECT p.codigo, p.nombre, 'STOCK_MINIMO' AS alerta, sum(l.stock_actual) AS stock, NULL::DATE AS vence
FROM producto p LEFT JOIN lote l ON l.id_producto = p.id_producto
WHERE p.activo
GROUP BY p.id_producto
HAVING coalesce(sum(l.stock_actual), 0) <= p.stock_minimo
UNION ALL
SELECT p.codigo, p.nombre,
       CASE WHEN l.fecha_vencimiento < CURRENT_DATE THEN 'VENCIDO' ELSE 'POR_VENCER' END,
       l.stock_actual, l.fecha_vencimiento
FROM lote l JOIN producto p ON p.id_producto = l.id_producto
WHERE l.stock_actual > 0 AND l.fecha_vencimiento < CURRENT_DATE + 30;

CREATE VIEW v_ventas_diarias AS
SELECT v.fecha_hora::DATE AS dia, p.medio, count(DISTINCT v.id_venta) AS ventas, sum(p.monto) AS total
FROM venta v JOIN pago p USING (id_venta)
WHERE v.estado = 'CONFIRMADA'
GROUP BY 1, 2;
