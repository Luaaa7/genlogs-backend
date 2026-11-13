package com.genlogs.app.fuentes.config;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Único punto para guardar y leer las credenciales de una tienda (K2). Cifra al
 * guardar y descifra al leer; el valor en claro nunca se guarda ni se devuelve por
 * la API (L5 solo escribe y pregunta si hay credencial). Lo usan el servicio de
 * sesión (L1) y los endpoints de configuración (L5).
 */
@Service
@RequiredArgsConstructor
public class CredencialesFuenteService {

    private final FuenteBusquedaConfigRepository fuenteRepository;
    private final FuenteCredencialRepository credencialRepository;
    private final CifradoFuentes cifrado;

    /** Crea o reemplaza el usuario, la clave o la sesión de una tienda. */
    @Transactional
    public void guardar(CodigoFuente codigo, TipoCredencial tipo, String valor, Long idUsuario, String usuario) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Indica el valor de " + tipo.name().toLowerCase());
        }
        FuenteBusquedaConfig fuente = fuenteRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la fuente " + codigo));
        FuenteCredencial credencial = credencialRepository.findByFuente_CodigoAndTipo(codigo, tipo)
                .orElseGet(() -> {
                    FuenteCredencial nueva = new FuenteCredencial();
                    nueva.setFuente(fuente);
                    nueva.setTipo(tipo);
                    nueva.setUserCreate(usuario);
                    nueva.setProcessCreate("CREDENCIAL_FUENTE");
                    return nueva;
                });
        credencial.setValorCifrado(cifrado.cifrar(valor, contexto(codigo, tipo)));
        credencial.setEditadoPor(idUsuario);
        if (credencial.getIdCredencial() != null) {
            credencial.setUserUpdate(usuario);
            credencial.setProcessUpdate("CREDENCIAL_FUENTE");
            credencial.setDateUpdate(LocalDateTime.now());
        }
        credencialRepository.save(credencial);
    }

    /** El valor en claro, solo para enviarlo al servicio de lectura. Vacío si no se cargó. */
    @Transactional(readOnly = true)
    public Optional<String> leer(CodigoFuente codigo, TipoCredencial tipo) {
        return credencialRepository.findByFuente_CodigoAndTipo(codigo, tipo)
                .map(c -> cifrado.descifrar(c.getValorCifrado(), contexto(codigo, tipo)));
    }

    /** Si la tienda tiene ese dato cargado, sin revelarlo (para la pantalla de fuentes). */
    @Transactional(readOnly = true)
    public boolean tiene(CodigoFuente codigo, TipoCredencial tipo) {
        return credencialRepository.existsByFuente_CodigoAndTipo(codigo, tipo);
    }

    /** Liga el cifrado a su fila: el valor de EBAY:CLAVE no se descifra como AMAZON:CLAVE. */
    static String contexto(CodigoFuente codigo, TipoCredencial tipo) {
        return codigo.name() + ":" + tipo.name();
    }
}
