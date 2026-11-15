package co.edu.ucaldas.monitores.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import co.edu.ucaldas.monitores.client.ServicioPhpClient;
import co.edu.ucaldas.monitores.config.Trazabilidad;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Estado del Controlador Java y de los servicios que tiene detrás.
 *
 * Java responde por sí mismo y consulta el /api/health del PHP. Si el PHP
 * responde 200 con estado "ok" toda la cadena funciona; en cualquier otro caso
 * se responde 502 y en "siguiente" se deja lo que se haya recibido (o null si
 * no hubo respuesta), para ver en qué punto se rompió la cadena.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final ServicioPhpClient servicioPhp;
    private final JsonMapper json;

    public HealthController(ServicioPhpClient servicioPhp, JsonMapper json) {
        this.servicioPhp = servicioPhp;
        this.json = json;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> estado(
            @RequestAttribute(Trazabilidad.ATRIBUTO_ID) String idPeticion) {

        JsonNode siguiente = null;
        boolean cadenaOk = false;
        String recorrido = Trazabilidad.SERVICIO;

        try {
            ResponseEntity<byte[]> respuestaPhp = servicioPhp.enviar(HttpMethod.GET, idPeticion, null, "/api/health");
            recorrido = Reenvio.recorrido(respuestaPhp.getHeaders());
            siguiente = leerJson(respuestaPhp.getBody());
            cadenaOk = respuestaPhp.getStatusCode().value() == 200
                    && siguiente != null
                    && "ok".equals(siguiente.path("estado").asString(""));
        } catch (RestClientException ex) {
            log.warn("[{}] El servicio PHP no respondió al chequeo de salud: {}", idPeticion, ex.getMessage());
        }

        // LinkedHashMap para conservar el orden de los campos y permitir "siguiente": null.
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("servicio", Trazabilidad.SERVICIO);
        cuerpo.put("estado", cadenaOk ? "ok" : "error");
        cuerpo.put("siguiente", siguiente);

        return ResponseEntity.status(cadenaOk ? HttpStatus.OK : HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .header(Trazabilidad.ENCABEZADO_RECORRIDO, recorrido)
                .body(cuerpo);
    }

    /** Devuelve el JSON recibido, o null si el cuerpo está vacío o no es JSON. */
    private JsonNode leerJson(byte[] cuerpo) {
        if (cuerpo == null || cuerpo.length == 0) {
            return null;
        }
        try {
            return json.readTree(cuerpo);
        } catch (JacksonException ex) {
            return null;
        }
    }
}
