package co.edu.ucaldas.monitores.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/monitores (registrar un monitor).
 *
 * Las anotaciones aplican la validación básica que el contrato le asigna al
 * Controlador Java. Lo que depende de los datos guardados (código duplicado,
 * materia inexistente) lo valida el servicio Python.
 */
public record MonitorRequest(

        @JsonProperty("codigo")
        @NotBlank(message = "es obligatorio")
        @Size(max = 20, message = "debe tener máximo 20 caracteres")
        @Pattern(regexp = "\\S*", message = "no puede contener espacios")
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

    /** Copia con los textos sin espacios sobrantes; es lo que se envía al PHP. */
    public MonitorRequest normalizado() {
        return new MonitorRequest(codigo, nombreCompleto.strip(), correo.strip(), materiaId);
    }
}
