package co.edu.ucaldas.monitores.exception;

import java.util.List;

/**
 * Se lanza cuando una regla que no se puede expresar con anotaciones no se
 * cumple (por ejemplo, el código del cuerpo distinto al de la ruta en un PUT).
 * El manejador global la convierte en una respuesta 400.
 */
public class DatosInvalidosException extends RuntimeException {

    private final List<String> detalles;

    public DatosInvalidosException(List<String> detalles) {
        super("Datos inválidos");
        this.detalles = List.copyOf(detalles);
    }

    public List<String> getDetalles() {
        return detalles;
    }
}
