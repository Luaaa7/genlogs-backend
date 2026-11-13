-- ============================================================================
-- Prueba de los prefijos telefónicos (cambio A). No deja cambios: termina en
-- ROLLBACK. Muestra qué países recibirían prefijo y cuáles quedarían vacíos.
-- ============================================================================

BEGIN;

ALTER TABLE public.pais ADD COLUMN IF NOT EXISTS codigo_telefono VARCHAR(8);

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

SELECT codigo_iso, nombre_pais, codigo_telefono
  FROM public.pais
 ORDER BY codigo_telefono IS NULL, nombre_pais;

ROLLBACK;