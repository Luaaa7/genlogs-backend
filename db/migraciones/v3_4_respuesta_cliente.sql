-- ============================================================================
-- GenLogS - Respuesta del cliente registrada por el vendedor o el jefe
-- Archivo : db/migraciones/v3_4_respuesta_cliente.sql
-- Paso    : 1 de 2. El paso 2 (renombrar APROBADA) está en
--           v3_4_respuesta_cliente_paso2.sql y se corre cuando el #125 ya está en Live.
--
-- Orden: después de v3_3.
-- Idempotente. La vuelta atrás está en v3_4_respuesta_cliente_rollback.sql.
--
-- Agrega ACEPTADA_CLIENTE si no existe. Agrega fecha_envio, el estado
-- anterior, fecha_respuesta, medio, ruta_adjunto e id_adjunto, con sus llaves.
-- No cambia el código de APROBADA, no mueve filas y no borra APROBADA.
-- ============================================================================

BEGIN;

INSERT INTO public.estado_cotizacion (
    codigo_estado, nombre_estado, orden_flujo, es_final,
    user_create, process_create, date_create, status)
SELECT 'ACEPTADA_CLIENTE', 'Aceptada por el cliente', 4, FALSE,
       'MIGRACION', 'v3_4_respuesta_cliente', CURRENT_TIMESTAMP, 'A'
WHERE NOT EXISTS (
    SELECT 1 FROM public.estado_cotizacion WHERE codigo_estado = 'ACEPTADA_CLIENTE');

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
    ADD COLUMN IF NOT EXISTS id_adjunto BIGINT;

ALTER TABLE public.seguimiento_cotizacion
    DROP CONSTRAINT IF EXISTS fk_seguimiento_estado_anterior;

ALTER TABLE public.seguimiento_cotizacion
    ADD CONSTRAINT fk_seguimiento_estado_anterior
    FOREIGN KEY (id_estado_anterior)
    REFERENCES public.estado_cotizacion (id_estado_cotizacion);

ALTER TABLE public.seguimiento_cotizacion
    DROP CONSTRAINT IF EXISTS fk_seguimiento_adjunto;

ALTER TABLE public.seguimiento_cotizacion
    ADD CONSTRAINT fk_seguimiento_adjunto
    FOREIGN KEY (id_adjunto)
    REFERENCES public.adjunto_cotizacion (id_adjunto);

ALTER TABLE public.seguimiento_cotizacion
    DROP CONSTRAINT IF EXISTS ck_seguimiento_cotizacion_medio;

ALTER TABLE public.seguimiento_cotizacion
    ADD CONSTRAINT ck_seguimiento_cotizacion_medio
    CHECK (medio IS NULL OR medio IN ('correo', 'whatsapp', 'llamada', 'otro'));

COMMIT;
