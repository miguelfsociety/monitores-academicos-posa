<?php
declare(strict_types=1);

/**
 * Registro de servicios del Broker.
 *
 * Responsabilidad: saber en qué servidor está cada servicio. Dado el nombre de
 * un recurso ("monitores", "materias") devuelve la URL base del servidor que
 * lo atiende. Es lo que le da al cliente (el Controlador Java) transparencia
 * de localización: él solo conoce al PHP.
 */
final class RegistroServicios
{
    /** @var array<string, string> nombre del servicio => URL base */
    private array $servicios = [];

    /**
     * @param array<string, string> $configuracion contenido de config/servicios.php
     */
    public function __construct(array $configuracion)
    {
        foreach ($configuracion as $nombre => $url) {
            $this->registrar((string) $nombre, (string) $url);
        }
    }

    /**
     * Agrega un servicio al registro. Una URL vacía (variable de entorno sin
     * definir) no se registra: ese servicio simplemente no existe para el Broker.
     */
    public function registrar(string $nombre, string $url): void
    {
        $url = rtrim(trim($url), '/');
        if ($url !== '') {
            $this->servicios[$nombre] = $url;
        }
    }

    /**
     * Devuelve la URL base del servicio o null si no está registrado.
     */
    public function localizar(string $nombre): ?string
    {
        return $this->servicios[$nombre] ?? null;
    }
}
