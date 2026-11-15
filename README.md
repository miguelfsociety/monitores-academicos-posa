# Servidor 3 — Servicio Python + Base de datos MySQL

Rama `servidor-3-python-bd` del proyecto **Gestión de monitores académicos**.
Contiene los componentes 4 y 5 de la arquitectura, que se despliegan juntos en el PC3:

```
Interfaz HTML/CSS/JS -> Controlador Java (PC1:8080) -> Servicio PHP (PC2:8081) -> Servicio Python (PC3:8000) -> MySQL (PC3)
```

- **Servicio Python** (FastAPI): es la capa de datos. Recibe las peticiones REST que le
  reenvía el Servicio PHP y hace las consultas y modificaciones sobre la base de datos.
- **MySQL 8.4**: guarda las tablas `materias` y `monitores`. Su puerto (3306) **no se
  publica**: solo el servicio Python puede llegar a la base de datos.

## Estructura

| Ruta | Contenido |
|---|---|
| `bd/init/01_esquema.sql` | Creación de las tablas |
| `bd/init/02_datos.sql` | Datos semilla: 6 materias y 2 monitores de ejemplo (`M100`, `M101`) |
| `app/main.py` | Aplicación FastAPI, trazabilidad (`X-Request-Id`, `X-Recorrido`) y manejo de errores |
| `app/db.py` | Conexión a MySQL (SQLAlchemy + PyMySQL) |
| `app/models.py` | Modelos de las tablas |
| `app/schemas.py` | Formato de los JSON y reglas de validación |
| `app/repositorio.py` | Consultas y modificaciones sobre la base de datos |
| `app/errores.py` | Formato único de error |
| `app/routers/` | Rutas: `monitores.py`, `materias.py`, `health.py` |
| `Dockerfile`, `docker-compose.yml` | Despliegue con Docker |
| `.env.example` | Plantilla de las variables de entorno |

## Endpoints

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| GET | `/api/monitores` | Lista los monitores ordenados por código | 200 |
| GET | `/api/monitores/{codigo}` | Consulta un monitor | 200, 404 |
| POST | `/api/monitores` | Registra un monitor | 201, 400, 409 |
| PUT | `/api/monitores/{codigo}` | Actualiza nombre, correo y materia | 200, 400, 404 |
| DELETE | `/api/monitores/{codigo}` | Elimina un monitor | 204, 404 |
| GET | `/api/materias` | Lista las materias | 200 |
| GET | `/api/health` | Estado del servicio y de la base de datos | 200, 503 |

Cualquier otra ruta responde 404 y cualquier otro método 405, también en JSON.
Los errores siempre tienen la forma `{"error": "...", "detalles": [...], "origen": "python"}`.
Todas las respuestas llevan los encabezados `X-Request-Id` y `X-Recorrido: python`.

## Requisitos

- Windows 10/11 con **Docker Desktop** instalado y encendido.
- Git.
- El puerto 8000 libre (se puede cambiar en el archivo `.env`).

No hace falta instalar Python ni MySQL: todo corre dentro de los contenedores.

## Arranque paso a paso (Windows, PowerShell)

1. Clonar solo esta rama y entrar a la carpeta:

   ```powershell
   git clone --single-branch -b servidor-3-python-bd <URL_DEL_REPOSITORIO> servidor-3
   cd servidor-3
   ```

2. Crear el archivo de configuración a partir de la plantilla y cambiar las claves:

   ```powershell
   Copy-Item .env.example .env
   notepad .env
   ```

3. Abrir Docker Desktop y esperar a que diga que el motor está en ejecución.

4. Construir y levantar los contenedores:

   ```powershell
   docker compose build --pull
   docker compose up -d
   ```

   La primera vez tarda un poco más: se descargan las imágenes y MySQL crea las tablas
   y carga los datos semilla. El servicio Python espera a que la base de datos esté
   lista antes de arrancar.

5. Comprobar que quedó funcionando:

   ```powershell
   docker compose ps
   curl.exe -i http://localhost:8000/api/health
   ```

   Debe responder `200` con `{"servicio":"python","estado":"ok","bd":"ok"}`.

Para detener el servicio sin perder los datos: `docker compose down`.
Para ver el registro de peticiones: `docker compose logs -f servicio-python`.

Es recomendable hacer el `docker compose build --pull` unos días antes de la
sustentación; ese día basta con `docker compose up -d` y no se depende de Internet.

## Variables de entorno (`.env`)

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `PUERTO` | `8000` | Puerto del equipo por el que se publica el servicio Python |
| `MYSQL_DATABASE` | `monitores` | Nombre de la base de datos |
| `MYSQL_USER` | `monitores` | Usuario (no administrador) con el que se conecta el servicio Python |
| `MYSQL_PASSWORD` | — (obligatoria) | Clave de ese usuario |
| `MYSQL_ROOT_PASSWORD` | — (obligatoria) | Clave del administrador de MySQL |

El archivo `.env` no se sube al repositorio. Las variables se leen cuando arrancan los
contenedores, no al construir la imagen. El usuario, la clave y el nombre de la base de
datos solo se aplican al crear la base de datos por primera vez; para cambiarlos después
hay que reiniciarla (ver más abajo).

## Firewall: permitir el puerto 8000

El Servicio PHP está en otro computador (PC2), así que Windows debe dejar entrar las
conexiones al puerto 8000. En **PowerShell como administrador**:

