package com.genlogs.app.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.dto.ConsultaDocumentoResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.ConsultaDocumento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.repository.ConsultaDocumentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.TerceroRepository;

/**
 * Autocompletado de RUC/DNI contra la API de Factiliza (RF-08: "Integrar
 * autocompletado de datos de RUC/DNI mediante Facilita APIs al registrar
 * un cliente"). Antes de esto, el formulario de alta de cliente/proveedor
 * no tenía ninguna fuente externa: el usuario escribía razón social y
 * dirección a mano.
 *
 * Orden de búsqueda, para no gastar consultas pagadas de Factiliza:
 * 1. Clientes y proveedores ya registrados en GENLOGS (tabla tercero).
 * 2. RUC y DNI consultados antes (tabla consulta_documento).
 * 3. Factiliza. Lo que responde se guarda en consulta_documento.
 *
 * Si FACTILIZA_API_KEY no está configurada (todavía no se gestionó la
 * cuenta), responde con datos simulados en vez de fallar, para no bloquear
 * el trabajo del frontend mientras se consigue la key real. Los datos
 * simulados no se guardan.
 */
@Service
public class FactilizaService {

    private static final Logger log = LoggerFactory.getLogger(FactilizaService.class);

    @Value("${factiliza.api-key:}")
    private String apiKey;

    @Value("${factiliza.base-url:https://api.factiliza.com/v1}")
    private String baseUrl;

    /** Días que un resultado guardado se reutiliza antes de volver a consultar Factiliza (0 = nunca vence). */
    @Value("${factiliza.cache-dias:30}")
    private int cacheDias;

    private static final String PROCESO_USUARIO = "SISTEMA";
    private static final String PROCESO_CACHE = "FACTILIZA_CACHE";

    private final ConsultaDocumentoCacheRepository cacheRepository;

    public FactilizaService(ConsultaDocumentoCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    private final DistritoRepository distritoRepository;
    private final TerceroRepository terceroRepository;
    private final ConsultaDocumentoRepository consultaDocumentoRepository;

    /** Distritos del catálogo por "DEPARTAMENTO|PROVINCIA|DISTRITO" (sin tildes); se arma una sola vez. */
    private volatile Map<String, Integer> distritosPorNombre;

    public FactilizaService(DistritoRepository distritoRepository,
                            TerceroRepository terceroRepository,
                            ConsultaDocumentoRepository consultaDocumentoRepository) {
        this.distritoRepository = distritoRepository;
        this.terceroRepository = terceroRepository;
        this.consultaDocumentoRepository = consultaDocumentoRepository;
    }

    public ConsultaDocumentoResponse consultarPorNumero(String numero) {
        if (numero == null) {
            throw new BusinessException("Debe indicar el número de documento");
        }
        String limpio = numero.trim();

        String tipo = switch (limpio.length()) {
            case 11 -> "RUC";
            case 8 -> "DNI";
            default -> throw new BusinessException(
                    "El autocompletado solo está disponible para RUC (11 dígitos) y DNI (8 dígitos). " +
                    "Para el pasaporte, ingrese los datos manualmente.");
        };
        if (!limpio.matches("\\d+")) {
            throw new BusinessException("El número de documento debe ser numérico");
        }
        // Se valida antes de consultar para no gastar consultas de Factiliza en números imposibles.
        if (tipo.equals("RUC") && !limpio.matches("^(10|20)\\d{9}$")) {
            throw new BusinessException("El RUC debe empezar con 10 o 20");
        }

        // 1. ¿Ya es cliente o proveedor?
        Optional<ConsultaDocumentoResponse> registrado = desdeTercero(tipo, limpio);
        if (registrado.isPresent()) {
            return registrado.get();
        }

        // 2. ¿Ya se consultó antes?
        Optional<ConsultaDocumento> guardado = consultaDocumentoRepository.findByNumeroDocumento(limpio);
        if (guardado.isPresent()) {
            return desdeGuardado(guardado.get());
        }

        // 3. Factiliza
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("FACTILIZA_API_KEY no configurada. Devolviendo datos simulados para {} {}", tipo, limpio);
            return simulado(limpio, tipo);
        }

        ConsultaDocumentoResponse respuesta = tipo.equals("RUC") ? consultarRuc(limpio) : consultarDni(limpio);
        guardar(respuesta);
        return respuesta;
    }

