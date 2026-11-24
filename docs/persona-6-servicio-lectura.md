# Servicio de lectura de Persona 6

GENLOGS versión 3.3

Este documento presenta el subproceso **Leer una página** que Persona 6 aporta a la búsqueda de repuestos. Describe el flujo, las entradas y salidas del lector, su relación con el cliente Java y las pruebas necesarias para que Alelhy revise la entrega.

**Responsable:** Persona 6. **Revisión:** Alelhy. **QA manual asignado:** Karla. **Fecha:** 13 de noviembre de 2026, actualizado el 23 de noviembre de 2026. **Estado:** listo para la aprobación del BPMN; el código ya está fusionado en `release/v3.3`. Este documento no constituye por sí mismo aprobación del equipo ni autorización de despliegue.

## Alcance de la entrega

| Tarea | Entrega de Persona 6 | Puntos |
|---|---|---|
| P1 | Servicio separado con Playwright y Chromium; endpoints internos, token, reglas por llamada y respuesta ante verificación. | 5 |
| P2 | `LectorRemotoClient` con presupuesto de tiempo, un reintento de lectura y errores tipificados. | 2 |
| P4 | Diez páginas HTML de prueba: búsqueda y ficha de cada una de las cinco fuentes. | 1 |

Las fuentes son Mercado Libre, eBay, Amazon, AliExpress y Alibaba. Persona 6 entrega el motor genérico; las URLs de búsqueda, los selectores reales y los adaptadores de cada tienda corresponden a A1, A2, A3 y M1. El lector recibe esos datos y no consulta tablas.

## Base de revisión

