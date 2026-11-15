package co.edu.ucaldas.monitores.controller;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.ucaldas.monitores.client.ServicioPhpClient;
import co.edu.ucaldas.monitores.config.Trazabilidad;
import co.edu.ucaldas.monitores.dto.MonitorRequest;
import co.edu.ucaldas.monitores.dto.MonitorUpdateRequest;
import co.edu.ucaldas.monitores.exception.DatosInvalidosException;
import jakarta.validation.Valid;

/**
 * Recibe las solicitudes de la interfaz sobre los monitores (CRUD), valida la
 * información básica y envía la petición al servicio PHP.
 *
 * Si los datos no son válidos, la anotación @Valid corta la petición antes de
 * entrar al método y el manejador global responde 400 sin llamar al PHP.
 */
@RestController
@RequestMapping("/api/monitores")
public class MonitorController {

    private static final String COLECCION = "/api/monitores";
    private static final String ELEMENTO = "/api/monitores/{codigo}";

    private final ServicioPhpClient servicioPhp;

    public MonitorController(ServicioPhpClient servicioPhp) {
        this.servicioPhp = servicioPhp;
    }

    /** RF2: listar todos los monitores. */
    @GetMapping
    public ResponseEntity<byte[]> listar(@RequestAttribute(Trazabilidad.ATRIBUTO_ID) String idPeticion) {
        return Reenvio.haciaLaInterfaz(servicioPhp.enviar(HttpMethod.GET, idPeticion, null, COLECCION));
    }

    /** RF3: consultar un monitor por su código. */
    @GetMapping("/{codigo}")
    public ResponseEntity<byte[]> consultar(@PathVariable String codigo,
            @RequestAttribute(Trazabilidad.ATRIBUTO_ID) String idPeticion) {
        return Reenvio.haciaLaInterfaz(servicioPhp.enviar(HttpMethod.GET, idPeticion, null, ELEMENTO, codigo));
    }

    /** RF1: registrar un monitor. */
    @PostMapping
    public ResponseEntity<byte[]> registrar(@Valid @RequestBody MonitorRequest monitor,
            @RequestAttribute(Trazabilidad.ATRIBUTO_ID) String idPeticion) {
        return Reenvio.haciaLaInterfaz(
                servicioPhp.enviar(HttpMethod.POST, idPeticion, monitor.normalizado(), COLECCION));
    }

    /** RF4: actualizar nombre, correo y materia. El código no se modifica. */
    @PutMapping("/{codigo}")
    public ResponseEntity<byte[]> actualizar(@PathVariable String codigo,
            @Valid @RequestBody MonitorUpdateRequest monitor,
            @RequestAttribute(Trazabilidad.ATRIBUTO_ID) String idPeticion) {

        if (monitor.codigo() != null && !monitor.codigo().equals(codigo)) {
            throw new DatosInvalidosException(
                    List.of("codigo: no se puede modificar; debe ser igual al de la ruta"));
        }
        return Reenvio.haciaLaInterfaz(
                servicioPhp.enviar(HttpMethod.PUT, idPeticion, monitor.normalizado(), ELEMENTO, codigo));
    }

    /** RF5: eliminar un monitor. */
    @DeleteMapping("/{codigo}")
    public ResponseEntity<byte[]> eliminar(@PathVariable String codigo,
            @RequestAttribute(Trazabilidad.ATRIBUTO_ID) String idPeticion) {
        return Reenvio.haciaLaInterfaz(servicioPhp.enviar(HttpMethod.DELETE, idPeticion, null, ELEMENTO, codigo));
    }
}
