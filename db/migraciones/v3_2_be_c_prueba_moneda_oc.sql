-- ============================================================================
-- Prueba de moneda_oc_cliente. No deja cambios: termina en ROLLBACK.
-- Correrlo dos veces no falla (el ALTER es idempotente y cada corrida se deshace).
-- ============================================================================

BEGIN;

ALTER TABLE public.orden_compra
    ADD COLUMN IF NOT EXISTS moneda_oc_cliente CHAR(3);

-- Segunda vez, en la misma transacción: IF NOT EXISTS no debe fallar.
ALTER TABLE public.orden_compra
    ADD COLUMN IF NOT EXISTS moneda_oc_cliente CHAR(3);

SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'orden_compra'
  AND column_name = 'moneda_oc_cliente';

ROLLBACK;
