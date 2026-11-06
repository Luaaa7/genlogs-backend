package com.genlogs.app.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genlogs.app.dto.ConsultaDocumentoResponse;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.ConsultaDocumentoCache;
import com.genlogs.app.repository.ConsultaDocumentoCacheRepository;

/**
 * Autocompletado de RUC/DNI contra la API de Factiliza (RF-08: "Integrar
 * autocompletado de datos de RUC/DNI mediante Facilita APIs al registrar
 * un cliente"). Antes de esto, el formulario de alta de cliente/proveedor
 * no tenía ninguna fuente externa: el usuario escribía razón social y
 * dirección a mano.
 *
 * Si FACTILIZA_API_KEY no está configurada (todavía no se gestionó la
 * cuenta), responde con datos simulados en vez de fallar, para no bloquear
 * el trabajo del frontend mientras se consigue la key real.
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
                    "Para Carné de Extranjería y Pasaporte, ingrese los datos manualmente.");
        };
        if (!limpio.matches("\\d+")) {
            throw new BusinessException("El número de documento debe ser numérico");
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("FACTILIZA_API_KEY no configurada. Devolviendo datos simulados para {} {}", tipo, limpio);
            return simulado(limpio, tipo);
        }

        // Si ya se consultó antes y no venció, se responde desde la BD sin gastar token.
        Optional<ConsultaDocumentoCache> enCache =
                cacheRepository.findByNumeroDocumentoAndStatus(limpio, "A");
        if (enCache.isPresent() && vigente(enCache.get())) {
            log.info("{} {} respondido desde caché (sin consumir token de Factiliza)", tipo, limpio);
            return desdeCache(enCache.get());
        }

        ConsultaDocumentoResponse respuesta =
                tipo.equals("RUC") ? consultarRuc(limpio) : consultarDni(limpio);
        guardarEnCache(respuesta);
        return respuesta;
    }

    private boolean vigente(ConsultaDocumentoCache cache) {
        if (cacheDias <= 0) {
            return true; // 0 o negativo = nunca vence
        }
        return cache.getFechaConsulta() != null
                && cache.getFechaConsulta().isAfter(LocalDateTime.now().minusDays(cacheDias));
    }

    private ConsultaDocumentoResponse desdeCache(ConsultaDocumentoCache cache) {
        return ConsultaDocumentoResponse.builder()
                .numeroDocumento(cache.getNumeroDocumento())
                .tipoDocumento(cache.getTipoDocumento())
                .razonSocial(cache.getRazonSocial())
                .direccion(cache.getDireccion())
                .simulado(false)
                .build();
    }

    /**
     * Inserta o actualiza (si estaba vencido o inactivo) el resultado.
     * Un fallo al guardar no debe romper la consulta: el token ya se gastó
     * y el usuario igual recibe sus datos.
     */
    private void guardarEnCache(ConsultaDocumentoResponse r) {
        try {
            LocalDateTime ahora = LocalDateTime.now();
            ConsultaDocumentoCache cache = cacheRepository.findByNumeroDocumento(r.getNumeroDocumento())
                    .orElse(null);
            if (cache == null) {
                cache = new ConsultaDocumentoCache();
                cache.setNumeroDocumento(r.getNumeroDocumento());
                cache.setUserCreate(PROCESO_USUARIO);
                cache.setProcessCreate(PROCESO_CACHE);
                cache.setDateCreate(ahora);
            } else {
                cache.setUserUpdate(PROCESO_USUARIO);
                cache.setProcessUpdate(PROCESO_CACHE);
                cache.setDateUpdate(ahora);
            }
            cache.setTipoDocumento(r.getTipoDocumento());
            cache.setRazonSocial(r.getRazonSocial());
            cache.setDireccion(r.getDireccion());
            cache.setFechaConsulta(ahora);
            cache.setStatus("A");
            cacheRepository.save(cache);
        } catch (Exception e) {
            log.error("No se pudo guardar en caché la consulta de {}", r.getNumeroDocumento(), e);
        }
    }

    private ConsultaDocumentoResponse consultarRuc(String ruc) {
        JsonNode data = consultar("/ruc/info/" + ruc);

        String razonSocial = primero(data, "nombre_o_razon_social", "razon_social", "nombre");
        String direccion = primero(data, "direccion", "domicilio_fiscal");

        return ConsultaDocumentoResponse.builder()
                .numeroDocumento(ruc)
                .tipoDocumento("RUC")
                .razonSocial(razonSocial)
                .direccion(direccion)
                .simulado(false)
                .build();
    }

    private ConsultaDocumentoResponse consultarDni(String dni) {
        JsonNode data = consultar("/dni/info/" + dni);

        String nombres = primero(data, "nombre_completo");
        if (nombres == null || nombres.isBlank()) {
            String nombre = primero(data, "nombres");
            String apPaterno = primero(data, "apellido_paterno");
            String apMaterno = primero(data, "apellido_materno");
            nombres = String.join(" ",
                    valorOVacio(nombre), valorOVacio(apPaterno), valorOVacio(apMaterno)).trim();
        }

        return ConsultaDocumentoResponse.builder()
                .numeroDocumento(dni)
                .tipoDocumento("DNI")
                .razonSocial(nombres)
                .direccion(null)
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

    private String valorOVacio(String valor) {
        return valor == null ? "" : valor;
    }

    private ConsultaDocumentoResponse simulado(String numero, String tipo) {
        String razonSocial = tipo.equals("RUC")
                ? "Empresa de Prueba S.A.C. (simulado)"
                : "Nombre Apellido de Prueba (simulado)";
        String direccion = tipo.equals("RUC") ? "Av. Simulación 123, Lima (simulado)" : null;

        return ConsultaDocumentoResponse.builder()
                .numeroDocumento(numero)
                .tipoDocumento(tipo)
                .razonSocial(razonSocial)
                .direccion(direccion)
                .simulado(true)
                .build();
    }
}
