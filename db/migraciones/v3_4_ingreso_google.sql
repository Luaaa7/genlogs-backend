-- ============================================================================
-- GenLogS - Migración v3.4: ingreso del cliente con Google
-- Archivo : db/migraciones/v3_4_ingreso_google.sql
-- Destino : Supabase (PostgreSQL), esquema public
-- Rama    : luana/v34-ingreso-google
--
-- Qué hace:
--   1. Agrega usuario.google_sub (identificador de la cuenta de Google, claim "sub").
--      Es NULL para el personal y para los clientes que entran con correo y contraseña.
--   2. Lo hace único: una cuenta de Google se vincula con un solo usuario.
--      UNIQUE admite varios NULL.
--
-- No cambia datos existentes. usuario.id_cliente ya admite NULL (20261106_portal_cliente.sql):
-- la cuenta nueva de Google queda sin empresa hasta que da su RUC en el portal.
--
-- Cómo ejecutarlo:
--   - Haz un respaldo antes.
--   - Debe correrse ANTES de desplegar el backend (ddl-auto=validate).
--   - Es idempotente y va dentro de una transacción.
-- ============================================================================

BEGIN;

ALTER TABLE public.usuario
    ADD COLUMN IF NOT EXISTS google_sub VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_usuario_google_sub') THEN
        ALTER TABLE public.usuario
            ADD CONSTRAINT uq_usuario_google_sub UNIQUE (google_sub);
    END IF;
END $$;

COMMENT ON COLUMN public.usuario.google_sub IS
    'Identificador de la cuenta de Google (claim sub) del cliente que entra con Google. NULL si no usa Google.';

COMMIT;

-- Verificación (aparte):
-- SELECT column_name, data_type, is_nullable
--   FROM information_schema.columns
--  WHERE table_schema = 'public' AND table_name = 'usuario' AND column_name = 'google_sub';
--
-- Reversión (solo si hace falta, y con el backend anterior desplegado):
-- ALTER TABLE public.usuario DROP CONSTRAINT IF EXISTS uq_usuario_google_sub;
-- ALTER TABLE public.usuario DROP COLUMN IF EXISTS google_sub;
