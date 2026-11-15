# Gestión de monitores académicos

Aplicación web para administrar los monitores académicos de la Universidad de Caldas y la materia a la que están asociados. Está construida como una **arquitectura distribuida, multicapa y heterogénea**: cinco componentes en lenguajes distintos, comunicados por servicios REST con JSON.

```
Interfaz HTML/CSS/JS  →  Controlador Java  →  Servicio PHP  →  Servicio Python  →  MySQL
     (navegador)            (PC1 :8080)        (PC2 :8081)       (PC3 :8000)        (PC3)
```

## Ramas del repositorio

Cada servidor tiene su propia rama. Cada computador clona únicamente la suya.

| Rama | Equipo | Contenido | Puerto |
|---|---|---|---|
| `main` | — | Documentación y entregables (esta rama) | — |
| `servidor-1-web-java` | PC1 | Interfaz HTML/CSS/JS y Controlador Java (Spring Boot) | 8080 |
| `servidor-2-php` | PC2 | Servicio PHP intermediario | 8081 |
| `servidor-3-python-bd` | PC3 | Servicio Python (FastAPI) y base de datos MySQL | 8000 |

## Entregables

| # | Entregable | Documento |
|---|---|---|
| 1 | Diagrama de arquitectura, componentes, responsabilidades y patrones | [docs/01-arquitectura.md](docs/01-arquitectura.md) |
| 2 | Diagrama de secuencia del registro de un monitor | [docs/02-secuencia-registro.md](docs/02-secuencia-registro.md) |
| 3 | Implementación funcional del CRUD | Ramas `servidor-1-web-java`, `servidor-2-php` y `servidor-3-python-bd` |
| 4 | Documentación de endpoints y métodos HTTP | [docs/03-endpoints.md](docs/03-endpoints.md) |
| 5 | Ventajas y dificultades de la arquitectura distribuida y heterogénea | [docs/04-ventajas-dificultades.md](docs/04-ventajas-dificultades.md) |

## Requerimientos funcionales

| Requisito | Cómo se cumple |
|---|---|
| RF1. Registrar monitores | Formulario → `POST /api/monitores` |
| RF2. Consultar monitores | Tabla → `GET /api/monitores` |
| RF3. Consultar un monitor | Búsqueda por código → `GET /api/monitores/{codigo}` |
| RF4. Actualizar información | Botón Editar → `PUT /api/monitores/{codigo}` |
| RF5. Eliminar monitores | Botón Eliminar → `DELETE /api/monitores/{codigo}` |
| RF6. Validar información | Registros incompletos: Controlador Java (`400`). Códigos duplicados: Servicio Python y llave primaria en MySQL (`409`) |

## Despliegue en tres computadores

### Requisitos en cada computador