El modelo se contrastó con el reparto del 12/11/2026 y con el código fusionado en `release/v3.3`. Los commits `7bda1d9` (Implementación) y `0adf919` (Testers) entraron con el merge `51073de` del 15/11/2026. Después se sumaron el rango de precio de Alibaba (Karla, PR #112), el `Dockerfile.lector` (Nicole, PR #118) y el ingreso de tienda en dos pantallas D-42 (Daniel, commit `957f288`). Estos diagramas y este documento se suben con la rama `p6/v33-bpmn-leer-pagina` a `docs/bpmn/` del backend.

Las decisiones D-35 a D-38 y la revisión del BPMN requieren constancia de aprobación del equipo. El criterio Docker de P1 queda cubierto por `Dockerfile.lector` (P3, Nicole): instala Chromium al construir y el lector no descarga navegadores al ejecutarse.

## BPMN principal de lectura

Archivo editable: [`bpmn/bpmn-9-b-1-leer-pagina.bpmn`](bpmn/bpmn-9-b-1-leer-pagina.bpmn). Un pool GENLOGS y un carril Sistema. La actividad con borde grueso y signo más es una actividad llamada que remite al detalle del siguiente diagrama.

![Figura 1. Validación del pedido, llamada al lector y resultados posibles.](bpmn/bpmn-9-b-1-leer-pagina.svg)

*Figura 1. Validación del pedido, llamada al lector y resultados posibles.*

El temporizador de borde interrumpe la espera del servidor a los 20 segundos por defecto, según `GENLOGS_LECTOR_TIMEOUT_MS`. El backend recibe `TIEMPO_AGOTADO`; la liberación del contexto se completa al salir del trabajo. Una sesión ausente permite lectura pública; una sesión informada pero vencida se rechaza.

El pedido y la respuesta se representan como objetos de datos, no como almacenes. No se dibujan tablas porque Persona 6 no lee ni escribe directamente en la BD.

## Detalle de lectura con Chromium

Archivo editable: [`bpmn/bpmn-9-b-1a-lectura-chromium.bpmn`](bpmn/bpmn-9-b-1a-lectura-chromium.bpmn). Expande la actividad Leer con Chromium (proceso `P6_LecturaChromium`). Las tiendas no son carriles: acceder a ellas es una tarea del Sistema.

![Figura 2. Navegación, detección de impedimentos, extracción y cierre del contexto.](bpmn/bpmn-9-b-1a-lectura-chromium.svg)

*Figura 2. Navegación, detección de impedimentos, extracción y cierre del contexto.*

Los fines con símbolo de error propagan un estado al evento de borde del diagrama principal. Son una representación descriptiva del manejo de estados Java, no excepciones ejecutadas por un motor BPMN. Ante CAPTCHA o código se devuelve `VERIFICACION` sin resolverlo.

## Contrato del servicio

El servicio se ejecuta en un proceso Java independiente. Todas las rutas exigen `Authorization: Bearer` con el token interno; los POST reciben JSON de hasta 512 KiB. No existe una pantalla propia de Persona 6.

| Método y ruta | Finalidad |
|---|---|
| `POST /interno/sesion` | Enviar credenciales y selectores del formulario; devolver sesión solo tras comprobar éxito. |
| `POST /interno/buscar` | Leer una URL de búsqueda y devolver hasta `maxResultados`, 8 por defecto. |
| `POST /interno/ficha` | Leer una ficha de producto con las reglas recibidas. |
| `POST /interno/url` | Leer una URL de producto; usa el mismo motor que ficha. |
| `GET /interno/salud` | Comprobar que el servicio responde y acepta el token. |

### Entradas y resultados

El backend envía `fuente`, `url`, `reglas`, `sesion` opcional y `maxResultados`. Cada regla de campo contiene un selector CSS y, cuando corresponde, un atributo como `content`, `href`, `src` o `data-src`. La búsqueda exige un contenedor y un enlace por resultado.

Los seis campos de salida son **nombre, descripción, precio, imagen, fuente y enlace**. Se agrega `codigoMoneda` para interpretar el precio. No se convierte a soles, no se aplica margen y no se guarda el resultado en el historial. Un rango de precio se rechaza, salvo que las reglas traigan `precioRango="mayor"` (solo lo envía el lector de Alibaba): entonces «US$12.50-15.00» vale el mayor, porque el menor suele exigir cientos de unidades. Una lista vacía solo es válida cuando la página muestra la señal configurada de sin resultados.

### Inicio de sesión y cliente Java

El inicio de sesión de P1 acepta un formulario de usuario y contraseña del mismo origen. El flujo es abrir página, comprobar impedimentos, llenar el formulario, enviar, esperar la señal de éxito y devolver cookies y localStorage de la fuente junto con `validaHasta`. Con D-42, si las reglas de login traen el selector opcional `continuar`, el lector llena primero el usuario, pulsa CONTINUAR, comprueba que la página siga en HTTPS y en el mismo dominio, y espera el campo de la clave; el ingreso de una sola pantalla no cambia. No persiste credenciales ni sesiones. Esta operación apoya a L1 y no reemplaza el proceso 9-A de Luana.

P2 permite como máximo dos intentos en lecturas: uno inicial y un reintento por transporte, timeout o HTTP 502/503/504. El presupuesto total es 45 segundos por defecto (`GENLOGS_LECTOR_TIMEOUT_TOTAL_MS`). No reintenta login, CAPTCHA, bloqueo, sesión vencida ni errores de reglas. El llamador recibe `LectorRemotoException` con `getEstado()` cuando no hay éxito.

## Estados y protección de datos

| Estado | HTTP | Significado y siguiente acción |
|---|---|---|
| `OK` | 200 | Datos disponibles; el llamador decide cómo utilizarlos. |
| `VERIFICACION` | 200 | La tienda pide CAPTCHA o código. Derivar a L1/L2; no resolver ni reintentar. |
| `BLOQUEO` | 200 | La tienda impide la lectura. Informar al llamador sin intentar evadirla. |
| `SESION_VENCIDA` | 200 | Sesión expirada o señal de login. El backend debe renovarla. |
| `REGLAS_INVALIDAS` | 200 | Las reglas no permiten una captura válida; revisar el adaptador. |
| `SOLICITUD_INVALIDA` / `URL_NO_PERMITIDA` | 400 | Rechazar el pedido antes de una navegación no autorizada. |
| `NO_AUTORIZADO` | 401 | Token interno ausente o incorrecto. |
| `OCUPADO` | 503 | Se alcanzó el límite de dos trabajos simultáneos. |
| `SIN_RESPUESTA` | 502 | Fallo de lectura o transporte. P2 puede reintentar una vez. |
| `TIEMPO_AGOTADO` | 504 | Se agotó el tiempo máximo. P2 respeta su presupuesto total. |

También se rechazan rutas inexistentes con HTTP 404, cuerpos grandes con 413 y contenido distinto de JSON con 415. Los errores no incluyen el mensaje original de Playwright, URLs con secretos ni cuerpos de respuesta de la tienda.

El lector restringe las fuentes a los dominios permitidos, HTTPS y puerto 443. El proxy valida todos los destinos DNS y conecta a la IP validada; aplica el control a redirecciones y subrecursos. Se bloquean direcciones privadas, reservadas y de metadata, además de WebSockets y service workers.

Chromium se instala antes de arrancar. El lector desactiva las descargas automáticas de navegadores durante las peticiones. El contexto se cierra al finalizar y no se guarda el estado en archivos o tablas. El backend es responsable de cifrar y custodiar la sesión que reciba.

## Trazabilidad de las tareas BPMN

Cada fila corresponde a una tarea o actividad llamada de los diagramas. En la columna Endpoint, *Lecturas* significa `POST /interno/buscar`, `/interno/ficha` y `/interno/url`. P6 no tiene pantalla y no accede directamente a tablas.

| Tarea BPMN | Rol | Pantalla | Endpoint | Tablas | RF de apoyo | Prueba |
|---|---|---|---|---|---|---|
| `P6_Validar` Validar token URL y reglas | Sistema | No aplica | Lecturas | Ninguna | 90 y 92 | T3, T4, T7 |
| `P6_Leer` Leer con Chromium | Sistema | No aplica | Lecturas | Ninguna | 90 y 92 | T1 |
| `P6_ResponderDatos` Responder datos al backend | Sistema | No aplica | Lecturas | Ninguna | 90 y 92 | T1, T4 |
| `P6_ResponderTiempo` Cancelar y responder tiempo | Sistema | No aplica | Lecturas | Ninguna | 90 y 92 | T4, T5 |
| `P6_ResponderEstado` Responder estado de consulta | Sistema | No aplica | Lecturas | Ninguna | 90 y 92 | T1, T4, T5 |
| `D_Abrir` Abrir página aislada | Sistema | No aplica | Lecturas | Ninguna | 90 y 92 | T1, T3, T6 |
| `D_Esperar` Esperar y comprobar datos | Sistema | No aplica | Lecturas | Ninguna | 90 y 92 | T1 |
| `D_Extraer` Extraer seis campos y moneda | Sistema | No aplica | Lecturas | Ninguna | 90 y 92 | T1, T2 |
| `D_Cerrar` Cerrar contexto | Sistema | No aplica | Lecturas | Ninguna | 90 y 92 | T1 + revisión del cierre |

RF-90 corresponde a N1 y RF-92 a N2 en el reparto; P6 les presta soporte técnico. No se atribuyen nuevos RF propios a P6 ni se inventa el detalle de RF-88/RF-91, que el reparto no desarrolla. El endpoint de sesión apoya RF-89 y se verifica con T1 y T4, sin ampliar el diagrama 9-B a las tareas de Luana.

Los nombres terminan en «(v3.3)» para identificar la ampliación de la versión. La implementación y las pruebas ya están fusionadas en `release/v3.3`; los BPMN documentan ese código, pero no certifican su despliegue ni la prueba de diez pedidos.

## Código y evidencia de pruebas

| Referencia | Código o evidencia verificable |
|---|---|
| `P6_Validar` | `ServicioLectura.handle`; `ValidacionLectura.pedido`, `url` y `validarSesion`. |
| `P6_Leer` / `D_Abrir` | `LectorChromium.ejecutar`; `ProxySalidaSegura`; `crearPlaywright` desactiva descargas. |
| `D_Esperar` / `D_Extraer` | `LectorChromium.esperar`, `detectar` y `extraer`. |
| `D_Cerrar` | Cierre con try-with-resources en `LectorChromium.ejecutar`. Revisión de código; no existe una aserción independiente de liberación en todas las rutas. |
| `P6_ResponderDatos` / `Estado` / `Tiempo` | `ServicioLectura.handle`, `status` y `send`; el timeout cancela el `Future`. |
| P2 | `LectorRemotoClient.ejecutar`; `LectorRemotoException` y `ContratoLector`. |

| Prueba | Clase y cobertura |
|---|---|
| T1 · 4 casos | `LectorChromiumIntegracionTest`: cliente, servicio y Chromium con cinco fuentes; CAPTCHA/código/bloqueo/sesión vencida; login de una y de dos pantallas (D-42) y ausencia de contraseña en la sesión devuelta. |
| T2 · 11 casos | `ExtraccionLecturaTest`: búsqueda y ficha por fuente; un rango sin regla se rechaza; con `precioRango="mayor"` vale el mayor y solo se acepta ese valor; señal explícita de vacío; límite y enlaces; reglas y credenciales. |
| T3 · 4 casos | `SeguridadLecturaTest`: IP privadas/reservadas, URL de fuente, todos los registros DNS y sesiones vencidas/de otra fuente. |
| T4 · 5 casos | `ServicioLecturaTest`: token, cuerpo, cuatro endpoints, límite de tiempo y errores sin secretos. |
| T5 · 11 casos | `LectorRemotoClientTest`: contrato, reintento, estados, redirecciones, tiempo total, respuestas inválidas y configuración opcional. |
| T6 · 1 caso | `ArranquePlaywrightIntegracionTest`: JVM nueva y caché vacía sin descarga automática de navegadores. |
| T7 · 3 casos | `ValidacionLecturaTest` (D-42): el login de una pantalla sigue siendo válido; con el selector opcional `continuar` se aceptan dos pantallas; un selector ajeno sigue siendo inválido. |

Ejecución local del 23/11/2026 sobre `release/v3.3`, con las siete clases: **39 pruebas, 0 fallos, 0 errores y 5 omitidas**. Las 5 omitidas son las de Chromium real (T1 y T6), que solo corren con `-Dlector.integration=true` en un equipo con Chromium; su última corrida completa fue la del 13/11/2026 (33 pruebas, 0 fallos) y deben repetirse con Chromium instalado o dentro de `Dockerfile.lector` antes de aprobar. Es evidencia de los fixtures y del contrato interno; no certifica cuentas reales, selectores vigentes ni la prueba de diez pedidos de M2.

```powershell
.\mvnw.cmd "-Dtest=LectorChromiumIntegracionTest,ExtraccionLecturaTest,SeguridadLecturaTest,ServicioLecturaTest,LectorRemotoClientTest,ArranquePlaywrightIntegracionTest,ValidacionLecturaTest" test
```

```powershell
.\mvnw.cmd "-Dlector.integration=true" "-Dtest=LectorChromiumIntegracionTest,ArranquePlaywrightIntegracionTest" test
```

## Revisión manual del subproceso

Alelhy debe recorrer el BPMN con un pedido real antes de aprobarlo. Como ejemplo preparatorio puede usarse el repuesto CAT 1R-0750 incluido en P4. El ejemplo no sustituye la revisión con una solicitud u OC real identificada por el equipo.

| Escenario | Recorrido y resultado esperado |
|---|---|
| Lectura correcta | Pedido válido, sesión vigente o ausente, página legible y reglas válidas: devolver `OK` con los seis campos y moneda. |
| Token incorrecto | Detener en la validación; HTTP 401 `NO_AUTORIZADO`. |
| URL interna | Rechazar la solicitud; HTTP 400 `URL_NO_PERMITIDA`. |
| Sesión vencida | Tomar la salida No de la compuerta de sesión; no abrir la tienda. |
| CAPTCHA o código | Tomar la salida de verificación; devolver `VERIFICACION` sin resolverlo. |
| Bloqueo de tienda | Tomar la salida Bloqueo; devolver `BLOQUEO`. |
| Error de reglas | No convertir un dato ausente o un rango en un precio inventado; devolver error tipificado. |
| Tiempo excedido | Disparar el evento de borde; responder `TIEMPO_AGOTADO`. |
| Caída temporal del lector | P2 reintenta una sola vez dentro de su presupuesto; si vuelve a fallar, entrega un error al llamador. |

### Constancia de revisión

1. Identificar la solicitud u OC real y la fuente utilizada, sin incluir credenciales en el acta.
2. Recorrer éxito, verificación, bloqueo y timeout; contrastar el resultado con el código y las pruebas.
3. Registrar observaciones de Alelhy y confirmar el QA manual de Karla.
4. Registrar la aprobación del BPMN y de D-35 a D-38 antes de la integración que disponga el equipo.

Estado de la constancia: **pendiente de firma de Alelhy**. Completar con la OC real, la fuente usada, la fecha y el resultado de cada escenario. Esta entrega no atribuye aprobación ni participación a otra persona.

| Dato | Valor |
|---|---|
| Solicitud u OC real | |
| Fuente usada | |
| Fecha del recorrido | |
| Éxito / verificación / bloqueo / timeout | |
| Observaciones de Alelhy | |
| QA manual | |
| Aprobado por y fecha | |

## Integración y pendientes del equipo

| Responsable | Punto de integración que conserva |
|---|---|
| Luana · L1 y L2 | Cifrado, persistencia y renovación de sesión ya integrados (PR #109, #113, #115 y #117). Queda por acordar cómo transferir una sesión completada manualmente, porque P1 no mantiene un navegador interactivo. |
| Karla · K1 y K2 | Tablas, entidades y cifrado. El lector recibe datos ya preparados y no se conecta a Supabase. |
| Alelhy, Mell y Nicole · adaptadores | Construcción de URLs, selectores reales y reglas por fuente ya integrados: Mercado Libre y Alibaba (Karla, PR #111 y #112), Amazon y AliExpress (Alelhy, PR #107) y eBay (Mell). A4 conserva la lectura de `fuente_selector` y el acuerdo de normalización. |
| Nicole · N1 N2 N3 y P3 | Fuentes activas, pausas, captura por URL, historial y despliegue en Render. El llamador conserva la alternativa manual cuando falla una fuente. Pendiente con P3: el lector admite dos trabajos simultáneos, pero en la prueba del 19/11/2026 con 512 MiB dos lecturas llegaron al tope de memoria; acordar si se baja a uno o se sube la memoria. |

Resuelto: la descripción llega hasta el historial (`ResultadoBusqueda` y `HistorialSourcing` tienen `descripcion`), y el rango de Alibaba se acordó como el mayor del rango, con la regla `precioRango="mayor"`.

P4 contiene HTML sintético guardado en Java, con una búsqueda y una ficha por fuente. Las capturas saneadas de páginas reales y la validación de sus selectores siguen pendientes; se harán en la rama `p6/v33-paginas-prueba`. El arranque en contenedor quedó cubierto por `Dockerfile.lector`.

## Fuentes y archivos de entrega

Fuente organizativa: `genlogs_v3_3_reparto_del_subproceso_8_2_entre_6_personas.md`, fechado 12/11/2026, secciones Persona 6, BPMN de cada persona y Orden ramas y PR. Fuente de código: rama `release/v3.3` del backend al 23/11/2026. No se consultaron ni modificaron cuentas de producción.

- `docs/bpmn/bpmn-9-b-1-leer-pagina.bpmn` y `docs/bpmn/bpmn-9-b-1a-lectura-chromium.bpmn`: modelos editables BPMN 2.0 con BPMN DI, no ejecutables.
- Imágenes PNG y SVG con el mismo nombre: revisión y documentación.
- `docs/persona-6-servicio-lectura.md` y `persona-6-servicio-lectura-v2.docx`: este documento y su trazabilidad.

Para revisar el modelo, abrir los archivos `.bpmn` en Camunda Modeler o bpmn.io. La actividad llamada del principal remite al proceso `P6_LecturaChromium` del segundo archivo. Los dos modelos se importaron en bpmn-js sin advertencias el 23/11/2026. Se integran por PR a `release/v3.3` con 1 aprobación de la pareja y el check `test` en verde.
