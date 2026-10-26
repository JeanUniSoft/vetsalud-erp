-- =====================================================================
-- VetSalud ERP · Instalador completo de la base de datos (APF3)
--   cd vetsalud-erp/db && psql -d postgres -f instalar.sql
-- Borra y vuelve a crear vetsalud_erp: úsese solo en desarrollo.
-- =====================================================================
\set ON_ERROR_STOP on
SELECT pg_terminate_backend(pid) FROM pg_stat_activity
WHERE datname = 'vetsalud_erp' AND pid <> pg_backend_pid();
DROP DATABASE IF EXISTS vetsalud_erp;
\echo '== 1/11 Roles, base de datos y esquema'
\ir 01_crear_bd.sql
\echo '== 2/11 Tablas, restricciones e índices'
\ir 02_esquema.sql
\echo '== 3/11 Triggers, funciones y vistas'
\ir 03_logica.sql
\echo '== 4/11 Permisos'
\ir 04_seguridad.sql
\echo '== 5/11 Datos de prueba'
\ir 05_datos_prueba.sql
\echo '== 6/11 Pruebas de reglas de negocio'
\ir 06_pruebas.sql
\echo '== 7/11 Vista de stock para reportes (S10)'
\ir 07_vista_stock_reportes.sql
\echo '== 8/11 Ajustes para la aplicación web (APF3)'
\ir 08_aplicacion_web.sql
\echo '== 9/11 Datos de demostración (APF3)'
\ir 09_datos_demo.sql
\echo '== 10/11 Motivo de cancelación en el historial de citas (APF3)'
\ir 10_motivo_cita.sql
\echo '== 11/11 Descarte de ventas pendientes (APF3)'
\ir 11_descartar_venta.sql
\echo '== Listo: vetsalud_erp instalada, probada y con datos de demostración'
