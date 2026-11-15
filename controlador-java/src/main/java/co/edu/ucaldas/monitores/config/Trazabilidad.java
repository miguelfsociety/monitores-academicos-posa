package co.edu.ucaldas.monitores.config;

/**
 * Nombres de los encabezados de trazabilidad definidos en el contrato REST.
 */
public final class Trazabilidad {

    /** Identificador único de la petición; se propaga sin cambios hasta Python. */
    public static final String ENCABEZADO_ID = "X-Request-Id";

    /** Lista de servicios que atendieron la petición, por ejemplo java>php>python. */
    public static final String ENCABEZADO_RECORRIDO = "X-Recorrido";

    /** Nombre con el que este servicio aparece en el recorrido y en el campo "origen". */
    public static final String SERVICIO = "java";

    /** Atributo de la petición donde el filtro deja el identificador. */
    public static final String ATRIBUTO_ID = "idPeticion";

    private Trazabilidad() {
    }
}
