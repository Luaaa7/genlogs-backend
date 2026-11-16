-- =============================================================================
-- GENLOGS v3.3 · ME (Mell) · Reglas de extracción de eBay para fuente_selector
-- Tomadas del HTML real de eBay (15/11/2026):
--   búsqueda: https://www.ebay.com/sch/i.html?_nkw=CAT+1R-0750
--   ficha:    https://www.ebay.com/itm/375375806750
-- Requisito: v3_3.sql de Karla ya aplicado (tabla fuente_selector con ITEM en el CHECK
-- y la fila EBAY en fuente_busqueda).
-- Se puede correr varias veces: si la regla ya existe, la actualiza.
-- Atributo con @ al final: "a.s-card__link@href" = leer el atributo href.
-- =============================================================================

INSERT INTO public.fuente_selector (id_fuente, pagina, campo, regla, user_create, process_create)
SELECT f.id_fuente, r.pagina, r.campo, r.regla, 'SISTEMA', 'V3_3_REGLAS_EBAY'
FROM public.fuente_busqueda f
CROSS JOIN (VALUES
    -- Página de búsqueda. data-item-pos deja fuera la tarjeta falsa "Shop on eBay".
    ('BUSQUEDA', 'ITEM',   'li.s-card[data-item-pos]'),
    ('BUSQUEDA', 'NOMBRE', '.s-card__title span.su-styled-text.primary'),
    ('BUSQUEDA', 'PRECIO', '.s-card__price'),
    ('BUSQUEDA', 'ENLACE', 'a.s-card__link@href'),
    ('BUSQUEDA', 'IMAGEN', 'img.s-card__image@src'),
    -- Ficha del producto (el enlace es la misma URL de la ficha).
    ('FICHA',    'NOMBRE', 'h1.x-item-title__mainTitle span.ux-textspans--BOLD'),
    ('FICHA',    'PRECIO', '.x-price-primary__price'),
    ('FICHA',    'IMAGEN', '.ux-image-carousel-item img@src')
) AS r (pagina, campo, regla)
WHERE f.codigo = 'EBAY'
ON CONFLICT (id_fuente, pagina, campo) DO UPDATE
    SET regla          = EXCLUDED.regla,
        status         = 'A',
        user_update    = 'SISTEMA',
        process_update = 'V3_3_REGLAS_EBAY',
        date_update    = CURRENT_TIMESTAMP;

-- Verificación: deben salir 8 filas de EBAY.
-- SELECT s.pagina, s.campo, s.regla
-- FROM public.fuente_selector s JOIN public.fuente_busqueda f ON f.id_fuente = s.id_fuente
-- WHERE f.codigo = 'EBAY' ORDER BY s.pagina, s.campo;