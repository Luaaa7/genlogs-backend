-- ============================================================================
-- GenLogS - Costo y margen de cada línea de cotización
-- Archivo : db/migraciones/v3_4_costo_margen_linea.sql
--
-- Hay que ejecutarlo en Supabase antes de desplegar. Render usa
-- ddl-auto=validate y no arranca si la entidad tiene las columnas y la
-- base no.
--
-- Idempotente. Las dos columnas son nulas y no tienen llave foránea.
-- Las líneas viejas quedan en null. No cambia precio_unitario ni importe_linea.
-- ============================================================================

BEGIN;

ALTER TABLE public.cotizacion_detalle
    ADD COLUMN IF NOT EXISTS costo_unitario NUMERIC(14, 4),
    ADD COLUMN IF NOT EXISTS margen_porcentaje NUMERIC(6, 2);

COMMIT;
