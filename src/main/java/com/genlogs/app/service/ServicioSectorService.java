package com.genlogs.app.service;

import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.exception.ResourceNotFoundException;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.Servicio;
import com.genlogs.app.model.ServicioSector;
import com.genlogs.app.model.ServicioSectorId;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.ServicioRepository;
import com.genlogs.app.repository.ServicioSectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Esta tabla es puramente una asociacion (que sectores economicos usan un
 * servicio), sin id propio. Por eso no maneja status "A"/"I": para quitar
 * la asociacion simplemente se borra la fila.
 */
@Service
@RequiredArgsConstructor
public class ServicioSectorService {

    private final ServicioSectorRepository servicioSectorRepository;
    private final ServicioRepository servicioRepository;
    private final SectorEconomicoRepository sectorEconomicoRepository;

    @Transactional(readOnly = true)
    public List<ServicioSector> listarPorServicio(Long idServicio) {
        return servicioSectorRepository.findByServicio_IdServicio(idServicio);
    }

    @Transactional
    public ServicioSector asociar(Long idServicio, Integer idSectorEconomico) {
        ServicioSectorId id = new ServicioSectorId(idServicio, idSectorEconomico);
        if (servicioSectorRepository.existsById(id)) {
            throw new BusinessException("Este servicio ya está asociado a ese sector económico");
        }

        Servicio servicio = servicioRepository.findById(idServicio)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado"));
        SectorEconomico sector = sectorEconomicoRepository.findById(idSectorEconomico)
                .orElseThrow(() -> new ResourceNotFoundException("Sector económico no encontrado"));

        ServicioSector nueva = ServicioSector.builder()
                .servicio(servicio)
                .sectorEconomico(sector)
                .build();
        nueva.setStatus("A");
        nueva.setUserCreate(usuarioActual());
        nueva.setProcessCreate("ALTA_SERVICIO_SECTOR");
        nueva.setDateCreate(LocalDateTime.now());
        return servicioSectorRepository.save(nueva);
    }

    @Transactional
    public void desasociar(Long idServicio, Integer idSectorEconomico) {
        ServicioSectorId id = new ServicioSectorId(idServicio, idSectorEconomico);
        if (!servicioSectorRepository.existsById(id)) {
            throw new ResourceNotFoundException("Esa asociación servicio-sector no existe");
        }
        servicioSectorRepository.deleteById(id);
    }

    private String usuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
