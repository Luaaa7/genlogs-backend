 # GenLogs Backend

API REST de **GenLogs S.A.C.** — Sistema de Gestión Logística (clientes, proveedores, productos, empresas mineras, cotizaciones, facturación, órdenes de compra y reportes).

Construido con **Spring Boot 4.1.1** (Java 17), **Spring Security + JWT**, **Spring Data JPA / Hibernate**, **PostgreSQL**, **Cloudinary** y **Brevo** (correo por API HTTPS).

---

## 1. Requisitos

| Herramienta | Version | Notas |
|---|---|---|
| **JDK** | 17 | Requisito de `pom.xml` (`<java.version>17</java.version>`). Verificado con Temurin 17.0.20.1. |
| **Maven** | 3.9+ | El repo incluye wrapper (`mvnw` / `mvnw.cmd`, Maven 3.9.16), asi que no hace falta instalarlo. |
| **PostgreSQL** | 13+ | Probado con PostgreSQL 18.4. Debe existir la base de datos con el esquema completo (ver seccion 4). |
| **Docker** | 20.10+ | Opcional, solo para la imagen de la seccion 7. |

Verificacion rapida del entorno:

```bash
java -version      # debe indicar 17
mvn -v             # o: ./mvnw -v
psql --version
```

---

## 2. Estructura del proyecto

```
genlogs-backend/
|-- .github/                    # Workflows de CI
|-- .mvn/                      # Maven wrapper (configuracion)
|-- mvnw / mvnw.cmd            # Maven wrapper (script de arranque)
|-- Dockerfile                 # Build multi-stage (JDK -> JRE)
|-- pom.xml
|-- .env                       # Variables locales (NO se versiona; ver .gitignore)
`-- src/
    |-- main/
    |   |-- java/com/genlogs/app/
    |   |   |-- AppApplication.java
    |   |   |-- config/       # SecurityConfig, CorsConfig, CloudinaryConfig, PasswordEncoderConfig
    |   |   |-- controller/   # Controladores REST
    |   |   |-- dto/          # DTOs de entrada/salida
    |   |   |-- exception/    # Manejador global de errores
    |   |   |-- model/        # Entidades JPA (@Entity)
    |   |   |-- repository/   # Repositorios Spring Data
    |   |   |-- security/     # JwtUtil, JwtAuthenticationFilter, CustomUserDetailsService
    |   |   `-- service/      # Logica de negocio
    |   `-- resources/application.properties
    `-- test/java/com/genlogs/app/AppApplicationTests.java
```

---

## 3. Variables de entorno

Todas las variables se leen desde el entorno del sistema o desde un archivo `.env` en el directorio de trabajo (ver seccion 3.2).

### 3.1 Tabla de variables

