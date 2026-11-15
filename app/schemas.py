"""Esquemas de Pydantic: forma de los JSON de entrada y de salida.

Aquí están las reglas de validación del contrato. El Controlador Java ya las
revisa, pero se repiten por si alguien llama directamente a este servicio.
"""

from email_validator import EmailNotValidError, validate_email
from pydantic import BaseModel, ConfigDict, StrictInt, field_validator

LARGO_CODIGO = 20
LARGO_TEXTO = 120


class DatosMonitor(BaseModel):
    """Campos que se pueden modificar de un monitor, con sus reglas."""

    nombre_completo: str
    correo: str
    materia_id: StrictInt

    @field_validator("nombre_completo")
    @classmethod
    def validar_nombre(cls, valor: str) -> str:
        valor = valor.strip()
        if not valor:
            raise ValueError("no puede quedar en blanco")
        if len(valor) > LARGO_TEXTO:
            raise ValueError(f"no puede superar {LARGO_TEXTO} caracteres")
        return valor

    @field_validator("correo")
    @classmethod
    def validar_correo(cls, valor: str) -> str:
        valor = valor.strip()
        if not valor:
            raise ValueError("es obligatorio")
        if len(valor) > LARGO_TEXTO:
            raise ValueError(f"no puede superar {LARGO_TEXTO} caracteres")
        try:
            # Solo se revisa el formato; no se consulta si el dominio existe.
            validate_email(valor, check_deliverability=False)
        except EmailNotValidError:
            raise ValueError("debe ser un correo electrónico válido")
        return valor

    @field_validator("materia_id")
    @classmethod
    def validar_materia(cls, valor: int) -> int:
        if valor < 1:
            raise ValueError("debe ser un entero mayor o igual a 1")
        return valor


class MonitorCrear(DatosMonitor):
    """Cuerpo del POST /api/monitores."""

    codigo: str

    @field_validator("codigo")
    @classmethod
    def validar_codigo(cls, valor: str) -> str:
        if not valor:
            raise ValueError("es obligatorio")
        if any(caracter.isspace() for caracter in valor):
            raise ValueError("no puede contener espacios")
        if len(valor) > LARGO_CODIGO:
            raise ValueError(f"no puede superar {LARGO_CODIGO} caracteres")
        return valor


class MonitorActualizar(DatosMonitor):
    """Cuerpo del PUT /api/monitores/{codigo}.

    El código no se modifica. Si viene en el cuerpo, la ruta comprueba que sea
    igual al de la URL.
    """

    codigo: str | None = None


class MateriaSalida(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    codigo: str
    nombre: str


class MonitorSalida(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    codigo: str
    nombre_completo: str
    correo: str
    materia_id: int
    materia: MateriaSalida
