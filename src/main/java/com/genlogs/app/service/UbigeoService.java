package com.genlogs.app.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.repository.DepartamentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.PaisRepository;
import com.genlogs.app.repository.ProvinciaRepository;
import com.genlogs.app.util.NombreUbigeo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UbigeoService {

    private final PaisRepository paisRepository;
    private final DepartamentoRepository departamentoRepository;
    private final ProvinciaRepository provinciaRepository;
    private final DistritoRepository distritoRepository;

    /** Índices del catálogo por nombre normalizado; se arman una sola vez. */
    private volatile Indice indice;

    private record Indice(Map<String, Integer> porNombreCompleto, Map<String, List<Integer>> porDepartamentoYDistrito) {
    }

    @Transactional(readOnly = true)
    public List<Pais> listarPaises() {
        return paisRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Departamento> listarDepartamentos(Integer idPais) {
        return departamentoRepository.findByPais_IdPais(idPais);
    }

    @Transactional(readOnly = true)
    public List<Provincia> listarProvincias(Integer idDepartamento) {
        return provinciaRepository.findByDepartamento_IdDepartamento(idDepartamento);
    }

    @Transactional(readOnly = true)
    public List<Distrito> listarDistritos(Integer idProvincia) {
        return distritoRepository.findByProvincia_IdProvincia(idProvincia);
    }

    /**
     * D-15: busca en el catálogo del INEI el distrito que Factiliza devuelve como
     * texto. Compara sin tildes ni mayúsculas y con las equivalencias de
     * {@link NombreUbigeo} (NASCA = NAZCA). Si la provincia viene escrita de otra
     * forma, se acepta el distrito cuando es el único con ese nombre en el
     * departamento. Sin coincidencia segura devuelve vacío y el usuario lo elige.
     */
    @Transactional(readOnly = true)
    public Optional<Integer> buscarIdDistrito(String departamento, String provincia, String distrito) {
        if (vacio(departamento) || vacio(distrito)) {
            return Optional.empty();
        }
        Indice i = indice();
        if (!vacio(provincia)) {
            Integer exacto = i.porNombreCompleto().get(NombreUbigeo.clave(departamento, provincia, distrito));
            if (exacto != null) {
                return Optional.of(exacto);
            }
        }
        List<Integer> candidatos = i.porDepartamentoYDistrito()
                .getOrDefault(NombreUbigeo.clave(departamento) + "|" + NombreUbigeo.clave(distrito), List.of());
        return candidatos.size() == 1 ? Optional.of(candidatos.get(0)) : Optional.empty();
    }

    /**
     * D-15 / RF-81: país y ubicación del tercero. El distrito del catálogo es
     * obligatorio solo si el país es Perú; si no, se guardan región, ciudad y
     * código postal en texto libre y el distrito queda vacío. Sin país se
     * asume Perú (todos los terceros actuales lo son).
     */
    @Transactional(readOnly = true)
    public void aplicarUbicacion(Tercero tercero, Integer idPais, Integer idDistrito,
                                 String regionExtranjero, String ciudadExtranjero, String codigoPostalExtranjero) {
        Pais pais = idPais != null
                ? paisRepository.findById(idPais).orElseThrow(() -> new ResourceNotFoundException("País no existe"))
                : peru().orElse(null);
        boolean esPeru = pais == null || esPeru(pais);

        tercero.setPais(pais);
        if (esPeru) {
            if (idDistrito == null) {
                throw new BusinessException("Debe seleccionar un distrito: es obligatorio cuando el país es Perú");
            }
            tercero.setDistrito(distritoRepository.findById(idDistrito)
                    .orElseThrow(() -> new ResourceNotFoundException("Distrito no existe")));
            tercero.setRegionExtranjero(null);
            tercero.setCiudadExtranjero(null);
            tercero.setCodigoPostalExtranjero(null);
        } else {
            tercero.setDistrito(null);
            tercero.setRegionExtranjero(texto(regionExtranjero, 100));
            tercero.setCiudadExtranjero(texto(ciudadExtranjero, 100));
            tercero.setCodigoPostalExtranjero(texto(codigoPostalExtranjero, 20));
        }
    }

    private Optional<Pais> peru() {
        return paisRepository.findByCodigoIso("PE").or(() -> paisRepository.findByCodigoIso("PER"));
    }

    private static boolean esPeru(Pais pais) {
        String iso = pais.getCodigoIso() == null ? "" : pais.getCodigoIso().trim().toUpperCase();
        return iso.equals("PE") || iso.equals("PER");
    }

    private Indice indice() {
        Indice actual = indice;
        if (actual == null) {
            Map<String, Integer> completo = new HashMap<>();
            Map<String, List<Integer>> porDistrito = new HashMap<>();
            for (Distrito d : distritoRepository.listarConProvinciaYDepartamento()) {
                String dep = d.getProvincia().getDepartamento().getNombreDepartamento();
                completo.putIfAbsent(NombreUbigeo.clave(dep, d.getProvincia().getNombreProvincia(), d.getNombreDistrito()),
                        d.getIdDistrito());
                porDistrito.computeIfAbsent(NombreUbigeo.clave(dep) + "|" + NombreUbigeo.clave(d.getNombreDistrito()),
                        k -> new ArrayList<>()).add(d.getIdDistrito());
            }
            actual = new Indice(completo, porDistrito);
            indice = actual;
        }
        return actual;
    }

    /** Las pruebas crean distritos nuevos: con esto el siguiente uso vuelve a leer el catálogo. */
    public void olvidarIndice() {
        indice = null;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String texto(String valor, int maximo) {
        if (vacio(valor)) {
            return null;
        }
        String v = valor.trim();
        return v.length() > maximo ? v.substring(0, maximo) : v;
    }
}
