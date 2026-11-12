package com.genlogs.app.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.genlogs.app.model.Notificacion;
import com.genlogs.app.model.Usuario;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByIdUsuarioOrderByFechaCreacionDesc(Long idUsuario, Pageable pageable);

    long countByIdUsuarioAndLeidaFalse(Long idUsuario);

    Optional<Notificacion> findByIdNotificacionAndIdUsuario(Long idNotificacion, Long idUsuario);

    /** true si esa factura ya se avisó en el día (desde inclusive, hasta exclusive). */
    boolean existsByTipoAndEnlaceAndFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
            String tipo, String enlace, LocalDateTime desde, LocalDateTime hasta);

    /**
     * Usuarios activos y no bloqueados de un rol. Va aqui (solo lectura) para no
     * tocar UsuarioRepository, que es de BE-A.
     */
    @Query("""
            SELECT u FROM Usuario u
             WHERE u.rol.nombreRol = :nombreRol
               AND u.status = 'A'
               AND u.bloqueado = false
            """)
    List<Usuario> usuariosActivosDelRol(@Param("nombreRol") String nombreRol);
}