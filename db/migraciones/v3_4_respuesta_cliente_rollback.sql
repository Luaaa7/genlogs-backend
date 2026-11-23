-- ============================================================================
-- Vuelta atrás del paso 1 (v3_4_respuesta_cliente.sql).
-- Quita las columnas y restricciones nuevas.
-- No renombra ACEPTADA_CLIENTE, no mueve filas y no borra cotizaciones
-- que ya estén en ACEPTADA_CLIENTE. El estado se borra solo si nadie lo usa.
-- Los valores de fecha_envio, medio, fecha de respuesta, ruta y adjunto se pierden.
-- ============================================================================

BEGIN;

ALTER TABLE public.seguimiento_cotizacion
    DROP CONSTRAINT IF EXISTS ck_seguimiento_cotizacion_medio;

ALTER TABLE public.seguimiento_cotizacion
    DROP CONSTRAINT IF EXISTS fk_seguimiento_adjunto;

ALTER TABLE public.seguimiento_cotizacion
    DROP CONSTRAINT IF EXISTS fk_seguimiento_estado_anterior;

ALTER TABLE public.seguimiento_cotizacion
    DROP COLUMN IF EXISTS id_adjunto;

ALTER TABLE public.seguimiento_cotizacion
    DROP COLUMN IF EXISTS ruta_adjunto;

ALTER TABLE public.seguimiento_cotizacion
    DROP COLUMN IF EXISTS medio;

ALTER TABLE public.seguimiento_cotizacion
    DROP COLUMN IF EXISTS fecha_respuesta;

ALTER TABLE public.seguimiento_cotizacion
    DROP COLUMN IF EXISTS id_estado_anterior;

ALTER TABLE public.cotizacion
    DROP COLUMN IF EXISTS fecha_envio;

DELETE FROM public.estado_cotizacion e
 WHERE e.codigo_estado = 'ACEPTADA_CLIENTE'
   AND NOT EXISTS (
       SELECT 1 FROM public.cotizacion c WHERE c.id_estado_cotizacion = e.id_estado_cotizacion)
   AND NOT EXISTS (
       SELECT 1 FROM public.seguimiento_cotizacion s
        WHERE s.id_estado_cotizacion = e.id_estado_cotizacion);

COMMIT;
