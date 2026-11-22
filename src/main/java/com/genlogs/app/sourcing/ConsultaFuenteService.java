package com.genlogs.app.sourcing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.genlogs.app.fuentes.ControlCuota;
import com.genlogs.app.fuentes.FuenteNoDisponibleException;
import com.genlogs.app.fuentes.config.CodigoFuente;
import com.genlogs.app.fuentes.config.EstadoFuente;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfig;
import com.genlogs.app.fuentes.config.FuenteBusquedaConfigRepository;
import com.genlogs.app.fuentes.config.FuenteSelectorRepository;
import com.genlogs.app.fuentes.config.LectorFuente;
import com.genlogs.app.fuentes.config.LectorFuenteDespachador;
import com.genlogs.app.fuentes.config.LectorFuenteNoConectado;
import com.genlogs.app.fuentes.config.ResultadoLectura;
import com.genlogs.app.fuentes.config.SelectorFuenteDto;
import com.genlogs.app.fuentes.config.SesionFuenteService;

import lombok.extern.slf4j.Slf4j;

/**
 * BPMN 9-B.1 · Consultar una fuente. No escribe en historial_sourcing.
 * La sesión sale solo de {@link SesionFuenteService}. P2 no conectado no es CON_ERROR.
 */
@Slf4j
@Service
public class ConsultaFuenteService {

    private static final String PROCESO = "CONSULTA_FUENTE";
    private static final int LARGO_ERROR = 500;

    private final FuenteBusquedaConfigRepository fuenteRepository;
    private final FuenteSelectorRepository selectorRepository;
    private final SesionFuenteService sesiones;
    private final LectorFuente lector;
    private final PausaFuente pausa;
    private final AvisoFuenteConError avisos;
    private final ConcurrentHashMap<CodigoFuente, ControlCuota> cuotas = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemDefaultZone();

