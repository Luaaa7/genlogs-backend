package com.genlogs.app.service;

import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.model.ServicioImagen;
import com.genlogs.app.repository.ServicioImagenRepository;
import com.genlogs.app.repository.ServicioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicioImagenService {

    private static final String CARPETA_IMAGENES = "genlogs/servicios/imagenes";

    private final ServicioImagenRepository servicioImagenRepository;
    private final ServicioRepository servicioRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional(readOnly = true)
    public List<ServicioImagen> listarPorServicio(Long idServicio) {
        return servicioImagenRepository.findByServicio_IdServicio(idServicio);
    }

    @Transactional(readOnly = true)
    public ServicioImagen buscarPorId(Long id) {
        return servicioImagenRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Imagen de servicio no encontrada"));
    }

    @Transactional
    public void subirImagen(Long idServicio, MultipartFile archivo, boolean esPrincipal) {
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado"));

        String url = cloudinaryService.subirImagen(archivo, CARPETA_IMAGENES);

        ServicioImagen imagen = ServicioImagen.builder()
                .servicio(servicio)
                .urlImagen(url)
                .esPrincipal(esPrincipal)
                .build();
        imagen.setStatus("A");
        imagen.setUserCreate(usuarioActual());
        imagen.setProcessCreate("SUBIR_IMAGEN_SERVICIO");
        imagen.setDateCreate(LocalDateTime.now());
        servicioImagenRepository.save(imagen);
    }

    @Transactional
    public ServicioImagen registrar(Long idServicio, ServicioImagen nueva) {
        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado"));

        nueva.setServicio(servicio);
        if (nueva.getEsPrincipal() == null) {
            nueva.setEsPrincipal(false);
        }
        nueva.setStatus("A");
        nueva.setUserCreate(usuarioActual());
        nueva.setProcessCreate("ALTA_SERVICIO_IMAGEN");
        nueva.setDateCreate(LocalDateTime.now());
        return servicioImagenRepository.save(nueva);
    }

    @Transactional
    public void eliminar(Long id) {
        ServicioImagen existente = buscarPorId(id);
        servicioImagenRepository.delete(existente);
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
