-- ============================================================================
-- GenLogS - Respuesta del cliente registrada por el vendedor o el jefe
-- Archivo : db/migraciones/v3_4_respuesta_cliente.sql
--
-- Orden: después de v3_3.
-- Idempotente. La vuelta atrás está en v3_4_respuesta_cliente_rollback.sql.
--
-- 1. Renombra el estado del cliente APROBADA -> ACEPTADA_CLIENTE
--    ("Aceptada por el cliente"). Las cotizaciones y el seguimiento apuntan
--    al mismo id, así que el cambio de código arrastra los datos.
-- 2. fecha_envio en cotizacion.
-- 3. En seguimiento_cotizacion: estado anterior, fecha de la respuesta,
--    medio y ruta del adjunto.
-- ============================================================================

BEGIN;

UPDATE public.estado_cotizacion
   SET codigo_estado = 'ACEPTADA_CLIENTE',
       nombre_estado = 'Aceptada por el cliente',
       user_update = 'MIGRACION',
       process_update = 'v3_4_respuesta_cliente',
       date_update = CURRENT_TIMESTAMP
 WHERE codigo_estado = 'APROBADA'
   AND NOT EXISTS (
       SELECT 1 FROM public.estado_cotizacion WHERE codigo_estado = 'ACEPTADA_CLIENTE');

INSERT INTO public.estado_cotizacion (
    codigo_estado, nombre_estado, orden_flujo, es_final,
    user_create, process_create, date_create, status)
SELECT 'ACEPTADA_CLIENTE', 'Aceptada por el cliente', 4, FALSE,
       'MIGRACION', 'v3_4_respuesta_cliente', CURRENT_TIMESTAMP, 'A'
WHERE NOT EXISTS (
    SELECT 1 FROM public.estado_cotizacion WHERE codigo_estado = 'ACEPTADA_CLIENTE');

-- Si ACEPTADA_CLIENTE ya existía y APROBADA también, pasa las filas a la nueva.
UPDATE public.cotizacion c
   SET id_estado_cotizacion = destino.id_estado_cotizacion,
       user_update = 'MIGRACION',
       process_update = 'v3_4_respuesta_cliente',
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
        WHERE s.id_estado_cotizacion = e.id_estado_cotizacion);

ALTER TABLE public.cotizacion
    ADD COLUMN IF NOT EXISTS fecha_envio TIMESTAMP;

ALTER TABLE public.seguimiento_cotizacion
    ADD COLUMN IF NOT EXISTS id_estado_anterior INTEGER;

ALTER TABLE public.seguimiento_cotizacion
    ADD COLUMN IF NOT EXISTS fecha_respuesta DATE;

ALTER TABLE public.seguimiento_cotizacion
    ADD COLUMN IF NOT EXISTS medio VARCHAR(20);

ALTER TABLE public.seguimiento_cotizacion
    ADD COLUMN IF NOT EXISTS ruta_adjunto TEXT;

ALTER TABLE public.seguimiento_cotizacion
    DROP CONSTRAINT IF EXISTS fk_seguimiento_estado_anterior;

ALTER TABLE public.seguimiento_cotizacion
    ADD CONSTRAINT fk_seguimiento_estado_anterior
    FOREIGN KEY (id_estado_anterior)
    REFERENCES public.estado_cotizacion (id_estado_cotizacion);

ALTER TABLE public.seguimiento_cotizacion
    DROP CONSTRAINT IF EXISTS ck_seguimiento_cotizacion_medio;

ALTER TABLE public.seguimiento_cotizacion
    ADD CONSTRAINT ck_seguimiento_cotizacion_medio
    CHECK (medio IS NULL OR medio IN ('correo', 'whatsapp', 'llamada', 'otro'));

COMMIT;