| Variable | Obligatoria | Default | Descripcion |
|---|:---:|---|---|
| `DATABASE_URL` | **Si** | — | URL JDBC de PostgreSQL. Ej.: `jdbc:postgresql://localhost:5432/genlogs` |
| `DATABASE_USERNAME` | **Si** | — | Usuario de la base de datos. |
| `DATABASE_PASSWORD` | **Si** | — | Contrasena de la base de datos. |
| `JWT_SECRET` | **Si** | — | Clave de firma de los tokens JWT. Debe ser larga y aleatoria. |
| `BREVO_API_KEY` | **Si** | — | API key de Brevo para el envio de correos. |
| `APP_MAIL_FROM` | **Si** | — | Remitente del correo de recuperacion (formato `correo@dominio`). |
| `SERVER_PORT` / `PORT` | No | `8080` | Puerto HTTP. Spring lee `PORT` (`server.port=${PORT:8080}`); `SERVER_PORT` tambien funciona por la precedencia de Spring Boot sobre `application.properties`. |
| `CORS_ALLOWED_ORIGINS` | No | `https://genlogs-frontend-kym5.onrender.com,https://*.onrender.com,http://localhost:5173,http://127.0.0.1:5173` | Origenes permitidos separados por comas. |
| `FRONTEND_URL` | No | `https://genlogs-frontend-kym5.onrender.com` | URL publica del frontend; se usa para armar el enlace del correo de recuperacion. |
| `MAIL_FROM_EMAIL` | No | vacio | Remitente del correo de cotizaciones (`MailService`). |
| `MAIL_FROM_NAME` | No | `GenLogs` | Nombre del remitente. |
| `CLOUDINARY_CLOUD_NAME` | No | vacio | Nube de Cloudinary para imagenes y adjuntos. |
| `CLOUDINARY_API_KEY` | No | vacio | API key de Cloudinary. |
| `CLOUDINARY_API_SECRET` | No | vacio | API secret de Cloudinary. |
| `FACTILIZA_API_KEY` | No | vacio | Autocompletado de RUC/DNI. Sin ella el servicio responde datos simulados. |
| `FACTILIZA_BASE_URL` | No | `https://api.factiliza.com/v1` | Base URL de Factiliza. |
| `HIKARI_MAX_POOL_SIZE` | No | `5` | Conexiones maximas del pool Hikari. |
| `HIKARI_MIN_IDLE` | No | `1` | Conexiones ociosas minimas. |
| `MAX_INTENTOS_FALLIDOS` | No | `5` | Intentos de login fallidos antes de bloquear la cuenta. |
| `SPRING_JPA_SHOW_SQL` | No | `false` | Volcado de SQL a los logs. |
| `SPRING_JPA_FORMAT_SQL` | No | `false` | Formatea el SQL en los logs. |

> **Importante:** `BREVO_API_KEY` y `APP_MAIL_FROM` son obligatorias aunque no aparezcan con valor por defecto en `application.properties`. `PasswordResetEmailService` las lee con `@Value("${brevo.api.key}")` y `@Value("${app.mail.from}")` (sin `:` de default), por lo que **si faltan, la aplicacion no arranca**. Comprobado empiricamente:
>
> ```
> # Sin BREVO_API_KEY:
> Caused by: org.springframework.util.PlaceholderResolutionException: Could not resolve placeholder
> 'brevo.api.key' in value "${brevo.api.key}"
>
> # Sin APP_MAIL_FROM:
> Caused by: org.springframework.util.PlaceholderResolutionException: Could not resolve placeholder
> 'app.mail.from' in value "${app.mail.from}"
> ```
>
> Spring las resuelve por *relaxed binding* desde las variables de entorno `BREVO_API_KEY` y `APP_MAIL_FROM`.
>
> Ojo: `MAIL_FROM_EMAIL` (usada por `MailService`) es **distinta** de `APP_MAIL_FROM` y si falta **no** impide el arranque: en ese caso `MailService` escribe el enlace de recuperacion en los logs en lugar de enviarlo.

### 3.2 Carga del archivo `.env`

El proyecto usa la libreria `me.paulschwarz:springboot4-dotenv` (v5.1.0), que carga automaticamente un archivo `.env` **ubicado en el directorio de trabajo** del proceso.

- Al ejecutar con `mvn spring-boot:run` o `java -jar target/app-0.0.1-SNAPSHOT.jar` **desde la raiz del proyecto**, el `.env` de la raiz se carga automaticamente.
- Si ejecutas el `.jar` desde otro directorio, debes copiar el `.env` a ese directorio o exportar las variables a mano.
- **Precedencia:** las variables de entorno del sistema tienen prioridad sobre las del `.env`. Si exportas `DATABASE_URL` en la consola, esa gana sobre el valor del `.env`.

Ejemplo de `.env` (crear en la raiz del proyecto):

