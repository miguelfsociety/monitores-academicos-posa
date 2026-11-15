"""Ruta de salud: indica si el servicio responde y si alcanza la base de datos."""

import logging

from fastapi import APIRouter
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

from app.db import motor
from app.errores import RespuestaJSON

router = APIRouter(prefix="/api/health", tags=["health"])
registro = logging.getLogger("servicio-python")


@router.get("")
def salud():
    try:
        with motor.connect() as conexion:
            conexion.execute(text("SELECT 1"))
    except SQLAlchemyError as error:
        registro.warning("La base de datos no responde: %s", error.__class__.__name__)
        return RespuestaJSON(
            status_code=503,
            content={"servicio": "python", "estado": "error", "bd": "error"},
        )
    return {"servicio": "python", "estado": "ok", "bd": "ok"}
