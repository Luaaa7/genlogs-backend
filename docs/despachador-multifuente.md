# Despachador multifuente (BPMN 9-B.1)

`ConsultaFuenteService` sigue recibiendo un solo `LectorFuente`. Ese bean es `LectorFuenteDespachador` (`lectorFuenteDespachador`). El despachador no lee tiendas: elige al lector que declaró el `CodigoFuente` y le delega `buscar` y `leerUrl`.

Si ninguna implementación declaró esa tienda, responde `ResultadoLectura.noConectado` con el mensaje `No hay un lector registrado para <CODIGO>; no se consulta la tienda.` El servicio ya trata `NO_CONECTADO` así: no reserva cuota, no reintenta, no marca la fuente `CON_ERROR` y no avisa al Jefe. La captura por URL conserva su fallback a `LectorUrl`.

Los códigos reales son los de `CodigoFuente`: `MERCADO_LIBRE`, `EBAY`, `AMAZON`, `ALIEXPRESS`, `ALIBABA`.

## Cómo registrar un lector

Implementar `LectorFuenteEspecifico` y publicarlo como `@Component` (o `@Bean`). No implementar `LectorFuente` y no anotar `@Primary`. El despachador solo recoge beans de `LectorFuenteEspecifico`, así que no hace falta tocarlo para sumar una tienda y no se inyecta a sí mismo.

`fuentes()` declara las tiendas. Se lee una vez al arrancar. Un bean puede devolver varias. Dos beans para el mismo `CodigoFuente` abortan el arranque con `IllegalStateException` (`Dos lectores registrados para <CODIGO>`). No se elige uno en silencio.

La sesión, la URL y los selectores se reenvían tal cual. El resultado del lector se devuelve sin cambiarlo (`OK`, `SIN_RESULTADOS`, `BLOQUEADA`, `VERIFICACION`, `ERROR`). No registrar sesiones, tokens ni credenciales.

### Karla — Mercado Libre y Alibaba

```java
@Component
class LectorKarla implements LectorFuenteEspecifico {

    @Override
    public Set<CodigoFuente> fuentes() {
        return Set.of(CodigoFuente.MERCADO_LIBRE, CodigoFuente.ALIBABA);
    }

    @Override
    public ResultadoLectura buscar(CodigoFuente codigo, String urlBase, String sesion,
                                   ConsultaBusqueda consulta, List<SelectorFuenteDto> selectores) {
        // codigo es MERCADO_LIBRE o ALIBABA; no mezclar las tiendas
    }

    @Override
    public ResultadoLectura leerUrl(CodigoFuente codigo, String url, String sesion,
                                    List<SelectorFuenteDto> selectores) {
        // misma distinción por codigo
    }
}
```

### Mell — eBay

```java
@Component
class LectorMell implements LectorFuenteEspecifico {

    @Override
    public Set<CodigoFuente> fuentes() {
        return Set.of(CodigoFuente.EBAY);
    }
    // buscar y leerUrl
}
```

`EbayFuente` (Browse API) no se reemplaza. Sigue siendo el `FuenteBusqueda` de respaldo cuando 9-B.1 no leyó la tienda.

### Ale — Amazon y AliExpress

```java
@Component
class LectorAle implements LectorFuenteEspecifico {

    @Override
    public Set<CodigoFuente> fuentes() {
        return Set.of(CodigoFuente.AMAZON, CodigoFuente.ALIEXPRESS);
    }
    // buscar y leerUrl, distinguiendo codigo
}
```

Hasta que ese bean exista, la tienda responde `NO_CONECTADO` y el backend arranca igual.

## Cuota de la verificación de las 6:10

No hay un valor nuevo. El límite actual no se modificó: falta la confirmación de Karla.

### Qué hay hoy

| Contador | Dónde se fija | Quién lo consume |
|---|---|---|
| `fuente_busqueda.limite_diario` | Columna de cada tienda. El default de la entidad es 50. Lo edita Configuración > Fuentes (`FuentesConfigService`). | `ConsultaFuenteService` (9-B.1), en memoria, un `ControlCuota` por `CodigoFuente`. `reservar()` corre solo después de una búsqueda que no fue `NO_CONECTADO` ni sesión ausente. `consultarUrl` no reserva. |
| `genlogs.fuentes.ebay.limite-diario` | Propiedad, default 1000, dentro de `EbayFuente`. | Solo la Browse API (`FuenteConToken`). Es otro contador. |

La tarea de las 6:10 es `VerificacionVitrinaTarea` (`genlogs.vitrina.verificacion.cron`, default `0 10 6 * * *`, `America/Lima`). Llama a `VerificacionVitrinaService.verificarPublicados()`. Esa verificación usa `FuenteBusqueda` (hoy, eBay si hay llaves), no `ConsultaFuenteService` ni `LectorFuente`. No comparte el contador de 9-B.1.

Cuántas consultas gasta: una `FuenteBusqueda.buscar` por producto `PUBLICADO` al que le toca verificar (última verificación anterior a `VERIFICACION_DIAS`, semilla 15), con N.º de parte y con historial cuya fuente tenga bean conectado. No recorre las cinco tiendas por producto. Si la fuente no está conectada, o la llamada lanza (incluida cuota agotada), el producto queda `SIN_CONSULTA` y no se oculta por eso. El número depende del catálogo; no está fijo en el código.

### Propuesta (sin implementar)

Cuando la verificación pase a usar los lectores de 9-B.1, separar la cuota de cada tienda en dos topes que salgan del mismo `limite_diario`:

- tope de búsquedas manuales = `limite_diario - reserva_verificacion`;
- tope de la corrida de las 6:10 = `reserva_verificacion`.

La reserva sería por `CodigoFuente`, en el mismo `ControlCuota` de 9-B.1, y solo aplicaría a la corrida programada. `NO_CONECTADO` seguiría sin consumir. Sin un número confirmado, `reserva_verificacion` no debe existir como default: el comportamiento actual se mantiene.

Cambios que quedan pendientes de Karla, y que no se hicieron:

- no se añadió propiedad ni columna;
- no se partió `ControlCuota`;
- no se movió la verificación de las 6:10 a `ConsultaFuenteService`;
- no se tocó `FuentesConfigService` ni `EbayFuente`.

### Qué tiene que confirmar Karla

1. Si las 6:10 van a seguir en `FuenteBusqueda` / Browse API o van a consultar por `LectorFuente` (9-B.1). Solo en el segundo caso comparten contador con la búsqueda manual.
2. Si el límite a repartir es `fuente_busqueda.limite_diario` (default 50) o el de la API de eBay (default 1000). Son contadores distintos.
3. Cuántos productos publicados suelen estar vencidos cada mañana (depende del catálogo y de `VERIFICACION_DIAS`).
4. Si cada producto vencido es una consulta a una sola tienda (la del historial) o hay que pegarle a más de una.
5. Cuántas consultas diarias deben quedar sí o sí para la búsqueda manual de 9-B, por tienda.
6. Si una consulta de las 6:10 que falla por red debe contar dentro de la reserva.
