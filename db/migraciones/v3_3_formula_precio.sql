-- =====================================================================
-- v3.3 · Fórmula de precio (D-40)
-- Precio = costo / (1 − margen/100): el margen es sobre el precio de venta.
--
-- Para los servicios que ya existen se MANTIENE el precio y se recalcula
-- solo el margen, para que ningún cliente vea un cambio de precio:
--   margen = (precio − costo) / precio × 100   (130 sobre costo 100 pasa de 30 % a 23.08 %)
--
-- ⚠ NO correr en Supabase (producción) antes del deploy.
--
-- Rango de aprobación: 15 y 45 (MARGEN_SERV_MIN y MARGEN_SERV_MAX).
-- Si en Parámetros son otros, cámbialos en el PASO 1 y en el PASO 2.
-- Con la fórmula nueva todos los márgenes bajan (20 % sobre el costo pasa a 16.67 %),
-- así que los que queden debajo de 15 pasan a pendiente. Si en la vista previa
-- salen muchos, se lleva al grupo para ajustar los límites (el equivalente
-- al rango de hoy sería 13–31 %).
-- Los RECHAZADOS no cambian de estado. Los que no tienen costo o precio, o
-- tienen un precio menor que el costo, no se tocan.
-- =====================================================================

-- ---------------------------------------------------------------------
-- PASO 1. Vista previa: NO cambia nada. Revisar sobre todo cambia_estado.
-- ---------------------------------------------------------------------
SELECT x.*,
       x.estado_nuevo <> x.estado_actual AS cambia_estado
FROM (
    SELECT c.*,
           CASE WHEN c.estado_actual = 'RECHAZADO' THEN 'RECHAZADO'
                WHEN c.margen_nuevo < 15 OR c.margen_nuevo > 45 THEN 'PENDIENTE_APROBACION'
                ELSE 'APROBADO' END AS estado_nuevo
    FROM (
        SELECT s.id_servicio, s.codigo_servicio, s.nombre_servicio, s.precio_manual, s.costo,
               s.precio_referencial AS precio,
               s.margen AS margen_actual,
               ROUND((s.precio_referencial - s.costo) * 100.0 / s.precio_referencial, 2) AS margen_nuevo,
               s.estado_aprobacion AS estado_actual
        FROM servicio s
        WHERE s.costo > 0
          AND s.precio_referencial >= s.costo
    ) c
) x
ORDER BY cambia_estado DESC, x.codigo_servicio;

-- ---------------------------------------------------------------------
-- PASO 2. Aplicar (solo después del deploy).
-- ---------------------------------------------------------------------
BEGIN;

UPDATE servicio
SET margen            = ROUND((precio_referencial - costo) * 100.0 / precio_referencial, 2),
    estado_aprobacion = CASE
        WHEN estado_aprobacion = 'RECHAZADO' THEN 'RECHAZADO'
        WHEN ROUND((precio_referencial - costo) * 100.0 / precio_referencial, 2) < 15
          OR ROUND((precio_referencial - costo) * 100.0 / precio_referencial, 2) > 45 THEN 'PENDIENTE_APROBACION'
        ELSE 'APROBADO' END,
    user_update       = 'MIGRACION',
    process_update    = 'V3_3_FORMULA_PRECIO',
    date_update       = NOW()
WHERE costo > 0
  AND precio_referencial >= costo;

COMMIT;