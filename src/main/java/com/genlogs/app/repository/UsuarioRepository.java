package com.genlogs.app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import com.genlogs.app.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByNombreUsuario(String nombreUsuario);

    @EntityGraph(attributePaths = "cliente")
    Optional<Usuario> findWithClienteByNombreUsuario(String nombreUsuario);

    /** Ingreso del cliente al portal: se busca por correo (único) con su rol. */
    @EntityGraph(attributePaths = "rol")
    Optional<Usuario> findWithRolByCorreoIgnoreCase(String correo);

    /** Ingreso con Google: se busca por el identificador de la cuenta de Google con su rol. */
    @EntityGraph(attributePaths = {"rol", "cliente"})
    Optional<Usuario> findWithRolByGoogleSub(String googleSub);

    boolean existsByCorreoIgnoreCase(String correo);

    /** Accesos del portal pendientes de activación (status 'I' y process_create de registro pendiente). */
    List<Usuario> findByStatusAndProcessCreate(String status, String processCreate);

    /**
     * Accesos pendientes del portal: los que se registraron con una empresa que ya es cliente
     * (process_create, sin cambios después) y las cuentas de Google que completaron sus datos con esa
     * empresa (process_update). Igual que PortalClienteService.esAccesoPendiente.
     */
    @Query("SELECT u FROM Usuario u WHERE u.status = 'I' AND (u.processUpdate = :proceso"
            + " OR (u.processCreate = :proceso AND u.processUpdate IS NULL)) ORDER BY u.idUsuario")
    List<Usuario> buscarPendientesPortal(@Param("proceso") String proceso);

    /** Usuarios del portal de una empresa (para avisarles si alguien pide acceso con su RUC). */
    List<Usuario> findByCliente_IdClienteAndStatus(Long idCliente, String status);

    Optional<Usuario> findByCorreoIgnoreCaseAndStatus(String correo, String status);

    boolean existsByNombreUsuario(String nombreUsuario);

    boolean existsByCorreo(String correo);

    List<Usuario> findByStatus(String status);

    // Nuevos métodos indicados en el paso 2[cite: 1]
    boolean existsByCorreoIgnoreCaseAndIdUsuarioNot(String correo, Long idUsuario);
    
    Optional<Usuario> findByIdUsuarioAndStatus(Long idUsuario, String status);
}