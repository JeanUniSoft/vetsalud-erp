-- =====================================================================
-- VetSalud ERP · Paso 6: pruebas de las reglas de negocio
-- Se ejecuta con el usuario de la APLICACIÓN (no con el dueño):
-- cada prueba intenta romper una regla y debe imprimir "OK ... bloqueado".
-- =====================================================================
SET ROLE vetsalud_app;
SET search_path TO vetsalud, public;
SELECT set_config('vetsalud.id_usuario', (SELECT id_usuario::TEXT FROM usuario WHERE usuario = 'dramos'), false);

-- Plantilla: si la sentencia NO falla, se lanza 'FALLÓ' y el script se detiene.
DO $$ BEGIN
  INSERT INTO cliente (tipo_documento, num_documento, nombres, apellidos, telefono)
  VALUES ('DNI', '45678912', 'Otra', 'Persona', '999888777');
  RAISE EXCEPTION 'FALLÓ: se duplicó el DNI';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK RN-01 cliente duplicado bloqueado: %', SQLERRM;
END $$;

DO $$ BEGIN
  INSERT INTO cliente (tipo_documento, num_documento, nombres, apellidos, telefono)
  VALUES ('DNI', '1234', 'Dato', 'Inválido', '999888777');
  RAISE EXCEPTION 'FALLÓ: se aceptó un DNI de 4 dígitos';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK Dominio DNI inválido bloqueado: %', SQLERRM;
END $$;

DO $$ BEGIN
  INSERT INTO cita (id_mascota, id_servicio, id_veterinario, id_registrado_por, inicio, fin)
  SELECT m.id_mascota, 1, v.id_usuario, v.id_usuario,
         date_trunc('day', now()) + interval '9 hours 15 minutes',
         date_trunc('day', now()) + interval '9 hours 45 minutes'
  FROM mascota m, usuario v WHERE m.nombre = 'Toby' AND v.usuario = 'cmendoza';
  RAISE EXCEPTION 'FALLÓ: se aceptó una cita solapada';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK RN-03 cita solapada bloqueada: %', SQLERRM;
END $$;

DO $$ BEGIN
  UPDATE cita SET estado = 'EN_ESPERA' WHERE estado = 'ATENDIDA';
  RAISE EXCEPTION 'FALLÓ: se reabrió una cita atendida';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK RF-12 transición inválida bloqueada: %', SQLERRM;
END $$;

DO $$ BEGIN
  UPDATE consulta SET indicaciones = 'Texto cambiado' WHERE estado = 'FIRMADA';
  RAISE EXCEPTION 'FALLÓ: se modificó una consulta firmada';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK RN-05 consulta firmada protegida: %', SQLERRM;
END $$;

DO $$ BEGIN
  INSERT INTO consulta_diagnostico (id_consulta, descripcion)
  SELECT id_consulta, 'Diagnóstico agregado después' FROM consulta WHERE estado = 'FIRMADA';
  RAISE EXCEPTION 'FALLÓ: se agregó un diagnóstico a una consulta firmada';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK RN-05 detalle de consulta firmada protegido: %', SQLERRM;
END $$;

DO $$
DECLARE v_id BIGINT;
BEGIN
  INSERT INTO consulta (id_mascota, id_veterinario, motivo)
  SELECT m.id_mascota, u.id_usuario, 'Control de peso'
  FROM mascota m, usuario u WHERE m.nombre = 'Toby' AND u.usuario = 'jhuaman'   -- técnico
  RETURNING id_consulta INTO v_id;
  INSERT INTO consulta_diagnostico (id_consulta, descripcion, es_principal) VALUES (v_id, 'Sobrepeso', TRUE);
  UPDATE consulta SET estado = 'FIRMADA' WHERE id_consulta = v_id;
  RAISE EXCEPTION 'FALLÓ: un técnico firmó la consulta';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK RN-04 firma por no veterinario bloqueada: %', SQLERRM;
END $$;

-- Ventas: turno nuevo del cajero para las pruebas
INSERT INTO caja_turno (id_cajero, monto_inicial)
SELECT id_usuario, 150.00 FROM usuario WHERE usuario = 'dramos';

DO $$
DECLARE v_id BIGINT;
BEGIN
  INSERT INTO venta (id_turno, id_cliente, serie)
  SELECT t.id_turno, c.id_cliente, 'T001' FROM caja_turno t, cliente c
  WHERE t.estado = 'ABIERTA' AND c.num_documento = '70123456' RETURNING id_venta INTO v_id;
  INSERT INTO venta_detalle (id_venta, id_lote, cantidad, precio_unitario)
  SELECT v_id, id_lote, 10, 145.00 FROM lote WHERE codigo_lote = 'BRV-0925';    -- hay 6
  INSERT INTO pago (id_venta, medio, monto) VALUES (v_id, 'EFECTIVO', 1450.00);
  PERFORM fn_confirmar_venta(v_id);
  RAISE EXCEPTION 'FALLÓ: se vendió más stock del disponible';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK RN-06 stock negativo bloqueado: %', SQLERRM;
END $$;

DO $$
DECLARE v_id BIGINT;
BEGIN
  INSERT INTO venta (id_turno, id_cliente, serie)
  SELECT t.id_turno, c.id_cliente, 'T001' FROM caja_turno t, cliente c
  WHERE t.estado = 'ABIERTA' AND c.num_documento = '70123456' RETURNING id_venta INTO v_id;
  INSERT INTO venta_detalle (id_venta, id_lote, cantidad, precio_unitario)
  SELECT v_id, id_lote, 1, 12.00 FROM lote WHERE codigo_lote = 'DRP-0801';     -- vencido
  INSERT INTO pago (id_venta, medio, monto) VALUES (v_id, 'EFECTIVO', 12.00);
  PERFORM fn_confirmar_venta(v_id);
  RAISE EXCEPTION 'FALLÓ: se vendió un lote vencido';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK RN-06 lote vencido bloqueado: %', SQLERRM;
