-- ============================================================================
-- Vuelta atrás de v3_4_respuesta_cliente.sql
-- Devuelve el código APROBADA / "Aprobada" y quita las columnas nuevas.
-- Los valores de fecha_envio, medio, nota de respuesta y ruta se pierden.
-- ============================================================================

BEGIN;

ALTER TABLE public.seguimiento_cotizacion
    DROP CONSTRAINT IF EXISTS ck_seguimiento_cotizacion_medio;

ALTER TABLE public.seguimiento_cotizacion
    DROP CONSTRAINT IF EXISTS fk_seguimiento_estado_anterior;

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

UPDATE public.estado_cotizacion
   SET codigo_estado = 'APROBADA',
       nombre_estado = 'Aprobada',
       user_update = 'MIGRACION',
       process_update = 'v3_4_respuesta_cliente_rollback',
       date_update = CURRENT_TIMESTAMP
 WHERE codigo_estado = 'ACEPTADA_CLIENTE'
   AND NOT EXISTS (
       SELECT 1 FROM public.estado_cotizacion WHERE codigo_estado = 'APROBADA');

UPDATE public.cotizacion c
   SET id_estado_cotizacion = destino.id_estado_cotizacion,
       user_update = 'MIGRACION',
       process_update = 'v3_4_respuesta_cliente_rollback',
       date_update = CURRENT_TIMESTAMP
  FROM public.estado_cotizacion origen,
       public.estado_cotizacion destino
 WHERE c.id_estado_cotizacion = origen.id_estado_cotizacion
   AND origen.codigo_estado = 'ACEPTADA_CLIENTE'
   AND destino.codigo_estado = 'APROBADA';

UPDATE public.seguimiento_cotizacion s
   SET id_estado_cotizacion = destino.id_estado_cotizacion
  FROM public.estado_cotizacion origen,
       public.estado_cotizacion destino
 WHERE s.id_estado_cotizacion = origen.id_estado_cotizacion
   AND origen.codigo_estado = 'ACEPTADA_CLIENTE'
   AND destino.codigo_estado = 'APROBADA';

DELETE FROM public.estado_cotizacion e
 WHERE e.codigo_estado = 'ACEPTADA_CLIENTE'
   AND NOT EXISTS (
       SELECT 1 FROM public.cotizacion c WHERE c.id_estado_cotizacion = e.id_estado_cotizacion)
   AND NOT EXISTS (
       SELECT 1 FROM public.seguimiento_cotizacion s
        WHERE s.id_estado_cotizacion = e.id_estado_cotizacion);

COMMIT;
