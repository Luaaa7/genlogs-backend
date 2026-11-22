-- D-42: ingreso en dos pantallas. CONTINUAR es el botón opcional que pasa
-- de la casilla del usuario a la de la clave. No es una regla mínima:
-- el ingreso de una sola pantalla sigue con USUARIO, CLAVE, ENVIAR y EXITO.
ALTER TABLE public.fuente_selector DROP CONSTRAINT IF EXISTS ck_fuente_selector_campo;
ALTER TABLE public.fuente_selector ADD CONSTRAINT ck_fuente_selector_campo
    CHECK (campo IN ('ITEM', 'NOMBRE', 'DESCRIPCION', 'PRECIO', 'MONEDA', 'IMAGEN', 'ENLACE',
                     'USUARIO', 'CLAVE', 'ENVIAR', 'EXITO', 'CONTINUAR'));
