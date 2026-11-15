"""Errores del servicio y su formato único de respuesta.

Todos los errores salen como {"error": ..., "detalles": [...], "origen": "python"},
igual que en los servicios Java y PHP.
"""

from fastapi.responses import JSONResponse

ORIGEN = "python"


class RespuestaJSON(JSONResponse):
    """Respuesta JSON que declara el charset para que las tildes se lean bien."""

    media_type = "application/json; charset=utf-8"


class ErrorApi(Exception):
    """Error previsto del servicio (no encontrado, duplicado, datos inválidos...)."""

    def __init__(self, estado: int, mensaje: str, detalles: list[str] | None = None):
        super().__init__(mensaje)
        self.estado = estado
        self.mensaje = mensaje
        self.detalles = detalles or []


def respuesta_error(estado: int, mensaje: str, detalles: list[str] | None = None,
                    encabezados: dict | None = None) -> RespuestaJSON:
    """Arma la respuesta de error con la estructura acordada en el contrato."""
    cuerpo = {"error": mensaje, "detalles": detalles or [], "origen": ORIGEN}
    return RespuestaJSON(status_code=estado, content=cuerpo, headers=encabezados)
