-- =====================================================================
-- VetSalud ERP · Paso 5: datos de prueba (catálogos + un flujo completo)
-- Recorre el proceso de la Figura 24 del APF1:
-- cita → consulta firmada → receta → venta con stock → cierre de caja
-- =====================================================================
SET ROLE vetsalud_owner;
SET search_path TO vetsalud, public;

-- 5.1 Seguridad
INSERT INTO rol (nombre, descripcion) VALUES
  ('ADMINISTRADOR', 'Configura usuarios, parámetros y autoriza excepciones'),
  ('RECEPCION',     'Clientes, mascotas, agenda y llegada'),
  ('VETERINARIO',   'Consulta, diagnóstico, receta y firma'),
  ('TECNICO',       'Signos vitales y apoyo en procedimientos'),
  ('FARMACIA',      'Catálogo, lotes, kardex y dispensación'),
  ('CAJERO',        'Ventas, pagos, comprobantes y cierre de turno');

INSERT INTO permiso (codigo, modulo, descripcion) VALUES
  ('CLIENTE_GESTIONAR', 'CLIENTES',   'Registrar y editar clientes y mascotas'),
  ('CITA_GESTIONAR',    'AGENDA',     'Programar, reprogramar y cancelar citas'),
  ('CONSULTA_FIRMAR',   'CLINICA',    'Firmar consultas'),
  ('INVENTARIO_AJUSTAR','INVENTARIO', 'Registrar ajustes de stock'),
  ('VENTA_PROCESAR',    'VENTAS',     'Registrar ventas y pagos'),
  ('VENTA_ANULAR',      'VENTAS',     'Anular ventas confirmadas'),
  ('DESCUENTO_AUTORIZAR','VENTAS',    'Autorizar descuentos mayores al 10 %');

INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso FROM rol r JOIN permiso p ON
     (r.nombre = 'ADMINISTRADOR')
  OR (r.nombre = 'RECEPCION'   AND p.codigo IN ('CLIENTE_GESTIONAR', 'CITA_GESTIONAR'))
  OR (r.nombre = 'VETERINARIO' AND p.codigo = 'CONSULTA_FIRMAR')
  OR (r.nombre = 'FARMACIA'    AND p.codigo = 'INVENTARIO_AJUSTAR')
  OR (r.nombre = 'CAJERO'      AND p.codigo = 'VENTA_PROCESAR');

-- clave_hash: en la app real lo genera BCrypt; aquí es un valor de ejemplo
INSERT INTO usuario (id_rol, nombres, apellidos, usuario, correo, clave_hash, colegiatura_cmvp)
SELECT r.id_rol, u.nombres, u.apellidos, u.usuario, u.correo,
       '$2a$10$ejemploHashNoRealParaPruebasAcademicas000000000000000', u.cmvp
FROM (VALUES
  ('ADMINISTRADOR', 'Rosa',   'Paredes Quispe', 'rparedes',  'rparedes@vetsalud.pe',  NULL),
  ('RECEPCION',     'Lucía',  'Torres Vega',    'ltorres',   'ltorres@vetsalud.pe',   NULL),
  ('VETERINARIO',   'Carlos', 'Mendoza Ríos',   'cmendoza',  'cmendoza@vetsalud.pe',  '04512'),
  ('VETERINARIO',   'Ana',    'Salazar León',   'asalazar',  'asalazar@vetsalud.pe',  '05233'),
  ('TECNICO',       'Jorge',  'Huamán Cruz',    'jhuaman',   'jhuaman@vetsalud.pe',   NULL),
  ('FARMACIA',      'Milagros','Ccori Apaza',   'mccori',    'mccori@vetsalud.pe',    NULL),
  ('CAJERO',        'Diego',  'Ramos Chávez',   'dramos',    'dramos@vetsalud.pe',    NULL)
) AS u(rol, nombres, apellidos, usuario, correo, cmvp)
JOIN rol r ON r.nombre = u.rol;

-- 5.2 Clientes y mascotas
INSERT INTO cliente (tipo_documento, num_documento, nombres, apellidos, telefono, correo, distrito, acepta_recordatorios) VALUES
  ('DNI', '45678912', 'María',   'Gonzales Díaz',  '987654321', 'maria.gonzales@gmail.com', 'Surco',     TRUE),
  ('DNI', '70123456', 'José',    'Pérez Lozano',   '912345678', 'jperez@hotmail.com',       'San Borja', FALSE),
  ('CE',  '001234567','Valentina','Rossi',         '955112233', 'vrossi@gmail.com',         'Miraflores',TRUE),
  ('RUC', '20601234567', 'Albergue Patitas Felices S.A.C.', NULL, '014567890', 'contacto@patitas.pe', 'Chorrillos', TRUE);

