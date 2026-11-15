# Imagen del servicio Python (FastAPI + Uvicorn).
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /srv

# Primero las dependencias: así Docker reutiliza esta capa mientras
# requirements.txt no cambie.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# El servicio no necesita permisos de administrador dentro del contenedor.
RUN useradd --create-home --shell /usr/sbin/nologin servicio
USER servicio

EXPOSE 8000

# --host 0.0.0.0 para aceptar peticiones de otros equipos. El log de acceso de
# Uvicorn se apaga porque la aplicación ya escribe una línea por petición
# con el X-Request-Id.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
