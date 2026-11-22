package com.genlogs.app.sourcing;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HistorialSourcingRepository extends JpaRepository<HistorialSourcing, Long> {

    /** Reutilización: búsquedas del mismo N.º de parte hechas después de "desde" (24 h). */
    @EntityGraph(attributePaths = {"moneda", "solicitudItem", "marcadaUtilPor"})
    List<HistorialSourcing> findByNumeroParteIgnoreCaseAndMetodoNotAndCreadoEnAfterOrderByCreadoEnDesc(
            String numeroParte, MetodoBusqueda metodoExcluido, LocalDateTime desde);

    /** Referencias de un ítem de solicitud (panel de búsqueda de FE-D). */
    @EntityGraph(attributePaths = {"moneda", "solicitudItem", "marcadaUtilPor"})
    List<HistorialSourcing> findBySolicitudItem_IdSolicitudItemOrderByCreadoEnDesc(Long idSolicitudItem);

    /** Todo el historial de un N.º de parte, para ver qué se encontró antes. */
    @EntityGraph(attributePaths = {"moneda", "solicitudItem", "marcadaUtilPor"})
    List<HistorialSourcing> findByNumeroParteIgnoreCaseOrderByCreadoEnDesc(String numeroParte);

    /**
     * Buscador de la cotización: título, N.º de parte y marca, ya sin acentos y en minúsculas.
     * {@code patron} trae los comodines escapados. {@code soloElegibles} deja fuera DESCARTADO.
     */
    @EntityGraph(attributePaths = {"moneda", "solicitudItem", "marcadaUtilPor"})
    @Query("""
            SELECT h FROM HistorialSourcing h
            WHERE (:soloElegibles = false OR h.estado <> com.genlogs.app.sourcing.EstadoSourcing.DESCARTADO)
              AND (
                CAST(FUNCTION('translate', LOWER(COALESCE(h.titulo, '')), :desde, :hacia) AS string) LIKE :patron ESCAPE '\\'
                OR CAST(FUNCTION('translate', LOWER(COALESCE(h.numeroParte, '')), :desde, :hacia) AS string) LIKE :patron ESCAPE '\\'
                OR CAST(FUNCTION('translate', LOWER(COALESCE(h.marcaTexto, '')), :desde, :hacia) AS string) LIKE :patron ESCAPE '\\'
              )
            ORDER BY h.creadoEn DESC
            """)
    List<HistorialSourcing> buscarPorTexto(
            @Param("patron") String patron,
            @Param("desde") String desde,
            @Param("hacia") String hacia,
            @Param("soloElegibles") boolean soloElegibles,
            Pageable pageable);

    /** Referencia de la que salió un producto de la vitrina (para conocer su costo puesto en Lima). */
    Optional<HistorialSourcing> findFirstByIdProductoOrderByCreadoEnDesc(Long idProducto);
}
