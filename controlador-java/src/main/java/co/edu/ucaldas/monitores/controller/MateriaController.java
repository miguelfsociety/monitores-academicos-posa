package co.edu.ucaldas.monitores.controller;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.ucaldas.monitores.client.ServicioPhpClient;
import co.edu.ucaldas.monitores.config.Trazabilidad;

/**
 * Lista de materias para el selector del formulario. Como el resto de la
 * información, se obtiene pasando por el PHP: Java no conoce a Python ni a la
 * base de datos.
 */
@RestController
@RequestMapping("/api/materias")
public class MateriaController {

    private final ServicioPhpClient servicioPhp;

    public MateriaController(ServicioPhpClient servicioPhp) {
        this.servicioPhp = servicioPhp;
    }

    @GetMapping
    public ResponseEntity<byte[]> listar(@RequestAttribute(Trazabilidad.ATRIBUTO_ID) String idPeticion) {
        return Reenvio.haciaLaInterfaz(servicioPhp.enviar(HttpMethod.GET, idPeticion, null, "/api/materias"));
    }
}