```dotenv
CLOUDINARY_CLOUD_NAME=tu_cloud_name
CLOUDINARY_API_KEY=tu_api_key
CLOUDINARY_API_SECRET=tu_api_secret

DATABASE_URL=jdbc:postgresql://localhost:5432/genlogs
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=tu_password
JWT_SECRET=cadena_larga_y_aleatoria_para_firmar_los_tokens

BREVO_API_KEY=xkeysib-tu-api-key
APP_MAIL_FROM=no-reply@genlogs.pe
```

> `.env` esta listado en `.gitignore` y en `.dockerignore`; **nunca lo subas al repositorio**.

---

## 4. Base de datos

### 4.1 Advertencia importante: el esquema NO esta versionado

**Este repositorio no incluye ningun archivo `.sql` ni script de migraciones.** No existe carpeta `db/`, `schema.sql`, `data.sql` ni Flyway/Liquibase en el `pom.xml`.

Consecuencia directa: la aplicacion esta configurada con `spring.jpa.hibernate.ddl-auto=validate`, por lo que **no crea las tablas**. Necesitas una base de datos que ya tenga el esquema completo antes de arrancar.

Al iniciar contra una base de datos vacia o con esquema incompleto, la aplicacion falla con un error similar a:

```
Schema-validation: missing table [usuario] ...
Schema-validation: wrong column type encountered in column [usuario].[intentos_fallidos]
```

### 4.2 Opciones para obtener el esquema

#### Opcion A — Base de datos existente (recomendada en produccion)

Conecta la API a la base de datos ya desplegada (la de Render, Neon, Supabase, etc.) y no ejecutes nada mas: el esquema ya existe.

#### Opcion B — Generar el esquema automaticamente (solo desarrollo)

Para levantar un entorno limpio puedes dejar que Hibernate genere las tablas a partir de las entidades. **Esto borra y recrea el esquema**, asi que nunca lo ejecutes contra una base de datos con datos reales.

```bash
# Crear la base vacia primero
createdb -U postgres genlogs

# Arrancar generando el esquema (SOLO desarrollo)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.jpa.hibernate.ddl-auto=create"
```

O bien, contra el JAR ya empaquetado:

```bash
java -jar target/app-0.0.1-SNAPSHOT.jar \
  --spring.jpa.hibernate.ddl-auto=create \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/genlogs \
  --spring.datasource.username=postgres \
  --spring.datasource.password=tu_password
```

> `ddl-auto=create` genera tablas, indices y claves foraneas a partir de las entidades JPA, pero **no inserta datos iniciales** (no hay usuario admin, ni catalogos base, ni datos de ubigeo). Tras generarlo, exporta el esquema resultante como `.sql` y versiona ese dump en tu control de versiones para que el equipo no dependa de este paso.

```bash
# Volcar el esquema generado para versionarlo
pg_dump -U postgres -s genlogs > schema.sql
```

#### Opcion C — Cargar un dump existente

Si tienes un dump del esquema, restauralo antes de arrancar:

```bash
psql -U postgres -d genlogs -f schema.sql
```

### 4.3 Datos minimos para poder hacer login

El flujo de autenticacion (`POST /api/auth/login`) consulta la tabla `usuario` y su rol en `rol`. Para que el login funcione necesitas al menos:

1. La tabla `usuario` con las columnas que valida Hibernate (id, correo/email, password, rol, estado, intentos_fallidos, etc.).
2. La tabla `rol` con el registro `ADMINISTRADOR` (id `1`). Ese es el nombre de rol que devuelve el login en el campo `nombreRol`.
3. La contrasena almacenada como **BCrypt** (usa `PasswordEncoderConfig` / `new BCryptPasswordEncoder().encode("clave")` para generarla).

Verificado empiricamente: con `usuario.nombre_usuario = 'admin'`, `usuario.correo = 'admin@genlogs.pe'`, `usuario.status = 'A'`, `usuario.bloqueado = false`, `usuario.id_rol = 1` (rol `ADMINISTRADOR`) y un hash BCrypt valido, el login devuelve **HTTP 200** con `token`, `nombreUsuario` y `nombreRol`.

