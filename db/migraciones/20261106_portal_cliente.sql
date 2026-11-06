-- ============================================================================
-- GenLogS - Migración: portal de clientes y landing pública
-- Archivo : db/migraciones/20261106_portal_cliente.sql
-- Destino : Supabase (PostgreSQL), esquema public
-- Rama    : feat/portal-cliente
--
-- Qué hace:
--   1. Crea el rol CLIENTE.
--   2. Agrega usuario.id_cliente (nullable, único) para vincular el portal
--      con un cliente existente. Los usuarios internos quedan con NULL.
--   3. Crea solicitud_cotizacion y solicitud_item.
--   4. Activa RLS en las tablas nuevas (sin políticas y sin FORCE) y quita
--      permisos a los roles de la API REST de Supabase.
--
-- Cómo ejecutarlo:
--   - Haz un respaldo antes. Pruébalo primero en una copia de la base.
--   - Debe correrse ANTES de desplegar el backend (ddl-auto=validate).
--   - Es idempotente: si se corre dos veces no duplica nada.
--   - Va dentro de una transacción: si algo falla, no queda nada a medias.
--
-- Revisión previa (opcional, ejecutar aparte):
--   -- Que la secuencia de rol esté al día (evita choque de id_rol):
--   SELECT last_value FROM public.rol_id_rol_seq;
--   SELECT MAX(id_rol) FROM public.rol;
--   -- Que el usuario de conexión de Render ignore RLS:
--   SELECT rolname, rolbypassrls FROM pg_roles WHERE rolname = 'postgres';
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Rol CLIENTE
-- ----------------------------------------------------------------------------
INSERT INTO public.rol (nombre_rol, descripcion, user_create, process_create)
SELECT 'CLIENTE',
       'Cliente externo con acceso al portal de clientes',
       'MIGRACION',
       'portal_cliente_20261106'
WHERE NOT EXISTS (SELECT 1 FROM public.rol WHERE nombre_rol = 'CLIENTE');

-- ----------------------------------------------------------------------------
-- 2. usuario.id_cliente
-- ----------------------------------------------------------------------------
ALTER TABLE public.usuario
    ADD COLUMN IF NOT EXISTS id_cliente BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_usuario_cliente') THEN
        ALTER TABLE public.usuario
            ADD CONSTRAINT fk_usuario_cliente
            FOREIGN KEY (id_cliente) REFERENCES public.cliente (id_cliente)
            ON DELETE RESTRICT;
    END IF;

    -- Un cliente tiene como máximo un usuario del portal.
    -- UNIQUE admite varios NULL: los usuarios internos no chocan entre sí.
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_usuario_cliente') THEN
        ALTER TABLE public.usuario
            ADD CONSTRAINT uq_usuario_cliente UNIQUE (id_cliente);
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- 3. solicitud_cotizacion
--    Pedido de cotización creado por un cliente desde el portal.
--    id_cotizacion queda vacío hasta que el equipo la convierta en cotización.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.solicitud_cotizacion (
    id_solicitud       BIGSERIAL,
    id_cliente         BIGINT        NOT NULL,
    id_usuario         BIGINT        NOT NULL,
    id_cotizacion      BIGINT,
    codigo_solicitud   VARCHAR(20)   NOT NULL,
    asunto             VARCHAR(150)  NOT NULL,
    descripcion        TEXT          NOT NULL,
    estado_solicitud   VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    user_create        VARCHAR(50)   NOT NULL,
    process_create     VARCHAR(100)  NOT NULL,
    date_create        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_update        VARCHAR(50),
    process_update     VARCHAR(100),
    date_update        TIMESTAMP,
    status             CHAR(1)       NOT NULL DEFAULT 'A'::bpchar,
    CONSTRAINT pk_solicitud_cotizacion PRIMARY KEY (id_solicitud),
    CONSTRAINT uq_solicitud_cotizacion_codigo UNIQUE (codigo_solicitud),
    CONSTRAINT fk_solicitud_cotizacion_cliente FOREIGN KEY (id_cliente)
        REFERENCES public.cliente (id_cliente) ON DELETE RESTRICT,
    CONSTRAINT fk_solicitud_cotizacion_usuario FOREIGN KEY (id_usuario)
        REFERENCES public.usuario (id_usuario) ON DELETE RESTRICT,
    CONSTRAINT fk_solicitud_cotizacion_cotiz FOREIGN KEY (id_cotizacion)
        REFERENCES public.cotizacion (id_cotizacion) ON DELETE RESTRICT,
    CONSTRAINT ck_solicitud_cotizacion_codigo
        CHECK (codigo_solicitud ~ '^SOL-[0-9]{4}-[0-9]+$'),
    CONSTRAINT ck_solicitud_cotizacion_estado
        CHECK (estado_solicitud IN ('PENDIENTE', 'EN_REVISION', 'COTIZADA', 'RECHAZADA', 'CANCELADA')),
    CONSTRAINT ck_solicitud_cotizacion_status CHECK (status IN ('A', 'I'))
);

CREATE INDEX IF NOT EXISTS ix_solicitud_cotizacion_cliente
    ON public.solicitud_cotizacion (id_cliente, date_create DESC);
CREATE INDEX IF NOT EXISTS ix_solicitud_cotizacion_usuario
    ON public.solicitud_cotizacion (id_usuario);
CREATE INDEX IF NOT EXISTS ix_solicitud_cotizacion_estado
    ON public.solicitud_cotizacion (estado_solicitud);
