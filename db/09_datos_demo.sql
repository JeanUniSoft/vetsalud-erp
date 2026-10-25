-- =====================================================================
-- VetSalud ERP · Paso 9 (APF3): datos de demostración para la aplicación web
--   · Claves de prueba con PBKDF2-SHA256 (210 000 iteraciones, sal aleatoria) — RNF-04
--     Clave de laboratorio de TODOS los usuarios demo: VetSalud2026!
--   · Agenda de hoy y mañana, consultas, inventario con alertas y una caja abierta
-- =====================================================================
SET ROLE vetsalud_owner;
SET search_path TO vetsalud, public;

-- 9.1 Claves de prueba (nunca texto plano)
UPDATE usuario u SET clave_hash = c.hash, clave_actualizada_en = now()
FROM (VALUES
  ('rparedes', 'pbkdf2_sha256$210000$OiZ1kVjP1Y8n7KOOaL6vmQ==$yvlEQbgdBdOe3E1K/UoqoHo93GLSjxts/VMFa8ZxO6U='),
  ('ltorres',  'pbkdf2_sha256$210000$ehByzYJ2zOhdL5/Mq6yptg==$YmmfG9fLrtXhtPQkdfsS8WCd9V7rwMu/ncs8bwJD88w='),
  ('cmendoza', 'pbkdf2_sha256$210000$uboft13X6CwRt5+9S9k36g==$yRgD/rF0Xf+hadJTa7KGtmCTVNMv7sNKY2AwueyCBeo='),
  ('asalazar', 'pbkdf2_sha256$210000$B8ntQr1Xx3FhoawTb8M5cQ==$ZeCdrOdxXl/rh27mrvsbS4A3c9wqFN+Mnu57e7cFLig='),
  ('jhuaman',  'pbkdf2_sha256$210000$rrdWfKO+x2dlAXQmyHAMJg==$rHB1RzDvD7+RURjsvUBQoAedU/ntZ7+q/XhRZROwTgo='),
  ('mccori',   'pbkdf2_sha256$210000$e9M7oelib7kXu8EycqlTVQ==$FHYQPpf9VXwDeZbi7qMzJA4QtkmTA/9wTaJ32imxPgc='),
  ('dramos',   'pbkdf2_sha256$210000$U2fZFthLxv4Zwiy8N9+Rkw==$o4VAtKWlI6cs665efyRhe2pNBFg/PPEADvkohF9PuoQ=')
) AS c(usuario, hash)
WHERE u.usuario = c.usuario;

-- 9.2 Más clientes y mascotas
INSERT INTO cliente (tipo_documento, num_documento, nombres, apellidos, telefono, correo, direccion, distrito, acepta_recordatorios) VALUES
  ('DNI', '41256789', 'Jorge',    'Paredes Luna',    '951234876', 'jorge.paredes@gmail.com',  'Av. Benavides 1520',  'Miraflores',  TRUE),
  ('DNI', '72345618', 'Valeria',  'Quispe Rojas',    '962113450', 'valeria.qr@hotmail.com',   'Jr. Los Pinos 230',   'San Borja',   TRUE),
  ('DNI', '46781235', 'Ricardo',  'Flores Huamán',   '934556712', NULL,                       'Calle Las Uvas 118',  'La Molina',   FALSE),
  ('CE',  '002345678','Sophie',   'Martin',          '987001122', 'sophie.martin@outlook.com','Av. Pardo 455',       'Miraflores',  TRUE),
  ('DNI', '70991234', 'Camila',   'Vargas Soto',     '945667788', 'camila.vargas@gmail.com',  'Av. Primavera 890',   'Surco',       TRUE),
  ('RUC', '20456789123','Refugio Patitas Felices', NULL, '014456789', 'contacto@patitasfelices.org', 'Jr. Huáscar 77', 'Jesús María', TRUE);

INSERT INTO raza (id_especie, nombre)
SELECT e.id_especie, r.nombre FROM (VALUES
  ('Canino', 'Golden Retriever'), ('Canino', 'Pug'), ('Canino', 'Bulldog Francés'),
  ('Felino', 'Maine Coon'), ('Felino', 'British Shorthair')
) AS r(especie, nombre) JOIN especie e ON e.nombre = r.especie;

