-- ============================================================================
-- GenLogS - Migración v3.2 · Bloque BE-B: feriados (D-18)
-- Archivo : db/migraciones/v3_2_be_b.sql
-- Destino : Supabase (PostgreSQL), esquema public
-- Rama    : luana/be-b-feriados
--
-- Qué hace:
--   1. Crea la tabla feriado (baja lógica con status = 'I', D-29).
--   2. Una sola fecha activa por día (índice único parcial).
--   3. Precarga los 16 feriados nacionales de 2026 y de 2027
--      (D. Leg. 713 y leyes 31381, 31788 y 31822).
--   4. Activa RLS y quita permisos a la API REST de Supabase.
--
-- Cómo ejecutarlo:
--   - Va después de v3_2.sql (orden del Paso 0) y ANTES de desplegar el
--     backend (ddl-auto=validate).
--   - Es idempotente: si se corre dos veces no duplica nada.
--   - Va dentro de una transacción.
--
-- Nota: los "días no laborables" que el Gobierno declara para el sector
-- público no se precargan; si GENLOGS no atiende alguno, lo agrega el
-- Jefe Administrativo desde la pantalla de feriados.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Tabla feriado
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.feriado (
    id_feriado      SERIAL,
    fecha           DATE          NOT NULL,
    descripcion     VARCHAR(100)  NOT NULL,
    user_create     VARCHAR(50)   NOT NULL,
    process_create  VARCHAR(100)  NOT NULL,
    date_create     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_update     VARCHAR(50),
    process_update  VARCHAR(100),
    date_update     TIMESTAMP,
    status          CHAR(1)       NOT NULL DEFAULT 'A'::bpchar,
    CONSTRAINT pk_feriado PRIMARY KEY (id_feriado),
    CONSTRAINT ck_feriado_status CHECK (status IN ('A', 'I'))
);

-- Una fecha solo puede tener un feriado activo; los dados de baja se conservan.
CREATE UNIQUE INDEX IF NOT EXISTS uq_feriado_fecha_activo
    ON public.feriado (fecha) WHERE status = 'A';

-- ----------------------------------------------------------------------------
-- 2. Feriados nacionales 2026 y 2027
-- ----------------------------------------------------------------------------
INSERT INTO public.feriado (fecha, descripcion, user_create, process_create)
SELECT v.fecha, v.descripcion, 'MIGRACION', 'v3_2_be_b'
FROM (VALUES
    (DATE '2026-01-01', 'Año Nuevo'),
    (DATE '2026-04-02', 'Jueves Santo'),
    (DATE '2026-04-03', 'Viernes Santo'),
    (DATE '2026-05-01', 'Día del Trabajo'),
    (DATE '2026-06-07', 'Batalla de Arica y Día de la Bandera'),
    (DATE '2026-06-29', 'San Pedro y San Pablo'),
    (DATE '2026-07-23', 'Día de la Fuerza Aérea del Perú'),
    (DATE '2026-07-28', 'Fiestas Patrias'),
    (DATE '2026-07-29', 'Fiestas Patrias'),
    (DATE '2026-08-06', 'Batalla de Junín'),
    (DATE '2026-08-30', 'Santa Rosa de Lima'),
    (DATE '2026-10-08', 'Combate de Angamos'),
    (DATE '2026-11-01', 'Día de Todos los Santos'),
    (DATE '2026-12-08', 'Inmaculada Concepción'),
    (DATE '2026-12-09', 'Batalla de Ayacucho'),
    (DATE '2026-12-25', 'Navidad'),
    (DATE '2027-01-01', 'Año Nuevo'),
    (DATE '2027-03-25', 'Jueves Santo'),
    (DATE '2027-03-26', 'Viernes Santo'),
    (DATE '2027-05-01', 'Día del Trabajo'),
    (DATE '2027-06-07', 'Batalla de Arica y Día de la Bandera'),
    (DATE '2027-06-29', 'San Pedro y San Pablo'),
    (DATE '2027-07-23', 'Día de la Fuerza Aérea del Perú'),
    (DATE '2027-07-28', 'Fiestas Patrias'),
    (DATE '2027-07-29', 'Fiestas Patrias'),
    (DATE '2027-08-06', 'Batalla de Junín'),
    (DATE '2027-08-30', 'Santa Rosa de Lima'),
    (DATE '2027-10-08', 'Combate de Angamos'),
    (DATE '2027-11-01', 'Día de Todos los Santos'),
    (DATE '2027-12-08', 'Inmaculada Concepción'),
    (DATE '2027-12-09', 'Batalla de Ayacucho'),
    (DATE '2027-12-25', 'Navidad')
) AS v (fecha, descripcion)
WHERE NOT EXISTS (
    SELECT 1 FROM public.feriado f WHERE f.fecha = v.fecha AND f.status = 'A'
);

-- ----------------------------------------------------------------------------
-- 3. RLS: el backend entra con el dueño de las tablas (ignora RLS);
--    la API REST de Supabase (anon / authenticated) queda sin acceso.
-- ----------------------------------------------------------------------------
ALTER TABLE public.feriado ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    rol_api TEXT;
BEGIN
    FOREACH rol_api IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = rol_api) THEN
            EXECUTE format('REVOKE ALL ON TABLE public.feriado FROM %I', rol_api);
            EXECUTE format('REVOKE ALL ON SEQUENCE public.feriado_id_feriado_seq FROM %I', rol_api);
        END IF;
    END LOOP;
END $$;

COMMIT;

-- ============================================================================
-- Verificación posterior (ejecutar aparte)
-- ============================================================================
-- SELECT EXTRACT(YEAR FROM fecha) AS anio, COUNT(*) FROM public.feriado
--  WHERE status = 'A' GROUP BY 1 ORDER BY 1;          -- 2026: 16, 2027: 16
