-- ============================================================================
-- GenLogS - Migración v3.2 (documento "Procesos, roles e impacto en la BD v3.2")
-- Archivo : db/migraciones/v3_2.sql
-- Orden   : portal -> v3_0_roles.sql -> ESTE -> v3_2_ubigeo.sql -> v3_2_be_a ... e
-- Solo agrega tablas, columnas y reglas; no borra datos. Idempotente.
--
-- Antes de correrlo: el usuario del backend (postgres) debe tener rolbypassrls,
--   SELECT rolname, rolbypassrls FROM pg_roles WHERE rolname = 'postgres';
-- ============================================================================

BEGIN;

-- 1. Solicitud: identificación del repuesto (RF-76) y plazos (RF-84)
ALTER TABLE public.solicitud_item
    ADD COLUMN IF NOT EXISTS id_marca     INTEGER REFERENCES public.marca (id_marca),
    ADD COLUMN IF NOT EXISTS marca_texto  VARCHAR(100),
    ADD COLUMN IF NOT EXISTS numero_parte VARCHAR(60),
    ADD COLUMN IF NOT EXISTS gtin         VARCHAR(14),
    ADD COLUMN IF NOT EXISTS url_foto     TEXT;
ALTER TABLE public.solicitud_item DROP CONSTRAINT IF EXISTS ck_solicitud_item_gtin;
ALTER TABLE public.solicitud_item ADD CONSTRAINT ck_solicitud_item_gtin
    CHECK (gtin IS NULL OR gtin ~ '^[0-9]{8,14}$');

ALTER TABLE public.solicitud_cotizacion
    ADD COLUMN IF NOT EXISTS tipo_plazo             VARCHAR(12) NOT NULL DEFAULT 'COMUN',
    ADD COLUMN IF NOT EXISTS fecha_limite_interna   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS pausada_desde          TIMESTAMP,
    ADD COLUMN IF NOT EXISTS motivo_pausa           VARCHAR(40),
    ADD COLUMN IF NOT EXISTS fecha_estimada_tercero DATE,
    ADD COLUMN IF NOT EXISTS reclasificaciones      SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS id_usuario_asignado    BIGINT REFERENCES public.usuario (id_usuario);
ALTER TABLE public.solicitud_cotizacion DROP CONSTRAINT IF EXISTS ck_solicitud_cotizacion_tipo_plazo;
ALTER TABLE public.solicitud_cotizacion ADD CONSTRAINT ck_solicitud_cotizacion_tipo_plazo
    CHECK (tipo_plazo IN ('COMUN', 'IMPORTADO', 'ESPECIAL'));
ALTER TABLE public.solicitud_cotizacion DROP CONSTRAINT IF EXISTS ck_solicitud_cotizacion_estado;
ALTER TABLE public.solicitud_cotizacion ADD CONSTRAINT ck_solicitud_cotizacion_estado
    CHECK (estado_solicitud IN ('PENDIENTE', 'EN_REVISION', 'EN_ESPERA_TERCERO',
                                'COTIZADA', 'RECHAZADA', 'CANCELADA'));