INSERT INTO mascota (id_cliente, id_raza, nombre, sexo, fecha_nacimiento, color, peso_kg, microchip, esterilizado, alertas)
SELECT c.id_cliente, r.id_raza, m.nombre, m.sexo, m.nac::DATE, m.color, m.peso, m.chip, m.est, m.alerta
FROM (VALUES
  ('41256789',    'Golden Retriever', 'Canino', 'Max',     'M', '2020-02-14', 'Dorado',      31.20, '985141000654321', TRUE,  NULL),
  ('41256789',    'Pug',              'Canino', 'Lola',    'H', '2022-09-03', 'Beige',        8.10, NULL,              FALSE, 'Braquicéfala: vigilar respiración'),
  ('72345618',    'Maine Coon',       'Felino', 'Simba',   'M', '2021-05-30', 'Atigrado',     7.40, NULL,              TRUE,  NULL),
  ('46781235',    'Bulldog Francés',  'Canino', 'Bruno',   'M', '2023-04-11', 'Atigrado',    12.30, NULL,              FALSE, 'Alergia alimentaria al pollo'),
  ('002345678',   'British Shorthair','Felino', 'Nala',    'H', '2020-12-01', 'Azul',         4.80, NULL,              TRUE,  NULL),
  ('70991234',    'Schnauzer',        'Canino', 'Coco',    'H', '2018-08-19', 'Sal y pimienta', 9.50, NULL,            TRUE,  'Cardiopatía leve'),
  ('20456789123', 'Mestizo',          'Canino', 'Chispa',  'H', '2024-01-20', 'Negro',        6.20, NULL,              FALSE, NULL),
  ('20456789123', 'Mestizo',          'Felino', 'Tomás',   'M', '2023-10-05', 'Naranja',      3.90, NULL,              TRUE,  NULL)
) AS m(doc, raza, especie, nombre, sexo, nac, color, peso, chip, est, alerta)
JOIN cliente c ON c.num_documento = m.doc
JOIN especie e ON e.nombre = m.especie
JOIN raza r ON r.nombre = m.raza AND r.id_especie = e.id_especie;

-- 9.3 Usuario del portal del cliente (María Gonzales, propietaria de Rocky y Misha)
INSERT INTO usuario (id_rol, nombres, apellidos, usuario, correo, clave_hash, clave_actualizada_en)
SELECT r.id_rol, 'María', 'Gonzales Díaz', 'mgonzales', 'maria.gonzales@gmail.com',
       'pbkdf2_sha256$210000$Wvcz9cEcSpSdZYr5k62rKg==$UaIsrKlATNxC5fd7MaIQr52Mv1yoxTXGrde3+c2u3Ew=', now()
FROM rol r WHERE r.nombre = 'CLIENTE';
INSERT INTO cliente_usuario (id_usuario, id_cliente)
SELECT u.id_usuario, c.id_cliente FROM usuario u, cliente c
WHERE u.usuario = 'mgonzales' AND c.num_documento = '45678912';

-- 9.4 Agenda de hoy y mañana (las transiciones pasan por el trigger de estados)
SELECT set_config('vetsalud.id_usuario', (SELECT id_usuario::TEXT FROM usuario WHERE usuario = 'ltorres'), false);

INSERT INTO cita (id_mascota, id_servicio, id_veterinario, id_registrado_por, inicio, fin, motivo)
SELECT m.id_mascota, s.id_servicio, v.id_usuario, fn_usuario_actual(),
       date_trunc('day', now()) + c.hora::INTERVAL,
       date_trunc('day', now()) + c.hora::INTERVAL + make_interval(mins => s.duracion_min),
       c.motivo
FROM (VALUES
  ('Max',    'Consulta general',         'cmendoza', '0 days 08:30', 'Vómitos desde ayer'),
  ('Simba',  'Vacuna triple felina',     'asalazar', '0 days 09:00', 'Refuerzo anual'),
  ('Lola',   'Consulta general',         'asalazar', '0 days 10:00', 'Control respiratorio'),
  ('Bruno',  'Desparasitación',          'cmendoza', '0 days 11:00', 'Desparasitación trimestral'),
  ('Nala',   'Consulta general',         'cmendoza', '0 days 15:00', 'Pérdida de apetito'),
  ('Coco',   'Hemograma (laboratorio ext.)', 'asalazar', '0 days 16:00', 'Control cardiológico'),
  ('Chispa', 'Vacuna séxtuple canina',   'cmendoza', '1 days 09:30', 'Primera dosis'),
  ('Tomás',  'Consulta general',         'asalazar', '1 days 11:00', 'Revisión post esterilización')
) AS c(mascota, servicio, vet, hora, motivo)
JOIN mascota m ON m.nombre = c.mascota
JOIN servicio s ON s.nombre = c.servicio
JOIN usuario v ON v.usuario = c.vet;

