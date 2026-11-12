package com.genlogs.app.support;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.genlogs.app.model.Auditable;
import com.genlogs.app.model.Cliente;
import com.genlogs.app.model.Departamento;
import com.genlogs.app.model.Distrito;
import com.genlogs.app.model.Pais;
import com.genlogs.app.model.Provincia;
import com.genlogs.app.model.Rol;
import com.genlogs.app.model.SectorEconomico;
import com.genlogs.app.model.Tercero;
import com.genlogs.app.model.TipoDocumento;
import com.genlogs.app.model.Usuario;
import com.genlogs.app.repository.ClienteRepository;
import com.genlogs.app.repository.DepartamentoRepository;
import com.genlogs.app.repository.DistritoRepository;
import com.genlogs.app.repository.PaisRepository;
import com.genlogs.app.repository.ProvinciaRepository;
import com.genlogs.app.repository.RolRepository;
import com.genlogs.app.repository.SectorEconomicoRepository;
import com.genlogs.app.repository.TerceroRepository;
import com.genlogs.app.repository.TipoDocumentoRepository;
import com.genlogs.app.repository.UsuarioRepository;

/**
 * Datos de prueba del portal: crea un cliente activo con su usuario CLIENTE y usuarios
 * internos con rol. Se usa con @Import(ClientesDePrueba.class).
 */
@TestComponent
public class ClientesDePrueba {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(5000);

    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private TerceroRepository terceroRepository;
    @Autowired private SectorEconomicoRepository sectorEconomicoRepository;
    @Autowired private PaisRepository paisRepository;
    @Autowired private DepartamentoRepository departamentoRepository;
    @Autowired private ProvinciaRepository provinciaRepository;
    @Autowired private DistritoRepository distritoRepository;
    @Autowired private TipoDocumentoRepository tipoDocumentoRepository;

    /** Cliente activo con usuario del portal; devuelve el nombre de usuario. */
    public String nuevoCliente() {
        int n = SECUENCIA.incrementAndGet();
        Pais pais = paisRepository.save(auditar(new Pais(), e -> {
            e.setCodigoIso(String.format("Z%02d", n % 100));
            e.setNombrePais("Pais " + n);
        }));
        Departamento dep = departamentoRepository.save(auditar(new Departamento(), e -> {
            e.setPais(pais);
            e.setNombreDepartamento("Dep " + n);
        }));
        Provincia prov = provinciaRepository.save(auditar(new Provincia(), e -> {
            e.setDepartamento(dep);
            e.setNombreProvincia("Prov " + n);
        }));
        Distrito dist = distritoRepository.save(auditar(new Distrito(), e -> {
            e.setProvincia(prov);
            e.setUbigeo(String.format("%06d", n));
            e.setNombreDistrito("Dist " + n);
        }));
        TipoDocumento ruc = tipoDocumentoRepository.findAll().stream()
                .filter(t -> "RUC".equals(t.getCodigoTipo())).findFirst()
                .orElseGet(() -> tipoDocumentoRepository.save(auditar(new TipoDocumento(), e -> {
                    e.setCodigoTipo("RUC");
                    e.setNombreTipo("RUC");
                    e.setLongitudDocumento((short) 11);
                    e.setSoloNumerico(true);
                })));
        SectorEconomico sector = sectorEconomicoRepository.save(auditar(new SectorEconomico(), e -> e.setNombreSector("Sector " + n)));
        Tercero tercero = terceroRepository.save(auditar(new Tercero(), e -> {
            e.setTipoDocumento(ruc);
            e.setDistrito(dist);
            e.setNumeroDocumento("2080" + String.format("%07d", n));
            e.setRazonSocial("Minera " + n);
        }));
        Cliente cliente = clienteRepository.save(auditar(new Cliente(), e -> {
            e.setTercero(tercero);
            e.setSectorEconomico(sector);
            e.setSituacion("ACTIVO");
        }));
        String nombre = "cli" + n;
        usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(rol("CLIENTE"));
            e.setCliente(cliente);
            e.setNombreUsuario(nombre);
            e.setNombres("Contacto " + n);
            e.setCorreo(nombre + "@minera.pe");
            e.setPasswordHash(passwordEncoder.encode("clave1234"));
            e.setIniciales("CP");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
        }));
        return nombre;
    }

    /** Usuario interno activo con ese rol; devuelve el nombre de usuario. */
    public String nuevoInterno(String rolNombre) {
        int n = SECUENCIA.incrementAndGet();
        String nombre = rolNombre.toLowerCase() + n;
        usuarioRepository.save(auditar(new Usuario(), e -> {
            e.setRol(rol(rolNombre));
            e.setNombreUsuario(nombre);
            e.setNombres("Interno " + n);
            e.setCorreo(nombre + "@genlogs.pe");
            e.setPasswordHash(passwordEncoder.encode("clave1234"));
            e.setIniciales("IN");
            e.setBloqueado(false);
            e.setIntentosFallidos((short) 0);
        }));
        return nombre;
    }

    public Long idUsuario(String nombreUsuario) {
        return usuarioRepository.findByNombreUsuario(nombreUsuario).orElseThrow().getIdUsuario();
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombreRolIgnoreCase(nombre)
                .orElseGet(() -> rolRepository.save(auditar(new Rol(), e -> {
                    e.setNombreRol(nombre);
                    e.setDescripcion(nombre);
                })));
    }

    public static <T extends Auditable> T auditar(T entidad, Consumer<T> completar) {
        completar.accept(entidad);
        entidad.setUserCreate("test");
        entidad.setProcessCreate("TEST");
        entidad.setDateCreate(LocalDateTime.now());
        if (entidad.getStatus() == null) {
            entidad.setStatus("A");
        }
        return entidad;
    }
}
