-- =====================================================================
-- VetSalud ERP · Instalador completo de la base de datos (APF3)
--   cd vetsalud-erp/db && psql -d postgres -f instalar.sql
-- Borra y vuelve a crear vetsalud_erp: úsese solo en desarrollo.
-- =====================================================================
\set ON_ERROR_STOP on
SELECT pg_terminate_backend(pid) FROM pg_stat_activity
WHERE datname = 'vetsalud_erp' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS vetsalud_erp;
\echo '== 1/9 Roles, base de datos y esquema'
\ir 01_crear_bd.sql
\echo '== 2/9 Tablas, restricciones e índices'
\ir 02_esquema.sql
\echo '== 3/9 Triggers, funciones y vistas'
\ir 03_logica.sql
\echo '== 4/9 Permisos'
\ir 04_seguridad.sql
\echo '== 5/9 Datos de prueba'
\ir 05_datos_prueba.sql
\echo '== 6/9 Pruebas de reglas de negocio'
\ir 06_pruebas.sql
\echo '== 7/9 Vista de stock para reportes (S10)'
\ir 07_vista_stock_reportes.sql
\echo '== 8/9 Ajustes para la aplicación web (APF3)'
\ir 08_aplicacion_web.sql
\echo '== 9/9 Datos de demostración (APF3)'
\ir 09_datos_demo.sql
\echo '== Listo: vetsalud_erp instalada, probada y con datos de demostración'