    /** Datos de un cliente o proveedor ya registrado con ese documento. */
    private Optional<ConsultaDocumentoResponse> desdeTercero(String tipo, String numero) {
        return terceroRepository.buscarRegistrado(tipo, numero).map((Tercero t) -> {
            Distrito d = t.getDistrito();
            return ConsultaDocumentoResponse.builder()
                    .numeroDocumento(numero)
                    .tipoDocumento(tipo)
                    .razonSocial(t.getRazonSocial())
                    .nombres(t.getNombres())
                    .apellidoPaterno(t.getApellidoPaterno())
                    .apellidoMaterno(t.getApellidoMaterno())
                    .direccion(t.getDireccion())
                    .ubigeo(d != null ? d.getUbigeo() : null)
                    .idDistrito(d != null ? d.getIdDistrito() : null)
                    .origen("REGISTRADO")
                    .simulado(false)
                    .build();
        });
    }

    /** Datos de una consulta anterior; suma una vez más al contador. */
    private ConsultaDocumentoResponse desdeGuardado(ConsultaDocumento c) {
        c.setVecesConsultado((c.getVecesConsultado() == null ? 1 : c.getVecesConsultado()) + 1);
        consultaDocumentoRepository.save(c);
        return ConsultaDocumentoResponse.builder()
                .numeroDocumento(c.getNumeroDocumento())
                .tipoDocumento(c.getTipoDocumento())
                .razonSocial(c.getRazonSocial())
                .nombres(c.getNombres())
                .apellidoPaterno(c.getApellidoPaterno())
                .apellidoMaterno(c.getApellidoMaterno())
                .direccion(c.getDireccion())
                .ubigeo(c.getUbigeo())
                .idDistrito(c.getIdDistrito())
                .origen("GUARDADO")
                .simulado(false)
                .build();
    }

    /** Guarda la respuesta de Factiliza. Si falla, la consulta igual responde. */
    private void guardar(ConsultaDocumentoResponse r) {
        try {
            consultaDocumentoRepository.save(ConsultaDocumento.builder()
                    .numeroDocumento(r.getNumeroDocumento())
                    .tipoDocumento(r.getTipoDocumento())
                    .razonSocial(recortar(r.getRazonSocial(), 200))
                    .nombres(recortar(r.getNombres(), 120))
                    .apellidoPaterno(recortar(r.getApellidoPaterno(), 60))
                    .apellidoMaterno(recortar(r.getApellidoMaterno(), 60))
                    .direccion(recortar(r.getDireccion(), 255))
                    .ubigeo(r.getUbigeo())
                    .idDistrito(r.getIdDistrito())
                    .fechaConsulta(LocalDateTime.now())
                    .vecesConsultado(1)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Otra persona consultó el mismo número al mismo tiempo: ya quedó guardado.
            log.info("La consulta de {} ya estaba guardada", r.getNumeroDocumento());
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar la consulta de {}: {}", r.getNumeroDocumento(), e.getMessage());
        }
    }

    private static String recortar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        String v = valor.trim();
        return v.length() > maximo ? v.substring(0, maximo) : v;
    }

    private ConsultaDocumentoResponse consultarRuc(String ruc) {
        JsonNode data = consultar("/ruc/info/" + ruc);

        String razonSocial = primero(data, "nombre_o_razon_social", "razon_social", "nombre");
        String direccion = primero(data, "direccion", "domicilio_fiscal");
        String ubigeo = ubigeoDe(data);

        return ConsultaDocumentoResponse.builder()
                .numeroDocumento(ruc)
                .tipoDocumento("RUC")
                .razonSocial(razonSocial)
                .direccion(direccion)
                .ubigeo(ubigeo)
                .idDistrito(idDistritoDe(ubigeo, data))
                .origen("FACTILIZA")
                .simulado(false)
                .build();
    }

    private ConsultaDocumentoResponse consultarDni(String dni) {
        JsonNode data = consultar("/dni/info/" + dni);

        String nombres = primero(data, "nombres");
        String apPaterno = primero(data, "apellido_paterno");
        String apMaterno = primero(data, "apellido_materno");

        String nombreCompleto = primero(data, "nombre_completo");
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            nombreCompleto = String.join(" ",
                    valorOVacio(nombres), valorOVacio(apPaterno), valorOVacio(apMaterno)).trim();
        }

        String ubigeo = ubigeoDe(data);

