# Mantenimiento

## 1. Copias de seguridad de Supabase

El plan gratuito de Supabase **no guarda copias descargables**. La acción
[`.github/workflows/backup-supabase.yml`](.github/workflows/backup-supabase.yml)
crea una copia diaria del esquema `public` y la guarda cifrada durante 30 días.

### Configuración (una sola vez)

1. En Supabase: **Project Settings → Database → Connection string → URI**, modo
   **Session pooler** (puerto 5432). Reemplaza `[YOUR-PASSWORD]` por la contraseña real.
2. En GitHub (repositorio del backend): **Settings → Secrets and variables → Actions → New repository secret**:
   - `SUPABASE_DB_URL`: la URI del paso 1.
   - `BACKUP_PASSPHRASE`: una contraseña larga para cifrar las copias.
     **Guárdala fuera de GitHub** (gestor de contraseñas): sin ella no se puede restaurar.
3. El workflow tiene que estar en `main` (GitHub solo programa workflows de la rama principal).
4. Pruébalo: **Actions → Copia de seguridad Supabase → Run workflow**.

Corre todos los días a las 03:00 (hora de Perú). Si falla, GitHub envía un correo a quien
hizo el último cambio en el workflow.

### Restaurar una copia

```bash
# 1. Descargar el artifact desde Actions → la ejecución → "Artifacts" y descomprimir el .zip
# 2. Descifrar
gpg --decrypt --output genlogs.dump genlogs_AAAA-MM-DD_HHMM.dump.gpg
# 3. Ver qué contiene (opcional)
pg_restore --list genlogs.dump
# 4. Restaurar en una base VACÍA (otro proyecto de Supabase o una base local)
pg_restore --no-owner --no-privileges --dbname "postgresql://...destino..." genlogs.dump
```

> No restaures encima de la base de producción sin antes sacar una copia de cómo está.
> Para recuperar una sola tabla: `pg_restore --table=cliente --data-only ...`.

## 2. Evitar que Render "duerma" el servicio

En el plan gratuito, Render apaga el servicio tras 15 minutos sin tráfico; la siguiente
petición tarda ~50 s en despertarlo. Hay dos pings:

| Opción | Dónde | Frecuencia | Nota |
|---|---|---|---|
| 1 | GitHub Action en el repo `UptimeRobot` | cada 14 min | GitHub puede retrasar o desactivar los cron (tras 60 días sin commits). |
| 2 | **UptimeRobot** (o cron-job.org) | cada 10 min | Respaldo fiable; además avisa por correo si el backend se cae. |

### Endpoints de salud (públicos, sin login)

- `GET https://genlogs-backend-bkuv.onrender.com/api/health`: el servidor está vivo.
- `GET https://genlogs-backend-bkuv.onrender.com/api/health/db`: además ejecuta `SELECT 1` en
  Supabase; devuelve **503** si la base no responde. Usar este en el monitor: mantiene
  despiertos Render **y** Supabase (que pausa proyectos gratis tras una semana sin actividad).

### Configurar UptimeRobot

1. Crear cuenta en https://uptimerobot.com (plan Free).
2. **New monitor**:
   - Monitor type: **HTTP(s)**
   - Friendly name: `GenLogs backend`
   - URL: `https://genlogs-backend-bkuv.onrender.com/api/health/db`
   - Monitoring interval: **10 minutes** (el mínimo gratis es 5)
   - Timeout: 60 s (el arranque en frío tarda)
3. En **Alert contacts**, agregar el correo del equipo.

### Alternativa: cron-job.org

1. Crear cuenta en https://cron-job.org.
2. **Create cronjob** → URL: la misma de arriba → Schedule: **Every 10 minutes** →
   en *Advanced* poner timeout de 30 s y activar "notify on failure".

> Render gratuito da 750 horas al mes por cuenta: un servicio siempre encendido usa ~744.
> Si la cuenta tiene otro servicio gratis encendido 24/7, se acabarán las horas.

## 3. Mensajes de error

- **Backend** ([`GlobalExceptionHandler`](src/main/java/com/genlogs/app/exception/GlobalExceptionHandler.java)
  y [`MensajesError`](src/main/java/com/genlogs/app/exception/MensajesError.java)):
  todas las respuestas de error tienen `{ status, message }` en español. Duplicados, registros
  en uso, campos obligatorios, valores no permitidos, fechas mal escritas y sesión vencida
  indican el campo concreto. El detalle técnico solo queda en el log de Render.
- **Frontend** (`src/lib/errores.ts` en genlogs-frontend): `mensajeError(error, porDefecto)`
  usa el mensaje del backend y, si no hay, explica la causa (sin internet, servidor iniciando,
  tiempo agotado, sin permisos…). `<ErrorBanner error={error} />` añade esa causa al texto
  de la pantalla.
- **Al agregar código nuevo**: lanzar `BusinessException("mensaje para el usuario")` o
  `ResourceNotFoundException(...)` en los services, y en el frontend usar
  `mensajeError(err, 'No se pudo …')` en vez de leer `err.response.data.message` a mano.

## 4. Vencimiento automático de cotizaciones

Todos los días a la 01:00 (hora de Perú) las cotizaciones en `ENVIADA` o
`EN_NEGOCIACION` con más de 15 días pasan a `VENCIDA`. Ese estado es final y la
aplicación no lo revierte. La base de producción ya tiene cotizaciones antiguas:
la primera corrida real no debe hacerse al arrancar ni de golpe.

Variables de entorno (en Render o en el entorno local):

| Variable | Por defecto | Qué hace |
|---|---|---|
| `VENCIMIENTO_ACTIVO` | `true` | Enciende o apaga todo: la programación de la 01:00 y la corrida al arrancar. |
| `VENCIMIENTO_AL_ARRANCAR` | `false` | Si es `true`, al terminar de arrancar ejecuta una corrida. La de la 01:00 no depende de esta variable. |
| `VENCIMIENTO_SIMULACION` | `true` | Si es `true`, no modifica nada ni crea seguimientos ni avisa: solo cuenta y escribe en el log hasta 50 códigos y fechas. |
| `VENCIMIENTO_MAXIMO_POR_CORRIDA` | `500` | Tope de cada corrida, de la fecha más antigua a la más nueva. El resto queda para la siguiente. |

El valor por defecto es simulación: no vence nada hasta que se apague a propósito.
En producción hay que poner `VENCIMIENTO_SIMULACION=false` para vencer de verdad,
después de revisar el log. Para trabajar en local contra la base de producción,
dejar la simulación en `true` y `VENCIMIENTO_AL_ARRANCAR=false`. Con
`VENCIMIENTO_ACTIVO=false` no corre ni la 01:00 ni el arranque.
