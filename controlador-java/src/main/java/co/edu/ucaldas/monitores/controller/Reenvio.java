package co.edu.ucaldas.monitores.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import co.edu.ucaldas.monitores.config.Trazabilidad;

/**
 * Arma la respuesta para la interfaz a partir de la que entregó el PHP.
 *
 * Se conservan el estado y el cuerpo; de los encabezados solo se reemiten
 * Content-Type y X-Recorrido (nunca Content-Length, Transfer-Encoding ni
 * Connection, que pertenecen a cada conexión). X-Request-Id lo pone el filtro.
 */
final class Reenvio {

    private Reenvio() {
    }

    static ResponseEntity<byte[]> haciaLaInterfaz(ResponseEntity<byte[]> respuestaPhp) {
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(respuestaPhp.getStatusCode())
                .header(Trazabilidad.ENCABEZADO_RECORRIDO, recorrido(respuestaPhp.getHeaders()));

        byte[] cuerpo = respuestaPhp.getBody();
        if (respuestaPhp.getStatusCode().value() == 204 || cuerpo == null || cuerpo.length == 0) {
            return respuesta.build();
        }

        MediaType tipo = respuestaPhp.getHeaders().getContentType();
        return respuesta.contentType(tipo != null ? tipo : MediaType.APPLICATION_JSON).body(cuerpo);
    }

    /** Antepone "java" al recorrido que informó el PHP. */
    static String recorrido(HttpHeaders encabezadosPhp) {
        String recibido = encabezadosPhp.getFirst(Trazabilidad.ENCABEZADO_RECORRIDO);
        if (recibido == null || recibido.isBlank()) {
            // El PHP respondió pero no informó su recorrido: al menos se sabe que la petición llegó a él.
            recibido = "php";
        }
        return Trazabilidad.SERVICIO + ">" + recibido.trim();
    }
}
