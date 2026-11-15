"""Punto de entrada del servicio Python (FastAPI).

Responsabilidades de este archivo:
- crear la aplicación y registrar las rutas;
- trazabilidad: X-Request-Id y X-Recorrido en todas las respuestas, y una
  línea de log por petición;
- convertir cualquier error al formato único del contrato.
"""

import logging
import uuid

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from sqlalchemy.exc import InterfaceError, OperationalError
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.routing import Match

from app.errores import ErrorApi, RespuestaJSON, respuesta_error
from app.routers import health, materias, monitores

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
registro = logging.getLogger("servicio-python")

# redirect_slashes=False: una ruta con barra final de más responde 404 en JSON
# en lugar de una redirección 307 que los otros servicios tendrían que seguir.
app = FastAPI(
    title="Servicio Python - Monitores académicos",
    redirect_slashes=False,
    default_response_class=RespuestaJSON,
    # Sin páginas de documentación automática: el contrato dice que cualquier
    # ruta no declarada responde 404 en JSON.
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)

ENRUTADORES = [monitores.router, materias.router, health.router]
for enrutador in ENRUTADORES:
    app.include_router(enrutador)


# ---------------------------------------------------------------- trazabilidad

@app.middleware("http")
async def trazabilidad(request: Request, call_next):
    """Usa el X-Request-Id recibido (o genera uno), lo devuelve junto con
    X-Recorrido y deja en el log el método, la ruta y el estado."""
    id_peticion = request.headers.get("X-Request-Id", "").strip()[:100] or str(uuid.uuid4())
    try:
        respuesta = await call_next(request)
    except Exception:
        # Error no previsto: se registra completo y se responde 500 uniforme.
        registro.exception("[%s] Error interno no controlado", id_peticion)
        respuesta = respuesta_error(500, "Error interno del servicio Python")

    respuesta.headers["X-Request-Id"] = id_peticion
    respuesta.headers["X-Recorrido"] = "python"
    registro.info(
        "[%s] %s %s -> %s",
        id_peticion, request.method, request.url.path, respuesta.status_code,
    )
    return respuesta


# ------------------------------------------------------- manejadores de errores

MENSAJES_HTTP = {
    400: "No se pudo leer el cuerpo de la petición (debe ser JSON en UTF-8)",
    404: "Ruta no encontrada",
    405: "Método no permitido para esta ruta",
}


@app.exception_handler(ErrorApi)
async def manejar_error_api(request: Request, error: ErrorApi):
    return respuesta_error(error.estado, error.mensaje, error.detalles)


@app.exception_handler(StarletteHTTPException)
async def manejar_error_http(request: Request, error: StarletteHTTPException):
    """Rutas inexistentes (404) y métodos no permitidos (405), también en JSON."""
    mensaje = MENSAJES_HTTP.get(error.status_code, str(error.detail))
    encabezados = None
    if error.status_code == 405:
        encabezados = {"Allow": ", ".join(_metodos_permitidos(request))}
    return respuesta_error(error.status_code, mensaje, encabezados=encabezados)


def _metodos_permitidos(request: Request) -> list[str]:
    """Reúne los métodos de todas las rutas declaradas que coinciden con la URL."""
    metodos = set()
    for enrutador in ENRUTADORES:
        for ruta in enrutador.routes:
            coincidencia, _ = ruta.matches(request.scope)
            if coincidencia != Match.NONE:
                metodos.update(ruta.methods or [])
    return sorted(metodos)


@app.exception_handler(RequestValidationError)
async def manejar_validacion(request: Request, error: RequestValidationError):
    """Datos incompletos o inválidos: 400 (FastAPI respondería 422 por defecto)."""
    errores = error.errors()
    if any(e["type"] == "json_invalid" for e in errores):
        return respuesta_error(400, "JSON mal formado", ["cuerpo: no es un JSON válido"])
    return respuesta_error(400, "Datos inválidos", [_describir(e) for e in errores])


@app.exception_handler(OperationalError)
@app.exception_handler(InterfaceError)
async def manejar_bd_caida(request: Request, error: Exception):
    registro.error("Fallo de conexión con la base de datos: %s", error.__class__.__name__)
    return respuesta_error(503, "El servicio Python no puede conectarse a la base de datos")


def _describir(error: dict) -> str:
    """Convierte un error de Pydantic en un texto "campo: mensaje" en español."""
    campo = ".".join(str(parte) for parte in error["loc"] if parte != "body") or "cuerpo"
    tipo = error["type"]
    contexto = error.get("ctx", {})

    if tipo == "missing":
        mensaje = "es obligatorio"
    elif tipo == "value_error":
        # Mensajes escritos en los validadores de schemas.py.
        mensaje = str(contexto.get("error", "valor inválido"))
    elif tipo in ("string_type", "bytes_type"):
        mensaje = "debe ser un texto"
    elif tipo in ("int_type", "int_parsing", "int_from_float"):
        mensaje = "debe ser un número entero"
    elif tipo in ("model_attributes_type", "dict_type"):
        mensaje = "debe ser un objeto JSON"
    else:
        mensaje = "valor inválido"
    return f"{campo}: {mensaje}"
