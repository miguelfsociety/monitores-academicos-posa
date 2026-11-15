package co.edu.ucaldas.monitores.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configuración de la conexión con el servicio PHP.
 *
 * La URL se toma de la propiedad servicio.php.url, que a su vez se llena con la
 * variable de entorno PHP_SERVICE_URL al arrancar el contenedor. Así el mismo
 * artefacto sirve en cualquier computador: solo cambia el archivo .env.
 */
@Configuration
public class ServicioPhpConfig {

    private static final Duration TIEMPO_CONEXION = Duration.ofSeconds(3);
    private static final Duration TIEMPO_LECTURA = Duration.ofSeconds(10);

    @Bean
    public RestClient clienteHttpPhp(@Value("${servicio.php.url}") String urlBase) {
        // Cliente HTTP del JDK, fijado en HTTP/1.1 para que no intente negociar HTTP/2 con Apache.
        HttpClient clienteJdk = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(TIEMPO_CONEXION)
                .build();

        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(clienteJdk);
        fabrica.setReadTimeout(TIEMPO_LECTURA);

        return RestClient.builder()
                .baseUrl(quitarBarraFinal(urlBase.trim()))
                .requestFactory(fabrica)
                .build();
    }

    private static String quitarBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