```powershell
# La red debe estar como "Privada"
Get-NetConnectionProfile
Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Private

# Regla de entrada para el servicio Python
New-NetFirewallRule -DisplayName "POSA Servicio Python 8000" -Direction Inbound -Protocol TCP -LocalPort 8000 -Action Allow -Profile Private
```

(Cambiar `"Wi-Fi"` por el nombre del adaptador que muestre el primer comando.)

Para saber la IP de este equipo: `ipconfig` (dirección IPv4 del adaptador en uso). Esa
IP es la que se escribe en el `.env` del Servicio PHP.

La prueba que vale se hace **desde otro equipo** de la misma red (o un celular), porque
probar con la IP propia no pasa por el firewall:

```powershell
curl.exe -i http://IP_PC3:8000/api/health
```

El puerto 3306 de MySQL no se abre ni se publica: no hay que crear ninguna regla para él.

## Reiniciar la base de datos

Los scripts de `bd/init/` solo se ejecutan cuando el volumen de datos está vacío. Si se
cambia el esquema, los datos semilla o las credenciales, o si se quiere volver al estado
inicial, hay que borrar el volumen:

```powershell
docker compose down -v
docker compose up -d
```

**Atención:** `down -v` borra todos los monitores registrados. `docker compose down`
(sin `-v`) conserva los datos.

## Pruebas con curl.exe

En PowerShell se escribe `curl.exe` (con la extensión) porque `curl` es un alias de otro
comando. En los comandos que envían JSON se pone `--%` justo después de `curl.exe`: le
indica a PowerShell que pase el resto de la línea tal cual, para que no altere las comillas
escapadas (`\"`). En el símbolo del sistema (cmd) se usan los mismos comandos sin el `--%`.

```powershell
# Estado del servicio y de la base de datos
curl.exe -i http://localhost:8000/api/health

# Listar materias y monitores
curl.exe -i http://localhost:8000/api/materias
curl.exe -i http://localhost:8000/api/monitores

# Registrar un monitor (201)
curl.exe --% -i -X POST http://localhost:8000/api/monitores -H "Content-Type: application/json" -d "{\"codigo\":\"M001\",\"nombre_completo\":\"Ana Gomez\",\"correo\":\"ana.gomez@ucaldas.edu.co\",\"materia_id\":2}"

# Repetir el mismo registro: código duplicado (409)
curl.exe --% -i -X POST http://localhost:8000/api/monitores -H "Content-Type: application/json" -d "{\"codigo\":\"M001\",\"nombre_completo\":\"Ana Gomez\",\"correo\":\"ana.gomez@ucaldas.edu.co\",\"materia_id\":2}"

# Datos incompletos (400)
curl.exe --% -i -X POST http://localhost:8000/api/monitores -H "Content-Type: application/json" -d "{\"codigo\":\"M002\"}"

# Materia que no existe (400)
curl.exe --% -i -X POST http://localhost:8000/api/monitores -H "Content-Type: application/json" -d "{\"codigo\":\"M002\",\"nombre_completo\":\"Luis Rios\",\"correo\":\"luis.rios@ucaldas.edu.co\",\"materia_id\":99}"

# Consultar un monitor (200) y uno que no existe (404)
curl.exe -i http://localhost:8000/api/monitores/M001
curl.exe -i http://localhost:8000/api/monitores/NOEXISTE

# Actualizar (200)
curl.exe --% -i -X PUT http://localhost:8000/api/monitores/M001 -H "Content-Type: application/json" -d "{\"nombre_completo\":\"Ana Gomez Rios\",\"correo\":\"ana.gomez@ucaldas.edu.co\",\"materia_id\":3}"

# Intentar cambiar el código (400)
curl.exe --% -i -X PUT http://localhost:8000/api/monitores/M001 -H "Content-Type: application/json" -d "{\"codigo\":\"M999\",\"nombre_completo\":\"Ana Gomez\",\"correo\":\"ana.gomez@ucaldas.edu.co\",\"materia_id\":3}"

# Eliminar (204, sin cuerpo) y volver a eliminar (404)
curl.exe -i -X DELETE http://localhost:8000/api/monitores/M001
curl.exe -i -X DELETE http://localhost:8000/api/monitores/M001

# Ruta inexistente (404) y método no permitido (405), ambos en JSON
curl.exe -i http://localhost:8000/api/otra
curl.exe -i -X PATCH http://localhost:8000/api/monitores/M100

# Enviar un X-Request-Id propio: vuelve en la respuesta y queda en el log
curl.exe -i http://localhost:8000/api/monitores -H "X-Request-Id: prueba-123"
docker compose logs --tail 5 servicio-python
```

Si la consola muestra las tildes con símbolos raros es un asunto de la consola de
Windows, no del servicio: las respuestas van en UTF-8 (`Content-Type: application/json; charset=utf-8`).
Se corrige con `chcp 65001` antes de ejecutar los comandos.

## Problemas frecuentes

| Síntoma | Causa probable y solución |
|---|---|
| `Falta MYSQL_PASSWORD en el archivo .env` | No se creó el `.env` (paso 2) |
| `port is already allocated` | El puerto 8000 está ocupado: cambiar `PUERTO` en `.env` y volver a ejecutar `docker compose up -d` |
| `/api/health` responde 503 | La base de datos no está arriba: revisar `docker compose ps` y `docker compose logs bd` |
| Se cambió la clave en `.env` y el servicio ya no conecta | La base de datos conserva la clave anterior: `docker compose down -v` y `up -d` |
| Desde otro equipo no responde, pero en `localhost` sí | Firewall o red en perfil "Público": revisar la sección del firewall |
