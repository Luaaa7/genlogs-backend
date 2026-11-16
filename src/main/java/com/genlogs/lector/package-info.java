/**
 * Persona 6, v3.3: P1 servicio de lectura, P2 cliente en
 * {@link com.genlogs.app.sourcing.lector.LectorRemotoClient}, P4 páginas de prueba.
 * No se registra como componente Spring: se ejecuta en un proceso independiente.
 *
 * <h2>Contrato para los otros bloques</h2>
 * <ul>
 * <li>POST /interno/sesion, /interno/buscar, /interno/ficha y /interno/url.
 * GET /interno/salud. Todas exigen Authorization: Bearer con token interno de 32+
 * caracteres; los POST exigen application/json, máximo 512 KiB.</li>
 * <li>Peticion: fuente, url, reglas, sesion opcional, maxResultados (1..50, default 8).
 * Solo /sesion acepta credenciales {usuario, clave} y reglas.login con selectores
 * usuario, clave, enviar y exito. Las reglas CSS se reciben en cada llamada.</li>
 * <li>Reglas de campos: nombre, descripcion, precio, moneda, imagen y enlace como
 * objetos {selector, atributo opcional}. Atributos permitidos: content/href/src/data-src.
 * Buscar exige contenedor y enlace. Nombre/precio son obligatorios; moneda procede
 * de una regla o codigoMoneda ISO. separadorDecimal acepta punto o coma.</li>
 * <li>listo, sinResultados, verificacion, bloqueo y sesionVencida son selectores
 * auxiliares. Sin contenedores solo se devuelve lista vacía si aparece sinResultados.
 * Una regla rota no debe interpretarse como falta de disponibilidad.</li>
 * <li>Respuesta: estado, resultados, sesion opcional. Cada resultado contiene nombre,
 * descripcion, precio BigDecimal, codigoMoneda, imagen, fuente y enlace. No aplica
 * tipo de cambio, costos de importación ni margen.</li>
 * <li>OK, VERIFICACION, BLOQUEO, SESION_VENCIDA y REGLAS_INVALIDAS son estados de
 * negocio (HTTP 200). Solicitud/URL inválida: 400, token: 401, cuerpo grande: 413,
 * formato: 415, ocupado: 503, sin respuesta: 502, tiempo agotado: 504.</li>
 * <li>P2 transforma los estados distintos de OK en LectorRemotoException con
 * getEstado(). Un reintento solo para lecturas con error transitorio: transporte,
 * timeout o HTTP 502/503/504. No repite logins, CAPTCHA ni bloqueos.</li>
 * </ul>
 *
 * <h2>Límites de responsabilidad e integración</h2>
 * Luana (L1/L2) renueva/cifra/guarda la sesión, decide estados de fuente y proporciona
 * la verificación humana. El lector devuelve VERIFICACION y cierra el contexto:
 * no resuelve CAPTCHA ni expone un navegador interactivo. La transferencia de una
 * sesión completada manualmente sigue siendo un acuerdo pendiente con L1/L2.
 * El login genérico admite un formulario de usuario y contraseña del mismo origen;
 * SSO y formularios de varias pantallas requieren un contrato adicional.
 *
 * <p>Karla (K1/K2) conserva las migraciones y el cifrado. No hay acceso directo a BD.
 * La sesión devuelve storageState de Playwright (cookies/localStorage) y validaHasta
 * conservador (hasta una hora). No se debe entregar al frontend ni registrar en logs.
 * Se permite lectura pública sin sesión; una sesión informada y vencida se rechaza.</p>
 *
 * <p>Alelhy/Mell/Nicole (A1/A2/A3/M1) construyen las URLs de cada tienda y aportan
 * reglas reales. Los fixtures P4 son sintéticos, no capturas verificadas de las tiendas.
 * nombre se mapea a ResultadoBusqueda.titulo, enlace a urlReferencia e imagen a
 * urlImagen; marca y numeroParte provienen de ConsultaBusqueda. El contrato actual
 * ResultadoBusqueda no tiene descripcion: los responsables de adaptadores/N3 deben
 * acordar su propagación antes de guardar en historial. Persona 6 no modifica ese DTO.</p>
 *
 * <p>Nicole conserva N1/N2/N3 y P3: fuentes activas, pausa, historial, captura por URL
 * y despliegue. El cliente no se conecta automáticamente a SourcingService ni a
 * CapturaUrlService. Sus errores son RuntimeException, compatibles con el aislamiento
 * por fuente ya presente en SourcingService. No se activa GENLOGS_BUSQUEDA_HABILITADA.</p>
 *
 * <p>No hay ramas remotas v33/v3.3 disponibles en la revisión del 13/11/2026;
 * compatibilidad contrastada con release/v3.0 (1564dc0) y el reparto del 12/11/2026,
 * no una garantía de integración con cambios de otros todavía no disponibles.</p>
 *
 * <h2>Ejecución y pruebas</h2>
 * <pre>
 * .\mvnw.cmd compile dependency:build-classpath -Dmdep.outputFile=target/lector-classpath.txt
 * $lectorCp = 'target/classes;' + (Get-Content target/lector-classpath.txt -Raw).Trim()
 * java -cp $lectorCp com.microsoft.playwright.CLI install chromium
 * # Definir GENLOGS_LECTOR_TOKEN en ambos procesos; no imprimir ni versionar su valor.
 * java -cp $lectorCp com.genlogs.lector.ServicioLectura
 * .\mvnw.cmd -Dlector.integration=true test
 * </pre>
 * En Linux el separador del classpath es ':'. El backend se configura directamente
 * con GENLOGS_LECTOR_URL y GENLOGS_LECTOR_TOKEN, sin cambiar application.properties.
 * El lector usa PORT=3001 y GENLOGS_LECTOR_TIMEOUT_MS=20000 por defecto; P2 usa
 * GENLOGS_LECTOR_TIMEOUT_TOTAL_MS=45000 (presupuesto conjunto para ambos intentos).
 * Mantener el presupuesto del cliente por encima de dos veces el del lector más red.
 * Playwright se excluye del jar Spring Boot: solo el proceso lector necesita Chromium.
 * El lector desactiva las descargas automáticas al crear Playwright; instalar Chromium
 * con la CLI antes de arrancar. No requiere configurar PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD
 * en la consola y nunca instala Firefox/WebKit durante una petición.
 * No se añadieron archivos de Docker/Render ni workflows por indicación del usuario.
 *
 * <p>Flujo del subproceso 9-B: recibir pedido, validar token/URL/reglas, comprobar
 * sesión, abrir contexto aislado, detectar verificación/bloqueo, extraer seis campos
 * y moneda, cerrar contexto y responder. El servidor limita el tiempo de toda la
 * llamada; P2 comparte su presupuesto entre un máximo de dos intentos de lectura.</p>
 */
package com.genlogs.lector;
