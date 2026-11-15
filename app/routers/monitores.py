"""Rutas del CRUD de monitores (RF1 a RF6)."""

from fastapi import APIRouter, Depends, Response
from sqlalchemy.orm import Session

from app import repositorio
from app.db import obtener_sesion
from app.errores import ErrorApi
from app.schemas import MonitorActualizar, MonitorCrear, MonitorSalida

# Las colecciones se declaran con "" (sin barra final) para que
# /api/monitores responda directamente y no con una redirección 307.
router = APIRouter(prefix="/api/monitores", tags=["monitores"])


@router.get("", response_model=list[MonitorSalida])
def listar(sesion: Session = Depends(obtener_sesion)):
    return repositorio.listar_monitores(sesion)


@router.get("/{codigo}", response_model=MonitorSalida)
def consultar(codigo: str, sesion: Session = Depends(obtener_sesion)):
    return repositorio.buscar_monitor(sesion, codigo)


@router.post("", response_model=MonitorSalida, status_code=201)
def registrar(datos: MonitorCrear, sesion: Session = Depends(obtener_sesion)):
    return repositorio.crear_monitor(sesion, datos)


@router.put("/{codigo}", response_model=MonitorSalida)
def actualizar(codigo: str, datos: MonitorActualizar, sesion: Session = Depends(obtener_sesion)):
    # El código es el identificador y no se cambia.
    if datos.codigo is not None and datos.codigo != codigo:
        raise ErrorApi(
            400,
            "Datos inválidos",
            ["codigo: no se puede modificar; debe ser igual al de la ruta"],
        )
    return repositorio.actualizar_monitor(sesion, codigo, datos)


@router.delete("/{codigo}", status_code=204)
def eliminar(codigo: str, sesion: Session = Depends(obtener_sesion)):
    repositorio.eliminar_monitor(sesion, codigo)
    return Response(status_code=204)