> El proyecto **no tiene migraciones ni datos seed**, y el `mvn test` actual solo ejecuta una prueba de carga de contexto, no verifica datos de negocio.

---

## 5. Ejecucion local

### 5.1 Preparar la base de datos

```bash
createdb -U postgres genlogs
```

Y asegurate de tener el esquema completo (seccion 4).

### 5.2 Configurar las variables

Opcion 1 — archivo `.env` en la raiz (recomendado para desarrollo).

Opcion 2 — variables de entorno en la consola:

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/genlogs
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=tu_password
export JWT_SECRET=una_cadena_larga_y_aleatoria
export BREVO_API_KEY=xkeysib-tu-api-key
export APP_MAIL_FROM=no-reply@genlogs.pe
```

En Windows (PowerShell):

```powershell
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/genlogs"
$env:DATABASE_USERNAME="postgres"
$env:DATABASE_PASSWORD="tu_password"
$env:JWT_SECRET="una_cadena_larga_y_aleatoria"
$env:BREVO_API_KEY="xkeysib-tu-api-key"
$env:APP_MAIL_FROM="no-reply@genlogs.pe"
```

### 5.3 Arrancar

```bash
# Con el wrapper (no requiere Maven instalado)
./mvnw spring-boot:run          # Linux / macOS
.\mvnw.cmd spring-boot:run      # Windows

# O con Maven del sistema
mvn spring-boot:run

# O desde el JAR empaquetado
mvn clean package -DskipTests
java -jar target/app-0.0.1-SNAPSHOT.jar
```

La aplicacion queda disponible en `http://localhost:8080`.

Para cambiar el puerto usa `PORT` (es la variable que lee `server.port=${PORT:8080}`):

```bash
PORT=9090 ./mvnw spring-boot:run          # Linux / macOS
$env:PORT="9090"; .\mvnw.cmd spring-boot:run   # Windows PowerShell
```

> `SERVER_PORT` tambien funciona: Spring Boot le da precedencia a las variables de entorno `SERVER_PORT` sobre el valor de `server.port` del `application.properties`. Verificado: con `SERVER_PORT=9090` la app arranco en el puerto 9090, y con `PORT=8099` arranco en el 8099.

### 5.4 Verificar que arranco

```bash
# Health check (publico)
curl http://localhost:8080/health
curl http://localhost:8080/api/health
```

Ambos devuelven **HTTP 200**. Alternativamente:

```bash
curl -i http://localhost:8080/api/health
```

## 6. Tests

```bash
# Con wrapper
./mvnw test
.\mvnw.cmd test

# Con Maven del sistema
mvn test
```

Estado verificado en este repositorio: **1 test ejecutado, 0 fallos** (`AppApplicationTests`, prueba de carga del contexto de Spring).

> La prueba de contexto requiere que la aplicacion pueda inicializar JPA/Hibernate. Si no hay base de datos disponible, el test falla con errores de conexion. Para ejecutar los tests sin base de datos, desactiva la validacion del esquema:
>
> ```bash
> mvn test -Dspring.jpa.hibernate.ddl-auto=none
> ```

Veredicto de una ejecucion completa:

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 7. Empaquetado

```bash
# Limpiar, compilar y empaquetar sin tests
mvn clean package -DskipTests

# Con tests
mvn clean package

# Solo compilar
mvn clean compile
```

El artefacto queda en:

```
target/app-0.0.1-SNAPSHOT.jar
```

Ejecucion del JAR:

```bash
java -jar target/app-0.0.1-SNAPSHOT.jar
```

---

## 8. Docker

### 8.1 Construir la imagen

```bash
docker build -t genlogs-backend:latest .
```

El `Dockerfile` es *multi-stage*: la primera etapa usa `eclipse-temurin:17-jdk-jammy` para compilar y la segunda usa `eclipse-temurin:17-jre-jammy` para ejecutar. Ambas builds requieren acceso a internet para descargar imagenes base y dependencias.

