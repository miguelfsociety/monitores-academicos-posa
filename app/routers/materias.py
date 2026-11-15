"""Ruta de materias: alimenta el selector del formulario de la interfaz."""

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app import repositorio
from app.db import obtener_sesion
from app.schemas import MateriaSalida

router = APIRouter(prefix="/api/materias", tags=["materias"])


@router.get("", response_model=list[MateriaSalida])
def listar(sesion: Session = Depends(obtener_sesion)):
    return repositorio.listar_materias(sesion)
