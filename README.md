# Servidor 2 — Servicio PHP (intermediario / Broker)

Rama `servidor-2-php` del proyecto **Gestión de monitores académicos**. Contiene el tercer componente de la cadena:

```
Interfaz HTML/CSS/JS → Controlador Java (PC1:8080) → Servicio PHP (PC2:8081) → Servicio Python (PC3:8000) → MySQL
```

El Servicio PHP recibe las peticiones REST del Controlador Java, localiza qué servicio atiende el recurso pedido y le reenvía la petición. La respuesta vuelve por el mismo camino sin modificarse. **No valida reglas de negocio ni toca la base de datos**: lo primero es responsabilidad de Java y de Python, lo segundo solo de Python.

Está hecho en PHP 8.3 puro (sin Composer ni frameworks) sobre Apache, y se ejecuta con Docker.

## Por qué actúa como Broker

Un intermediario que solo copiara peticiones a una dirección fija sería un proxy inverso. Este servicio aplica el patrón **Broker** de POSA porque tiene un **registro de servicios** y decide el destino a partir de él:

| Participante del patrón | En este proyecto |
|---|---|
| Cliente | Controlador Java: solo conoce la dirección del PHP |
| Broker | Este servicio: registro de servicios + tabla de rutas + reenvío |
| Servidor | Servicio Python, que atiende los servicios `monitores` y `materias` |

- **Registro de servicios** (`config/servicios.php` y `src/RegistroServicios.php`): cada servicio tiene su propia entrada con la URL del servidor que lo atiende (`monitores` → `SERVICIO_MONITORES_URL`, `materias` → `SERVICIO_MATERIAS_URL`). En el despliegue las dos apuntan a PC3, pero podrían estar en equipos distintos.
- **Transparencia de localización**: si el Servicio Python cambia de equipo o de puerto, solo se edita el `.env` de este servidor. El Controlador Java y la interfaz no se enteran.
- **Heterogeneidad**: Java y Python se comunican sin conocerse; lo único que comparten es el contrato REST/JSON.

Lo que **no** se implementa del patrón completo: registro dinámico (los servicios no se inscriben solos, se configuran al arrancar), varios servidores para un mismo servicio y puentes entre brokers.

## Estructura

| Archivo | Responsabilidad |
|---|---|
| `public/index.php` | Front controller: recibe todas las peticiones y coordina el flujo |
| `public/.htaccess` | Envía cualquier ruta a `index.php` |
| `config/servicios.php` | Entradas del registro, leídas de variables de entorno |
| `src/RegistroServicios.php` | Devuelve la URL del servidor que atiende un servicio |
| `src/Rutas.php` | Tabla explícita de rutas (método + patrón), igual al contrato |
| `src/Reenviador.php` | Cliente HTTP con cURL que reenvía la petición |
| `src/Respuesta.php` | Escribe la respuesta y los encabezados de trazabilidad |
| `Dockerfile`, `docker-compose.yml` | Imagen `php:8.3-apache` y despliegue |

## Endpoints

Son los mismos del contrato del proyecto (`docs/03-endpoints.md` en la rama `main`):

| Método | Ruta | Servicio destino |
|---|---|---|
| `GET` | `/api/monitores` | monitores |
| `GET` | `/api/monitores/{codigo}` | monitores |
| `POST` | `/api/monitores` | monitores |
| `PUT` | `/api/monitores/{codigo}` | monitores |
| `DELETE` | `/api/monitores/{codigo}` | monitores |
| `GET` | `/api/materias` | materias |
| `GET` | `/api/health` | responde el PHP y consulta al servidor de `monitores` |

Respuestas que genera el propio PHP (siempre con `"origen": "php"` y `X-Recorrido: php`):

