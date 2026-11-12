package com.genlogs.app.config;

import java.util.Arrays;
import java.util.stream.Stream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import com.genlogs.app.security.CustomUserDetailsService;
import com.genlogs.app.security.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Roles del sistema interno. El portal de clientes (CLIENTE) no entra aquí:
     * cualquier ruta que no sea pública, de autenticación o del portal exige uno de estos.
     * Los nombres coinciden con {@code rol.nombre_rol}; el prefijo ROLE_ lo agrega Spring.
     */
    private static final String[] ROLES_INTERNOS = {
            "ADMINISTRADOR",
            "VENDEDOR",
            "JEFE_ADMINISTRATIVO",
            "GERENTE_GENERAL",
            "CONTADOR_GENERAL"
    };

    /** Internos + CLIENTE: rutas que usan el portal y el personal (Paso 0). */
    private static final String[] ROLES_INTERNOS_Y_CLIENTE =
            Stream.concat(Arrays.stream(ROLES_INTERNOS), Stream.of("CLIENTE")).toArray(String[]::new);

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final PasswordEncoder passwordEncoder;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CorsConfigurationSource corsConfigurationSource,
            PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsConfigurationSource = corsConfigurationSource;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Sin sesión / token inválido -> 401 (el frontend lo usa para mandar al login).
            // Sin permiso para la ruta -> 403 en JSON ("message" es lo que lee el frontend).
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"status\":401,\"error\":\"Tu sesión expiró o no has iniciado sesión\","
                            + "\"message\":\"Tu sesión expiró o no has iniciado sesión. Vuelve a ingresar.\"}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"status\":403,\"error\":\"No tienes permiso para esta acción\","
                            + "\"message\":\"No tienes permiso para esta acción\"}");
                }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/error").permitAll()
                // Health check público para monitoreo de Render
                .requestMatchers("/", "/health", "/api/health", "/api/health/db").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                // El alta del portal no lleva token. Va antes que /api/portal/** para no pedir rol CLIENTE.
                .requestMatchers(HttpMethod.POST, "/api/portal/registro").permitAll()
                // Público SOLO para consultar (catálogo web y mapa). Crear/editar/borrar exige rol interno.
                .requestMatchers(HttpMethod.GET, "/api/catalogo/**", "/api/catalogos/**", "/api/empresas-mineras/**").permitAll()
                // ---- Paso 0 (v3.2): reglas de URL nuevas. Karla aprueba esta parte. ----
                // Endpoint unico de solicitudes: portal, landing (con registro, D-16) y vitrina.
                .requestMatchers(HttpMethod.POST, "/api/solicitudes", "/api/solicitudes/fotos").hasAnyRole(ROLES_INTERNOS_Y_CLIENTE)
                // Vitrina (Fase 3, apagada): catalogo de la landing sin precio, repuestos del portal con precio.
                .requestMatchers(HttpMethod.GET, "/api/public/catalogo", "/api/public/catalogo/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/portal/repuestos", "/api/portal/repuestos/**").hasRole("CLIENTE")
                // Solo personal interno: campanita (D-21, D-25), feriados (D-18), parametros (RF-87),
                // tipo de cambio (RF-86) y busqueda (Fase 2). Quien edita lo decide el @PreAuthorize de cada bloque.
                .requestMatchers("/api/notificaciones/**", "/api/feriados/**", "/api/parametros/**",
                        "/api/tipo-cambio/**", "/api/sourcing/**").hasAnyRole(ROLES_INTERNOS)
                // ---- fin Paso 0 ----
                // El resto del portal es exclusivo de CLIENTE. Un rol interno recibe 403.
                .requestMatchers("/api/portal/**").hasRole("CLIENTE")
                // Todo lo interno (aunque el método no tenga @PreAuthorize) rechaza a CLIENTE.
                .anyRequest().hasAnyRole(ROLES_INTERNOS)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}