-- Estados del día: Max atendido, Simba en atención, Lola en espera, Bruno confirmado
UPDATE cita SET estado = 'CONFIRMADA'  WHERE motivo IN ('Vómitos desde ayer', 'Refuerzo anual', 'Control respiratorio', 'Desparasitación trimestral');
UPDATE cita SET estado = 'EN_ESPERA'   WHERE motivo IN ('Vómitos desde ayer', 'Refuerzo anual', 'Control respiratorio');
UPDATE cita SET estado = 'EN_ATENCION' WHERE motivo IN ('Vómitos desde ayer', 'Refuerzo anual');
UPDATE cita SET estado = 'ATENDIDA'    WHERE motivo = 'Vómitos desde ayer';

-- 9.5 Consulta firmada de Max (Dr. Mendoza)
SELECT set_config('vetsalud.id_usuario', (SELECT id_usuario::TEXT FROM usuario WHERE usuario = 'cmendoza'), false);
INSERT INTO consulta (id_mascota, id_cita, id_veterinario, motivo, anamnesis, temperatura_c, frec_cardiaca,
                      frec_respiratoria, peso_kg, examen_clinico, indicaciones, fecha_control)
SELECT c.id_mascota, c.id_cita, c.id_veterinario, c.motivo,
       'Tres episodios de vómito; comió basura en el parque', 39.2, 110, 28, 31.00,
       'Abdomen sensible a la palpación, mucosas rosadas, hidratación adecuada',
       'Dieta blanda 3 días, hidratación oral, control si persiste', CURRENT_DATE + 5
FROM cita c WHERE c.motivo = 'Vómitos desde ayer';
INSERT INTO consulta_diagnostico (id_consulta, descripcion, es_principal)
SELECT id_consulta, 'Gastroenteritis aguda por indiscreción alimentaria', TRUE
FROM consulta WHERE motivo = 'Vómitos desde ayer';
UPDATE consulta SET estado = 'FIRMADA' WHERE motivo = 'Vómitos desde ayer';
INSERT INTO recordatorio (id_cliente, id_consulta, programado_para)
SELECT m.id_cliente, c.id_consulta, (c.fecha_control - 1) + time '09:00'
FROM consulta c JOIN mascota m USING (id_mascota) WHERE c.motivo = 'Vómitos desde ayer';

-- Consulta abierta de Simba (Dra. Salazar), aún sin firmar
SELECT set_config('vetsalud.id_usuario', (SELECT id_usuario::TEXT FROM usuario WHERE usuario = 'asalazar'), false);
INSERT INTO consulta (id_mascota, id_cita, id_veterinario, motivo, temperatura_c, frec_cardiaca, peso_kg)
SELECT c.id_mascota, c.id_cita, c.id_veterinario, c.motivo, 38.5, 140, 7.40
FROM cita c WHERE c.motivo = 'Refuerzo anual';

-- 9.6 Inventario: más productos y lotes con alertas
SELECT set_config('vetsalud.id_usuario', (SELECT id_usuario::TEXT FROM usuario WHERE usuario = 'mccori'), false);
INSERT INTO producto (id_categoria, codigo, nombre, unidad, precio_venta, stock_minimo, requiere_receta)
SELECT c.id_categoria, p.codigo, p.nombre, p.unidad, p.precio, p.minimo, p.receta
FROM (VALUES
  ('Antibióticos',     'MED-004', 'Enrofloxacina 50 mg x 10 tab',      'CAJA',    32.00, 4,  TRUE),
  ('Antiparasitarios', 'MED-005', 'NexGard 4-10 kg',                   'UND',     78.00, 5,  FALSE),
  ('Alimentos',        'ALI-002', 'Royal Canin Gastrointestinal 2 kg', 'BOLSA',   135.00, 3,  FALSE),
  ('Accesorios',       'ACC-002', 'Correa retráctil 5 m',              'UND',     45.00, 2,  FALSE)
) AS p(categoria, codigo, nombre, unidad, precio, minimo, receta)
JOIN categoria c ON c.nombre = p.categoria;