### 8.2 Ejecutar el contenedor

```bash
docker run -d --name genlogs-backend -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/genlogs \
  -e DATABASE_USERNAME=postgres \
  -e DATABASE_PASSWORD=tu_password \
  -e JWT_SECRET=una_cadena_larga_y_aleatoria \
  -e BREVO_API_KEY=xkeysib-tu-api-key \
  -e APP_MAIL_FROM=no-reply@genlogs.pe \
  genlogs-backend:latest
```

En Linux, para alcanzar PostgreSQL instalado en el host usa `--add-host=host.docker.internal:host-gateway`. Si PostgreSQL corre en otro contenedor, usa el nombre del servicio en la red.

Verifica los logs:

```bash
docker logs -f genlogs-backend
```

### 8.3 Notas sobre el Dockerfile

- **No copia el `.env` al contenedor** (esta en `.dockerignore`); todas las variables deben inyectarse con `-e` o con `--env-file`.
- El `Dockerfile` invoca `mvnw`/`.mvn`, es decir el wrapper. Si la build falla descargando el wrapper, cambia la linea a `mvn -B -DskipTests package` o monta un `.m2` cacheado.
- La imagen final usa el usuario `root` por defecto; conviene fijar un `USER` no privilegiado antes de desplegar en produccion.

---

## 9. Autenticacion (JWT)

### 9.1 Flujo

1. `POST /api/auth/login` con usuario y contrasena.
2. El backend valida contra la tabla `usuario` (BCrypt) y devuelve un token JWT.
3. El cliente envia el token en la cabecera `Authorization: Bearer <token>`.
4. `JwtAuthenticationFilter` valida la firma y carga el `UserDetails` en el `SecurityContext`.
5. Las rutas protegidas responden **401** si falta o es invalido el token.

### 9.2 Obtener un token

El DTO de entrada (`LoginRequest`) espera **`nombreUsuario`** y **`password`** (no `correo`):

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"nombreUsuario":"admin","password":"tu_password"}'
```

La respuesta (`LoginResponse`) tiene esta forma:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9....",
  "nombreUsuario": "admin",
  "nombreRol": "ADMINISTRADOR"
}
```

> El token dura **24 horas** (`jwt.expiration=86400000` ms).

### 9.3 Usar el token

```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9...."

# Endpoint protegido (requiere token)
curl -i http://localhost:8080/api/catalogos/monedas \
  -H "Authorization: Bearer $TOKEN"

# Endpoint publico (no requiere token)
curl -i http://localhost:8080/api/empresas-mineras
```

Comportamiento verificado empiricamente:

| Peticion | Resultado |
|---|---|
| `GET /api/health` | `200` |
| `GET /api/empresas-mineras` (publico) | `200` |
| `GET /api/catalogos/monedas` sin token | `401` |
| `GET /api/catalogos/monedas` con token valido | `200` |
| `GET /api/productos/web`, `/api/paises`, `/api/marcas` sin token | `401` |

### 9.4 Recuperacion de contrasena

```bash
# 1) Solicitar enlace (publico)
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"correo":"usuario@genlogs.pe"}'

# 2) Validar el token recibido por correo
curl -X POST http://localhost:8080/api/auth/validate-reset-token \
  -H "Content-Type: application/json" \
  -d '{"token":"<token-del-correo>"}'

# 3) Restablecer la contrasena
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"token":"<token-del-correo>","nuevaPassword":"NuevaClave123"}'
```

El enlace se arma con `FRONTEND_URL` y es de un solo uso, con vigencia limitada (ver `PasswordResetService.DURACION_MINUTOS`).

Comportamiento verificado:

- `POST /api/auth/forgot-password` responde **HTTP 200** siempre, exista o no el correo, para no revelar que correos estan registrados:
  `{"message":"Si el correo está registrado, recibirás un enlace para cambiar tu contraseña."}`
