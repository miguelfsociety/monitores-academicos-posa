# Documentación de endpoints y métodos HTTP

Los tres servicios (Controlador Java, Servicio PHP y Servicio Python) exponen **el mismo contrato REST**. Lo único que cambia entre ellos es el host y el puerto, y la responsabilidad de cada uno frente a la petición.

| Servicio | Equipo | URL base | Quién lo consume | Responsabilidad |
|---|---|---|---|---|
| Controlador Java | PC1 | `http://IP_PC1:8080` | Interfaz HTML/CSS/JS (navegador) | Recibe las solicitudes de la interfaz, valida la información básica y envía la petición al PHP |
| Servicio PHP | PC2 | `http://IP_PC2:8081` | Controlador Java | Intermediario: localiza el servicio que atiende el recurso y reenvía la petición al Python |
| Servicio Python | PC3 | `http://IP_PC3:8000` | Servicio PHP | Persistencia: consultas y modificaciones sobre la base de datos |
| MySQL | PC3 | puerto 3306, **no publicado** | Servicio Python | Almacena monitores y materias |

## Tipos de comunicación

| Enlace | Protocolo | Formato |
|---|---|---|
| Interfaz → Controlador Java | HTTP (REST) | JSON |
| Controlador Java → Servicio PHP | HTTP (REST) | JSON |
| Servicio PHP → Servicio Python | HTTP (REST) | JSON |
| Servicio Python → MySQL | Protocolo de MySQL (SQL sobre TCP), solo dentro de PC3 | — |

Los tres saltos entre componentes de aplicación son REST con JSON. El último enlace es el acceso a datos que el enunciado asigna al servicio Python ("gestionando las consultas y modificaciones sobre la base de datos"); la base de datos no es alcanzable desde ningún otro componente.

## Endpoints

| # | Método | Ruta | Requisito | Descripción | Éxito | Errores |
|---|---|---|---|---|---|---|
| 1 | `GET` | `/api/monitores` | RF2 | Lista todos los monitores, ordenados por código | `200` | `502`, `504` |
| 2 | `GET` | `/api/monitores/{codigo}` | RF3 | Consulta un monitor por su código | `200` | `404`, `502`, `504` |
| 3 | `POST` | `/api/monitores` | RF1, RF6 | Registra un monitor | `201` | `400`, `409`, `502`, `504` |
| 4 | `PUT` | `/api/monitores/{codigo}` | RF4, RF6 | Actualiza nombre, correo y materia de un monitor | `200` | `400`, `404`, `502`, `504` |
| 5 | `DELETE` | `/api/monitores/{codigo}` | RF5 | Elimina un monitor | `204` (sin cuerpo) | `404`, `502`, `504` |
| 6 | `GET` | `/api/materias` | — | Lista las materias (para el selector del formulario) | `200` | `502`, `504` |
| 7 | `GET` | `/api/health` | — | Estado del servicio y de los que tiene detrás | `200` | `502`, `503` |

Cualquier otra ruta responde `404` y cualquier otro método sobre una ruta existente responde `405`, ambos en JSON.

## Modelo de datos en JSON

### Monitor (respuesta)

```json
{
  "codigo": "M001",
  "nombre_completo": "Ana María Gómez",
  "correo": "ana.gomez@ucaldas.edu.co",
  "materia_id": 2,
  "materia": { "id": 2, "codigo": "IS3", "nombre": "Ingeniería de Software III" }
}
```

### Monitor (cuerpo de `POST`)

```json
{
  "codigo": "M001",
  "nombre_completo": "Ana María Gómez",
  "correo": "ana.gomez@ucaldas.edu.co",
  "materia_id": 2
}
```

### Monitor (cuerpo de `PUT`)

```json
{
  "nombre_completo": "Ana María Gómez Ríos",
  "correo": "ana.gomez@ucaldas.edu.co",
  "materia_id": 3
}
```

El código es el identificador del monitor y **no se modifica**: para cambiarlo se elimina el registro y se crea de nuevo. Si el cuerpo del `PUT` incluye `codigo`, debe ser igual al de la ruta; de lo contrario se responde `400`.

### Materia

```json
{ "id": 2, "codigo": "IS3", "nombre": "Ingeniería de Software III" }
```

### Reglas de validación

| Campo | Regla |
|---|---|
| `codigo` | Obligatorio, de 1 a 20 caracteres, sin espacios |
| `nombre_completo` | Obligatorio, no puede quedar en blanco, máximo 120 caracteres |
| `correo` | Obligatorio, formato de correo electrónico válido, máximo 120 caracteres |
| `materia_id` | Obligatorio, entero mayor o igual a 1 que corresponda a una materia existente |