    public ConsultaFuenteService(FuenteBusquedaConfigRepository fuenteRepository,
                                 FuenteSelectorRepository selectorRepository,
                                 SesionFuenteService sesiones,
                                 @Qualifier(LectorFuenteDespachador.BEAN) LectorFuente lector,
                                 PausaFuente pausa,
                                 AvisoFuenteConError avisos) {
        this.fuenteRepository = fuenteRepository;
        this.selectorRepository = selectorRepository;
        this.sesiones = sesiones;
        this.lector = lector;
        this.pausa = pausa;
        this.avisos = avisos;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public ConsultaFuente consultar(FuenteBusquedaConfig fuente, ConsultaBusqueda consulta) {
        CodigoFuente codigo = fuente.getCodigo();
        String nombre = codigo.name();
        if (!sesiones.configuracionCompleta(codigo)) {
            return ConsultaFuente.de(nombre, EstadoConsultaFuente.OMITIDA_SESION,
                    "Fuente omitida: sin configurar.", List.of());
        }
        int limite = limiteDe(fuente);
        ControlCuota cuota = cuotaDe(codigo);
        if (cuota.usadasHoy() >= limite) {
            return ConsultaFuente.de(nombre, EstadoConsultaFuente.OMITIDA_CUOTA,
                    fuente.getNombre() + " se omitió: sin cuota diaria.", List.of());
        }

        pausa.esperar(fuente.getPausaSegundos() == null ? 0 : fuente.getPausaSegundos());

        Optional<String> sesion = pedirSesion(codigo);
        if (sesion.isEmpty()) {
            avisarSesionNoRenovada(codigo);
            return ConsultaFuente.de(nombre, EstadoConsultaFuente.OMITIDA_SESION,
                    fuente.getNombre() + " se omitió: sin sesión vigente.", List.of());
        }

        List<SelectorFuenteDto> selectores = selectoresDe(codigo);
        ResultadoLectura lectura = invocarBuscar(codigo, fuente.getUrlBase(), sesion.get(), consulta, selectores);
        if (lectura.tipo() == ResultadoLectura.Tipo.NO_CONECTADO) {
            return ConsultaFuente.de(nombre, EstadoConsultaFuente.NO_CONECTADO,
                    lectura.mensaje() != null ? lectura.mensaje() : LectorFuenteNoConectado.MENSAJE, List.of());
        }

        try {
            cuota.reservar(limite);
        } catch (FuenteNoDisponibleException e) {
            return ConsultaFuente.de(nombre, EstadoConsultaFuente.OMITIDA_CUOTA,
                    fuente.getNombre() + " se omitió: sin cuota diaria.", List.of());
        }

        if (lectura.tipo() == ResultadoLectura.Tipo.ERROR && lectura.reintentable()) {
            lectura = invocarBuscar(codigo, fuente.getUrlBase(), sesion.get(), consulta, selectores);
        }
        return aplicar(fuente, consulta, lectura);
    }

    /**
     * Lectura de una ficha con sesión (9-B tarea 5). No aplica C2 cuando P2 no está conectado.
     * El llamador decide el fallback a {@link LectorUrl}.
     */
    public ConsultaFuente consultarUrl(FuenteBusquedaConfig fuente, String url, ConsultaBusqueda consulta) {
        CodigoFuente codigo = fuente.getCodigo();
        Optional<String> sesion = pedirSesion(codigo);
        if (sesion.isEmpty()) {
            avisarSesionNoRenovada(codigo);
            return ConsultaFuente.de(codigo.name(), EstadoConsultaFuente.OMITIDA_SESION,
                    fuente.getNombre() + " se omitió: sin sesión vigente.", List.of());
        }
        ResultadoLectura lectura;
        try {
            lectura = lector.leerUrl(codigo, url, sesion.get(), selectoresDe(codigo));
        } catch (RuntimeException e) {
            log.warn("Falló la lectura de URL en {}: {}", codigo, recortar(e.getMessage()));
            lectura = ResultadoLectura.error("No se pudo leer la página de la tienda.", true);
        }
        if (lectura == null) {
            lectura = ResultadoLectura.error("El servicio de lectura no respondió.", true);
        }
        if (lectura.tipo() == ResultadoLectura.Tipo.NO_CONECTADO) {
            return ConsultaFuente.de(codigo.name(), EstadoConsultaFuente.NO_CONECTADO,
                    lectura.mensaje() != null ? lectura.mensaje() : LectorFuenteNoConectado.MENSAJE, List.of());
        }
        return aplicar(fuente, consulta, lectura);
    }

    private Optional<String> pedirSesion(CodigoFuente codigo) {
        try {
            return sesiones.sesionVigente(codigo);
        } catch (RuntimeException e) {
            log.warn("No se pudo pedir la sesión de {}: {}", codigo, recortar(e.getMessage()));
            return Optional.empty();
        }
    }

    private List<SelectorFuenteDto> selectoresDe(CodigoFuente codigo) {
        return selectorRepository.findByFuente_CodigoAndStatus(codigo, "A").stream()
                .map(s -> new SelectorFuenteDto(s.getPagina(), s.getCampo(), s.getRegla()))
                .toList();
    }

    private ResultadoLectura invocarBuscar(CodigoFuente codigo, String urlBase, String sesion,
                                           ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores) {
        try {
            ResultadoLectura r = lector.buscar(codigo, urlBase, sesion, consulta, selectores);
            return r == null ? ResultadoLectura.error("El servicio de lectura no respondió.", true) : r;
        } catch (RuntimeException e) {
            log.warn("Falló la lectura de {}: {}", codigo, recortar(e.getMessage()));
            return ResultadoLectura.error("No se pudo leer la tienda.", true);
        }
    }

    private ConsultaFuente aplicar(FuenteBusquedaConfig fuente, ConsultaBusqueda consulta,
                                   ResultadoLectura lectura) {
        String nombre = fuente.getCodigo().name();
        return switch (lectura.tipo()) {
            case NO_CONECTADO -> ConsultaFuente.de(nombre, EstadoConsultaFuente.NO_CONECTADO,
                    lectura.mensaje() != null ? lectura.mensaje() : LectorFuenteNoConectado.MENSAJE, List.of());
            case BLOQUEADA -> ConsultaFuente.de(nombre, EstadoConsultaFuente.BLOQUEADA,
                    lectura.mensaje() != null ? lectura.mensaje() : "La tienda bloqueó la consulta.", List.of());
            case VERIFICACION -> {
                String motivo = lectura.mensaje() != null ? lectura.mensaje()
                        : "La tienda pidió un código o CAPTCHA al buscar.";
                sesiones.marcarVerificacion(fuente.getCodigo(), motivo);
                avisos.avisarSiCorresponde(nombre, EstadoFuente.REQUIERE_VERIFICACION.name(), motivo);
                yield ConsultaFuente.de(nombre, EstadoConsultaFuente.REQUIERE_VERIFICACION,
                        lectura.mensaje(), List.of());
            }
            case ERROR -> {
                marcarError(fuente, lectura.mensaje());
                avisos.avisarSiCorresponde(nombre, EstadoFuente.CON_ERROR.name(), fuente.getUltimoError());
                yield ConsultaFuente.de(nombre, EstadoConsultaFuente.CON_ERROR,
                        lectura.mensaje() != null ? lectura.mensaje() : "La tienda no respondió.", List.of());
            }
            case SIN_RESULTADOS -> ConsultaFuente.de(nombre, EstadoConsultaFuente.SIN_RESULTADOS, null, List.of());
            case OK -> {
                List<ResultadoBusqueda> fichas = NormalizadorFicha.normalizar(lectura.fichas(), consulta);
                if (fichas.isEmpty()) {
                    yield ConsultaFuente.de(nombre, EstadoConsultaFuente.SIN_RESULTADOS, null, List.of());
                }
                yield ConsultaFuente.de(nombre, EstadoConsultaFuente.OK, null, fichas);
            }
        };
    }

    private void marcarError(FuenteBusquedaConfig fuente, String mensaje) {
        fuente.setEstado(EstadoFuente.CON_ERROR);
        fuente.setUltimoError(recortar(mensaje != null ? mensaje : "No se pudo leer la tienda."));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String nombre = auth != null && auth.getName() != null ? auth.getName() : "SISTEMA";
        fuente.setUserUpdate(nombre);
        fuente.setProcessUpdate(PROCESO);
        fuente.setDateUpdate(LocalDateTime.now(clock));
        fuenteRepository.save(fuente);
    }

    /**
     * 9-B (evento de error): la sesión no se pudo renovar en plena búsqueda. {@link SesionFuenteService}
     * ya dejó la fuente en CON_ERROR o REQUIERE_VERIFICACION; se lee ese estado y se avisa al Jefe.
     * Si la fuente sigue ACTIVA (p. ej. falló la lectura de la sesión) no hay nada que avisar, y el
     * aviso no se repite más de una vez al día por fuente. Nunca corta la búsqueda.
     */
    private void avisarSesionNoRenovada(CodigoFuente codigo) {
        try {
            fuenteRepository.findByCodigo(codigo).ifPresent(actual -> {
                if (actual.getEstado() != null) {
                    avisos.avisarSiCorresponde(codigo.name(), actual.getEstado().name(), actual.getUltimoError());
                }
            });
        } catch (RuntimeException e) {
            log.warn("No se pudo avisar la sesión no renovada de {}: {}", codigo, recortar(e.getMessage()));
        }
    }

    private ControlCuota cuotaDe(CodigoFuente codigo) {
        return cuotas.computeIfAbsent(codigo, c -> new ControlCuota(c.name(), clock));
    }

    /** El mismo criterio de siempre: un límite ausente o menor que 1 cuenta como 1. */
    private static int limiteDe(FuenteBusquedaConfig fuente) {
        Integer configurado = fuente.getLimiteDiario();
        return configurado == null || configurado < 1 ? 1 : configurado;
    }

    /**
     * {@code historial_sourcing.fuente} solo admite los códigos de v3_2.sql.
     * AliExpress se persiste como ALIBABA y el código real queda en {@code consulta}.
     */
    static String codigoHistorial(CodigoFuente codigo) {
        return codigo == CodigoFuente.ALIEXPRESS ? "ALIBABA" : codigo.name();
    }

    static String consultaConFuente(CodigoFuente codigo, String texto) {
        if (codigo == CodigoFuente.ALIEXPRESS) {
            return "ALIEXPRESS | " + texto;
        }
        return texto;
    }

    private static String recortar(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= LARGO_ERROR ? s : s.substring(0, LARGO_ERROR);
    }
}
