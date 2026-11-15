package co.edu.ucaldas.monitores.dto;

import java.util.List;

import co.edu.ucaldas.monitores.config.Trazabilidad;

/**
 * Formato único de error del contrato: mensaje, lista de detalles y el
 * componente que detectó el problema.
 */
public record ErrorRespuesta(String error, List<String> detalles, String origen) {

    /** Error detectado por el Controlador Java. */
    public static ErrorRespuesta deJava(String error, List<String> detalles) {
        return new ErrorRespuesta(error, detalles, Trazabilidad.SERVICIO);
    }
}
