-- ============================================================================
-- GenLogS - Respuesta del cliente, paso 2
-- Archivo : db/migraciones/v3_4_respuesta_cliente_paso2.sql
--
-- Se corre solo cuando el #125 ya está en Live.
-- Idempotente. Mientras APROBADA y ACEPTADA_CLIENTE convivan, este
-- archivo mueve las filas y borra APROBADA si ya no tiene ninguna.
--
-- Pasa el código APROBADA a ACEPTADA_CLIENTE, mueve cotizacion y
-- seguimiento_cotizacion, y borra APROBADA solo si ya no tiene filas.
-- ============================================================================

BEGIN;

UPDATE public.estado_cotizacion
   SET codigo_estado = 'ACEPTADA_CLIENTE',
       nombre_estado = 'Aceptada por el cliente',
       user_update = 'MIGRACION',
       process_update = 'v3_4_respuesta_cliente_paso2',
       date_update = CURRENT_TIMESTAMP
 WHERE codigo_estado = 'APROBADA'
   AND NOT EXISTS (
       SELECT 1 FROM public.estado_cotizacion WHERE codigo_estado = 'ACEPTADA_CLIENTE');

UPDATE public.cotizacion c
   SET id_estado_cotizacion = destino.id_estado_cotizacion,
       user_update = 'MIGRACION',
       process_update = 'v3_4_respuesta_cliente_paso2',
       date_update = CURRENT_TIMESTAMP
  FROM public.estado_cotizacion origen,
       public.estado_cotizacion destino
 WHERE c.id_estado_cotizacion = origen.id_estado_cotizacion
   AND origen.codigo_estado = 'APROBADA'
   AND destino.codigo_estado = 'ACEPTADA_CLIENTE';

UPDATE public.seguimiento_cotizacion s
   SET id_estado_cotizacion = destino.id_estado_cotizacion
  FROM public.estado_cotizacion origen,
       public.estado_cotizacion destino
 WHERE s.id_estado_cotizacion = origen.id_estado_cotizacion
   AND origen.codigo_estado = 'APROBADA'
   AND destino.codigo_estado = 'ACEPTADA_CLIENTE';

DELETE FROM public.estado_cotizacion e
 WHERE e.codigo_estado = 'APROBADA'
   AND NOT EXISTS (
       SELECT 1 FROM public.cotizacion c WHERE c.id_estado_cotizacion = e.id_estado_cotizacion)
   AND NOT EXISTS (
       SELECT 1 FROM public.seguimiento_cotizacion s
        WHERE s.id_estado_cotizacion = e.id_estado_cotizacion
           OR s.id_estado_anterior = e.id_estado_cotizacion);

COMMIT;
