package co.edu.ucaldas.monitores.dto;

/**
 * Regla adicional para el correo, compartida por los dos DTO.
 *
 * La anotación @Email acepta direcciones sin dominio de primer nivel (por
 * ejemplo "ana@localhost"); con esta expresión se exige además un punto en el
 * dominio y que no haya espacios.
 */
final class ReglasCorreo {

    static final String FORMATO = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private ReglasCorreo() {
    }
}