INSERT INTO ingreso (id_proveedor, id_usuario, tipo_doc, num_doc, fecha)
SELECT id_proveedor, fn_usuario_actual(), 'FACTURA', 'F001-00004988', CURRENT_DATE - 3
FROM proveedor WHERE ruc = '20100123456';
INSERT INTO lote (id_producto, codigo_lote, fecha_vencimiento, costo_unitario)
SELECT p.id_producto, l.lote, (CURRENT_DATE + l.dias)::DATE, l.costo
FROM (VALUES
  ('MED-004', 'ENR-2602', 300, 19.00),
  ('MED-005', 'NXG-2601',  20, 52.00),   -- por vencer
  ('ALI-002', 'RCG-2604', 240, 96.00),
  ('ACC-002', 'SIN-LOTE', NULL, 24.00)
) AS l(codigo, lote, dias, costo)
JOIN producto p ON p.codigo = l.codigo;
INSERT INTO movimiento_inventario (id_lote, tipo, cantidad, id_ingreso, id_usuario)
SELECT l.id_lote, 'INGRESO', q.cantidad, (SELECT max(id_ingreso) FROM ingreso), fn_usuario_actual()
FROM (VALUES ('ENR-2602', 12), ('NXG-2601', 4), ('RCG-2604', 6)) AS q(lote, cantidad)
JOIN lote l ON l.codigo_lote = q.lote;
INSERT INTO movimiento_inventario (id_lote, tipo, cantidad, id_ingreso, id_usuario)
SELECT l.id_lote, 'INGRESO', 1, (SELECT max(id_ingreso) FROM ingreso), fn_usuario_actual()
FROM lote l JOIN producto p USING (id_producto) WHERE p.codigo = 'ACC-002';

-- Alertas internas pendientes por rol (RF-38)
INSERT INTO alerta (id_rol_destino, tipo, mensaje)
SELECT r.id_rol, a.tipo, a.mensaje FROM (VALUES
  ('FARMACIA',      'POR_VENCER',   'NexGard 4-10 kg (lote NXG-2601) vence en 20 días'),
  ('FARMACIA',      'STOCK_MINIMO', 'Correa retráctil 5 m llegó al stock mínimo'),
  ('CAJERO',        'CAJA',         'Recordar arqueo parcial a las 13:00'),
  ('ADMINISTRADOR', 'OTRO',         'Revisar permisos del nuevo técnico veterinario')
) AS a(rol, tipo, mensaje) JOIN rol r ON r.nombre = a.rol;

-- 9.7 Caja abierta de hoy con dos ventas confirmadas
SELECT set_config('vetsalud.id_usuario', (SELECT id_usuario::TEXT FROM usuario WHERE usuario = 'dramos'), false);
-- (06_pruebas.sql ya deja un turno abierto de S/ 150 para dramos; solo se crea si no existe)
INSERT INTO caja_turno (id_cajero, monto_inicial)
SELECT u.id_usuario, 150.00 FROM usuario u
WHERE u.usuario = 'dramos'
  AND NOT EXISTS (SELECT 1 FROM caja_turno t WHERE t.id_cajero = u.id_usuario AND t.estado = 'ABIERTA');

-- Venta 1: consulta de Max + producto de la receta
INSERT INTO venta (id_turno, id_cliente, id_mascota, id_consulta, serie)
SELECT t.id_turno, m.id_cliente, m.id_mascota, c.id_consulta, 'T001'
FROM caja_turno t, consulta c JOIN mascota m USING (id_mascota)
WHERE t.estado = 'ABIERTA' AND c.motivo = 'Vómitos desde ayer';
INSERT INTO venta_detalle (id_venta, id_servicio, cantidad, precio_unitario)
SELECT max(v.id_venta), s.id_servicio, 1, s.tarifa FROM venta v, servicio s
WHERE s.nombre = 'Consulta general' GROUP BY s.id_servicio, s.tarifa;
INSERT INTO venta_detalle (id_venta, id_lote, cantidad, precio_unitario)
SELECT (SELECT max(id_venta) FROM venta), fn_lote_sugerido(p.id_producto, 1), 1, p.precio_venta
FROM producto p WHERE p.codigo = 'ALI-002';
INSERT INTO pago (id_venta, medio, monto)
SELECT max(id_venta), 'EFECTIVO', 195.00 FROM venta;
SELECT fn_confirmar_venta((SELECT max(id_venta) FROM venta));

-- Venta 2: antiparasitario en mostrador pagado con tarjeta
INSERT INTO venta (id_turno, id_cliente, serie)
SELECT t.id_turno, c.id_cliente, 'T001'
FROM caja_turno t, cliente c WHERE t.estado = 'ABIERTA' AND c.num_documento = '72345618';
INSERT INTO venta_detalle (id_venta, id_lote, cantidad, precio_unitario)
SELECT (SELECT max(id_venta) FROM venta), fn_lote_sugerido(p.id_producto, 1), 1, p.precio_venta
FROM producto p WHERE p.codigo = 'MED-005';
INSERT INTO pago (id_venta, medio, monto, referencia)
SELECT max(id_venta), 'TARJETA', 78.00, 'VISA-004512' FROM venta;
SELECT fn_confirmar_venta((SELECT max(id_venta) FROM venta));

RESET ROLE;
