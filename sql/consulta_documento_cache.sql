-- ============================================================
-- Caché de consultas RUC/DNI a Factiliza.
-- Ejecutar en Supabase (SQL Editor) ANTES de arrancar el backend:
-- spring.jpa.hibernate.ddl-auto=validate falla si la tabla no existe.
--
-- Cada número consultado con éxito en Factiliza se guarda aquí; las
-- consultas siguientes del mismo número se responden desde esta tabla
-- sin gastar tokens, hasta que pasen factiliza.cache-dias días.
-- ============================================================

CREATE TABLE public.consulta_documento_cache (
    id_consulta_documento_cache BIGSERIAL PRIMARY KEY,
    numero_documento VARCHAR NOT NULL,
    tipo_documento VARCHAR NOT NULL,
    razon_social VARCHAR,
    direccion VARCHAR,
    fecha_consulta TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_create VARCHAR NOT NULL,
    process_create VARCHAR NOT NULL,
    date_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_update VARCHAR,
    process_update VARCHAR,
    date_update TIMESTAMP,
    status CHAR(1) NOT NULL DEFAULT 'A',
    CONSTRAINT uq_consulta_documento_cache_numero UNIQUE (numero_documento),
    CONSTRAINT ck_consulta_documento_cache_tipo CHECK (tipo_documento IN ('RUC', 'DNI'))
);
