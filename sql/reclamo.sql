-- ============================================================
-- Módulo de Reclamos e Incidencias.
-- Ejecutar en Supabase (SQL Editor) ANTES de arrancar el backend:
-- spring.jpa.hibernate.ddl-auto=validate falla si las tablas no existen.
--
-- reclamo              : cabecera (tipo, estado, prioridad, cliente, responsable...)
-- seguimiento_reclamo  : bitácora de cambios de estado y comentarios
-- ============================================================

CREATE TABLE public.reclamo (
    id_reclamo BIGSERIAL PRIMARY KEY,
    codigo_reclamo VARCHAR(20) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'REGISTRADO',
    prioridad VARCHAR(20) NOT NULL DEFAULT 'MEDIA',
    id_cliente BIGINT REFERENCES public.cliente (id_cliente),
    id_orden_compra BIGINT REFERENCES public.orden_compra (id_orden_compra),
    id_usuario_responsable BIGINT REFERENCES public.usuario (id_usuario),
    asunto VARCHAR(200) NOT NULL,
    descripcion TEXT NOT NULL,
    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_limite DATE,
    fecha_cierre TIMESTAMP,
    solucion TEXT,
    user_create VARCHAR(50) NOT NULL,
    process_create VARCHAR(100) NOT NULL,
    date_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_update VARCHAR(50),
    process_update VARCHAR(100),
    date_update TIMESTAMP,
    status CHAR(1) NOT NULL DEFAULT 'A',
    CONSTRAINT uq_reclamo_codigo UNIQUE (codigo_reclamo),
    CONSTRAINT ck_reclamo_tipo CHECK (tipo IN ('RECLAMO', 'INCIDENCIA')),
    CONSTRAINT ck_reclamo_estado CHECK (estado IN ('REGISTRADO', 'EN_PROCESO', 'RESUELTO', 'CERRADO', 'ANULADO')),
    CONSTRAINT ck_reclamo_prioridad CHECK (prioridad IN ('BAJA', 'MEDIA', 'ALTA', 'CRITICA'))
);

CREATE INDEX ix_reclamo_estado ON public.reclamo (estado) WHERE status = 'A';
CREATE INDEX ix_reclamo_cliente ON public.reclamo (id_cliente);
CREATE INDEX ix_reclamo_responsable ON public.reclamo (id_usuario_responsable);

CREATE TABLE public.seguimiento_reclamo (
    id_seguimiento_reclamo BIGSERIAL PRIMARY KEY,
    id_reclamo BIGINT NOT NULL REFERENCES public.reclamo (id_reclamo),
    estado_anterior VARCHAR(20),
    estado_nuevo VARCHAR(20) NOT NULL,
    id_usuario BIGINT REFERENCES public.usuario (id_usuario),
    fecha_evento TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comentario TEXT,
    user_create VARCHAR(50) NOT NULL,
    process_create VARCHAR(100) NOT NULL,
    date_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_update VARCHAR(50),
    process_update VARCHAR(100),
    date_update TIMESTAMP,
    status CHAR(1) NOT NULL DEFAULT 'A'
);

CREATE INDEX ix_seguimiento_reclamo_reclamo ON public.seguimiento_reclamo (id_reclamo);
