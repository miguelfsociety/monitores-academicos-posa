package co.edu.ucaldas.monitores.exception;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import co.edu.ucaldas.monitores.config.Trazabilidad;
import co.edu.ucaldas.monitores.dto.ErrorRespuesta;

/**
 * Convierte las excepciones en respuestas con el formato único de error del
 * contrato ({"error", "detalles", "origen": "java"}).
 *
 * Todas estas respuestas las produce Java sin haber obtenido respuesta del
 * PHP, por eso el recorrido que se informa es solo "java".
 */
@RestControllerAdvice
public class ManejadorGlobal {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobal.class);

    /** Campos que no pasaron las anotaciones de validación de los DTO. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> datosInvalidos(MethodArgumentNotValidException ex) {
        // Se agrupa por campo (en orden alfabético) para que la respuesta sea siempre igual.
        Map<String, List<FieldError>> porCampo = new TreeMap<>();
        for (FieldError fallo : ex.getBindingResult().getFieldErrors()) {
            porCampo.computeIfAbsent(nombreJson(fallo.getField()), campo -> new ArrayList<>()).add(fallo);
        }

        List<String> detalles = new ArrayList<>();
        porCampo.forEach((campo, fallos) -> {
            // Si el campo falta, basta con decir que es obligatorio.
            boolean falta = fallos.stream().anyMatch(ManejadorGlobal::esObligatorio);
            fallos.stream()
                    .filter(fallo -> !falta || esObligatorio(fallo))
                    .map(FieldError::getDefaultMessage)
                    .distinct()
                    .sorted()
                    .forEach(mensaje -> detalles.add(campo + ": " + mensaje));
        });

        return responder(HttpStatus.BAD_REQUEST, "Datos inválidos", detalles);
    }

    /** Reglas comprobadas a mano en los controladores. */
    @ExceptionHandler(DatosInvalidosException.class)
    public ResponseEntity<ErrorRespuesta> datosInvalidos(DatosInvalidosException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Datos inválidos", ex.getDetalles());
    }

    /** Cuerpo vacío, JSON mal formado o un valor de un tipo que no corresponde. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> jsonIlegible(HttpMessageNotReadableException ex) {
        return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido",
                List.of("Se esperaba un objeto JSON con los campos del monitor y sus tipos de dato correctos"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorRespuesta> tipoNoSoportado(HttpMediaTypeNotSupportedException ex) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El cuerpo debe enviarse como application/json",
                List.of());
    }

    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<ErrorRespuesta> rutaInexistente(Exception ex) {
        return responder(HttpStatus.NOT_FOUND, "Ruta no encontrada", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorRespuesta> metodoNoPermitido(HttpRequestMethodNotSupportedException ex) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED,
                "Método " + ex.getMethod() + " no permitido para esta ruta", List.of());
    }

    /** No se pudo establecer la comunicación con el PHP: 504 si se agotó el tiempo, 502 en otro caso. */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorRespuesta> phpInalcanzable(ResourceAccessException ex) {
        log.warn("Fallo al comunicarse con el servicio PHP: {}", ex.getMessage());
        if (esTiempoAgotado(ex)) {
            return responder(HttpStatus.GATEWAY_TIMEOUT, "El servicio PHP no respondió a tiempo", List.of());
        }
        return responder(HttpStatus.BAD_GATEWAY, "El servicio PHP no está disponible", List.of());
    }

    /** Cualquier otro fallo del cliente HTTP (por ejemplo, una respuesta que no se pudo leer). */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorRespuesta> falloDelCliente(RestClientException ex) {
        log.warn("Respuesta inesperada del servicio PHP: {}", ex.getMessage());
        return responder(HttpStatus.BAD_GATEWAY, "No se pudo interpretar la respuesta del servicio PHP", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorRespuesta> errorInterno(Exception ex) {
        log.error("Error no controlado", ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del Controlador Java", List.of());
    }

    /** Indica si en la cadena de causas hay un tiempo de espera agotado (de conexión o de lectura). */
    public static boolean esTiempoAgotado(Throwable ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof HttpTimeoutException || causa instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static boolean esObligatorio(FieldError fallo) {
        return "NotBlank".equals(fallo.getCode()) || "NotNull".equals(fallo.getCode());
    }

    /** Pasa el nombre del atributo Java al nombre del campo en el JSON (nombreCompleto -> nombre_completo). */
    private static String nombreJson(String atributo) {
        return atributo.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private static ResponseEntity<ErrorRespuesta> responder(HttpStatusCode estado, String error,
            List<String> detalles) {
        return ResponseEntity.status(estado)
                .contentType(MediaType.APPLICATION_JSON)
                .header(Trazabilidad.ENCABEZADO_RECORRIDO, Trazabilidad.SERVICIO)
                .body(ErrorRespuesta.deJava(error, detalles));
    }
}
