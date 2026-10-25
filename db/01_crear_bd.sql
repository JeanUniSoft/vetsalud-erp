-- =====================================================================
-- VetSalud ERP · Paso 1: roles, base de datos, extensiones y esquema
-- Ejecutar como superusuario de PostgreSQL (en Mac/Homebrew: tu usuario)
--   psql -d postgres -f 00_instalar.sql      (lo llama automáticamente)
-- =====================================================================

-- 1.1 Roles de base de datos (privilegio mínimo, RNF-04 / RNF-15)
--     owner    : dueño de las tablas; solo se usa para migraciones.
--     app      : usuario con el que se conecta Tomcat/Java (JDBC).
--     reportes : solo lectura de vistas para paneles y reportes.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vetsalud_owner') THEN
    CREATE ROLE vetsalud_owner LOGIN PASSWORD 'Owner_CambiarEsto_2026';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vetsalud_app') THEN
    CREATE ROLE vetsalud_app LOGIN PASSWORD 'App_CambiarEsto_2026';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vetsalud_reportes') THEN
    CREATE ROLE vetsalud_reportes LOGIN PASSWORD 'Rep_CambiarEsto_2026';
  END IF;
END $$;

-- 1.2 Base de datos en UTF-8 (tildes y ñ) con zona horaria de Perú (RNF-17)
--     Si necesitas reinstalar desde cero, descomenta la siguiente línea:
-- DROP DATABASE IF EXISTS vetsalud_erp;
CREATE DATABASE vetsalud_erp
  OWNER vetsalud_owner
  ENCODING 'UTF8'
  TEMPLATE template0;

ALTER DATABASE vetsalud_erp SET timezone TO 'America/Lima';
ALTER DATABASE vetsalud_erp SET datestyle TO 'ISO, DMY';

\connect vetsalud_erp

-- 1.3 Extensiones
--     btree_gist: permite la restricción EXCLUDE que impide citas solapadas (RN-03)
--     pg_trgm   : búsqueda rápida de clientes por nombre aproximado (RF-06)
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 1.4 Esquema propio (no usamos "public") y cierre de accesos por defecto
CREATE SCHEMA vetsalud AUTHORIZATION vetsalud_owner;
REVOKE ALL ON DATABASE vetsalud_erp FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE vetsalud_erp TO vetsalud_app, vetsalud_reportes;
ALTER ROLE vetsalud_owner IN DATABASE vetsalud_erp SET search_path TO vetsalud, public;
ALTER ROLE vetsalud_app IN DATABASE vetsalud_erp SET search_path TO vetsalud, public;
ALTER ROLE vetsalud_reportes IN DATABASE vetsalud_erp SET search_path TO vetsalud, public;

-- A partir de aquí todo lo crea el dueño, no el superusuario
SET ROLE vetsalud_owner;
SET search_path TO vetsalud, public;
