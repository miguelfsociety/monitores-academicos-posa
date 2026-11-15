"""Conexión a MySQL.

Arma el motor de SQLAlchemy con los datos que llegan por variables de entorno
y entrega una sesión por petición. Al importar este archivo no se abre ninguna
conexión: se abre la primera vez que una petición la necesita.
"""

import os

from sqlalchemy import URL, create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

URL_BD = URL.create(
    "mysql+pymysql",
    username=os.getenv("BD_USUARIO", "monitores"),
    password=os.getenv("BD_CLAVE", ""),
    host=os.getenv("BD_HOST", "bd"),
    port=int(os.getenv("BD_PUERTO", "3306")),
    database=os.getenv("BD_NOMBRE", "monitores"),
    query={"charset": "utf8mb4"},
)

# pool_pre_ping revisa que la conexión siga viva antes de usarla y
# pool_recycle la renueva cada 30 minutos, para que MySQL no la cierre por inactiva.
motor = create_engine(
    URL_BD,
    pool_pre_ping=True,
    pool_recycle=1800,
    connect_args={"connect_timeout": 5},
)

SesionLocal = sessionmaker(bind=motor, expire_on_commit=False)


class Base(DeclarativeBase):
    """Clase base de los modelos."""


def obtener_sesion():
    """Dependencia de FastAPI: abre una sesión y la cierra al terminar la petición."""
    with SesionLocal() as sesion:
        yield sesion
