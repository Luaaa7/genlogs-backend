-- =============================================================================
-- GENLOGS v3.3 · Fórmula de precio única (decisión del grupo, 16/11/2026)
-- precio = costo / (1 − margen / 100), igual que la cotización (S/ 100 con 30 % → S/ 142.86).
-- Recalcula los servicios ya guardados con la fórmula anterior, costo × (1 + margen / 100).
--
-- ⚠ Producción: correr SOLO con el ok del grupo y después de desplegar el backend con la
--   fórmula nueva. Primero la VISTA PREVIA (paso 1), revisar, y luego el paso 2.
-- Los repuestos de la vitrina no se tocan: guardan el precio y su margen se calcula al leer.
-- =============================================================================

-- 1. VISTA PREVIA: cómo quedaría cada servicio (no cambia nada).
SELECT id_servicio, nombre_servicio, costo, margen, precio_manual,
       precio_referencial                                            AS precio_actual,
       CASE WHEN precio_manual THEN precio_referencial
            ELSE ROUND(costo / (1 - margen / 100.0), 2) END          AS precio_nuevo,
       CASE WHEN precio_manual THEN ROUND((precio_referencial - costo) * 100.0 / precio_referencial, 2)
            ELSE margen END                                          AS margen_nuevo
FROM public.servicio
WHERE costo IS NOT NULL
  AND ((precio_manual = false AND margen IS NOT NULL AND margen < 100)
    OR (precio_manual = true AND precio_referencial > 0))
ORDER BY id_servicio;

-- 2. CAMBIO (en una transacción).
BEGIN;

-- 2a. Precio calculado: se mantiene el margen y se recalcula el precio.
UPDATE public.servicio
SET precio_referencial = ROUND(costo / (1 - margen / 100.0), 2),
    user_update = 'SISTEMA', process_update = 'V3_3_FORMULA_PRECIO', date_update = CURRENT_TIMESTAMP
WHERE precio_manual = false AND costo IS NOT NULL AND margen IS NOT NULL AND margen < 100;

-- 2b. Precio manual: se mantiene el precio y se recalcula el margen real (sobre el precio).
UPDATE public.servicio
SET margen = ROUND((precio_referencial - costo) * 100.0 / precio_referencial, 2),
    user_update = 'SISTEMA', process_update = 'V3_3_FORMULA_PRECIO', date_update = CURRENT_TIMESTAMP
WHERE precio_manual = true AND costo IS NOT NULL AND precio_referencial > 0;

COMMIT;