INSERT INTO especie (nombre) VALUES ('Canino'), ('Felino'), ('Ave'), ('Roedor');

INSERT INTO raza (id_especie, nombre)
SELECT e.id_especie, r.nombre FROM (VALUES
  ('Canino', 'Mestizo'), ('Canino', 'Labrador Retriever'), ('Canino', 'Shih Tzu'), ('Canino', 'Schnauzer'),
  ('Felino', 'Mestizo'), ('Felino', 'Persa'), ('Felino', 'Siamés'),
  ('Ave', 'Periquito'), ('Roedor', 'Hámster sirio')
) AS r(especie, nombre) JOIN especie e ON e.nombre = r.especie;

INSERT INTO mascota (id_cliente, id_raza, nombre, sexo, fecha_nacimiento, color, peso_kg, microchip, esterilizado, alertas)
SELECT c.id_cliente, r.id_raza, m.nombre, m.sexo, m.nac::DATE, m.color, m.peso, m.chip, m.est, m.alerta
FROM (VALUES
  ('45678912', 'Labrador Retriever', 'Rocky', 'M', '2021-03-10', 'Dorado',  28.40, '985141000123456', TRUE,  NULL),
  ('45678912', 'Siamés',             'Misha', 'H', '2022-07-22', 'Crema',    4.10, NULL,              TRUE,  'Alérgica a la penicilina'),
  ('70123456', 'Shih Tzu',           'Toby',  'M', '2019-11-05', 'Blanco',   6.80, NULL,              FALSE, NULL),
  ('001234567','Persa',              'Luna',  'H', '2023-01-15', 'Gris',     3.60, NULL,              FALSE, NULL),
  ('20601234567','Mestizo',          'Canela','H', '2020-06-01', 'Marrón',  15.20, NULL,              TRUE,  'Agresiva al manipular patas')
) AS m(doc, raza, nombre, sexo, nac, color, peso, chip, est, alerta)
JOIN cliente c ON c.num_documento = m.doc
JOIN raza r ON r.nombre = m.raza AND r.id_especie = (CASE WHEN m.raza IN ('Siamés', 'Persa') THEN
                                                        (SELECT id_especie FROM especie WHERE nombre = 'Felino')
                                                      ELSE (SELECT id_especie FROM especie WHERE nombre = 'Canino') END);

-- 5.3 Servicios
INSERT INTO servicio (nombre, tipo, duracion_min, tarifa) VALUES
  ('Consulta general',            'CONSULTA',      30,  60.00),
  ('Vacuna séxtuple canina',      'VACUNACION',    20,  85.00),
  ('Vacuna triple felina',        'VACUNACION',    20,  75.00),
  ('Desparasitación',             'PROCEDIMIENTO', 15,  35.00),
  ('Curación de herida',          'PROCEDIMIENTO', 30,  70.00),
  ('Hemograma (laboratorio ext.)','LABORATORIO',   15,  90.00);

-- 5.4 Farmacia: catálogo, proveedor, ingreso y lotes
INSERT INTO categoria (nombre) VALUES ('Antibióticos'), ('Antiparasitarios'), ('Alimentos'), ('Accesorios');

INSERT INTO producto (id_categoria, codigo, nombre, unidad, precio_venta, stock_minimo, requiere_receta)
SELECT c.id_categoria, p.codigo, p.nombre, p.unidad, p.precio, p.minimo, p.receta
FROM (VALUES
  ('Antibióticos',     'MED-001', 'Amoxicilina 250 mg x 10 tab',     'CAJA',   28.00, 5,  TRUE),
  ('Antiparasitarios', 'MED-002', 'Bravecto 10-20 kg',               'UND',   145.00, 3,  FALSE),
  ('Antiparasitarios', 'MED-003', 'Drontal Plus perro',              'TABLETA', 12.00, 20, FALSE),
  ('Alimentos',        'ALI-001', 'Pro Plan Adulto Razas Medianas 3 kg','BOLSA', 119.00, 4, FALSE),
  ('Accesorios',       'ACC-001', 'Collar isabelino talla M',        'UND',    25.00, 2,  FALSE)
) AS p(categoria, codigo, nombre, unidad, precio, minimo, receta)
JOIN categoria c ON c.nombre = p.categoria;

