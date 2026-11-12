-- ============================================================================
-- GenLogS - Migracion v3.2 - Ajustes del bloque BE-A
-- Archivo : db/migraciones/v3_2_be_a.sql
--
-- Aqui van SOLO las tablas y columnas que agregue este bloque durante el
-- desarrollo; cada bloque toca tablas distintas.
-- Orden: portal -> v3_0_roles -> v3_2 -> v3_2_ubigeo -> v3_2_be_a ... v3_2_be_e
-- Reglas: idempotente (IF NOT EXISTS), dentro de BEGIN/COMMIT y con RLS.
-- Baja logica (D-29): status = 'I' (no se agrega columna nueva).
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- RF-70: el cliente califica desde el portal la atencion de su reclamo
-- (1 a 5), cuando ya esta RESUELTO o CERRADO. Una sola vez por reclamo.
-- ----------------------------------------------------------------------------
ALTER TABLE public.reclamo
    ADD COLUMN IF NOT EXISTS calificacion            SMALLINT,
    ADD COLUMN IF NOT EXISTS comentario_calificacion TEXT,
    ADD COLUMN IF NOT EXISTS fecha_calificacion      TIMESTAMP;

ALTER TABLE public.reclamo DROP CONSTRAINT IF EXISTS ck_reclamo_calificacion;
ALTER TABLE public.reclamo ADD CONSTRAINT ck_reclamo_calificacion
    CHECK (calificacion IS NULL OR calificacion BETWEEN 1 AND 5);

COMMIT;

-- Verificacion:
--   SELECT column_name FROM information_schema.columns
--    WHERE table_name = 'reclamo' AND column_name LIKE '%calificacion%';  -- 3 filas