- `POST /api/auth/reset-password` responde **HTTP 200** con `{"message":"Contraseña actualizada correctamente."}` cuando el token es valido.
- Con un token inexistente o vencido, `validate-reset-token` y `reset-password` responden **HTTP 400**.
- Si falta `MAIL_FROM_EMAIL`, el enlace de recuperacion se imprime en los logs:
  `BREVO_API_KEY o MAIL_FROM_EMAIL no configuradas. Enlace de recuperación (válido 15 min): https://.../reset-password?token=...`

---

## 10. Endpoints principales

Todos los endpoints cuelgan de `/api`. Salvo indicacion contraria, requieren `Authorization: Bearer <token>`.

### 10.1 Publicos

Verificado empiricamente contra la app en ejecucion (peticiones **sin** token):

| Metodo | Ruta | Descripcion |
|---|---|---|
| `GET` | `/`, `/health`, `/api/health` | Health check (un mismo controlador expone las tres rutas). |
| `POST` | `/api/auth/login` | Inicio de sesion; devuelve JWT. |
| `POST` | `/api/auth/forgot-password` | Solicita enlace de recuperacion. |
| `POST` | `/api/auth/validate-reset-token` | Valida el token de recuperacion. |
| `POST` | `/api/auth/reset-password` | Restablece la contrasena. |
| `GET` | `/api/empresas-mineras` | Listado de empresas mineras. |
| `GET` | `/api/empresas-mineras/{id}` | Detalle de una empresa minera. |

> `SecurityConfig` marca `/api/empresas-mineras/**` como publico, asi que **tambien** quedan expuestas las rutas `POST`, `PUT` y `DELETE` de ese controlador. Es un detalle a revisar antes de produccion.

> `GET /api/productos/web`, `/api/productos/buscar`, `/api/paises`, `/api/departamentos`, `/api/provincias`, `/api/distritos`, `/api/categorias-producto`, `/api/marcas` y `/api/unidades-medida` **NO son publicos**: devuelven `401` sin token, aunque el nombre de algunos endpoints sugiera lo contrario.

### 10.2 Protegidos (resumen)

Estos requieren token; sin el devuelven **HTTP 401**:

| Area | Rutas base |
|---|---|
| Usuarios y roles | `/api/usuarios`, `/api/roles` |
| Clientes / proveedores / terceros | `/api/clientes`, `/api/proveedores`, `/api/terceros` |
| Productos y servicios | `/api/productos`, `/api/servicios` |
| Catalogo de productos | `/api/productos/web`, `/api/productos/buscar` |
| Ubigeo | `/api/paises`, `/api/departamentos`, `/api/provincias`, `/api/distritos` |
| Catalogos de apoyo | `/api/categorias-producto`, `/api/marcas`, `/api/unidades-medida` |
| Catalogos | `/api/catalogos/monedas`, `/api/catalogos/minerales`, `/api/catalogos/unidades-medida`, `/api/catalogos/categorias-servicio`, `/api/catalogos/condiciones-pago`, `/api/catalogos/sectores-economicos`, `/api/catalogos/tipos-operacion-minera`, `/api/catalogos/etapas-comerciales-minera`, `/api/catalogo-productos` |
| Cotizaciones | `/api/cotizaciones`, `/api/estados-cotizacion` |
| Facturacion | `/api/facturacion`, `/api/estados-facturacion`, `/api/tipos-comprobante` |
| Ordenes de compra | `/api/ordenes-compra`, `/api/estados-orden-compra` |
| Minerales de empresas mineras | `/api/empresas-mineras/{idEmpresaMinera}/minerales` |
| Dashboard | `/api/dashboard/indicadores`, `/api/dashboard/graficos/*` |
| Reportes | `/api/reportes/generar` (Excel o PDF) |
| Archivos | `/api/archivos/upload` |
| Documentos | `/api/tipos-documento` |

Ejemplo de detalle:

