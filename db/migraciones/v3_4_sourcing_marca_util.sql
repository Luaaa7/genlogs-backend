-- ============================================================================
-- GenLogS - Marca de una referencia pasada a ÚTIL
-- Archivo : db/migraciones/v3_4_sourcing_marca_util.sql
--
-- Distingue si se marcó desde Búsqueda o desde la cotización.
-- Idempotente, dentro de una transaccion.
-- Orden: después de v3_2 (tabla historial_sourcing).
-- ============================================================================

BEGIN;

ALTER TABLE public.historial_sourcing
    ADD COLUMN IF NOT EXISTS marcada_util_desde VARCHAR(12);

ALTER TABLE public.historial_sourcing
    ADD COLUMN IF NOT EXISTS marcada_util_en TIMESTAMP;

ALTER TABLE public.historial_sourcing
    ADD COLUMN IF NOT EXISTS marcada_util_por BIGINT;

ALTER TABLE public.historial_sourcing
    DROP CONSTRAINT IF EXISTS fk_historial_sourcing_marcada_util_por;

ALTER TABLE public.historial_sourcing
    ADD CONSTRAINT fk_historial_sourcing_marcada_util_por
    FOREIGN KEY (marcada_util_por) REFERENCES public.usuario (id_usuario);

ALTER TABLE public.historial_sourcing
    DROP CONSTRAINT IF EXISTS ck_historial_sourcing_marca_util;

ALTER TABLE public.historial_sourcing
    ADD CONSTRAINT ck_historial_sourcing_marca_util
    CHECK (marcada_util_desde IS NULL OR marcada_util_desde IN ('BUSQUEDA', 'COTIZACION'));

UPDATE public.historial_sourcing
   SET marcada_util_desde = 'BUSQUEDA'
 WHERE estado = 'UTIL'
   AND marcada_util_desde IS NULL;

COMMIT;
