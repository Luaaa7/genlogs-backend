package com.genlogs.app.sourcing;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HistorialSourcingRepository extends JpaRepository<HistorialSourcing, Long> {

    /** Reutilización: búsquedas del mismo N.º de parte hechas después de "desde" (24 h). */
    @EntityGraph(attributePaths = {"moneda", "solicitudItem"})
    List<HistorialSourcing> findByNumeroParteIgnoreCaseAndMetodoNotAndCreadoEnAfterOrderByCreadoEnDesc(
            String numeroParte, MetodoBusqueda metodoExcluido, LocalDateTime desde);

    /** Referencias de un ítem de solicitud (panel de búsqueda de FE-D). */
    @EntityGraph(attributePaths = {"moneda", "solicitudItem"})
    List<HistorialSourcing> findBySolicitudItem_IdSolicitudItemOrderByCreadoEnDesc(Long idSolicitudItem);

    /** Todo el historial de un N.º de parte, para ver qué se encontró antes. */
    @EntityGraph(attributePaths = {"moneda", "solicitudItem"})
    List<HistorialSourcing> findByNumeroParteIgnoreCaseOrderByCreadoEnDesc(String numeroParte);

    /** Referencia de la que salió un producto de la vitrina (para conocer su costo puesto en Lima). */
    Optional<HistorialSourcing> findFirstByIdProductoOrderByCreadoEnDesc(Long idProducto);
}