```bash
curl http://localhost:8080/api/catalogos/monedas/1 \
  -H "Authorization: Bearer $TOKEN"
```

### 10.3 Nota sobre CORS

`CorsConfig` usa `app.cors.allowed-origins` (variable `CORS_ALLOWED_ORIGINS`, lista separada por comas). Si el frontend corre en otro origen y el navegador bloquea la peticion, agrega tu origen:

```bash
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://mi-frontend.vercel.app mvn spring-boot:run
```

Se permiten `GET, POST, PUT, DELETE, PATCH, OPTIONS`, cualquier cabecera y credenciales.

---

## 11. Integraciones externas

### 11.1 Brevo (correo)

El envio de correo usa la **API HTTPS de Brevo** (`https://api.brevo.com/v3/smtp/email`) y no SMTP, porque Render bloquea los puertos SMTP en el plan gratuito.

| Variable | Uso |
|---|---|
| `BREVO_API_KEY` | API key de Brevo (**obligatoria para arrancar**). |
| `APP_MAIL_FROM` | Remitente del correo de recuperacion de contrasena (**obligatoria para arrancar**). |
| `MAIL_FROM_EMAIL` | Remitente del correo de cotizaciones. Si falta, `MailService` escribe el enlace en los logs. |
| `MAIL_FROM_NAME` | Nombre visible del remitente (default `GenLogs`). |

Comportamiento:

- Si `BREVO_API_KEY` o `MAIL_FROM_EMAIL` estan vacias, **el enlace de recuperacion se escribe en el log** en lugar de enviarse. Esto permite probar el flujo completo sin correo real.
- Un error de Brevo (HTTP >= 300) se registra como `ERROR` pero **no propaga la excepcion**: el endpoint de recuperacion responde con exito igualmente.
- El remitente y la API key deben estar verificados en Brevo, y el dominio del remitente debe estar autenticado en la cuenta.

### 11.2 Cloudinary (imagenes y adjuntos)

```bash
CLOUDINARY_CLOUD_NAME=tu_cloud_name
CLOUDINARY_API_KEY=tu_api_key
CLOUDINARY_API_SECRET=tu_api_secret
```

Si faltan, la app arranca igual (los valores tienen default vacio) pero la carga de imagenes fallara en tiempo de ejecucion.

### 11.3 Factiliza (RUC / DNI)

```bash
FACTILIZA_API_KEY=tu_api_key
FACTILIZA_BASE_URL=https://api.factiliza.com/v1
```

Sin `FACTILIZA_API_KEY`, el servicio **responde datos simulados** en lugar de fallar, para no bloquear el formulario de clientes y proveedores.

---

## 12. Despliegue

El backend esta configurado para Render (plan gratuito), donde el servicio expone el puerto definido por la variable `PORT`.

Variables a configurar en el panel de Render:

```
DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD,
JWT_SECRET, BREVO_API_KEY, APP_MAIL_FROM,
FRONTEND_URL, CORS_ALLOWED_ORIGINS
```

Consideraciones:

- `JWT_SECRET` debe ser **distinta por entorno** y nunca versionada.
- Cambiar `JWT_SECRET` invalida todos los tokens emitidos.
- Si `PORT` esta definida, Spring Boot la respeta (`server.port=${PORT:8080}`).
- El `.env` **no** se usa en Render: configura las variables en el panel de Render. Este repositorio **no incluye un `render.yaml`**, asi que el deploy se configura a mano en el panel.

---

## 13. Troubleshooting

