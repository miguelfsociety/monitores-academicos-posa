"""Modelos de SQLAlchemy: representan las tablas `materias` y `monitores`."""

from sqlalchemy import ForeignKey, String
from sqlalchemy.dialects.mysql import INTEGER
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base


class Materia(Base):
    __tablename__ = "materias"

    id: Mapped[int] = mapped_column(INTEGER(unsigned=True), primary_key=True, autoincrement=True)
    codigo: Mapped[str] = mapped_column(String(20), unique=True)
    nombre: Mapped[str] = mapped_column(String(120))


class Monitor(Base):
    __tablename__ = "monitores"

    codigo: Mapped[str] = mapped_column(String(20), primary_key=True)
    nombre_completo: Mapped[str] = mapped_column(String(120))
    correo: Mapped[str] = mapped_column(String(120))
    materia_id: Mapped[int] = mapped_column(INTEGER(unsigned=True), ForeignKey("materias.id"))

    # La materia se trae en la misma consulta (JOIN) porque siempre va en la respuesta.
    materia: Mapped[Materia] = relationship(lazy="joined")
