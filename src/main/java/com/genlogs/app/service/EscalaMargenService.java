package com.genlogs.app.service;

import com.genlogs.app.dto.EscalaMargenDto;
import com.genlogs.app.exception.BusinessException;
import com.genlogs.app.model.CategoriaServicio;
import com.genlogs.app.model.EscalaMargen;
import com.genlogs.app.repository.CategoriaServicioRepository;
import com.genlogs.app.repository.EscalaMargenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Escala de márgenes para cotizar: por rango de costo (productos) y por
 * categoría (servicios). La lee cualquier usuario con sesión para sugerir el
 * margen al cotizar; solo el ADMINISTRADOR la cambia.
 */
@Service
@RequiredArgsConstructor
public class EscalaMargenService {

    /** Si la tabla está vacía, se usa este margen para cualquier servicio. */
    private static final BigDecimal SUGERIDO_DEFECTO = new BigDecimal("35.00");
    private static final BigDecimal MINIMO_DEFECTO = new BigDecimal("25.00");

    private final EscalaMargenRepository escalaMargenRepository;
    private final CategoriaServicioRepository categoriaServicioRepository;

    @Transactional(readOnly = true)
    public EscalaMargenDto.Escala obtener() {
        List<EscalaMargen> filas = escalaMargenRepository.findAll();

        List<EscalaMargenDto.RangoProducto> productos = filas.stream()
                .filter(f -> EscalaMargen.PRODUCTO.equals(f.getTipoItem()))
                .sorted(Comparator.comparing(EscalaMargen::getCostoDesde))
                .map(f -> new EscalaMargenDto.RangoProducto(
                        f.getCostoDesde(), f.getCostoHasta(), f.getMargenSugerido(), f.getMargenMinimo()))
                .toList();

        // Margen por defecto de servicios (fila sin categoría)
        EscalaMargen defecto = filas.stream()
                .filter(f -> EscalaMargen.SERVICIO.equals(f.getTipoItem()) && f.getCategoriaServicio() == null)
                .findFirst()
                .orElse(null);
        BigDecimal sugeridoDefecto = defecto != null ? defecto.getMargenSugerido() : SUGERIDO_DEFECTO;
        BigDecimal minimoDefecto = defecto != null ? defecto.getMargenMinimo() : MINIMO_DEFECTO;

        Map<Integer, EscalaMargen> porCategoria = new HashMap<>();
        for (EscalaMargen f : filas) {
            if (EscalaMargen.SERVICIO.equals(f.getTipoItem()) && f.getCategoriaServicio() != null) {
                porCategoria.put(f.getCategoriaServicio().getIdCategoriaServicio(), f);
            }
        }

        List<EscalaMargenDto.MargenServicio> servicios = new ArrayList<>();
        servicios.add(new EscalaMargenDto.MargenServicio(null, "Cualquier otro servicio", sugeridoDefecto, minimoDefecto));

        // Se listan todas las categorías principales activas; las que aún no
        // tienen fila propia aparecen con el margen por defecto.
        categoriasPrincipales().forEach(c -> {
            EscalaMargen f = porCategoria.get(c.getIdCategoriaServicio());
            servicios.add(new EscalaMargenDto.MargenServicio(
                    c.getIdCategoriaServicio(),
                    c.getNombreCategoria(),
                    f != null ? f.getMargenSugerido() : sugeridoDefecto,
                    f != null ? f.getMargenMinimo() : minimoDefecto));
        });

        return new EscalaMargenDto.Escala(productos, servicios);
    }