| Sintoma | Causa probable | Solucion |
|---|---|---|
| `Could not resolve placeholder 'brevo.api.key'` | Falta `BREVO_API_KEY` | Define `BREVO_API_KEY` en el entorno o en el `.env` de la raiz. |
| `Could not resolve placeholder 'app.mail.from'` | Falta `APP_MAIL_FROM` | Define `APP_MAIL_FROM` en el entorno o en el `.env` de la raiz. |
| `Schema-validation: missing table [...]` | `ddl-auto=validate` y la BD no tiene el esquema | Carga el esquema (seccion 4) o usa `--spring.jpa.hibernate.ddl-auto=create` solo en desarrollo. |
| `Connection to localhost:5432 refused` | PostgreSQL no esta arrancado o el puerto es otro | Verifica el servicio y el `DATABASE_URL`. |
| `Password authentication failed for user` | Credenciales incorrectas | Revisa `DATABASE_USERNAME` / `DATABASE_PASSWORD`. |
| `400 Usuario o contrasena incorrectos` | El login espera `nombreUsuario` (no `correo`) o la clave no coincide | Envia `{"nombreUsuario":"...","password":"..."}` y verifica que el hash en la BD sea BCrypt. |
| `401` en un endpoint protegido | Token ausente, vencido o con firma invalida | Repite el login y envia `Authorization: Bearer <token>`. |
| `403` / error de CORS desde el navegador | Origen no permitido | Agrega el origen a `CORS_ALLOWED_ORIGINS`. |
| El correo no llega | API key o remitente sin verificar | Revisa los logs (`ERROR Brevo respondio ...`) y la cuenta de Brevo. |
| El `.env` no se carga | Ejecutaste desde otro directorio | Copia el `.env` al directorio de trabajo o exporta las variables. |
| `Table "usuario" not found` | Sin datos iniciales | Inserta un usuario admin y el rol `ADMINISTRADOR` (seccion 4.3). |
| El puerto 8080 esta ocupado | Otro proceso en uso | `PORT=9090 mvn spring-boot:run` o `netstat -ano | findstr :8080`. |

---

## 14. Resumen de comandos

```bash
# Compilar
mvn clean compile

# Tests
mvn test

# Empaquetar
mvn clean package -DskipTests

# Ejecutar
mvn spring-boot:run
java -jar target/app-0.0.1-SNAPSHOT.jar

# Health check
curl http://localhost:8080/health

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"nombreUsuario":"admin","password":"tu_password"}'

# Docker
docker build -t genlogs-backend:latest .
docker run -d -p 8080:8080 --env-file .env genlogs-backend:latest
```

---

## 15. Limitaciones conocidas

- **No hay scripts SQL ni migraciones versionadas.** El esquema debe existir en la base de datos (seccion 4.2, opcion B para generarlo en desarrollo).
- **No hay datos seed.** El primer usuario (y el rol `ADMINISTRADOR`) deben insertarse manualmente.
- **La cobertura de tests es minima:** una unica prueba de carga de contexto.
- **Los catalogos `/api/catalogo/**` vs `/api/catalogos/**`:** `SecurityConfig` declara como publica la regla `/api/catalogo/**` (singular), pero los controladores de catalogos usan `/api/catalogos/**` (plural). En la practica, `/api/catalogos/**` **queda protegida** y devuelve `401` sin token, tal como se verifico. Si necesitas que esos catalogos sean publicos, hay que corregir la regla en `SecurityConfig`.
- **Endpoint con nombre "publico" que no lo es:** `/api/productos/web` y `/api/productos/buscar` devuelven `401` sin token. Si el frontend los consume antes de iniciar sesion, hay que anadir sus rutas a la seccion publica de `SecurityConfig`.
- **El envio real de correo no esta validado en este entorno**: solo se probo con una clave de Brevo ficticia.
- **`PasswordResetEmailService` esta sin uso:** existe como bean `@Service` pero ningun controlador lo invoca (el flujo real usa `MailService`). Es la razon por la que `BREVO_API_KEY` y `APP_MAIL_FROM` son obligatorias para arrancar aunque el envio real use `MAIL_FROM_EMAIL`.
- **La build de la imagen Docker no se pudo verificar**: Docker no esta instalado en el entorno de pruebas.

---

## 16. Licencia

Proyecto privado de GenLogs S.A.C. Todos los derechos reservados.