CREATE TABLE IF NOT EXISTS public.solicitud_evento (
    id_evento    BIGSERIAL PRIMARY KEY,
    id_solicitud BIGINT NOT NULL REFERENCES public.solicitud_cotizacion (id_solicitud) ON DELETE CASCADE,
    id_usuario   BIGINT REFERENCES public.usuario (id_usuario),
    tipo_evento  VARCHAR(20) NOT NULL
                 CHECK (tipo_evento IN ('CREADA', 'ESTADO', 'PAUSA', 'REANUDA',
                                        'RECLASIFICA', 'REASIGNA', 'EDICION')),
    detalle      TEXT,
    fecha_evento TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_solicitud_evento_solicitud ON public.solicitud_evento (id_solicitud);

-- 2. Historial de búsqueda (RF-76, RF-77)
CREATE TABLE IF NOT EXISTS public.historial_sourcing (
    id_sourcing       BIGSERIAL PRIMARY KEY,
    id_solicitud_item BIGINT REFERENCES public.solicitud_item (id_solicitud_item) ON DELETE SET NULL,
    fuente            VARCHAR(15) NOT NULL
                      CHECK (fuente IN ('MERCADO_LIBRE', 'EBAY', 'AMAZON', 'ALIBABA', 'PROVEEDOR')),
    metodo            VARCHAR(10) NOT NULL
                      CHECK (metodo IN ('NUMERO_PARTE', 'GTIN', 'PALABRAS', 'IMAGEN', 'URL')),
    consulta          TEXT,
    url_referencia    TEXT,
    url_imagen_ref    TEXT,
    titulo            TEXT,
    marca_texto       VARCHAR(100),
    numero_parte      VARCHAR(60),
    precio_ref        NUMERIC(12,2),
    id_moneda         INTEGER REFERENCES public.moneda (id_moneda),
    tipo_cambio_usado NUMERIC(10,4),
    origen            VARCHAR(10) CHECK (origen IN ('NACIONAL', 'IMPORTADO')),
    costo_puesto_lima NUMERIC(12,2),
    editado_a_mano    BOOLEAN NOT NULL DEFAULT FALSE,
    estado            VARCHAR(12) NOT NULL DEFAULT 'CAPTURADO'
                      CHECK (estado IN ('CAPTURADO', 'UTIL', 'DESCARTADO', 'CONVERTIDO')),
    id_producto       BIGINT REFERENCES public.producto (id_producto),
    creado_por        BIGINT REFERENCES public.usuario (id_usuario),
    creado_en         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_sourcing_item         ON public.historial_sourcing (id_solicitud_item);
CREATE INDEX IF NOT EXISTS ix_sourcing_numero_parte ON public.historial_sourcing (numero_parte);

-- 3. Vitrina: producto e imágenes (RF-78, RF-79, RF-82, RF-83)
ALTER TABLE public.producto
    ADD COLUMN IF NOT EXISTS numero_parte              VARCHAR(60),
    ADD COLUMN IF NOT EXISTS tipo_repuesto             VARCHAR(12) NOT NULL DEFAULT 'OEM',
    ADD COLUMN IF NOT EXISTS origen                    VARCHAR(10) NOT NULL DEFAULT 'NACIONAL',
    ADD COLUMN IF NOT EXISTS estado_publicacion        VARCHAR(10),
    ADD COLUMN IF NOT EXISTS precio_referencial        NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS fecha_ultima_verificacion TIMESTAMP,
    ADD COLUMN IF NOT EXISTS fallas_cotizacion         SMALLINT NOT NULL DEFAULT 0;
UPDATE public.producto
   SET estado_publicacion = CASE WHEN visible_web THEN 'PUBLICADO' ELSE 'BORRADOR' END,
       fecha_ultima_verificacion = CURRENT_TIMESTAMP
 WHERE estado_publicacion IS NULL;
ALTER TABLE public.producto ALTER COLUMN estado_publicacion SET DEFAULT 'BORRADOR';
ALTER TABLE public.producto ALTER COLUMN estado_publicacion SET NOT NULL;
ALTER TABLE public.producto DROP CONSTRAINT IF EXISTS ck_producto_estado_pub;
ALTER TABLE public.producto ADD CONSTRAINT ck_producto_estado_pub
    CHECK (estado_publicacion IN ('BORRADOR', 'PUBLICADO', 'OCULTO', 'RETIRADO'));
ALTER TABLE public.producto DROP CONSTRAINT IF EXISTS ck_producto_tipo_repuesto;
ALTER TABLE public.producto ADD CONSTRAINT ck_producto_tipo_repuesto
    CHECK (tipo_repuesto IN ('OEM', 'ALTERNATIVO'));
ALTER TABLE public.producto DROP CONSTRAINT IF EXISTS ck_producto_origen;
ALTER TABLE public.producto ADD CONSTRAINT ck_producto_origen
    CHECK (origen IN ('NACIONAL', 'IMPORTADO'));

ALTER TABLE public.producto_imagen
    ADD COLUMN IF NOT EXISTS fuente_imagen  VARCHAR(10),
    ADD COLUMN IF NOT EXISTS verificada_por BIGINT REFERENCES public.usuario (id_usuario),
    ADD COLUMN IF NOT EXISTS verificada_en  TIMESTAMP;
ALTER TABLE public.producto_imagen DROP CONSTRAINT IF EXISTS ck_producto_imagen_fuente;
ALTER TABLE public.producto_imagen ADD CONSTRAINT ck_producto_imagen_fuente
    CHECK (fuente_imagen IS NULL OR fuente_imagen IN ('RECEPCION', 'PROVEEDOR', 'CLIENTE'));

-- 4. Ítems cotizados: origen y referencia interna
ALTER TABLE public.cotizacion_detalle
    ADD COLUMN IF NOT EXISTS origen      VARCHAR(10),
    ADD COLUMN IF NOT EXISTS id_sourcing BIGINT REFERENCES public.historial_sourcing (id_sourcing);
ALTER TABLE public.cotizacion_detalle DROP CONSTRAINT IF EXISTS ck_cotizacion_detalle_origen;
ALTER TABLE public.cotizacion_detalle ADD CONSTRAINT ck_cotizacion_detalle_origen
    CHECK (origen IS NULL OR origen IN ('NACIONAL', 'IMPORTADO'));
ALTER TABLE public.cotizacion ADD COLUMN IF NOT EXISTS tipo_cambio_usado NUMERIC(10,4);

-- 5. OC de la minera (RF-73, RF-85)
ALTER TABLE public.orden_compra
    ADD COLUMN IF NOT EXISTS numero_oc_cliente VARCHAR(40),
    ADD COLUMN IF NOT EXISTS monto_oc_cliente  NUMERIC(14,2),
    ADD COLUMN IF NOT EXISTS oc_leida_auto     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS oc_confirmada_por BIGINT REFERENCES public.usuario (id_usuario);

-- 6. Servicios con margen (RF-80)
ALTER TABLE public.servicio
    ADD COLUMN IF NOT EXISTS costo              NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS margen             NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS precio_referencial NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS precio_manual      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.servicio DROP CONSTRAINT IF EXISTS ck_servicio_margen;
ALTER TABLE public.servicio ADD CONSTRAINT ck_servicio_margen
    CHECK (margen IS NULL OR margen BETWEEN 0 AND 100);
ALTER TABLE public.servicio DROP CONSTRAINT IF EXISTS ck_servicio_costo;
ALTER TABLE public.servicio ADD CONSTRAINT ck_servicio_costo
    CHECK (costo IS NULL OR costo > 0);

-- 7. Clientes extranjeros, versión mínima (RF-81)
ALTER TABLE public.tercero ADD COLUMN IF NOT EXISTS id_pais INTEGER REFERENCES public.pais (id_pais);
UPDATE public.tercero
   SET id_pais = (SELECT id_pais FROM public.pais WHERE codigo_iso IN ('PE', 'PER') LIMIT 1)
 WHERE id_pais IS NULL;
ALTER TABLE public.tercero ALTER COLUMN id_distrito DROP NOT NULL;

-- 8. Tipo de cambio diario (RF-86) y parámetros (RF-87)
CREATE TABLE IF NOT EXISTS public.tipo_cambio (
    id_tipo_cambio BIGSERIAL PRIMARY KEY,
    fecha          DATE NOT NULL,
    id_moneda      INTEGER NOT NULL REFERENCES public.moneda (id_moneda),
    valor          NUMERIC(10,4) NOT NULL CHECK (valor > 0),
    fuente         VARCHAR(10) NOT NULL CHECK (fuente IN ('SUNAT', 'SBS', 'MANUAL')),
    editado_por    BIGINT REFERENCES public.usuario (id_usuario),
    CONSTRAINT uq_tipo_cambio_fecha UNIQUE (fecha, id_moneda)
);

CREATE TABLE IF NOT EXISTS public.parametro_sistema (
    clave       VARCHAR(50) PRIMARY KEY,
    valor       VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255),
    editado_por BIGINT REFERENCES public.usuario (id_usuario),
    editado_en  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO public.parametro_sistema (clave, valor, descripcion) VALUES
    ('MONTO_ALTO_VALOR_PEN',   '20000', 'Desde este monto se notifica al Gerente General'),
    ('MARGEN_SERV_SUGERIDO',   '25',    'Margen sugerido de servicios (%)'),
    ('MARGEN_SERV_MIN',        '15',    'Debajo requiere aprobación del Jefe Administrativo'),
    ('MARGEN_SERV_AVISO',      '35',    'Desde aquí se muestra aviso'),
    ('MARGEN_SERV_MAX',        '45',    'Encima requiere aprobación del Jefe Administrativo'),
    ('PLAZO_COMUN_DIAS',       '1',     'Días hábiles para cotizar un pedido común'),
    ('PLAZO_IMPORTADO_DIAS',   '3',     'Días hábiles para importado o poco común'),
    ('PLAZO_ESPECIAL_DIAS',    '5',     'Días hábiles para foto, plano o fabricación'),
    ('ALERTA_PLAZO_PCT',       '75',    'Porcentaje del plazo en que se alerta al asistente'),
    ('VERIFICACION_DIAS',      '15',    'Cada cuántos días se verifica la vitrina'),
    ('OCULTAR_SIN_VERIF_DIAS', '60',    'Días sin verificar para ocultar un producto'),
    ('OCULTAR_FALLAS',         '2',     'Cotizaciones fallidas para ocultar un producto')
ON CONFLICT (clave) DO NOTHING;

-- 9. Seguridad: las tablas nuevas no quedan expuestas en la API de Supabase
ALTER TABLE public.historial_sourcing ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.solicitud_evento   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tipo_cambio        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.parametro_sistema  ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    rol_api TEXT;
    tabla   TEXT;
BEGIN
    FOREACH rol_api IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = rol_api) THEN
            FOREACH tabla IN ARRAY ARRAY['historial_sourcing', 'solicitud_evento', 'tipo_cambio', 'parametro_sistema'] LOOP
                EXECUTE format('REVOKE ALL ON TABLE public.%I FROM %I', tabla, rol_api);
            END LOOP;
        END IF;
    END LOOP;
END $$;

COMMIT;

-- Verificación (aparte):
-- SELECT COUNT(*) FROM public.parametro_sistema;             -- 12
-- SELECT COUNT(*) FROM public.tercero WHERE id_pais IS NULL;  -- 0