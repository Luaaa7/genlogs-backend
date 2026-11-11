-- ============================================================================
-- GenLogS - Migración v3.0 · Roles
-- Archivo : db/migraciones/v3_0_roles.sql
-- Orden   : portal -> ESTE -> v3_2.sql -> v3_2_ubigeo.sql -> v3_2_be_a ... e
--
-- Qué hace:
--   1. Renombra VENDEDOR a ASISTENTE_LOGISTICO (sus usuarios pasan con él).
--   2. Crea los roles que falten hasta dejar exactamente estos 7:
--      GERENTE_GENERAL, JEFE_ADMINISTRATIVO, CONTADOR_GENERAL, SUPERVISOR_SST,
--      ASISTENTE_LOGISTICO, ASISTENTE_ADMINISTRATIVO, CLIENTE
--   3. Mueve los usuarios de ADMINISTRADOR a JEFE_ADMINISTRATIVO y lo borra.
--   4. Quita uq_usuario_cliente: varios usuarios CLIENTE por empresa.
-- Se corre el día del despliegue. Idempotente y en una transacción.
-- ============================================================================

BEGIN;

-- Secuencia de rol al día (evita choque de id_rol al insertar)
SELECT setval(pg_get_serial_sequence('public.rol', 'id_rol'),
              GREATEST((SELECT COALESCE(MAX(id_rol), 0) FROM public.rol), 1));

-- 1. VENDEDOR -> ASISTENTE_LOGISTICO
UPDATE public.rol
   SET nombre_rol = 'ASISTENTE_LOGISTICO',
       descripcion = 'Confirma N.º de parte, busca, cotiza, publica en la vitrina, compra, aduana y despacho',
       user_update = 'MIGRACION', process_update = 'v3_0_roles', date_update = CURRENT_TIMESTAMP
 WHERE nombre_rol = 'VENDEDOR'
   AND NOT EXISTS (SELECT 1 FROM public.rol WHERE nombre_rol = 'ASISTENTE_LOGISTICO');

UPDATE public.usuario
   SET id_rol = (SELECT id_rol FROM public.rol WHERE nombre_rol = 'ASISTENTE_LOGISTICO'),
       user_update = 'MIGRACION', process_update = 'v3_0_roles', date_update = CURRENT_TIMESTAMP
 WHERE id_rol = (SELECT id_rol FROM public.rol WHERE nombre_rol = 'VENDEDOR')
   AND EXISTS (SELECT 1 FROM public.rol WHERE nombre_rol = 'ASISTENTE_LOGISTICO');

DELETE FROM public.rol r
 WHERE r.nombre_rol = 'VENDEDOR'
   AND NOT EXISTS (SELECT 1 FROM public.usuario u WHERE u.id_rol = r.id_rol);

-- 2. Crear los roles que falten
INSERT INTO public.rol (nombre_rol, descripcion, user_create, process_create)
SELECT v.nombre_rol, v.descripcion, 'MIGRACION', 'v3_0_roles'
  FROM (VALUES
        ('GERENTE_GENERAL',          'Ve márgenes, dashboard y reportes; recibe aviso de cotizaciones de alto valor'),
        ('JEFE_ADMINISTRATIVO',      'Aprueba cotizaciones; administra usuarios, parámetros, tipo de cambio y servicios'),
        ('CONTADOR_GENERAL',         'Registra el comprobante emitido en SUNAT con el N.º de OC y controla el cobro'),
        ('SUPERVISOR_SST',           'Confirma pase y SCTR antes de cada despacho; sin precios'),
        ('ASISTENTE_LOGISTICO',      'Confirma N.º de parte, busca, cotiza, publica en la vitrina, compra, aduana y despacho'),
        ('ASISTENTE_ADMINISTRATIVO', 'Sube y confirma la OC de la minera y carga documentos; sin márgenes'),
        ('CLIENTE',                  'Cliente externo con acceso al portal de clientes')
       ) AS v(nombre_rol, descripcion)
 WHERE NOT EXISTS (SELECT 1 FROM public.rol r WHERE r.nombre_rol = v.nombre_rol);

-- 3. ADMINISTRADOR -> JEFE_ADMINISTRATIVO
UPDATE public.usuario
   SET id_rol = (SELECT id_rol FROM public.rol WHERE nombre_rol = 'JEFE_ADMINISTRATIVO'),
       user_update = 'MIGRACION', process_update = 'v3_0_roles', date_update = CURRENT_TIMESTAMP
 WHERE id_rol = (SELECT id_rol FROM public.rol WHERE nombre_rol = 'ADMINISTRADOR');

DELETE FROM public.rol r
 WHERE r.nombre_rol = 'ADMINISTRADOR'
   AND NOT EXISTS (SELECT 1 FROM public.usuario u WHERE u.id_rol = r.id_rol);

-- 4. Varios usuarios CLIENTE por empresa
ALTER TABLE public.usuario DROP CONSTRAINT IF EXISTS uq_usuario_cliente;
CREATE INDEX IF NOT EXISTS ix_usuario_cliente
    ON public.usuario (id_cliente)
    WHERE id_cliente IS NOT NULL;

COMMIT;

-- Verificación (aparte):
-- SELECT nombre_rol FROM public.rol ORDER BY nombre_rol;   -- 7 filas