| Validación | Dónde se hace | Respuesta |
|---|---|---|
| Campos incompletos o con formato inválido | Controlador Java (y de nuevo en Python, por si se le llama directamente) | `400` |
| JSON mal formado | El primer servicio que lo recibe | `400` |
| Código duplicado | Servicio Python + llave primaria en MySQL | `409` |
| Materia inexistente | Servicio Python + llave foránea en MySQL | `400` |
| Monitor inexistente | Servicio Python | `404` |

### Formato único de error

Todos los servicios responden los errores con la misma estructura. El campo `origen` indica qué componente detectó el problema.

```json
{
  "error": "Ya existe un monitor con el código M001",
  "detalles": [],
  "origen": "python"
}
```

```json
{
  "error": "Datos inválidos",
  "detalles": ["correo: debe ser un correo electrónico válido", "nombre_completo: es obligatorio"],
  "origen": "java"
}
```

| Código | Significado en este sistema |
|---|---|
| `400` | Datos incompletos o inválidos, JSON mal formado, materia inexistente |
| `404` | Monitor, ruta o servicio no encontrado |
| `405` | Método no permitido para esa ruta |
| `409` | Código de monitor duplicado |
| `500` | Error interno del servicio indicado en `origen` |
| `502` | El servicio siguiente en la cadena no está disponible |
| `503` | El servicio Python no puede conectarse a la base de datos |
| `504` | El servicio siguiente no respondió a tiempo |

## Encabezados de trazabilidad

| Encabezado | Sentido | Descripción |
|---|---|---|
| `X-Request-Id` | Petición y respuesta | Identificador único de la petición. Lo genera el primer servicio que la recibe (normalmente Java), se propaga sin cambios hasta Python y cada servicio lo escribe en su registro (log). Permite seguir una misma operación en los tres equipos. |
| `X-Recorrido` | Respuesta | Lista de los servicios que atendieron la petición. Python responde `python`; PHP antepone su nombre (`php>python`); Java antepone el suyo (`java>php>python`). Si un servicio responde sin llamar al siguiente (por ejemplo, Java al rechazar datos inválidos), el recorrido termina en él (`java`). |

La interfaz muestra el recorrido de cada operación, lo que evidencia que el CRUD atraviesa todos los componentes de la arquitectura.

## Endpoint de salud

Cada servicio responde por sí mismo y consulta al siguiente.

Respuesta del servicio Python (`200`, o `503` si no hay conexión con la base de datos):

```json
{ "servicio": "python", "estado": "ok", "bd": "ok" }
```

Respuesta del Controlador Java cuando toda la cadena funciona (`200`):

```json
{
  "servicio": "java",
  "estado": "ok",
  "siguiente": {
    "servicio": "php",
    "estado": "ok",
    "siguiente": { "servicio": "python", "estado": "ok", "bd": "ok" }
  }
}
```

Si un eslabón falla, el servicio que lo detecta responde `502` con `"estado": "error"` y en `siguiente` deja lo que haya recibido (o `null` si no hubo respuesta), de modo que se ve en qué punto se rompió la cadena.

## Ejemplos

Los ejemplos usan el Controlador Java; son idénticos contra PHP (`:8081`) y Python (`:8000`). En Windows se usa `curl.exe` porque en PowerShell `curl` es un alias de otro comando; cuando el comando lleva un cuerpo JSON se añade `--%` para que PowerShell no altere las comillas.

```bash
# Listar monitores (RF2)
curl.exe -i http://IP_PC1:8080/api/monitores

# Registrar un monitor (RF1)
curl.exe --% -i -X POST http://IP_PC1:8080/api/monitores -H "Content-Type: application/json" -d "{\"codigo\":\"M001\",\"nombre_completo\":\"Ana Gomez\",\"correo\":\"ana.gomez@ucaldas.edu.co\",\"materia_id\":2}"

# Consultar un monitor por código (RF3)
curl.exe -i http://IP_PC1:8080/api/monitores/M001

# Actualizar un monitor (RF4)
curl.exe --% -i -X PUT http://IP_PC1:8080/api/monitores/M001 -H "Content-Type: application/json" -d "{\"nombre_completo\":\"Ana Gomez Rios\",\"correo\":\"ana.gomez@ucaldas.edu.co\",\"materia_id\":3}"

# Eliminar un monitor (RF5)
curl.exe -i -X DELETE http://IP_PC1:8080/api/monitores/M001

# Listar materias
curl.exe -i http://IP_PC1:8080/api/materias

# Estado de toda la cadena
curl.exe -i http://IP_PC1:8080/api/health
```

Ejemplo de respuesta al registrar (`201 Created`):

```http
HTTP/1.1 201
Content-Type: application/json
X-Request-Id: 3f0c2a6e-8b1d-4a57-9c55-0f6f2f6f8f11
X-Recorrido: java>php>python

{"codigo":"M001","nombre_completo":"Ana Gomez","correo":"ana.gomez@ucaldas.edu.co","materia_id":2,"materia":{"id":2,"codigo":"IS3","nombre":"Ingeniería de Software III"}}
```