        return ConsultaDocumentoResponse.builder()
                .numeroDocumento(dni)
                .tipoDocumento("DNI")
                .razonSocial(nombreCompleto)
                .nombres(nombres)
                .apellidoPaterno(apPaterno)
                .apellidoMaterno(apMaterno)
                .direccion(primero(data, "direccion"))
                .ubigeo(ubigeo)
                .idDistrito(idDistritoDe(ubigeo, data))
                .origen("FACTILIZA")
                .simulado(false)
                .build();
    }

    private JsonNode consultar(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                log.error("Factiliza respondió {}: {}", response.statusCode(), response.body());
                throw new BusinessException("No se pudo consultar el documento en Factiliza");
            }

            JsonNode body = mapper.readTree(response.body());
            JsonNode data = body.has("data") ? body.get("data") : body;

            if (data == null || data.isMissingNode() || data.isNull()) {
                throw new BusinessException("Factiliza no encontró datos para ese documento");
            }
            return data;
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("La consulta a Factiliza fue interrumpida");
        } catch (Exception e) {
            log.error("Error consultando Factiliza", e);
            throw new BusinessException("No se pudo consultar el documento en Factiliza");
        }
    }

    private String primero(JsonNode data, String... campos) {
        for (String campo : campos) {
            if (data.hasNonNull(campo) && !data.get(campo).asText().isBlank()) {
                return data.get(campo).asText();
            }
        }
        return null;
    }

    /**
     * Factiliza devuelve el UBIGEO como texto ("ubigeo_sunat": "150101") o como
     * lista ("ubigeo": ["15", "1501", "150101"]). Se toma el de 6 dígitos.
     * No se usa "ubigeo_reniec": RENIEC tiene su propia codificación y no
     * coincide con el UBIGEO del catálogo de distritos.
     */
    private String ubigeoDe(JsonNode data) {
        String texto = primero(data, "ubigeo_sunat");
        if (texto != null && texto.trim().matches("\\d{6}")) {
            return texto.trim();
        }
        JsonNode lista = data.get("ubigeo");
        if (lista != null && lista.isArray()) {
            for (JsonNode n : lista) {
                if (n.asText().trim().matches("\\d{6}")) {
                    return n.asText().trim();
                }
            }
        } else if (lista != null && lista.asText().trim().matches("\\d{6}")) {
            return lista.asText().trim();
        }
        return null;
    }

    /**
     * Distrito del catálogo de GenLogs para autocompletar departamento,
     * provincia y distrito. Primero por UBIGEO; si Factiliza no lo manda o no
     * está en el catálogo, por los nombres "departamento", "provincia" y
     * "distrito" de la respuesta (sin importar tildes ni mayúsculas).
     */
    private Integer idDistritoDe(String ubigeo, JsonNode data) {
        if (ubigeo != null) {
            Optional<Integer> porUbigeo = distritoRepository.findByUbigeo(ubigeo).map(Distrito::getIdDistrito);
            if (porUbigeo.isPresent()) {
                return porUbigeo.get();
            }
        }
        if (data == null) {
            return null;
        }
        String departamento = primero(data, "departamento");
        String provincia = primero(data, "provincia");
        String distrito = primero(data, "distrito");
        if (departamento == null || provincia == null || distrito == null) {
            return null;
        }
        return indiceDistritos().get(clave(departamento, provincia, distrito));
    }

    private Integer idDistritoDe(String ubigeo) {
        return idDistritoDe(ubigeo, null);
    }

    private Map<String, Integer> indiceDistritos() {
        Map<String, Integer> indice = distritosPorNombre;
        if (indice == null) {
            indice = new HashMap<>();
            List<Distrito> todos = distritoRepository.listarConProvinciaYDepartamento();
            for (Distrito d : todos) {
                indice.putIfAbsent(clave(
                        d.getProvincia().getDepartamento().getNombreDepartamento(),
                        d.getProvincia().getNombreProvincia(),
                        d.getNombreDistrito()), d.getIdDistrito());
            }
            distritosPorNombre = indice;
        }
        return indice;
    }

    private static String clave(String departamento, String provincia, String distrito) {
        return normalizar(departamento) + "|" + normalizar(provincia) + "|" + normalizar(distrito);
    }

    private static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toUpperCase().trim().replaceAll("\\s+", " ");
    }

    private String valorOVacio(String valor) {
        return valor == null ? "" : valor;
    }

    private ConsultaDocumentoResponse simulado(String numero, String tipo) {
        String razonSocial = tipo.equals("RUC")
                ? "Empresa de Prueba S.A.C. (simulado)"
                : "Nombre Apellido de Prueba (simulado)";
        String direccion = tipo.equals("RUC") ? "Av. Simulación 123 (simulado)" : "Calle Simulación 456 (simulado)";
        // UBIGEO de Ica, para que en modo de prueba también se llenen departamento, provincia y distrito.
        String ubigeo = "110101";

        boolean esDni = tipo.equals("DNI");
        return ConsultaDocumentoResponse.builder()
                .numeroDocumento(numero)
                .tipoDocumento(tipo)
                .razonSocial(razonSocial)
                .nombres(esDni ? "Nombre" : null)
                .apellidoPaterno(esDni ? "Apellido" : null)
                .apellidoMaterno(esDni ? "De Prueba" : null)
                .direccion(direccion)
                .ubigeo(ubigeo)
                .idDistrito(idDistritoDe(ubigeo))
                .origen("SIMULADO")
                .simulado(true)
                .build();
    }
}