| Código | Cuándo |
|---|---|
| `400` | `POST` o `PUT` con cuerpo vacío o JSON mal formado (no se llama al Python) |
| `404` | Ruta que no está en la tabla, o recurso sin servicio registrado |
| `405` | Ruta existente con un método no permitido (incluye el encabezado `Allow`) |
| `500` | Error interno del PHP |
| `502` | El servicio destino no está disponible |
| `504` | El servicio destino no respondió en 10 segundos |

En los demás casos el PHP devuelve **el mismo estado, cuerpo y `Content-Type`** que respondió el Python (por ejemplo el `409` de código duplicado o el `204` sin cuerpo al eliminar).

Trazabilidad:

- `X-Request-Id`: se usa el que llega (lo genera Java); si no llega, el PHP genera uno. Se envía al Python, se devuelve en la respuesta y queda en el log.
- `X-Recorrido`: el PHP antepone su nombre al recorrido que recibe (`php>python`). Si responde sin llegar al Python, queda `php`.

## Variables de entorno

Se definen en el archivo `.env` (no se sube al repositorio; `.env.example` es la plantilla).

| Variable | Descripción | Ejemplo |
|---|---|---|
| `PUERTO` | Puerto de este equipo por el que se publica el servicio | `8081` |
| `SERVICIO_MONITORES_URL` | URL base del servidor que atiende `monitores` (también se usa para `/api/health`) | `http://192.168.1.30:8000` |
| `SERVICIO_MATERIAS_URL` | URL base del servidor que atiende `materias` | `http://192.168.1.30:8000` |

Las URL van sin `/` al final. Se leen cuando arranca el contenedor, así que cambiarlas no requiere reconstruir la imagen.

## Arranque paso a paso (Windows con Docker Desktop)

Requisitos: Git y Docker Desktop instalados, y Docker Desktop **abierto** (ícono de la ballena sin animación).

1. Clonar solo esta rama (PowerShell):

   ```powershell
   git clone --single-branch -b servidor-2-php URL_DEL_REPOSITORIO servidor-2-php
   cd servidor-2-php
   ```

2. Crear el archivo de configuración a partir de la plantilla:

   ```powershell
   Copy-Item .env.example .env
   notepad .env
   ```

   Cambiar `IP_PC3` por la IP real del equipo donde corre el Servicio Python (se consulta con `ipconfig` en ese equipo).

3. Construir la imagen y levantar el servicio. Conviene hacerlo días antes de la entrega, porque la primera vez descarga la imagen base de Internet:

   ```powershell
   docker compose build --pull
   docker compose up -d
   ```

4. Comprobar que está arriba:

   ```powershell
   docker compose ps
   curl.exe -i http://localhost:8081/api/health
   ```

   Con el Servicio Python encendido responde `200`; si el Python no está disponible responde `502` con `"siguiente": null`, lo que de todas formas confirma que el PHP está funcionando.

Orden de arranque de todo el sistema: primero PC3 (Python y MySQL), luego PC2 (este servicio) y por último PC1 (Java e interfaz).

Comandos útiles:

```powershell
docker compose logs -f          # ver el registro de peticiones
docker compose restart          # reiniciar
docker compose up -d            # aplicar cambios hechos en .env
docker compose up -d --build    # aplicar cambios hechos en el código
docker compose down             # detener y eliminar el contenedor
```

### Prueba en un solo equipo

Si el Servicio Python corre en este mismo computador (publicado en el puerto 8000), en el `.env` se usa `host.docker.internal` en lugar de una IP:

```
SERVICIO_MONITORES_URL=http://host.docker.internal:8000
SERVICIO_MATERIAS_URL=http://host.docker.internal:8000
```

No sirve `localhost`, porque dentro del contenedor `localhost` es el propio contenedor.

## Firewall y red (puerto 8081)

Para que el Controlador Java (PC1) pueda llegar a este equipo hay que permitir la entrada al puerto 8081. En **PowerShell como administrador**:

```powershell
# 1. Ver el perfil de la red actual; si aparece "Public", pasarla a privada
Get-NetConnectionProfile
Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Private

# 2. Abrir el puerto del servicio PHP
New-NetFirewallRule -DisplayName "POSA - Servicio PHP 8081" -Direction Inbound -Protocol TCP -LocalPort 8081 -Action Allow

# 3. Revisar que el puerto no esté ocupado por otro programa (no debe mostrar nada antes de levantar el servicio)
Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue
```

En `-InterfaceAlias` va el nombre del adaptador que muestra el primer comando. Si se cambió `PUERTO` en el `.env`, se usa ese mismo número en la regla.

La prueba que de verdad cruza el firewall se hace **desde otro equipo** de la misma red (o un celular), no desde este:

```powershell
curl.exe -i http://IP_PC2:8081/api/health
```

Si desde otro equipo no responde pero en este sí, lo más común es que la red Wi-Fi aísle a los clientes entre sí; en ese caso se puede usar el punto de acceso de un celular para los tres computadores.

## Pruebas con curl.exe

En PowerShell se usa `curl.exe` porque `curl` es un alias de otro comando. Los ejemplos se ejecutan en este equipo; desde otro se cambia `localhost` por la IP de PC2.

```powershell
# Estado del PHP y del servicio siguiente
curl.exe -i http://localhost:8081/api/health

# Listar materias y monitores
curl.exe -i http://localhost:8081/api/materias
curl.exe -i http://localhost:8081/api/monitores

# Registrar un monitor (201; si se repite, 409 con origen "python")
curl.exe -i -X POST http://localhost:8081/api/monitores -H "Content-Type: application/json" -d '{\"codigo\":\"M001\",\"nombre_completo\":\"Ana Gomez\",\"correo\":\"ana.gomez@ucaldas.edu.co\",\"materia_id\":2}'

# Consultar, actualizar y eliminar
curl.exe -i http://localhost:8081/api/monitores/M001
curl.exe -i -X PUT http://localhost:8081/api/monitores/M001 -H "Content-Type: application/json" -d '{\"nombre_completo\":\"Ana Gomez Rios\",\"correo\":\"ana.gomez@ucaldas.edu.co\",\"materia_id\":3}'
curl.exe -i -X DELETE http://localhost:8081/api/monitores/M001

# Enviar un identificador propio y verlo de vuelta en la respuesta y en los logs
curl.exe -i -H "X-Request-Id: prueba-123" http://localhost:8081/api/monitores
```

Para enviar nombres con tildes desde la consola es más seguro guardar el JSON en un archivo UTF-8 y usar `--data-binary "@archivo.json"`, porque la consola de Windows puede cambiar la codificación de lo que se escribe en la línea de comandos.

Respuestas propias del PHP:

```powershell
# 404: ruta que no existe
curl.exe -i http://localhost:8081/api/otra

# 405: método no permitido en una ruta que sí existe
curl.exe -i -X DELETE http://localhost:8081/api/monitores

# 400: JSON mal formado (no llega al Python)
curl.exe -i -X POST http://localhost:8081/api/monitores -H "Content-Type: application/json" -d '{\"codigo\":'

# 502: detener el Servicio Python y repetir cualquier consulta
curl.exe -i http://localhost:8081/api/monitores
```

En cada respuesta se puede revisar el encabezado `X-Recorrido`: `php>python` cuando la petición llegó hasta el Python y `php` cuando la respondió este servicio.

### Demostración de la transparencia de localización

1. Cambiar en PC3 el puerto del Servicio Python (por ejemplo a 8001).
2. En este equipo, editar solo el `.env` (`http://IP_PC3:8001`) y ejecutar `docker compose up -d`.
3. Comprobar que la interfaz y el Controlador Java siguen funcionando sin ningún cambio.

## Registro de peticiones

Cada petición deja una línea en el log del contenedor (`docker compose logs -f`) con el identificador, el método, la ruta, el servicio destino y el estado devuelto:

```
[prueba-123] GET /api/monitores -> servicio=monitores estado=200
```

Buscando el mismo identificador en los logs de Java y de Python se sigue una operación por los tres equipos.
