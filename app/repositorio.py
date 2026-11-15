"""Repositorio: todas las consultas y modificaciones sobre la base de datos.

Las rutas no escriben SQL; llaman a estas funciones. Aquí también se aplican
las reglas de integridad: código duplicado (409), materia inexistente (400)
y monitor inexistente (404).
"""

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.errores import ErrorApi
from app.models import Materia, Monitor
from app.schemas import MonitorActualizar, MonitorCrear

# Códigos de error de MySQL para las restricciones de integridad.
MYSQL_DUPLICADO = 1062
MYSQL_LLAVE_FORANEA = 1452


def listar_materias(sesion: Session) -> list[Materia]:
    return list(sesion.scalars(select(Materia).order_by(Materia.id)))


def listar_monitores(sesion: Session) -> list[Monitor]:
    return list(sesion.scalars(select(Monitor).order_by(Monitor.codigo)))


def buscar_monitor(sesion: Session, codigo: str) -> Monitor:
    monitor = sesion.get(Monitor, codigo)
    if monitor is None:
        raise ErrorApi(404, f"No existe un monitor con el código {codigo}")
    return monitor


def crear_monitor(sesion: Session, datos: MonitorCrear) -> Monitor:
    if sesion.get(Monitor, datos.codigo) is not None:
        raise _duplicado(datos.codigo)
    _exigir_materia(sesion, datos.materia_id)

    monitor = Monitor(
        codigo=datos.codigo,
        nombre_completo=datos.nombre_completo,
        correo=datos.correo,
        materia_id=datos.materia_id,
    )
    sesion.add(monitor)
    _confirmar(sesion, datos.codigo, datos.materia_id)
    sesion.refresh(monitor)
    return monitor


def actualizar_monitor(sesion: Session, codigo: str, datos: MonitorActualizar) -> Monitor:
    monitor = buscar_monitor(sesion, codigo)
    _exigir_materia(sesion, datos.materia_id)

    monitor.nombre_completo = datos.nombre_completo
    monitor.correo = datos.correo
    monitor.materia_id = datos.materia_id
    _confirmar(sesion, codigo, datos.materia_id)
    sesion.refresh(monitor)
    return monitor


def eliminar_monitor(sesion: Session, codigo: str) -> None:
    monitor = buscar_monitor(sesion, codigo)
    sesion.delete(monitor)
    sesion.commit()


def _exigir_materia(sesion: Session, materia_id: int) -> None:
    if sesion.get(Materia, materia_id) is None:
        raise _materia_inexistente(materia_id)


def _confirmar(sesion: Session, codigo: str, materia_id: int) -> None:
    """Hace el commit. Si entre la verificación y el guardado otra petición se
    adelantó, MySQL rechaza la operación y aquí se traduce al mismo error."""
    try:
        sesion.commit()
    except IntegrityError as error:
        sesion.rollback()
        codigo_mysql = error.orig.args[0] if error.orig and error.orig.args else None
        if codigo_mysql == MYSQL_DUPLICADO:
            raise _duplicado(codigo)
        if codigo_mysql == MYSQL_LLAVE_FORANEA:
            raise _materia_inexistente(materia_id)
        raise


def _duplicado(codigo: str) -> ErrorApi:
    return ErrorApi(409, f"Ya existe un monitor con el código {codigo}")


def _materia_inexistente(materia_id: int) -> ErrorApi:
    return ErrorApi(
        400,
        "Datos inválidos",
        [f"materia_id: no existe una materia con el id {materia_id}"],
    )
