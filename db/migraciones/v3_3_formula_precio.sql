-- =====================================================================
-- v3.3 · Fórmula de precio (D-40)
-- Precio = costo / (1 − margen/100): el margen es sobre el precio de venta.
--
-- Para los servicios que ya existen se MANTIENE el precio y se recalcula
-- solo el margen, para que ningún cliente vea un cambio de precio:
--   margen = (precio − costo) / precio × 100   (130 sobre costo 100 pasa de 30 % a 23.08 %)
--
-- El estado de aprobación NO cambia: un aprobado sigue aprobado y un
-- pendiente sigue esperando al Jefe.
--
-- ⚠ NO correr en Supabase (producción) antes del deploy.
--
-- Rango de aprobación: 15 y 45 (MARGEN_SERV_MIN y MARGEN_SERV_MAX).
-- Si en Parámetros son otros, cámbialos en el PASO 1.
-- Con la fórmula nueva todos los márgenes bajan (20 % sobre el costo pasa a 16.67 %).
-- Si en la vista previa salen muchos fuera de rango, el grupo ajusta los
-- límites ANTES de correr el PASO 2 (el equivalente al rango de hoy sería 13–31 %).
-- Los que no tienen costo o precio, o tienen un precio menor que el costo, no se tocan.
-- =====================================================================

-- ---------------------------------------------------------------------
-- PASO 1. Vista previa: NO cambia nada. Revisar fuera_de_rango.
-- ---------------------------------------------------------------------
SELECT x.*,
       (x.margen_nuevo < 15 OR x.margen_nuevo > 45) AS fuera_de_rango
FROM (
    SELECT s.id_servicio, s.codigo_servicio, s.nombre_servicio, s.precio_manual, s.costo,
           s.precio_referencial AS precio,
           s.margen AS margen_actual,
           ROUND((s.precio_referencial - s.costo) * 100.0 / s.precio_referencial, 2) AS margen_nuevo,
           s.estado_aprobacion
    FROM servicio s
    WHERE s.costo > 0
      AND s.precio_referencial >= s.costo
) x
ORDER BY fuera_de_rango DESC, x.codigo_servicio;

-- ---------------------------------------------------------------------
-- PASO 2. Aplicar (solo después del deploy y de revisar el PASO 1).
-- ---------------------------------------------------------------------
BEGIN;

UPDATE servicio
SET margen         = ROUND((precio_referencial - costo) * 100.0 / precio_referencial, 2),
    user_update    = 'MIGRACION',
    process_update = 'V3_3_FORMULA_PRECIO',
    date_update    = NOW()
WHERE costo > 0
  AND precio_referencial >= costo;

COMMIT;