INSERT INTO proveedor (ruc, razon_social, telefono, correo) VALUES
  ('20100123456', 'Distribuidora Veterinaria Andina S.A.C.', '016543210', 'ventas@dvandina.pe'),
  ('20487654321', 'Nutrición Animal del Perú S.A.',          '017894561', 'pedidos@nutrianimal.pe');

-- Pruebas y datos corren con el usuario de farmacia (lo usan auditoría y kardex)
SELECT set_config('vetsalud.id_usuario', (SELECT id_usuario::TEXT FROM usuario WHERE usuario = 'mccori'), false);

INSERT INTO ingreso (id_proveedor, id_usuario, tipo_doc, num_doc, fecha)
SELECT id_proveedor, fn_usuario_actual(), 'FACTURA', 'F001-00004521', CURRENT_DATE - 20
FROM proveedor WHERE ruc = '20100123456';

INSERT INTO lote (id_producto, codigo_lote, fecha_vencimiento, costo_unitario)
SELECT p.id_producto, l.lote, (CURRENT_DATE + l.dias)::DATE, l.costo
FROM (VALUES
  ('MED-001', 'AMX-2407',  15, 16.50),   -- vence pronto: debe salir primero (FEFO)
  ('MED-001', 'AMX-2511', 400, 17.00),
  ('MED-002', 'BRV-0925', 500, 98.00),
  ('MED-003', 'DRP-1102', 250,  6.20),
  ('MED-003', 'DRP-0801',  -5,  6.00),   -- lote vencido: no se puede vender
  ('ALI-001', 'PPL-3301', 180, 82.00),
  ('ACC-001', 'SIN-LOTE', NULL, 11.00)
) AS l(codigo, lote, dias, costo)
JOIN producto p ON p.codigo = l.codigo;

-- El stock entra SOLO por el kardex; el trigger actualiza lote.stock_actual
INSERT INTO movimiento_inventario (id_lote, tipo, cantidad, id_ingreso, id_usuario)
SELECT l.id_lote, 'INGRESO', q.cantidad, (SELECT max(id_ingreso) FROM ingreso), fn_usuario_actual()
FROM (VALUES ('AMX-2407', 4), ('AMX-2511', 20), ('BRV-0925', 6), ('DRP-1102', 50),
             ('DRP-0801', 10), ('PPL-3301', 8), ('SIN-LOTE', 5)) AS q(lote, cantidad)
JOIN lote l ON l.codigo_lote = q.lote;

-- 5.5 Agenda: cita de Rocky con el Dr. Mendoza (recepción la registra)
SELECT set_config('vetsalud.id_usuario', (SELECT id_usuario::TEXT FROM usuario WHERE usuario = 'ltorres'), false);

INSERT INTO cita (id_mascota, id_servicio, id_veterinario, id_registrado_por, inicio, fin, motivo)
SELECT m.id_mascota, s.id_servicio, v.id_usuario, fn_usuario_actual(),
       date_trunc('day', now()) + interval '9 hours',
       date_trunc('day', now()) + interval '9 hours 30 minutes',
       'Cojera en pata trasera derecha'
FROM mascota m, servicio s, usuario v
WHERE m.nombre = 'Rocky' AND s.nombre = 'Consulta general' AND v.usuario = 'cmendoza';

INSERT INTO cita (id_mascota, id_servicio, id_veterinario, id_registrado_por, inicio, fin, motivo)
SELECT m.id_mascota, s.id_servicio, v.id_usuario, fn_usuario_actual(),
       date_trunc('day', now()) + interval '1 day 10 hours',
       date_trunc('day', now()) + interval '1 day 10 hours 20 minutes',
       'Vacuna anual'
FROM mascota m, servicio s, usuario v
WHERE m.nombre = 'Misha' AND s.nombre = 'Vacuna triple felina' AND v.usuario = 'asalazar';

-- Rocky llega y pasa a consulta (transiciones válidas de RF-12)
UPDATE cita SET estado = 'CONFIRMADA'  WHERE motivo LIKE 'Cojera%';
UPDATE cita SET estado = 'EN_ESPERA'   WHERE motivo LIKE 'Cojera%';
UPDATE cita SET estado = 'EN_ATENCION' WHERE motivo LIKE 'Cojera%';

-- 5.6 Consulta del Dr. Mendoza: diagnóstico, procedimiento, receta y firma
SELECT set_config('vetsalud.id_usuario', (SELECT id_usuario::TEXT FROM usuario WHERE usuario = 'cmendoza'), false);

