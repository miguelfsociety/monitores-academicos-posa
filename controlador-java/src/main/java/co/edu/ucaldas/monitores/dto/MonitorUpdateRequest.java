package co.edu.ucaldas.monitores.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de PUT /api/monitores/{codigo} (actualizar un monitor).
 *
 * El código no se modifica: es opcional en el cuerpo y, si viene, el
 * controlador comprueba que sea igual al de la ruta.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MonitorUpdateRequest(

        @JsonProperty("codigo")
        String codigo,

        @JsonProperty("nombre_completo")
        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "debe tener máximo 120 caracteres")
        String nombreCompleto,

        @JsonProperty("correo")
        @NotBlank(message = "es obligatorio")
        @Size(max = 120, message = "debe tener máximo 120 caracteres")
        @Email(regexp = ReglasCorreo.FORMATO, message = "debe ser un correo electrónico válido")
        String correo,

        @JsonProperty("materia_id")
        @NotNull(message = "es obligatorio")
        @Min(value = 1, message = "debe ser un entero mayor o igual a 1")
        Integer materiaId) {

    /**
     * Copia que se envía al PHP: textos sin espacios sobrantes y sin el código,
     * porque el identificador ya viaja en la ruta.
     */
    public MonitorUpdateRequest normalizado() {
        return new MonitorUpdateRequest(null, nombreCompleto.strip(), correo.strip(), materiaId);
    }
}
