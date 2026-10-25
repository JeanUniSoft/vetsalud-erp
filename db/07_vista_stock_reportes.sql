-- VetSalud ERP · S10: vista de stock por lote para el módulo de reportes
-- El rol vetsalud_reportes solo lee vistas (RNF-15, privilegio mínimo; ver 04_seguridad.sql).
-- Esta vista alimenta el reporte Excel de stock y vencimientos (RF-34) generado con Apache POI.
-- Ejecutar como dueño:  psql -d vetsalud_erp -f sql/07_vista_stock_reportes.sql

SET ROLE vetsalud_owner;
SET search_path TO vetsalud, public;

DROP VIEW IF EXISTS v_stock_lote;
CREATE VIEW v_stock_lote AS
SELECT p.codigo,
       p.nombre                                             AS producto,
       c.nombre                                             AS categoria,
       p.unidad,
       l.codigo_lote,
       l.fecha_vencimiento,
       l.stock_actual,
       p.stock_minimo,
       -- stock vendible del producto: excluye lotes vencidos (RN-06)
       coalesce(sum(l.stock_actual) FILTER (WHERE l.fecha_vencimiento IS NULL
                                              OR l.fecha_vencimiento >= CURRENT_DATE)
                OVER (PARTITION BY p.id_producto), 0)  AS stock_disponible_producto,
       l.costo_unitario
FROM lote l
JOIN producto p  ON p.id_producto  = l.id_producto
JOIN categoria c ON c.id_categoria = p.id_categoria
WHERE p.activo;

GRANT SELECT ON v_stock_lote TO vetsalud_reportes;

RESET ROLE;