    /** Reemplaza la escala completa (solo ADMINISTRADOR, ver EscalaMargenController). */
    @Transactional
    public EscalaMargenDto.Escala guardar(EscalaMargenDto.Escala escala) {
        if (escala == null || escala.productos() == null || escala.servicios() == null) {
            throw new BusinessException("Envía la escala completa de productos y servicios");
        }
        validarProductos(escala.productos());
        validarServicios(escala.servicios());

        String usuario = SecurityContextHolder.getContext().getAuthentication().getName();
        LocalDateTime ahora = LocalDateTime.now();

        Map<Integer, CategoriaServicio> categorias = new HashMap<>();
        categoriasPrincipales().forEach(c -> categorias.put(c.getIdCategoriaServicio(), c));

        List<EscalaMargen> nuevas = new ArrayList<>();
        for (EscalaMargenDto.RangoProducto r : escala.productos()) {
            nuevas.add(EscalaMargen.builder()
                    .tipoItem(EscalaMargen.PRODUCTO)
                    .costoDesde(dosDecimales(r.costoDesde()))
                    .costoHasta(r.costoHasta() != null ? dosDecimales(r.costoHasta()) : null)
                    .margenSugerido(dosDecimales(r.margenSugerido()))
                    .margenMinimo(dosDecimales(r.margenMinimo()))
                    .userUpdate(usuario)
                    .dateUpdate(ahora)
                    .build());
        }
        for (EscalaMargenDto.MargenServicio s : escala.servicios()) {
            CategoriaServicio categoria = null;
            if (s.idCategoriaServicio() != null) {
                categoria = categorias.get(s.idCategoriaServicio());
                if (categoria == null) {
                    throw new BusinessException("La categoría de servicio " + s.idCategoriaServicio()
                            + " no existe o no es una categoría principal activa");
                }
            }
            nuevas.add(EscalaMargen.builder()
                    .tipoItem(EscalaMargen.SERVICIO)
                    .categoriaServicio(categoria)
                    .margenSugerido(dosDecimales(s.margenSugerido()))
                    .margenMinimo(dosDecimales(s.margenMinimo()))
                    .userUpdate(usuario)
                    .dateUpdate(ahora)
                    .build());
        }

        escalaMargenRepository.deleteAllInBatch();
        escalaMargenRepository.saveAll(nuevas);
        return obtener();
    }

    private void validarProductos(List<EscalaMargenDto.RangoProducto> productos) {
        if (productos.isEmpty()) {
            throw new BusinessException("La escala de productos necesita al menos un rango");
        }
        List<EscalaMargenDto.RangoProducto> ordenados = productos.stream()
                .sorted(Comparator.comparing(EscalaMargenDto.RangoProducto::costoDesde))
                .toList();
        for (int i = 0; i < ordenados.size(); i++) {
            EscalaMargenDto.RangoProducto r = ordenados.get(i);
            validarPorcentajes(r.margenSugerido(), r.margenMinimo(), "el rango desde " + r.costoDesde());
            boolean ultimo = i == ordenados.size() - 1;
            if (r.costoHasta() == null && !ultimo) {
                throw new BusinessException("Solo el último rango de productos puede quedar sin \"hasta\"");
            }
            if (r.costoHasta() != null && r.costoHasta().compareTo(r.costoDesde()) <= 0) {
                throw new BusinessException("En cada rango, el costo \"hasta\" debe ser mayor que el \"desde\"");
            }
            if (!ultimo && r.costoHasta().compareTo(ordenados.get(i + 1).costoDesde()) > 0) {
                throw new BusinessException("Los rangos de costo de productos se cruzan entre sí");
            }
        }
    }

    private void validarServicios(List<EscalaMargenDto.MargenServicio> servicios) {
        long defectos = servicios.stream().filter(s -> s.idCategoriaServicio() == null).count();
        if (defectos != 1) {
            throw new BusinessException("Debe haber un único margen por defecto para servicios");
        }
        Set<Integer> vistas = new HashSet<>();
        for (EscalaMargenDto.MargenServicio s : servicios) {
            if (s.idCategoriaServicio() != null && !vistas.add(s.idCategoriaServicio())) {
                throw new BusinessException("Hay una categoría de servicio repetida en la escala");
            }
            String nombre = s.nombreCategoria() != null ? s.nombreCategoria() : "el servicio";
            validarPorcentajes(s.margenSugerido(), s.margenMinimo(), nombre);
        }
    }

    private void validarPorcentajes(BigDecimal sugerido, BigDecimal minimo, String donde) {
        if (sugerido == null || minimo == null) {
            throw new BusinessException("Falta el margen sugerido o el mínimo en " + donde);
        }
        if (minimo.compareTo(sugerido) > 0) {
            throw new BusinessException("En " + donde + ", el margen mínimo no puede ser mayor que el sugerido");
        }
    }

    private List<CategoriaServicio> categoriasPrincipales() {
        return categoriaServicioRepository.findByStatus("A").stream()
                .filter(c -> c.getCategoriaPadre() == null)
                .sorted(Comparator.comparing(CategoriaServicio::getNombreCategoria, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static BigDecimal dosDecimales(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}