package com.genlogs.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.ParametroSistema;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.ParametroSistemaRepository;
import com.genlogs.app.repository.UsuarioRepository;

/** Reglas de EdicionParametrosService sin base de datos. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EdicionParametrosServiceTest {

    @Mock private ParametroSistemaRepository repositorio;
    @Mock private UsuarioRepository usuarios;

    private EdicionParametrosService servicio;
    private ParametroSistema min;
    private ParametroSistema porDefecto;
    private ParametroSistema max;

    @BeforeEach
    void preparar() {
        servicio = new EdicionParametrosService(repositorio, usuarios);
        min = parametro(ClavesParametro.MARGEN_REP_MIN, "15");
        porDefecto = parametro(ClavesParametro.MARGEN_REP_DEFAULT, "35");
        max = parametro(ClavesParametro.MARGEN_REP_MAX, "45");
        when(repositorio.bloquearPorClaves(anyCollection())).thenAnswer(inv -> {
            Collection<?> claves = inv.getArgument(0);
            List<ParametroSistema> filas = new ArrayList<>();
            for (ParametroSistema p : List.of(porDefecto, max, min)) {
                if (claves.contains(p.getClave())) {
                    filas.add(p);
                }
            }
            return filas;
        });
        when(repositorio.save(any(ParametroSistema.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario jefe = new Usuario();
        jefe.setIdUsuario(7L);
        jefe.setNombreUsuario("jefe");
        when(usuarios.findByNombreUsuario("jefe")).thenReturn(Optional.of(jefe));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "jefe", null, List.of(new SimpleGrantedAuthority("ROLE_JEFE_ADMINISTRATIVO"))));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void editarUnMargenBloqueaLosTresAntesDeValidar() {
        servicio.actualizar(ClavesParametro.MARGEN_REP_DEFAULT, "40");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> claves = ArgumentCaptor.forClass(Collection.class);
        verify(repositorio).bloquearPorClaves(claves.capture());
        assertEquals(Set.of(ClavesParametro.MARGEN_REP_MIN, ClavesParametro.MARGEN_REP_DEFAULT,
                ClavesParametro.MARGEN_REP_MAX), Set.copyOf(claves.getValue()));
        assertEquals("40", porDefecto.getValor());
        assertEquals(7L, porDefecto.getEditadoPor());
    }

    @Test
    void unMargenNegativoNoSeGuarda() {
        assertThrows(BusinessException.class, () -> servicio.actualizar(ClavesParametro.MARGEN_REP_MIN, "-1"));
        verify(repositorio, never()).save(any());
        assertEquals("15", min.getValor());
    }

    @Test
    void elCambioConjuntoGuardaSoloLoQueCambia() {
        List<?> respuesta = servicio.actualizarMargenesRepuesto(
                new BigDecimal("15"), new BigDecimal("36.50"), new BigDecimal("45"));

        assertEquals(3, respuesta.size());
        assertEquals("36.5", porDefecto.getValor());
        verify(repositorio, times(1)).save(any());
        assertEquals(null, min.getEditadoPor());
    }

    @Test
    void elCambioConjuntoDesordenadoNoGuardaNada() {
        assertThrows(BusinessException.class, () -> servicio.actualizarMargenesRepuesto(
                new BigDecimal("50"), new BigDecimal("40"), new BigDecimal("70")));
        verify(repositorio, never()).save(any());
        assertEquals("15", min.getValor());
        assertEquals("35", porDefecto.getValor());
        assertEquals("45", max.getValor());
    }

    @Test
    void elCambioConjuntoConNegativoNoGuardaNada() {
        assertThrows(BusinessException.class, () -> servicio.actualizarMargenesRepuesto(
                new BigDecimal("-0.01"), new BigDecimal("35"), new BigDecimal("45")));
        verify(repositorio, never()).save(any());
    }

    @Test
    void siFaltaUnMargenEnLaBaseElErrorLoNombra() {
        when(repositorio.bloquearPorClaves(anyCollection())).thenReturn(List.of(min, porDefecto));
        BusinessException error = assertThrows(BusinessException.class, () -> servicio.actualizarMargenesRepuesto(
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30")));
        assertEquals(true, error.getMessage().contains(ClavesParametro.MARGEN_REP_MAX));
        verify(repositorio, never()).save(any());
    }

    @Test
    void unaClaveSinTipoConocidoSeGuardaComoTexto() {
        ParametroSistema libre = parametro("CLAVE_DE_OTRO_BLOQUE", "abc");
        when(repositorio.bloquearPorClaves(anyCollection())).thenReturn(List.of(libre));

        servicio.actualizar("CLAVE_DE_OTRO_BLOQUE", "texto-libre");

        assertEquals("texto-libre", libre.getValor());
    }

    private static ParametroSistema parametro(String clave, String valor) {
        ParametroSistema p = new ParametroSistema();
        p.setClave(clave);
        p.setValor(valor);
        return p;
    }
}
