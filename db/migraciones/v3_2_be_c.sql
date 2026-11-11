-- ============================================================================
-- GenLogS - Migracion v3.2 - Ajustes del bloque BE-C
-- Archivo : db/migraciones/v3_2_be_c.sql
--
-- Aprobacion interna de cotizaciones y marca del aviso de alto valor.
-- Idempotente, dentro de una transaccion.
-- Orden: portal -> v3_0_roles -> v3_2 -> v3_2_ubigeo -> v3_2_be_a -> b -> ESTE
-- ============================================================================

BEGIN;

-- codigo_estado es UNIQUE. WHERE NOT EXISTS evita duplicar si se corre dos veces.
INSERT INTO public.estado_cotizacion (
    codigo_estado, nombre_estado, orden_flujo, es_final,
    user_create, process_create, date_create, status)
SELECT 'PENDIENTE_APROBACION', 'Pendiente de aprobación', 8, FALSE,
       'MIGRACION', 'v3_2_be_c', CURRENT_TIMESTAMP, 'A'
WHERE NOT EXISTS (
    SELECT 1 FROM public.estado_cotizacion WHERE codigo_estado = 'PENDIENTE_APROBACION');

INSERT INTO public.estado_cotizacion (
    codigo_estado, nombre_estado, orden_flujo, es_final,
    user_create, process_create, date_create, status)
SELECT 'APROBADA_INTERNA', 'Aprobada por el Jefe', 9, FALSE,
       'MIGRACION', 'v3_2_be_c', CURRENT_TIMESTAMP, 'A'
WHERE NOT EXISTS (
    SELECT 1 FROM public.estado_cotizacion WHERE codigo_estado = 'APROBADA_INTERNA');

-- Una sola vez por cotización. No se repite la columna de v3_2.sql.
ALTER TABLE public.cotizacion
    ADD COLUMN IF NOT EXISTS aviso_alto_valor_enviado BOOLEAN NOT NULL DEFAULT FALSE;

-- Código leído o confirmado de la OC de la minera. Nulo si no hay un código de 3 letras.
ALTER TABLE public.orden_compra
    ADD COLUMN IF NOT EXISTS moneda_oc_cliente CHAR(3);

COMMIT;
