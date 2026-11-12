-- ============================================================================
-- GenLogS - Migración v3.2 · Bloque BE-D (finanzas, servicios y parámetros)
-- Archivo : db/migraciones/v3_2_be_d.sql
-- La tabla notificacion viene con el Paso 0 (D-21, D-25).
-- Orden: portal -> v3_0_roles -> v3_2 -> v3_2_ubigeo -> v3_2_be_a -> b -> c -> ESTE -> e
-- Idempotente y dentro de una transacción.
-- ============================================================================

BEGIN;

-- 1. notificacion: avisos internos de la campanita. Uno por usuario; no se borran.
--    tipo: ALTO_VALOR, PLAZO_POR_VENCER, PLAZO_VENCIDO, FACTURA_VENCIDA, TIPO_CAMBIO,
--    DOCUMENTO_POR_VENCER, MARGEN_FUERA_RANGO, GENERAL... (sin CHECK a propósito)
CREATE TABLE IF NOT EXISTS public.notificacion (
    id_notificacion BIGSERIAL    PRIMARY KEY,
    id_usuario      BIGINT       NOT NULL REFERENCES public.usuario (id_usuario) ON DELETE CASCADE,
    tipo            VARCHAR(30)  NOT NULL,
    titulo          VARCHAR(150) NOT NULL,
    mensaje         TEXT,
    enlace          VARCHAR(255),
    leida           BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_lectura   TIMESTAMP,
    correo_enviado  BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_creacion  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_notificacion_usuario
    ON public.notificacion (id_usuario, leida, fecha_creacion DESC);

-- 2. Seguridad
ALTER TABLE public.notificacion ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    rol_api TEXT;
BEGIN
    FOREACH rol_api IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = rol_api) THEN
            EXECUTE format('REVOKE ALL ON TABLE public.notificacion FROM %I', rol_api);
            EXECUTE format('REVOKE ALL ON SEQUENCE public.notificacion_id_notificacion_seq FROM %I', rol_api);
        END IF;
    END LOOP;
END $$;

-- Aquí agrega BE-D lo suyo más adelante (feat/be-d-parametros: MARGEN_REP_*, D-17).

COMMIT;