END $$;

DO $$
DECLARE v_id BIGINT;
BEGIN
  INSERT INTO venta (id_turno, id_cliente, serie)
  SELECT t.id_turno, c.id_cliente, 'T001' FROM caja_turno t, cliente c
  WHERE t.estado = 'ABIERTA' AND c.num_documento = '70123456' RETURNING id_venta INTO v_id;
  INSERT INTO venta_detalle (id_venta, id_servicio, cantidad, precio_unitario) VALUES (v_id, 1, 1, 60.00);
  INSERT INTO pago (id_venta, medio, monto) VALUES (v_id, 'EFECTIVO', 48.00);
  PERFORM fn_confirmar_venta(v_id, 12.00);                                        -- 20 % sin autorización
  RAISE EXCEPTION 'FALLÓ: se aplicó 20 %% de descuento sin autorización';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK RN-08 descuento sin autorización bloqueado: %', SQLERRM;
END $$;

DO $$
DECLARE v_id BIGINT;
BEGIN
  INSERT INTO venta (id_turno, id_cliente, serie)
  SELECT t.id_turno, c.id_cliente, 'T001' FROM caja_turno t, cliente c
  WHERE t.estado = 'ABIERTA' AND c.num_documento = '70123456' RETURNING id_venta INTO v_id;
  INSERT INTO venta_detalle (id_venta, id_servicio, cantidad, precio_unitario) VALUES (v_id, 1, 1, 60.00);
  INSERT INTO pago (id_venta, medio, monto) VALUES (v_id, 'EFECTIVO', 50.00);
  PERFORM fn_confirmar_venta(v_id);
  RAISE EXCEPTION 'FALLÓ: se confirmó una venta con pago incompleto';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK RN-12 pago incompleto bloqueado: %', SQLERRM;
END $$;

DO $$ BEGIN
  DELETE FROM movimiento_inventario;
  RAISE EXCEPTION 'FALLÓ: se borró el kardex';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK Kardex inmutable: %', SQLERRM;
END $$;

DO $$ BEGIN
  UPDATE lote SET stock_actual = 999 WHERE codigo_lote = 'BRV-0925';
  RAISE EXCEPTION 'FALLÓ: la app cambió el stock sin kardex';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK Stock solo por kardex (permiso): %', SQLERRM;
END $$;

-- Flujo correcto: venta FEFO, anulación y devolución de stock (RN-07, RN-09)
DO $$
DECLARE
  v_id       BIGINT;
  v_lote     INT := fn_lote_sugerido((SELECT id_producto FROM producto WHERE codigo = 'MED-003'), 2);
  v_antes    NUMERIC;
  v_despues  NUMERIC;
  v_comp     TEXT;
BEGIN
  IF (SELECT codigo_lote FROM lote WHERE id_lote = v_lote) <> 'DRP-1102' THEN
    RAISE EXCEPTION 'FALLÓ: FEFO sugirió un lote vencido';
  END IF;
  SELECT stock_actual INTO v_antes FROM lote WHERE id_lote = v_lote;

  INSERT INTO venta (id_turno, id_cliente, serie)
  SELECT t.id_turno, c.id_cliente, 'T001' FROM caja_turno t, cliente c
  WHERE t.estado = 'ABIERTA' AND c.num_documento = '70123456' RETURNING id_venta INTO v_id;
  INSERT INTO venta_detalle (id_venta, id_lote, cantidad, precio_unitario) VALUES (v_id, v_lote, 2, 12.00);
  INSERT INTO pago (id_venta, medio, monto) VALUES (v_id, 'EFECTIVO', 24.00);
  v_comp := fn_confirmar_venta(v_id);
  PERFORM fn_anular_venta(v_id, 'Cliente desistió de la compra');

  SELECT stock_actual INTO v_despues FROM lote WHERE id_lote = v_lote;
  IF v_antes <> v_despues THEN
    RAISE EXCEPTION 'FALLÓ: la anulación no devolvió el stock';
  END IF;
  RAISE NOTICE 'OK RN-07/RN-09 venta % por FEFO y anulada; stock % -> %', v_comp, v_antes, v_despues;
END $$;

DO $$ BEGIN
  PERFORM fn_cerrar_caja((SELECT id_turno FROM caja_turno WHERE estado = 'ABIERTA'), 100.00);
  RAISE EXCEPTION 'FALLÓ: se cerró caja con diferencia sin observación';
EXCEPTION WHEN OTHERS THEN
  IF SQLERRM LIKE 'FALLÓ%' THEN RAISE; END IF;
  RAISE NOTICE 'OK RN-10 diferencia sin justificar bloqueada: %', SQLERRM;
END $$;

RESET ROLE;

-- Evidencias para capturar en el informe
SELECT * FROM vetsalud.v_historia_clinica;
SELECT codigo, producto, codigo_lote, tipo, entrada, salida, saldo_lote FROM vetsalud.v_kardex ORDER BY codigo, id_movimiento;
SELECT * FROM vetsalud.v_alerta_inventario ORDER BY alerta, codigo;
SELECT serie, numero, total, estado FROM vetsalud.venta WHERE numero IS NOT NULL ORDER BY numero;
SELECT tabla, operacion, count(*) FROM vetsalud.auditoria GROUP BY 1, 2 ORDER BY 1, 2;
