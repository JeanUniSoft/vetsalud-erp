-- =====================================================================
-- VetSalud ERP · Paso 8 (APF3): ajustes del modelo para la aplicación web
--   · RF-03: cambio y restablecimiento controlado de contraseña
--   · RF-24: parámetros configurables (días para "por vencer", intentos, descuento)
--   · RF-37 / P11: acceso del cliente a su portal (rol CLIENTE)
--   · RF-02: permisos nuevos de la matriz por rol
-- =====================================================================
SET ROLE vetsalud_owner;
SET search_path TO vetsalud, public;

-- 8.1 Seguridad de la cuenta (RF-03)
ALTER TABLE usuario
  ADD COLUMN debe_cambiar_clave   BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN clave_actualizada_en TIMESTAMPTZ;

-- 8.2 Parámetros del sistema (RF-24 configurable, RNF-05)
CREATE TABLE parametro (
  clave        VARCHAR(40)  PRIMARY KEY,
  valor        VARCHAR(100) NOT NULL,
  descripcion  VARCHAR(200) NOT NULL,
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO parametro (clave, valor, descripcion) VALUES
  ('DIAS_POR_VENCER',    '30', 'Días de anticipación para alertar productos por vencer (RF-24)'),
  ('MAX_INTENTOS_LOGIN', '5',  'Intentos fallidos antes de bloquear la cuenta (RF-01)'),
  ('DESCUENTO_MAX_PCT',  '10', 'Descuento máximo sin autorización del administrador (RN-08)'),
  ('MINUTOS_SESION',     '15', 'Minutos de inactividad antes de cerrar la sesión (RNF-05)');

-- La alerta de vencimiento usa el parámetro en lugar de 30 días fijos
CREATE OR REPLACE VIEW v_alerta_inventario AS
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
WHERE l.stock_actual > 0
  AND l.fecha_vencimiento < CURRENT_DATE
      + (SELECT valor::INT FROM parametro WHERE clave = 'DIAS_POR_VENCER');

-- 8.3 Portal del cliente (P11): un usuario con rol CLIENTE ve solo sus mascotas
INSERT INTO rol (nombre, descripcion) VALUES
  ('CLIENTE', 'Propietario: consulta sus mascotas, citas y recordatorios');

CREATE TABLE cliente_usuario (
  id_usuario  INT PRIMARY KEY REFERENCES usuario,
  id_cliente  INT NOT NULL UNIQUE REFERENCES cliente
);

-- 8.4 Permisos adicionales de la matriz (RF-02)
INSERT INTO permiso (codigo, modulo, descripcion) VALUES
  ('USUARIO_ADMINISTRAR', 'SEGURIDAD',  'Crear usuarios, asignar roles y restablecer contraseñas'),
  ('INVENTARIO_INGRESAR', 'INVENTARIO', 'Registrar ingresos de mercadería por lote'),
  ('CAJA_CERRAR',         'CAJA',       'Abrir y cerrar turnos de caja'),
  ('REPORTE_EXPORTAR',    'REPORTES',   'Consultar y exportar reportes'),
  ('ALERTA_ATENDER',      'ALERTAS',    'Marcar alertas internas como atendidas');

INSERT INTO rol_permiso (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso FROM rol r JOIN permiso p ON
     (r.nombre = 'ADMINISTRADOR' AND p.codigo IN ('USUARIO_ADMINISTRAR', 'INVENTARIO_INGRESAR',
                                                  'CAJA_CERRAR', 'REPORTE_EXPORTAR', 'ALERTA_ATENDER'))
  OR (r.nombre = 'FARMACIA'    AND p.codigo IN ('INVENTARIO_INGRESAR', 'ALERTA_ATENDER'))
  OR (r.nombre = 'CAJERO'      AND p.codigo IN ('CAJA_CERRAR', 'ALERTA_ATENDER'))
  OR (r.nombre = 'RECEPCION'   AND p.codigo = 'ALERTA_ATENDER')
  OR (r.nombre = 'VETERINARIO' AND p.codigo IN ('CITA_GESTIONAR', 'ALERTA_ATENDER'));

-- 8.5 Privilegio mínimo para la aplicación (RNF-15)
GRANT SELECT, INSERT, UPDATE ON parametro, cliente_usuario TO vetsalud_app;
GRANT SELECT ON v_historia_clinica, v_kardex, v_alerta_inventario, v_ventas_diarias, v_stock_lote
  TO vetsalud_app;
GRANT SELECT ON parametro TO vetsalud_reportes;
GRANT SELECT ON v_alerta_inventario TO vetsalud_reportes;

-- Los cambios de parámetros se auditan desde la aplicación (logger AUDITORIA de Logback),
-- porque fn_auditar() espera una clave numérica.

RESET ROLE;
