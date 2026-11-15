# Servidor 1: Interfaz web + Controlador Java

Rama `servidor-1-web-java` del proyecto **Gestión de monitores académicos** (Universidad de Caldas, Ingeniería de Software III). Contiene los dos primeros componentes de la cadena, que se despliegan en el computador 1 (PC1):

```
Interfaz HTML/CSS/JS  →  Controlador Java  →  Servicio PHP  →  Servicio Python  →  Base de datos
      (PC1)                 (PC1:8080)          (PC2:8081)        (PC3:8000)          (PC3)
```

## Qué contiene

| Ruta | Descripción |
|---|---|
| `interfaz/` | Componente 1. Página única en HTML, CSS y JavaScript puro (`index.html`, `css/estilos.css`, `js/app.js`). Solo hace peticiones a rutas relativas `/api/...`; no conoce al PHP ni al Python. |
| `controlador-java/` | Componente 2. Proyecto Maven con Spring Boot 4 y Java 21. |
| `Dockerfile` | Compila el controlador con Maven y arma la imagen final con el JAR y la interfaz. |
| `docker-compose.yml` | Levanta el contenedor y publica el puerto 8080. |
| `.env.example` | Plantilla de las variables de entorno. |

### Organización del Controlador Java

Paquete base `co.edu.ucaldas.monitores`:

| Paquete | Clases | Responsabilidad |
|---|---|---|
| `controller` | `MonitorController`, `MateriaController`, `HealthController`, `Reenvio` | Reciben las solicitudes de la interfaz y devuelven la respuesta que entregó el PHP. |
| `dto` | `MonitorRequest`, `MonitorUpdateRequest`, `ErrorRespuesta` | Datos de entrada con sus reglas de validación y formato único de error. |
| `client` | `ServicioPhpClient` | Único punto que se comunica con el servicio PHP. |
| `exception` | `ManejadorGlobal`, `DatosInvalidosException` | Convierte los errores en respuestas JSON uniformes. |
| `config` | `ServicioPhpConfig`, `FiltroRequestId`, `Trazabilidad` | URL del PHP, tiempos de espera y encabezado `X-Request-Id`. |

### Qué hace el Controlador Java con cada petición

1. El filtro asigna un `X-Request-Id` (o respeta el que venga), lo devuelve en la respuesta y lo escribe en el log junto con el método, la ruta y el estado.
2. Valida la información básica: campos obligatorios, longitudes, formato del correo, código sin espacios, `materia_id` mayor o igual a 1 y, en el `PUT`, que el código del cuerpo (si viene) sea igual al de la ruta. Si algo falla responde `400` con `"origen": "java"` y **no llama al PHP**.
3. Si los datos son válidos, envía la petición al PHP con el mismo `X-Request-Id` y reenvía a la interfaz el estado y el cuerpo tal como llegaron (por ejemplo un `409` por código duplicado o un `404`).
4. Antepone su nombre al encabezado `X-Recorrido` (`java>php>python`). Si respondió sin llegar al PHP, el recorrido es solo `java`.
5. Si el PHP no está disponible responde `502`; si no contesta a tiempo (3 s para conectar, 10 s para responder), `504`.

El código duplicado no se valida aquí porque Java no tiene los datos: lo detecta el servicio Python y Java solo propaga el `409`.

## Requisitos

- Windows 10/11 con **Docker Desktop** instalado y encendido.
- Git.
- No se necesita instalar Java ni Maven: la compilación ocurre dentro de Docker.

## Arranque paso a paso (Windows, PowerShell)

1. Clonar solo esta rama:

   ```powershell
   git clone --single-branch -b servidor-1-web-java URL_DEL_REPOSITORIO servidor-1
   cd servidor-1
   ```

2. Crear el archivo `.env` a partir de la plantilla y escribir la IP del computador donde corre el PHP (PC2):

   ```powershell
   Copy-Item .env.example .env
   notepad .env
   ```

   ```
   PHP_SERVICE_URL=http://192.168.1.20:8081
   PUERTO=8080
   ```

3. Construir la imagen y levantar el contenedor. La primera vez descarga las dependencias de Maven, por lo que requiere Internet y tarda unos minutos; conviene hacerlo antes del día de la sustentación.

   ```powershell
   docker compose build --pull
   docker compose up -d
   ```

4. Comprobar que arrancó:

   ```powershell
   docker compose ps
   docker compose logs -f
   curl.exe -i http://localhost:8080/api/health
   ```

5. Abrir la interfaz en el navegador: `http://localhost:8080` (o `http://IP_PC1:8080` desde otro equipo).