INSERT INTO consulta (id_mascota, id_cita, id_veterinario, motivo, anamnesis,
                      temperatura_c, frec_cardiaca, frec_respiratoria, peso_kg,
                      examen_clinico, indicaciones, fecha_control)
SELECT c.id_mascota, c.id_cita, c.id_veterinario, c.motivo,
       'Cojea desde hace 3 días tras jugar en el parque',
       38.9, 96, 24, 28.60,
       'Herida superficial en almohadilla plantar, sin fractura a la palpación',
       'Reposo 5 días, limpieza diaria, collar isabelino', CURRENT_DATE + 7
FROM cita c WHERE c.motivo LIKE 'Cojera%';

INSERT INTO consulta_diagnostico (id_consulta, descripcion, es_principal)
SELECT id_consulta, 'Laceración de almohadilla plantar miembro posterior derecho', TRUE FROM consulta;
INSERT INTO consulta_diagnostico (id_consulta, descripcion, es_principal)
SELECT id_consulta, 'Pododermatitis leve secundaria', FALSE FROM consulta;

INSERT INTO consulta_procedimiento (id_consulta, id_servicio, id_responsable, precio_unitario, resultado)
SELECT c.id_consulta, s.id_servicio, u.id_usuario, s.tarifa, 'Limpieza y vendaje sin complicaciones'
FROM consulta c, servicio s, usuario u
WHERE s.nombre = 'Curación de herida' AND u.usuario = 'jhuaman';

INSERT INTO receta (id_consulta, indicaciones)
SELECT id_consulta, 'Administrar con comida. Volver si hay fiebre.' FROM consulta;

INSERT INTO receta_detalle (id_receta, id_producto, dosis, frecuencia, duracion_dias, via, cantidad)
SELECT r.id_receta, p.id_producto, '1 tableta (250 mg)', 'Cada 12 horas', 7, 'ORAL', 1
FROM receta r, producto p WHERE p.codigo = 'MED-001';

UPDATE consulta SET estado = 'FIRMADA';        -- el trigger valida rol y diagnóstico
UPDATE cita SET estado = 'ATENDIDA' WHERE motivo LIKE 'Cojera%';

INSERT INTO recordatorio (id_cliente, id_consulta, programado_para)
SELECT m.id_cliente, c.id_consulta, (c.fecha_control - 1) + time '09:00'
FROM consulta c JOIN mascota m USING (id_mascota);

-- 5.7 Caja: apertura, venta de servicios + productos, pago mixto y cierre
SELECT set_config('vetsalud.id_usuario', (SELECT id_usuario::TEXT FROM usuario WHERE usuario = 'dramos'), false);

INSERT INTO serie_comprobante (serie, tipo) VALUES ('T001', 'TICKET');

INSERT INTO caja_turno (id_cajero, monto_inicial)
SELECT id_usuario, 200.00 FROM usuario WHERE usuario = 'dramos';

INSERT INTO venta (id_turno, id_cliente, id_mascota, id_consulta, serie)
SELECT t.id_turno, m.id_cliente, c.id_mascota, c.id_consulta, 'T001'
FROM caja_turno t, consulta c JOIN mascota m USING (id_mascota);

-- Servicios de la atención (consulta + procedimiento)
INSERT INTO venta_detalle (id_venta, id_servicio, cantidad, precio_unitario)
SELECT v.id_venta, s.id_servicio, 1, s.tarifa
FROM venta v, servicio s WHERE s.nombre IN ('Consulta general', 'Curación de herida');

-- Productos: el lote lo sugiere FEFO (RN-07)
INSERT INTO venta_detalle (id_venta, id_lote, cantidad, precio_unitario)
SELECT v.id_venta, fn_lote_sugerido(p.id_producto, 1), 1, p.precio_venta
FROM venta v, producto p WHERE p.codigo IN ('MED-001', 'ACC-001');

-- Total = 60 + 70 + 28 + 25 = 183.00 ; pago mixto
INSERT INTO pago (id_venta, medio, monto, referencia)
SELECT v.id_venta, p.medio::medio_pago, p.monto, p.referencia
FROM venta v, (VALUES ('EFECTIVO', 100.00, NULL), ('YAPE_PLIN', 83.00, 'OP-558712')) AS p(medio, monto, referencia);

SELECT fn_confirmar_venta(id_venta) AS comprobante FROM venta;

-- Efectivo esperado = 200 inicial + 100 cobrado = 300.00
SELECT fn_cerrar_caja(id_turno, 300.00) AS diferencia FROM caja_turno;

RESET ROLE;