CREATE INDEX IF NOT EXISTS ix_solicitud_cotizacion_cotizacion
    ON public.solicitud_cotizacion (id_cotizacion)
    WHERE id_cotizacion IS NOT NULL;

-- ----------------------------------------------------------------------------
-- 4. solicitud_item
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.solicitud_item (
    id_solicitud_item  BIGSERIAL,
    id_solicitud       BIGINT        NOT NULL,
    descripcion        VARCHAR(255)  NOT NULL,
    cantidad           NUMERIC(12,2) NOT NULL,
    unidad             VARCHAR(30),
    user_create        VARCHAR(50)   NOT NULL,
    process_create     VARCHAR(100)  NOT NULL,
    date_create        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_update        VARCHAR(50),
    process_update     VARCHAR(100),
    date_update        TIMESTAMP,
    status             CHAR(1)       NOT NULL DEFAULT 'A'::bpchar,
    CONSTRAINT pk_solicitud_item PRIMARY KEY (id_solicitud_item),
    CONSTRAINT fk_solicitud_item_solicitud FOREIGN KEY (id_solicitud)
        REFERENCES public.solicitud_cotizacion (id_solicitud) ON DELETE CASCADE,
    CONSTRAINT ck_solicitud_item_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_solicitud_item_status CHECK (status IN ('A', 'I'))
);

CREATE INDEX IF NOT EXISTS ix_solicitud_item_solicitud
    ON public.solicitud_item (id_solicitud);

COMMENT ON TABLE public.solicitud_cotizacion IS
    'Solicitudes de cotización creadas por clientes desde el portal.';
COMMENT ON TABLE public.solicitud_item IS
    'Líneas opcionales de una solicitud de cotización.';
COMMENT ON COLUMN public.usuario.id_cliente IS
    'Cliente al que pertenece un usuario del portal (rol CLIENTE). NULL para usuarios internos.';

-- ----------------------------------------------------------------------------
-- 5. Seguridad: RLS sin políticas y sin FORCE
--    El backend se conecta por JDBC con el dueño de las tablas (postgres), que
--    ignora RLS. La API REST de Supabase (anon / authenticated) queda sin
--    acceso a estas tablas.
-- ----------------------------------------------------------------------------
ALTER TABLE public.solicitud_cotizacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.solicitud_item       ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    rol_api TEXT;
BEGIN
    FOREACH rol_api IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = rol_api) THEN
            EXECUTE format('REVOKE ALL ON TABLE public.solicitud_cotizacion FROM %I', rol_api);
            EXECUTE format('REVOKE ALL ON TABLE public.solicitud_item FROM %I', rol_api);
            EXECUTE format('REVOKE ALL ON SEQUENCE public.solicitud_cotizacion_id_solicitud_seq FROM %I', rol_api);
            EXECUTE format('REVOKE ALL ON SEQUENCE public.solicitud_item_id_solicitud_item_seq FROM %I', rol_api);
        END IF;
    END LOOP;
END $$;

COMMIT;

-- ============================================================================
-- Verificación posterior (ejecutar aparte; no forma parte de la migración)
-- ============================================================================
-- SELECT nombre_rol FROM public.rol WHERE nombre_rol = 'CLIENTE';
-- SELECT column_name, data_type, is_nullable
--   FROM information_schema.columns
--  WHERE table_schema = 'public' AND table_name = 'usuario' AND column_name = 'id_cliente';
-- SELECT relname, relrowsecurity FROM pg_class
--  WHERE relname IN ('solicitud_cotizacion', 'solicitud_item');   -- relrowsecurity = true
--
-- Activar un registro del portal (después de verificar que el RUC es del solicitante):
-- UPDATE public.usuario
--    SET status = 'A', user_update = 'ADMIN',
--        process_update = 'activacion_portal', date_update = now()
--  WHERE correo = '<correo>'
--    AND id_cliente IS NOT NULL
--    AND id_rol = (SELECT id_rol FROM public.rol WHERE nombre_rol = 'CLIENTE');
--
-- Ver registros pendientes de aprobación:
-- SELECT u.id_usuario, u.nombres, u.correo, u.id_cliente, t.razon_social, t.numero_documento, u.date_create
--   FROM public.usuario u
--   JOIN public.rol r ON r.id_rol = u.id_rol AND r.nombre_rol = 'CLIENTE'
--   LEFT JOIN public.cliente c ON c.id_cliente = u.id_cliente
--   LEFT JOIN public.tercero t ON t.id_tercero = c.id_tercero
--  WHERE u.status = 'I'
--  ORDER BY u.date_create DESC;

-- ============================================================================
-- REVERSIÓN (solo si hay que deshacer; ejecutar aparte)
-- ============================================================================
-- BEGIN;
-- DROP TABLE IF EXISTS public.solicitud_item;
-- DROP TABLE IF EXISTS public.solicitud_cotizacion;
-- ALTER TABLE public.usuario DROP CONSTRAINT IF EXISTS uq_usuario_cliente;
-- ALTER TABLE public.usuario DROP CONSTRAINT IF EXISTS fk_usuario_cliente;
-- ALTER TABLE public.usuario DROP COLUMN IF EXISTS id_cliente;
-- DELETE FROM public.rol WHERE nombre_rol = 'CLIENTE'
--    AND NOT EXISTS (SELECT 1 FROM public.usuario u WHERE u.id_rol = rol.id_rol);
-- COMMIT;
