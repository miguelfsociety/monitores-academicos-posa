# Imagen del servidor 1: Controlador Java + interfaz HTML/CSS/JS.
# El contexto de construccion es la raiz de la rama (docker compose build).

# ---------- Etapa 1: compilar el Controlador Java con Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS construccion
WORKDIR /build

# Primero solo el pom.xml: asi las dependencias quedan en cache mientras no cambie.
COPY controlador-java/pom.xml .
RUN mvn -B -q dependency:go-offline

COPY controlador-java/src ./src
RUN mvn -B -q package -DskipTests

# ---------- Etapa 2: imagen de ejecucion (solo el JRE) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=construccion /build/target/controlador-java-0.0.1-SNAPSHOT.jar /app/controlador-java.jar

# La interfaz es un componente aparte: se copia tal cual y Spring la sirve como
# archivos estaticos, de modo que la pagina y la API quedan en el mismo origen.
COPY interfaz/ /app/interfaz/
ENV SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/interfaz/

# La aplicacion no necesita permisos de administrador dentro del contenedor.
RUN useradd --system --no-create-home aplicacion
USER aplicacion

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/controlador-java.jar"]
