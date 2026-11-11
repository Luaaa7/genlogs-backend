package com.genlogs.app.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.genlogs.app.model.EstadoReclamo;

class MensajesErrorTest {

    private static DataIntegrityViolationException integridad(String estado, String mensajePg) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLException(mensajePg, estado));
    }

    @Test
    void duplicadoIndicaCampoYValor() {
        String m = MensajesError.integridad(integridad("23505",
                "ERROR: duplicate key value violates unique constraint \"uq_cliente_doc\"\n"
                        + "  Detail: Key (numero_documento)=(20100070970) already exists."));
        assertEquals("Ya existe un registro con número de documento \"20100070970\". Usa un valor diferente.", m);
    }

    @Test
    void referenciaAlEliminar() {
        String m = MensajesError.integridad(integridad("23503",
                "ERROR: update or delete on table \"cliente\" violates foreign key constraint \"fk_cot_cliente\" on table \"cotizacion\"\n"
                        + "  Detail: Key (id_cliente)=(5) is still referenced from table \"cotizacion\"."));
        assertTrue(m.startsWith("No se puede eliminar porque otros registros dependen de él (cotizacion)"), m);
    }

    @Test
    void referenciaInexistenteAlGuardar() {
        String m = MensajesError.integridad(integridad("23503",
                "ERROR: insert or update on table \"reclamo\" violates foreign key constraint \"x\"\n"
                        + "  Detail: Key (id_cliente)=(999) is not present in table \"cliente\"."));
        assertEquals("El cliente indicado no existe o fue eliminado.", m);
    }

    @Test
    void campoNulo() {
        String m = MensajesError.integridad(integridad("23502",
                "ERROR: null value in column \"asunto\" of relation \"reclamo\" violates not-null constraint"));
        assertEquals("Falta un dato obligatorio: asunto.", m);
    }

    @Test
    void enumInvalidoEnJson() {
        String m = MensajesError.cuerpoIlegible(new RuntimeException("JSON parse error: Cannot deserialize value of type "
                + "`com.genlogs.app.model.EstadoReclamo` from String \"TERMINADO\": not one of the values accepted for "
                + "Enum class: [REGISTRADO, EN_PROCESO, RESUELTO, CERRADO, ANULADO] (through reference chain: "
                + "com.genlogs.app.dto.CambiarEstadoReclamoRequest[\"estado\"])"));
        assertEquals("Valor no válido en el campo \"estado\": \"TERMINADO\". "
                + "Valores permitidos: REGISTRADO, EN_PROCESO, RESUELTO, CERRADO, ANULADO.", m);
    }

    @Test
    void fechaInvalidaEnJson() {
        String m = MensajesError.cuerpoIlegible(new RuntimeException("JSON parse error: Cannot deserialize value of type "
                + "`java.time.LocalDate` from String \"31/12/2026\" (through reference chain: "
                + "com.genlogs.app.dto.ReclamoRequest[\"fechaLimite\"])"));
        assertEquals("La fecha en el campo \"fechaLimite\" no tiene un formato válido (usa AAAA-MM-DD).", m);
    }

    @Test
    void parametroEnumInvalido() {
        String m = MensajesError.parametroInvalido("estado", "XYZ", EstadoReclamo.class);
        assertEquals("El valor \"XYZ\" no es válido para \"estado\". "
                + "Valores permitidos: REGISTRADO, EN_PROCESO, RESUELTO, CERRADO, ANULADO.", m);
    }

    @Test
    void parametroNumericoInvalido() {
        assertEquals("El valor \"abc\" no es válido para \"id\": debe ser un número.",
                MensajesError.parametroInvalido("id", "abc", Long.class));
    }
}
