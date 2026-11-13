-- ============================================================================
-- GenLogS - Migracion v3.2 - Ajustes del bloque BE-A
-- Archivo : db/migraciones/v3_2_be_a.sql
--
-- Aqui van SOLO las tablas y columnas que agregue este bloque durante el
-- desarrollo; cada bloque toca tablas distintas.
-- Orden: portal -> v3_0_roles -> v3_2 -> v3_2_ubigeo -> v3_2_be_a ... v3_2_be_e
-- Reglas: idempotente (IF NOT EXISTS), dentro de BEGIN/COMMIT y con RLS.
-- Baja logica (D-29): status = 'I' (no se agrega columna nueva).
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- RF-70: el cliente califica desde el portal la atencion de su reclamo
-- (1 a 5), cuando ya esta RESUELTO o CERRADO. Una sola vez por reclamo.
-- ----------------------------------------------------------------------------
ALTER TABLE public.reclamo
    ADD COLUMN IF NOT EXISTS calificacion            SMALLINT,
    ADD COLUMN IF NOT EXISTS comentario_calificacion TEXT,
    ADD COLUMN IF NOT EXISTS fecha_calificacion      TIMESTAMP;

ALTER TABLE public.reclamo DROP CONSTRAINT IF EXISTS ck_reclamo_calificacion;
ALTER TABLE public.reclamo ADD CONSTRAINT ck_reclamo_calificacion
    CHECK (calificacion IS NULL OR calificacion BETWEEN 1 AND 5);

COMMIT;

-- Verificacion:
--   SELECT column_name FROM information_schema.columns
--    WHERE table_name = 'reclamo' AND column_name LIKE '%calificacion%';  -- 3 filas

-- ============================================================================
-- Prefijos telefónicos por país (12/11/2026, documento "Cambios país,
-- ubicación y servicios", cambio A). Se corre aparte: las secciones de arriba
-- ya están en Supabase. Idempotente: no pisa un prefijo ya cargado.
--  - Se guarda con "+" (p. ej. +51).
--  - Países que comparten prefijo (+1: EE. UU., Canadá, Caribe; +7: Rusia y
--    Kazajistán) quedan vacíos: el usuario escribe el código.
--  - No toca los teléfonos ya guardados: el prefijo se usa en los nuevos.
--  - El código ISO puede estar en 2 o 3 letras (PE o PER): sirve con los dos.
-- Prueba antes con v3_2_be_a_prueba_prefijos.sql (termina en ROLLBACK).
-- ============================================================================

BEGIN;

ALTER TABLE public.pais ADD COLUMN IF NOT EXISTS codigo_telefono VARCHAR(8);
ALTER TABLE public.pais DROP CONSTRAINT IF EXISTS ck_pais_codigo_telefono;
ALTER TABLE public.pais ADD CONSTRAINT ck_pais_codigo_telefono
    CHECK (codigo_telefono IS NULL OR codigo_telefono ~ '^\+[0-9]{1,4}$');

UPDATE public.pais p
   SET codigo_telefono = v.prefijo
  FROM (VALUES
        ('PE','PER','+51'),  ('AR','ARG','+54'),  ('BO','BOL','+591'), ('BR','BRA','+55'),
        ('CL','CHL','+56'),  ('CO','COL','+57'),  ('EC','ECU','+593'), ('PY','PRY','+595'),
        ('UY','URY','+598'), ('VE','VEN','+58'),  ('MX','MEX','+52'),  ('CR','CRI','+506'),
        ('PA','PAN','+507'), ('GT','GTM','+502'), ('SV','SLV','+503'), ('HN','HND','+504'),
        ('NI','NIC','+505'), ('CU','CUB','+53'),  ('ES','ESP','+34'),  ('PT','PRT','+351'),
        ('FR','FRA','+33'),  ('DE','DEU','+49'),  ('IT','ITA','+39'),  ('GB','GBR','+44'),
        ('IE','IRL','+353'), ('NL','NLD','+31'),  ('BE','BEL','+32'),  ('CH','CHE','+41'),
        ('AT','AUT','+43'),  ('SE','SWE','+46'),  ('NO','NOR','+47'),  ('DK','DNK','+45'),
        ('FI','FIN','+358'), ('PL','POL','+48'),  ('CZ','CZE','+420'), ('GR','GRC','+30'),
        ('TR','TUR','+90'),  ('IL','ISR','+972'), ('AE','ARE','+971'), ('SA','SAU','+966'),
        ('CN','CHN','+86'),  ('JP','JPN','+81'),  ('KR','KOR','+82'),  ('IN','IND','+91'),
        ('AU','AUS','+61'),  ('NZ','NZL','+64'),  ('PH','PHL','+63'),  ('ID','IDN','+62'),
        ('TH','THA','+66'),  ('VN','VNM','+84'),  ('SG','SGP','+65'),  ('MY','MYS','+60'),
        ('TW','TWN','+886'), ('HK','HKG','+852'), ('ZA','ZAF','+27'),  ('EG','EGY','+20'),
        ('MA','MAR','+212'), ('NG','NGA','+234'), ('GH','GHA','+233'), ('KE','KEN','+254')
       ) AS v(iso2, iso3, prefijo)
 WHERE p.codigo_telefono IS NULL
   AND upper(btrim(p.codigo_iso)) IN (v.iso2, v.iso3);

COMMIT;

-- Verificación (aparte):
--   SELECT codigo_iso, nombre_pais, codigo_telefono FROM public.pais ORDER BY nombre_pais;
--   -- Perú debe salir con +51. Los que quedan vacíos los escribe el usuario.