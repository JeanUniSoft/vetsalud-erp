-- =====================================================================
-- VetSalud ERP · Paso 2: modelo físico (tipos, tablas, restricciones, índices)
-- 33 tablas en 7 módulos. Cada restricción indica la regla que implementa.
-- =====================================================================
SET search_path TO vetsalud, public;

-- ---------------------------------------------------------------------
-- 2.1 Dominios y tipos enumerados (categorización de tipos de datos)
-- ---------------------------------------------------------------------
CREATE DOMAIN dinero   AS NUMERIC(12,2) CHECK (VALUE >= 0);          -- soles (PEN), nunca FLOAT
CREATE DOMAIN correo   AS VARCHAR(120)  CHECK (VALUE ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$');
CREATE DOMAIN telefono AS VARCHAR(15)   CHECK (VALUE ~ '^\+?[0-9]{6,14}$');

CREATE TYPE tipo_documento  AS ENUM ('DNI', 'CE', 'RUC', 'PASAPORTE');
CREATE TYPE estado_cita     AS ENUM ('PENDIENTE', 'CONFIRMADA', 'EN_ESPERA', 'EN_ATENCION',
                                     'ATENDIDA', 'CANCELADA', 'NO_ASISTIO');     -- RF-12
CREATE TYPE tipo_movimiento AS ENUM ('INGRESO', 'VENTA', 'AJUSTE_ENTRADA', 'AJUSTE_SALIDA',
                                     'DEVOLUCION');
CREATE TYPE medio_pago      AS ENUM ('EFECTIVO', 'TARJETA', 'YAPE_PLIN', 'TRANSFERENCIA');
CREATE TYPE estado_venta    AS ENUM ('PENDIENTE', 'CONFIRMADA', 'ANULADA');

-- ---------------------------------------------------------------------
-- 2.2 Módulo SEGURIDAD (RF-01 a RF-04)
-- ---------------------------------------------------------------------
CREATE TABLE rol (
  id_rol       SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre       VARCHAR(30)  NOT NULL UNIQUE,
  descripcion  VARCHAR(200),
  activo       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE permiso (
  id_permiso   SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  codigo       VARCHAR(60)  NOT NULL UNIQUE,          -- ej. VENTA_ANULAR
  modulo       VARCHAR(30)  NOT NULL,
  descripcion  VARCHAR(200)
);

CREATE TABLE rol_permiso (                             -- resuelve la relación N:M
  id_rol       SMALLINT NOT NULL REFERENCES rol,
  id_permiso   SMALLINT NOT NULL REFERENCES permiso,
  PRIMARY KEY (id_rol, id_permiso)
);

CREATE TABLE usuario (
  id_usuario        INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_rol            SMALLINT     NOT NULL REFERENCES rol,
  nombres           VARCHAR(80)  NOT NULL,
  apellidos         VARCHAR(80)  NOT NULL,
  usuario           VARCHAR(40)  NOT NULL UNIQUE,
  correo            correo       NOT NULL UNIQUE,
  clave_hash        VARCHAR(100) NOT NULL,             -- BCrypt/Argon2 desde Java, nunca texto plano (RNF-04)
  colegiatura_cmvp  VARCHAR(10),                       -- solo médicos veterinarios
  intentos_fallidos SMALLINT     NOT NULL DEFAULT 0 CHECK (intentos_fallidos BETWEEN 0 AND 10),
  bloqueado         BOOLEAN      NOT NULL DEFAULT FALSE,
  activo            BOOLEAN      NOT NULL DEFAULT TRUE,
  ultimo_acceso     TIMESTAMPTZ,
  creado_en         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE acceso_log (                              -- RF-01: fecha, hora, equipo y resultado
  id_acceso          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_usuario         INT REFERENCES usuario,           -- NULL si el usuario no existe
  usuario_ingresado  VARCHAR(40) NOT NULL,
  ip_equipo          INET,
  exitoso            BOOLEAN     NOT NULL,
  fecha_hora         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE auditoria (                               -- RF-04 / RNF-06 (se conserva 24 meses)
  id_auditoria     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_usuario       INT REFERENCES usuario,
  fecha_hora       TIMESTAMPTZ NOT NULL DEFAULT now(),
  tabla            VARCHAR(40) NOT NULL,
  id_registro      BIGINT      NOT NULL,
  operacion        VARCHAR(10) NOT NULL CHECK (operacion IN ('INSERT', 'UPDATE', 'DELETE')),
  valores_antes    JSONB,
  valores_despues  JSONB
);

-- ---------------------------------------------------------------------
-- 2.3 Módulo CLIENTES Y MASCOTAS (RF-05 a RF-08)
-- ---------------------------------------------------------------------
CREATE TABLE cliente (
  id_cliente            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  tipo_documento        tipo_documento NOT NULL,
  num_documento         VARCHAR(15)    NOT NULL,
  nombres               VARCHAR(80)    NOT NULL,        -- o razón social si es RUC
  apellidos             VARCHAR(80),
  telefono              telefono       NOT NULL,
  correo                correo,
  direccion             VARCHAR(200),
  distrito              VARCHAR(60),
  acepta_recordatorios  BOOLEAN        NOT NULL DEFAULT FALSE,   -- consentimiento (RF-37)
  activo                BOOLEAN        NOT NULL DEFAULT TRUE,
  creado_en             TIMESTAMPTZ    NOT NULL DEFAULT now(),
  CONSTRAINT uq_cliente_documento UNIQUE (tipo_documento, num_documento),         -- RN-01
  CONSTRAINT ck_cliente_dni CHECK (tipo_documento <> 'DNI' OR num_documento ~ '^[0-9]{8}$'),
  CONSTRAINT ck_cliente_ruc CHECK (tipo_documento <> 'RUC' OR num_documento ~ '^(10|20)[0-9]{9}$')
);

CREATE TABLE especie (
  id_especie  SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre      VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE raza (                                    -- la raza determina la especie (3FN)
  id_raza     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_especie  SMALLINT    NOT NULL REFERENCES especie,
  nombre      VARCHAR(60) NOT NULL,
  UNIQUE (id_especie, nombre)
);

CREATE TABLE mascota (
  id_mascota        INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_cliente        INT          NOT NULL REFERENCES cliente,       -- RN-02
  id_raza           INT          NOT NULL REFERENCES raza,
  nombre            VARCHAR(50)  NOT NULL,
  sexo              CHAR(1)      NOT NULL CHECK (sexo IN ('M', 'H')),
  fecha_nacimiento  DATE         CHECK (fecha_nacimiento <= CURRENT_DATE),
  color             VARCHAR(40),
  peso_kg           NUMERIC(5,2) CHECK (peso_kg > 0),
  microchip         VARCHAR(15)  UNIQUE,
  esterilizado      BOOLEAN      NOT NULL DEFAULT FALSE,
  alertas           VARCHAR(300),                                   -- alergias, agresividad
  foto_ruta         VARCHAR(255),                                   -- la imagen va en disco, no en la BD
  estado            VARCHAR(10)  NOT NULL DEFAULT 'ACTIVA'
                    CHECK (estado IN ('ACTIVA', 'INACTIVA', 'FALLECIDA')),
  creado_en         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE mascota_propietario_hist (               -- RF-08: cambio de propietario auditado
  id_hist               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_mascota            INT          NOT NULL REFERENCES mascota,
  id_cliente_anterior   INT          NOT NULL REFERENCES cliente,
  id_cliente_nuevo      INT          NOT NULL REFERENCES cliente,
  motivo                VARCHAR(200) NOT NULL,
  id_usuario            INT          NOT NULL REFERENCES usuario,
  fecha_hora            TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CHECK (id_cliente_anterior <> id_cliente_nuevo)
);

-- ---------------------------------------------------------------------
-- 2.4 Módulo AGENDA (RF-09 a RF-12)
-- ---------------------------------------------------------------------
CREATE TABLE servicio (                               -- consultas, vacunas y procedimientos cobrables
  id_servicio   SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre        VARCHAR(80) NOT NULL UNIQUE,
  tipo          VARCHAR(15) NOT NULL
                CHECK (tipo IN ('CONSULTA', 'VACUNACION', 'PROCEDIMIENTO', 'LABORATORIO', 'ESTETICA')),
  duracion_min  SMALLINT    NOT NULL CHECK (duracion_min BETWEEN 5 AND 480),
  tarifa        dinero      NOT NULL,
  activo        BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE cita (
  id_cita           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_mascota        INT          NOT NULL REFERENCES mascota,
  id_servicio       SMALLINT     NOT NULL REFERENCES servicio,
  id_veterinario    INT          NOT NULL REFERENCES usuario,
  id_registrado_por INT          NOT NULL REFERENCES usuario,
  inicio            TIMESTAMPTZ  NOT NULL,
  fin               TIMESTAMPTZ  NOT NULL,
  estado            estado_cita  NOT NULL DEFAULT 'PENDIENTE',
  motivo            VARCHAR(200),
  creado_en         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_cita_rango CHECK (fin > inicio),
  -- RN-03: un mismo veterinario no puede tener dos citas vigentes que se crucen
  CONSTRAINT ex_cita_sin_solape EXCLUDE USING gist (
    id_veterinario WITH =,
    tstzrange(inicio, fin, '[)') WITH &&
  ) WHERE (estado NOT IN ('CANCELADA', 'NO_ASISTIO'))
);

CREATE TABLE cita_estado_hist (                       -- RF-10 / RF-12: historial de estados
  id_hist          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_cita          BIGINT       NOT NULL REFERENCES cita,
  estado_anterior  estado_cita,
  estado_nuevo     estado_cita  NOT NULL,
  motivo           VARCHAR(200),
  id_usuario       INT REFERENCES usuario,
  fecha_hora       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 2.5 Módulo CLÍNICO (RF-13 a RF-19)
-- ---------------------------------------------------------------------
CREATE TABLE consulta (
  id_consulta        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_mascota         INT          NOT NULL REFERENCES mascota,
  id_cita            BIGINT       UNIQUE REFERENCES cita,          -- 0..1: atención con o sin cita
  id_veterinario     INT          NOT NULL REFERENCES usuario,
  fecha_hora         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  motivo             VARCHAR(300) NOT NULL,
  anamnesis          TEXT,
  temperatura_c      NUMERIC(4,1) CHECK (temperatura_c BETWEEN 30 AND 45),
  frec_cardiaca      SMALLINT     CHECK (frec_cardiaca BETWEEN 1 AND 400),
  frec_respiratoria  SMALLINT     CHECK (frec_respiratoria BETWEEN 1 AND 200),
  peso_kg            NUMERIC(5,2) CHECK (peso_kg > 0),
  examen_clinico     TEXT,
  indicaciones       TEXT,
  fecha_control      DATE,                                          -- RF-19
  estado             VARCHAR(10)  NOT NULL DEFAULT 'ABIERTA' CHECK (estado IN ('ABIERTA', 'FIRMADA')),
  firmada_en         TIMESTAMPTZ,
  CONSTRAINT ck_consulta_firma CHECK ((estado = 'FIRMADA') = (firmada_en IS NOT NULL))
);

CREATE TABLE consulta_diagnostico (                   -- RF-15: principal y secundarios
  id_diagnostico  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_consulta     BIGINT       NOT NULL REFERENCES consulta,
  descripcion     VARCHAR(200) NOT NULL,
  es_principal    BOOLEAN      NOT NULL DEFAULT FALSE
);
-- Solo un diagnóstico principal por consulta (índice único parcial)
CREATE UNIQUE INDEX uq_diagnostico_principal ON consulta_diagnostico (id_consulta) WHERE es_principal;

CREATE TABLE consulta_procedimiento (                 -- RF-16
  id_proc_consulta  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_consulta       BIGINT    NOT NULL REFERENCES consulta,
  id_servicio       SMALLINT  NOT NULL REFERENCES servicio,
  id_responsable    INT       NOT NULL REFERENCES usuario,
  cantidad          SMALLINT  NOT NULL DEFAULT 1 CHECK (cantidad > 0),
  precio_unitario   dinero    NOT NULL,                             -- se copia la tarifa del momento
  resultado         TEXT
);

CREATE TABLE adenda (                                 -- RN-05: la consulta firmada no se edita
  id_adenda    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_consulta  BIGINT      NOT NULL REFERENCES consulta,
  id_usuario   INT         NOT NULL REFERENCES usuario,
  texto        TEXT        NOT NULL CHECK (length(trim(texto)) > 0),
  fecha_hora   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 2.6 Módulo FARMACIA E INVENTARIO (RF-20 a RF-25)
-- ---------------------------------------------------------------------
CREATE TABLE categoria (
  id_categoria  SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre        VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE producto (
  id_producto      INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_categoria     SMALLINT      NOT NULL REFERENCES categoria,
  codigo           VARCHAR(20)   NOT NULL UNIQUE,                  -- SKU interno
  nombre           VARCHAR(120)  NOT NULL,
  unidad           VARCHAR(10)   NOT NULL
                   CHECK (unidad IN ('UND', 'CAJA', 'FRASCO', 'TABLETA', 'AMPOLLA', 'BOLSA', 'KG')),
  precio_venta     dinero        NOT NULL,
  stock_minimo     NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (stock_minimo >= 0),
  requiere_receta  BOOLEAN       NOT NULL DEFAULT FALSE,           -- condición de venta
  activo           BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE TABLE receta (                                 -- RF-17 (va aquí porque referencia a producto)
  id_receta     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_consulta   BIGINT      NOT NULL REFERENCES consulta,
  fecha         TIMESTAMPTZ NOT NULL DEFAULT now(),
  indicaciones  TEXT
);

CREATE TABLE receta_detalle (
  id_receta_det  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_receta      BIGINT        NOT NULL REFERENCES receta,
  id_producto    INT           NOT NULL REFERENCES producto,
  dosis          VARCHAR(60)   NOT NULL,
  frecuencia     VARCHAR(60)   NOT NULL,
  duracion_dias  SMALLINT      NOT NULL CHECK (duracion_dias BETWEEN 1 AND 365),
  via            VARCHAR(10)   NOT NULL
                 CHECK (via IN ('ORAL', 'TOPICA', 'SC', 'IM', 'IV', 'OFTALMICA', 'OTICA')),
  cantidad       NUMERIC(10,2) NOT NULL CHECK (cantidad > 0),
  UNIQUE (id_receta, id_producto)
);

CREATE TABLE proveedor (
  id_proveedor  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ruc           CHAR(11)     NOT NULL UNIQUE CHECK (ruc ~ '^(10|20)[0-9]{9}$'),
  razon_social  VARCHAR(120) NOT NULL,
  telefono      telefono,
  correo        correo,
  activo        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE ingreso (                                -- UC-09: documento de compra
  id_ingreso    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_proveedor  INT         NOT NULL REFERENCES proveedor,
  id_usuario    INT         NOT NULL REFERENCES usuario,
  tipo_doc      VARCHAR(8)  NOT NULL CHECK (tipo_doc IN ('FACTURA', 'BOLETA', 'GUIA')),
  num_doc       VARCHAR(20) NOT NULL,
  fecha         DATE        NOT NULL DEFAULT CURRENT_DATE,
  UNIQUE (id_proveedor, tipo_doc, num_doc)
);

CREATE TABLE lote (
  id_lote            INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_producto        INT           NOT NULL REFERENCES producto,
  codigo_lote        VARCHAR(30)   NOT NULL,
  fecha_vencimiento  DATE,                                          -- NULL: accesorios no perecibles
  costo_unitario     dinero        NOT NULL,
  -- Redundancia controlada: saldo que mantiene el kardex por trigger
  stock_actual       NUMERIC(12,2) NOT NULL DEFAULT 0,
  CONSTRAINT ck_lote_stock_no_negativo CHECK (stock_actual >= 0),   -- RN-06
  UNIQUE (id_producto, codigo_lote)
);

CREATE TABLE movimiento_inventario (                  -- KARDEX (RF-21, RF-22, RF-25)
  id_movimiento    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_lote          INT             NOT NULL REFERENCES lote,
  tipo             tipo_movimiento NOT NULL,
  cantidad         NUMERIC(12,2)   NOT NULL CHECK (cantidad > 0),
  saldo_lote       NUMERIC(12,2)   NOT NULL,                        -- lo calcula el trigger
  id_ingreso       BIGINT REFERENCES ingreso,
  id_venta_detalle BIGINT,                                          -- FK se agrega tras crear venta_detalle
  id_usuario       INT             NOT NULL REFERENCES usuario,
  motivo           VARCHAR(200),
  fecha_hora       TIMESTAMPTZ     NOT NULL DEFAULT now(),
  CONSTRAINT ck_ajuste_con_motivo
    CHECK (tipo NOT IN ('AJUSTE_ENTRADA', 'AJUSTE_SALIDA') OR motivo IS NOT NULL),
  CONSTRAINT ck_ingreso_con_documento
    CHECK (tipo <> 'INGRESO' OR id_ingreso IS NOT NULL)
);

-- ---------------------------------------------------------------------
-- 2.7 Módulo VENTAS Y CAJA (RF-26 a RF-31)
-- ---------------------------------------------------------------------
CREATE TABLE caja_turno (
  id_turno           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_cajero          INT          NOT NULL REFERENCES usuario,
  apertura           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  monto_inicial      dinero       NOT NULL,
  cierre             TIMESTAMPTZ,
  efectivo_esperado  dinero,
  efectivo_contado   dinero,
  diferencia         NUMERIC(12,2) GENERATED ALWAYS AS (efectivo_contado - efectivo_esperado) STORED,
  observacion        VARCHAR(300),
  estado             VARCHAR(8)   NOT NULL DEFAULT 'ABIERTA' CHECK (estado IN ('ABIERTA', 'CERRADA')),
  CONSTRAINT ck_turno_cerrado_completo
    CHECK (estado = 'ABIERTA' OR (cierre IS NOT NULL AND efectivo_contado IS NOT NULL)),
  -- RN-10: toda diferencia requiere justificación
  CONSTRAINT ck_turno_diferencia_justificada
    CHECK (efectivo_contado IS NULL OR efectivo_contado = efectivo_esperado OR observacion IS NOT NULL)
);
-- RN-10: un cajero solo puede tener un turno abierto a la vez
CREATE UNIQUE INDEX uq_turno_abierto ON caja_turno (id_cajero) WHERE estado = 'ABIERTA';

CREATE TABLE serie_comprobante (                      -- RN-11: correlativo por serie
  serie          CHAR(4)     PRIMARY KEY,             -- ej. T001
  tipo           VARCHAR(12) NOT NULL CHECK (tipo IN ('TICKET', 'NOTA_VENTA')),
  ultimo_numero  INT         NOT NULL DEFAULT 0 CHECK (ultimo_numero >= 0)
);

CREATE TABLE venta (
  id_venta               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_turno               INT          NOT NULL REFERENCES caja_turno,   -- el turno determina al cajero
  id_cliente             INT          NOT NULL REFERENCES cliente,
  id_mascota             INT REFERENCES mascota,
  id_consulta            BIGINT REFERENCES consulta,                    -- RF-26: venta ligada a la atención
  serie                  CHAR(4)      NOT NULL REFERENCES serie_comprobante,
  numero                 INT,                                           -- se asigna al confirmar
  fecha_hora             TIMESTAMPTZ  NOT NULL DEFAULT now(),
  subtotal               dinero       NOT NULL DEFAULT 0,
  descuento              dinero       NOT NULL DEFAULT 0,
  total                  dinero       NOT NULL DEFAULT 0,
  id_autoriza_descuento  INT REFERENCES usuario,
  estado                 estado_venta NOT NULL DEFAULT 'PENDIENTE',
  motivo_anulacion       VARCHAR(200),
  anulada_en             TIMESTAMPTZ,
  CONSTRAINT uq_venta_correlativo UNIQUE (serie, numero),                -- RN-11
  CONSTRAINT ck_venta_total CHECK (total = subtotal - descuento),
  CONSTRAINT ck_venta_descuento                                          -- RN-08
    CHECK (descuento <= subtotal * 0.10 OR id_autoriza_descuento IS NOT NULL),
  CONSTRAINT ck_venta_confirmada_numerada CHECK (estado = 'PENDIENTE' OR numero IS NOT NULL),
  CONSTRAINT ck_venta_anulacion
    CHECK (estado <> 'ANULADA' OR (motivo_anulacion IS NOT NULL AND anulada_en IS NOT NULL))
);

CREATE TABLE venta_detalle (
  id_detalle       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_venta         BIGINT        NOT NULL REFERENCES venta,
  id_servicio      SMALLINT REFERENCES servicio,
  id_lote          INT REFERENCES lote,                              -- el lote ya indica el producto (3FN)
  cantidad         NUMERIC(10,2) NOT NULL CHECK (cantidad > 0),
  precio_unitario  dinero        NOT NULL,
  importe          NUMERIC(12,2) GENERATED ALWAYS AS (cantidad * precio_unitario) STORED,
  -- cada línea es un servicio o un producto, nunca ambos ni ninguno
  CONSTRAINT ck_detalle_servicio_o_producto CHECK (num_nonnulls(id_servicio, id_lote) = 1)
);

ALTER TABLE movimiento_inventario
  ADD CONSTRAINT fk_mov_venta_detalle FOREIGN KEY (id_venta_detalle) REFERENCES venta_detalle;

CREATE TABLE pago (                                   -- RF-28: uno o más medios de pago
  id_pago     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_venta    BIGINT      NOT NULL REFERENCES venta,
  medio       medio_pago  NOT NULL,
  monto       dinero      NOT NULL CHECK (monto > 0),
  referencia  VARCHAR(40),                                          -- n.° operación tarjeta/Yape
  fecha_hora  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (medio = 'EFECTIVO' OR referencia IS NOT NULL)
);

-- ---------------------------------------------------------------------
-- 2.8 Módulo NOTIFICACIONES (RF-37, RF-38)
-- ---------------------------------------------------------------------
CREATE TABLE recordatorio (
  id_recordatorio  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_cliente       INT         NOT NULL REFERENCES cliente,
  id_cita          BIGINT REFERENCES cita,
  id_consulta      BIGINT REFERENCES consulta,                      -- control posterior
  canal            VARCHAR(10) NOT NULL DEFAULT 'CORREO' CHECK (canal IN ('CORREO')),
  programado_para  TIMESTAMPTZ NOT NULL,
  estado           VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE'
                   CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO', 'CANCELADO')),
  enviado_en       TIMESTAMPTZ,
  CHECK (num_nonnulls(id_cita, id_consulta) = 1)
);

CREATE TABLE alerta (                                 -- RF-38: alertas internas por rol
  id_alerta       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  id_rol_destino  SMALLINT     NOT NULL REFERENCES rol,
  tipo            VARCHAR(20)  NOT NULL CHECK (tipo IN ('STOCK_MINIMO', 'POR_VENCER', 'VENCIDO', 'CAJA', 'OTRO')),
  mensaje         VARCHAR(300) NOT NULL,
  creada_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  atendida_por    INT REFERENCES usuario,
  atendida_en     TIMESTAMPTZ,
  CHECK ((atendida_por IS NULL) = (atendida_en IS NULL))
);

-- ---------------------------------------------------------------------
-- 2.9 Índices para las búsquedas frecuentes (RNF-01: ≤ 3 s)
--     PostgreSQL indexa solo PK y UNIQUE; las FK se indexan a mano.
-- ---------------------------------------------------------------------
CREATE INDEX ix_cliente_nombre_trgm ON cliente USING gin ((nombres || ' ' || coalesce(apellidos, '')) gin_trgm_ops);
CREATE INDEX ix_cliente_telefono    ON cliente (telefono);
CREATE INDEX ix_mascota_cliente     ON mascota (id_cliente);
CREATE INDEX ix_cita_mascota        ON cita (id_mascota);
CREATE INDEX ix_cita_agenda         ON cita (inicio, id_veterinario);
CREATE INDEX ix_consulta_historia   ON consulta (id_mascota, fecha_hora DESC);
CREATE INDEX ix_diag_consulta       ON consulta_diagnostico (id_consulta);
CREATE INDEX ix_proc_consulta       ON consulta_procedimiento (id_consulta);
CREATE INDEX ix_receta_consulta     ON receta (id_consulta);
CREATE INDEX ix_lote_fefo           ON lote (id_producto, fecha_vencimiento) WHERE stock_actual > 0;
CREATE INDEX ix_kardex_lote_fecha   ON movimiento_inventario (id_lote, fecha_hora);
CREATE INDEX ix_venta_fecha         ON venta (fecha_hora);
CREATE INDEX ix_venta_turno         ON venta (id_turno);
CREATE INDEX ix_detalle_venta       ON venta_detalle (id_venta);
CREATE INDEX ix_pago_venta          ON pago (id_venta);
CREATE INDEX ix_auditoria_registro  ON auditoria (tabla, id_registro);
CREATE INDEX ix_auditoria_fecha     ON auditoria (fecha_hora);
CREATE INDEX ix_recordatorio_cola   ON recordatorio (programado_para) WHERE estado = 'PENDIENTE';
