-- ============================================================================
-- GenLogS - Pago en línea del comprobante (BPMN 4.1, v3.4)
-- Archivo : db/migraciones/v3_4_pago_en_linea.sql
--
-- Hay que ejecutarlo en Supabase ANTES de desplegar el backend. Render usa
-- ddl-auto=validate y no arranca si la entidad existe y la tabla no.
--
-- Guarda cada pago en línea que ya se registró como cobro. La llave única
-- (proveedor, id_pago_externo) impide registrar dos veces el mismo pago
-- aunque Mercado Pago avise más de una vez.
--
-- Idempotente: se puede correr más de una vez. No toca tablas existentes.
-- ============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS public.pago_en_linea (
    id_pago_en_linea BIGSERIAL     PRIMARY KEY,
    id_facturacion   BIGINT        NOT NULL REFERENCES public.facturacion (id_facturacion),
    proveedor        VARCHAR(20)   NOT NULL,
    id_pago_externo  VARCHAR(40)   NOT NULL,
    estado           VARCHAR(20)   NOT NULL,
    monto            NUMERIC(16,2) NOT NULL,
    moneda           CHAR(3)       NOT NULL,
    fecha_pago       TIMESTAMP     NULL,
    user_create      VARCHAR(50)   NOT NULL,
    process_create   VARCHAR(100)  NOT NULL,
    date_create      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_update      VARCHAR(50)   NULL,
    process_update   VARCHAR(100)  NULL,
    date_update      TIMESTAMP     NULL,
    status           CHAR(1)       NOT NULL DEFAULT 'A',
    CONSTRAINT uq_pago_en_linea_externo UNIQUE (proveedor, id_pago_externo)
);

CREATE INDEX IF NOT EXISTS ix_pago_en_linea_facturacion
    ON public.pago_en_linea (id_facturacion);

COMMIT;

-- Verificación (debe devolver 15 columnas):
-- SELECT column_name, data_type FROM information_schema.columns
--  WHERE table_schema = 'public' AND table_name = 'pago_en_linea' ORDER BY ordinal_position;

-- Rollback (solo si se revierte la funcionalidad completa):
-- DROP TABLE IF EXISTS public.pago_en_linea;