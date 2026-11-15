package co.edu.ucaldas.monitores.client;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import co.edu.ucaldas.monitores.config.Trazabilidad;
import tools.jackson.databind.json.JsonMapper;

/**
 * Único punto del Controlador Java que conoce al servicio PHP.
 *
 * Envía la petición y devuelve la respuesta en crudo (estado, encabezados y
 * cuerpo como bytes), sin interpretarla: los estados 4xx y 5xx del PHP no se
 * convierten en excepción, para poder reenviarlos tal cual a la interfaz.
 * Solo se lanza excepción (ResourceAccessException) cuando no hay respuesta.
 */
@Component
public class ServicioPhpClient {

    private final RestClient clienteHttpPhp;
    private final JsonMapper json;

    public ServicioPhpClient(RestClient clienteHttpPhp, JsonMapper json) {
        this.clienteHttpPhp = clienteHttpPhp;
        this.json = json;
    }

    /**
     * @param metodo     método HTTP a usar
     * @param idPeticion valor de X-Request-Id que se propaga
     * @param cuerpo     objeto que se envía como JSON, o null si no hay cuerpo
     * @param plantilla  ruta con variables, por ejemplo /api/monitores/{codigo}
     * @param variables  valores de la plantilla; se codifican al armar la URL
     */
    public ResponseEntity<byte[]> enviar(HttpMethod metodo, String idPeticion, Object cuerpo,
            String plantilla, Object... variables) {

        RestClient.RequestBodySpec peticion = clienteHttpPhp.method(metodo)
                .uri(plantilla, variables)
                .accept(MediaType.APPLICATION_JSON)
                .header(Trazabilidad.ENCABEZADO_ID, idPeticion);

        if (cuerpo != null) {
            // Se convierte a JSON aquí para enviar los bytes con Content-Length conocido
            // (sin esto el cuerpo viajaría por fragmentos, con Transfer-Encoding: chunked).
            byte[] cuerpoJson = json.writeValueAsBytes(cuerpo);
            peticion.contentType(MediaType.APPLICATION_JSON)
                    .contentLength(cuerpoJson.length)
                    .body(cuerpoJson);
        }

        return peticion.retrieve()
                // Sin esta línea, un 404 o un 409 del PHP terminaría como excepción (y como 500).
                .onStatus(HttpStatusCode::isError, (solicitud, respuesta) -> { })
                .toEntity(byte[].class);
    }
}