- Windows con [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado y **encendido**.
- Git.
- Los tres equipos en la misma red (o en la misma red de Tailscale/hotspot, ver más abajo).

### 1. Obtener el código

En cada computador se clona solo la rama que le corresponde:

```powershell
# PC3
git clone --single-branch -b servidor-3-python-bd https://github.com/miguelfsociety/monitores-academicos-posa.git pc3
# PC2
git clone --single-branch -b servidor-2-php https://github.com/miguelfsociety/monitores-academicos-posa.git pc2
# PC1
git clone --single-branch -b servidor-1-web-java https://github.com/miguelfsociety/monitores-academicos-posa.git pc1
```

### 2. Conocer la IP de cada equipo

```powershell
ipconfig
```

Se anota la "Dirección IPv4" del adaptador con el que el equipo está conectado a la red.

### 3. Configurar y arrancar, en este orden: PC3 → PC2 → PC1

En la carpeta clonada de cada equipo:

```powershell
copy .env.example .env
notepad .env
docker compose up -d --build
```

| Equipo | Qué se escribe en `.env` |
|---|---|
| PC3 | Usuario y contraseñas de la base de datos (sirven los valores de ejemplo) |
| PC2 | `SERVICIO_MONITORES_URL` y `SERVICIO_MATERIAS_URL` = `http://IP_DE_PC3:8000` |
| PC1 | `PHP_SERVICE_URL` = `http://IP_DE_PC2:8081` |

Si cambia una IP, se edita el `.env` y se ejecuta de nuevo `docker compose up -d` (no hay que reconstruir la imagen).

> **Recomendación:** construir las imágenes (`docker compose build --pull`) días antes de la demostración, con buena conexión. El día de la presentación solo se ejecuta `docker compose up -d`, que ya no necesita Internet.

### 4. Abrir el puerto en el firewall de Windows

En cada equipo, en **PowerShell como administrador** (cambiando el puerto: `8080` en PC1, `8081` en PC2, `8000` en PC3):

```powershell
# La red debe estar como Privada para aceptar conexiones entrantes
Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Private

# Si alguna vez se canceló el aviso del firewall para Docker, queda una regla de bloqueo
Get-NetFirewallRule | Where-Object { $_.Action -eq 'Block' -and $_.DisplayName -match 'docker' } | Disable-NetFirewallRule

# Regla de entrada para el puerto del servicio
New-NetFirewallRule -DisplayName "Monitores 8081" -Direction Inbound -Protocol TCP -LocalPort 8081 -Action Allow -Profile Any
```

### 5. Verificar la cadena

Cada servicio tiene un endpoint de salud que revisa también a los que tiene detrás. Se prueba **desde otro equipo** (probar desde el mismo equipo no pasa por el firewall):

```powershell
curl.exe http://IP_DE_PC3:8000/api/health
curl.exe http://IP_DE_PC2:8081/api/health
curl.exe http://IP_DE_PC1:8080/api/health
```

Si los tres responden `"estado":"ok"`, se abre la aplicación en el navegador: `http://IP_DE_PC1:8080`

### Lista de comprobación antes de la demostración

- [ ] Docker Desktop encendido en los tres equipos.
- [ ] Imágenes ya construidas en los tres equipos.
- [ ] IP de cada equipo anotada y escrita en el `.env` del equipo anterior de la cadena.
- [ ] Puerto abierto en el firewall de cada equipo y red en perfil Privado.
- [ ] `curl.exe http://IP:puerto/api/health` responde desde otro equipo para PC3, PC2 y PC1.
- [ ] La aplicación abre en `http://IP_DE_PC1:8080` y permite registrar, consultar, editar y eliminar.

### Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| La interfaz muestra error `502` con origen `java` | PC1 no alcanza a PC2 | Revisar `PHP_SERVICE_URL`, el firewall de PC2 y que el contenedor PHP esté arriba |
| Error `502` con origen `php` | PC2 no alcanza a PC3 | Revisar las URL del `.env` de PC2, el firewall de PC3 y el contenedor Python |
| Error `503` con origen `python` | Python no se conecta a MySQL | `docker compose ps` en PC3; esperar a que la base de datos esté `healthy` |
| Desde otro equipo no responde ningún puerto | La red aísla los equipos entre sí (común en redes Wi-Fi de campus) | Conectar los tres a un mismo punto de acceso móvil (hotspot) o a una misma red de Tailscale y usar esas IP en los `.env` |
| Las tildes se ven mal | La base de datos se creó con otra codificación | En PC3: `docker compose down -v` y `docker compose up -d` (borra y recrea los datos) |

## Prueba en un solo computador

Las tres ramas pueden ejecutarse en el mismo equipo (un solo nivel físico). En los `.env` se usa `host.docker.internal` en lugar de las IP:

| Equipo simulado | Valor |
|---|---|
| PC2 | `SERVICIO_MONITORES_URL=http://host.docker.internal:8000` y `SERVICIO_MATERIAS_URL=http://host.docker.internal:8000` |
| PC1 | `PHP_SERVICE_URL=http://host.docker.internal:8081` |

Luego `docker compose up -d --build` en cada carpeta y se abre `http://localhost:8080`.
