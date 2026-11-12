package com.genlogs.app.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.genlogs.app.dto.PortalDespachoResponse;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Despacho;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.DespachoRepository;
import com.genlogs.app.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PortalDespachoService {

    private static final String NO_ENCONTRADO = "Despacho no encontrado";
    private static final String ACTIVO = "A";

    private final DespachoRepository despachoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<PortalDespachoResponse> listar() {
        Long idCliente = exigirCliente().getCliente().getIdCliente();
        return despachoRepository
                .findByCotizacion_Cliente_IdClienteAndStatusOrderByIdDespachoDesc(idCliente, ACTIVO)
                .stream()
                .map(PortalDespachoService::aRespuesta)
                .toList();
    }

    @Transactional(readOnly = true)
    public PortalDespachoResponse detalle(Long id) {
        return aRespuesta(visible(id, exigirCliente().getCliente().getIdCliente()));
    }

    private Despacho visible(Long id, Long idCliente) {
        Despacho despacho = despachoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(NO_ENCONTRADO));
        Long dueno = despacho.getCotizacion().getCliente() == null
                ? null
                : despacho.getCotizacion().getCliente().getIdCliente();
        if (!idCliente.equals(dueno) || !ACTIVO.equals(despacho.getStatus())) {
            throw new ResourceNotFoundException(NO_ENCONTRADO);
        }
        return despacho;
    }

    private Usuario exigirCliente() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("El acceso del portal todavía no está habilitado");
        }
        Usuario usuario = usuarioRepository.findWithClienteByNombreUsuario(auth.getName()).orElse(null);
        if (usuario == null || usuario.getCliente() == null) {
            throw new AccessDeniedException("El acceso del portal todavía no está habilitado");
        }
        return usuario;
    }

    private static PortalDespachoResponse aRespuesta(Despacho despacho) {
        return PortalDespachoResponse.builder()
                .id(despacho.getIdDespacho())
                .etapa(despacho.getEtapa())
                .etapaNombre(despacho.getEtapa().etiqueta())
                .plazoDesde(despacho.getPlazoDesde())
                .plazoHasta(despacho.getPlazoHasta())
                .numeroOrdenCompra(despacho.getOrdenCompra().getNumeroOrdenCompra())
                .build();
    }
}
