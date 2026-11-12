-- ============================================================================
-- GenLogS - Migracion v3.2 - Ajustes del bloque BE-C
-- Archivo : db/migraciones/v3_2_be_c.sql
--
-- Vacio a proposito (Paso 0). Aqui van SOLO las tablas y columnas que agregue
-- este bloque durante el desarrollo; cada bloque toca tablas distintas.
-- Orden: portal -> v3_0_roles -> v3_2 -> v3_2_ubigeo -> v3_2_be_a ... v3_2_be_e
-- Reglas: idempotente (IF NOT EXISTS), dentro de BEGIN/COMMIT y con RLS.
-- Baja logica (D-29):
--   ALTER TABLE ... ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE;
-- ============================================================================

BEGIN;

COMMIT;