-- ============================================================================
-- GenLogS - PDF de cada versión de la cotización
-- Archivo : db/migraciones/v3_4_version_pdf_seguimiento.sql
--
-- Hay que ejecutarlo en Supabase antes de desplegar el backend que mapea
-- esta columna. Hibernate validate no arranca si la entidad la tiene y la
-- base no.
--
-- Idempotente. Agrega ruta_version_pdf, nula y sin llave foránea.
-- No cambia id_adjunto, ruta_adjunto ni ningún estado.
-- ============================================================================

BEGIN;

ALTER TABLE public.seguimiento_cotizacion
    ADD COLUMN IF NOT EXISTS ruta_version_pdf TEXT;

COMMIT;