Para detenerlo: `docker compose down`. Si se cambia el `.env`, basta con `docker compose up -d` para que tome el nuevo valor (no hay que reconstruir la imagen). Si se cambia el código o la interfaz: `docker compose up -d --build`.

El orden recomendado para levantar todo el sistema es PC3 (Python y base de datos), luego PC2 (PHP) y por último PC1.

## Variables de entorno

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `PHP_SERVICE_URL` | `http://host.docker.internal:8081` | URL base del servicio PHP. Se lee al arrancar el contenedor, no al compilar. |
| `PUERTO` | `8080` | Puerto de este computador por el que se publica la interfaz y la API. |

Para probar los tres servicios en un mismo equipo se usa `PHP_SERVICE_URL=http://host.docker.internal:8081`: dentro del contenedor, `localhost` es el propio contenedor y no el computador.

## Firewall de Windows (puerto 8080)

Para que otros equipos puedan abrir la interfaz hay que permitir el puerto. En PowerShell **como administrador**:

```powershell
# La red debe estar en perfil Privado
Get-NetConnectionProfile
Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Private

# Regla de entrada para el puerto 8080
New-NetFirewallRule -DisplayName "POSA Controlador Java 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow

# Verificar que el puerto no esté ocupado por otro programa
netstat -ano | findstr ":8080"
```

La prueba real se hace desde **otro** equipo o un celular en la misma red (`http://IP_PC1:8080/api/health`); probar con la IP propia no pasa por el firewall. La IP se consulta con `ipconfig`. Si la red Wi-Fi aísla los equipos entre sí, se puede usar la zona Wi-Fi de un celular.

## Pruebas

En PowerShell se usa `curl.exe` porque `curl` es un alias de otro comando.

```powershell
# Estado de toda la cadena
curl.exe -i http://localhost:8080/api/health

# Listar materias y monitores
curl.exe -i http://localhost:8080/api/materias
curl.exe -i http://localhost:8080/api/monitores

# Registrar (201) y repetir para ver el duplicado (409, origen python)
curl.exe -i -X POST http://localhost:8080/api/monitores -H "Content-Type: application/json" -d "{\"codigo\":\"M001\",\"nombre_completo\":\"Ana Gomez\",\"correo\":\"ana.gomez@ucaldas.edu.co\",\"materia_id\":2}"

# Consultar, actualizar y eliminar
curl.exe -i http://localhost:8080/api/monitores/M001
curl.exe -i -X PUT http://localhost:8080/api/monitores/M001 -H "Content-Type: application/json" -d "{\"nombre_completo\":\"Ana Gomez Rios\",\"correo\":\"ana.gomez@ucaldas.edu.co\",\"materia_id\":3}"
curl.exe -i -X DELETE http://localhost:8080/api/monitores/M001

# Validación en Java (400, origen java, X-Recorrido: java)
curl.exe -i -X POST http://localhost:8080/api/monitores -H "Content-Type: application/json" -d "{\"codigo\":\"M 1\",\"correo\":\"malo\"}"

# PUT con un código distinto al de la ruta (400)
curl.exe -i -X PUT http://localhost:8080/api/monitores/M001 -H "Content-Type: application/json" -d "{\"codigo\":\"M002\",\"nombre_completo\":\"Ana\",\"correo\":\"ana@ucaldas.edu.co\",\"materia_id\":2}"

# Ruta inexistente (404) y método no permitido (405), ambos en JSON
curl.exe -i http://localhost:8080/api/otra
curl.exe -i -X DELETE http://localhost:8080/api/monitores
```

Qué revisar en las respuestas:

- `X-Recorrido: java>php>python` cuando la operación llegó hasta el final, y solo `java` cuando Java la rechazó.
- `X-Request-Id`: el mismo valor aparece en `docker compose logs` de los tres servidores.
- Con el PHP detenido, cualquier operación responde `502` con `"origen": "java"` y `/api/health` muestra `"siguiente": null`.

En la interfaz se pueden ejecutar las mismas operaciones: registrar, listar, consultar por código, editar y eliminar. La barra superior muestra el recorrido de la última operación y el botón **Probar conexión** indica el estado de cada eslabón.

## Problemas frecuentes

| Síntoma | Causa probable |
|---|---|
| La interfaz muestra "El servicio PHP no está disponible" (HTTP 502, detectado por: Controlador Java) | El PHP está apagado, la IP del `.env` es incorrecta o el firewall de PC2 bloquea el 8081. |
| Error 504 | El PHP recibe la conexión pero no responde en 10 segundos. |
| `port is already allocated` al levantar | Otro programa usa el 8080: cambiar `PUERTO` en el `.env`. |
| Desde otro equipo no abre la página | Falta la regla del firewall o la red está en perfil Público. |
