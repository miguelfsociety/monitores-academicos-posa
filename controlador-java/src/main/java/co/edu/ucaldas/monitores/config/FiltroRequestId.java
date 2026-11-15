package co.edu.ucaldas.monitores.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro que asigna el identificador de cada petición a la API.
 *
 * Si la petición ya trae X-Request-Id se respeta; si no, se genera uno. El
 * identificador se devuelve en la respuesta, queda disponible para los
 * controladores (que lo envían al PHP) y se escribe en el log junto con el
 * método, la ruta y el estado HTTP con que se respondió.
 */
@Component
public class FiltroRequestId extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FiltroRequestId.class);

    // Solo se aceptan identificadores "normales" para no copiar al log ni a los encabezados cualquier texto.
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        // Los archivos de la interfaz (HTML, CSS, JS) no hacen parte de la API.
        return !peticion.getRequestURI().startsWith("/api");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
            FilterChain cadena) throws ServletException, IOException {

        String id = peticion.getHeader(Trazabilidad.ENCABEZADO_ID);
        if (id == null || !ID_VALIDO.matcher(id).matches()) {
            id = UUID.randomUUID().toString();
        }

        peticion.setAttribute(Trazabilidad.ATRIBUTO_ID, id);
        respuesta.setHeader(Trazabilidad.ENCABEZADO_ID, id);

        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            log.info("[{}] {} {} -> {}", id, peticion.getMethod(), peticion.getRequestURI(),
                    respuesta.getStatus());
        }
    }
}
