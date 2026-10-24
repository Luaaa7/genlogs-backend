-- Esquema de pruebas (perfil "test", base H2 en memoria).
--
-- Las tablas las crea Hibernate (ddl-auto=create-drop); aquí solo se crea la
-- vista de totales, que en PostgreSQL es parte del script "BD V8" y que las
-- consultas nativas del backend necesitan para calcular subtotal/igv/total.
CREATE OR REPLACE VIEW vw_cotizacion_totales AS
SELECT c.id_cotizacion AS id_cotizacion,
       COALESCE(SUM(d.cantidad * (d.precio_unitario - d.descuento_unitario)), 0) AS subtotal,
       COALESCE(SUM(d.cantidad * (d.precio_unitario - d.descuento_unitario)) * 0.18, 0) AS igv,
       COALESCE(SUM(d.cantidad * (d.precio_unitario - d.descuento_unitario)) * 1.18, 0) AS total
FROM cotizacion c
LEFT JOIN cotizacion_detalle d ON d.id_cotizacion = c.id_cotizacion
GROUP BY c.id_cotizacion;
