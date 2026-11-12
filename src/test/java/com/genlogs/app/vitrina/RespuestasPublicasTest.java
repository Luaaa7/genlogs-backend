package com.genlogs.app.vitrina;

import com.genlogs.app.vitrina.dto.RepuestoPortalResponse;
import com.genlogs.app.vitrina.dto.RepuestoPublicoResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** D-13: lo que ve el cliente nunca trae fuente, enlace, costo, margen ni precio original. */
class RespuestasPublicasTest {

    private static List<String> campos(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName)
                .map(n -> n.toLowerCase(Locale.ROOT)).toList();
    }

    @Test
    void laLandingNoTraePrecioNiDatosDeLaFuente() {
        assertThat(campos(RepuestoPublicoResponse.class))
                .noneMatch(c -> c.contains("precio") || c.contains("costo") || c.contains("margen")
                        || c.contains("fuente") || c.contains("referencia") || c.contains("sourcing"));
    }

    @Test
    void elPortalTraePrecioReferencialPeroNadaDeLaFuente() {
        List<String> c = campos(RepuestoPortalResponse.class);
        assertThat(c).contains("precioreferencial");
        assertThat(c).noneMatch(x -> x.contains("costo") || x.contains("margen") || x.contains("fuente")
                || x.contains("referencia") && !x.equals("precioreferencial") || x.contains("original"));
    }
}
