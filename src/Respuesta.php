<?php
declare(strict_types=1);

/**
 * Respuestas HTTP del servicio PHP.
 *
 * Responsabilidad: escribir la respuesta al cliente, ya sea una propia del PHP
 * (JSON, incluido el formato único de error con origen "php") o la que
 * devolvió el servicio destino. Siempre agrega los encabezados de trazabilidad
 * X-Request-Id y X-Recorrido.
 */
final class Respuesta
{
    private const NOMBRE_SERVICIO = 'php';

    private static string $idPeticion = '';

    /** Guarda el identificador de la petición para devolverlo en la respuesta. */
    public static function iniciar(string $idPeticion): void
    {
        self::$idPeticion = $idPeticion;

        // Sin tipo por defecto: una respuesta sin cuerpo (204) no debe llevar Content-Type.
        ini_set('default_mimetype', '');
        header_remove('X-Powered-By');
    }

    /**
     * Respuesta JSON generada por el PHP.
     *
     * @param array<string, mixed> $datos
     * @param string $recorridoSiguiente X-Recorrido recibido del servicio destino, o '' si no se llegó a él
     */
    public static function json(int $estado, array $datos, string $recorridoSiguiente = ''): void
    {
        self::encabezadosComunes($estado, $recorridoSiguiente);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode($datos, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    }

    /**
     * Error detectado por el PHP, con el formato único del contrato.
     *
     * @param string[] $detalles
     */
    public static function error(int $estado, string $mensaje, array $detalles = []): void
    {
        self::json($estado, [
            'error'    => $mensaje,
            'detalles' => $detalles,
            'origen'   => self::NOMBRE_SERVICIO,
        ]);
    }

    /**
     * Devuelve al cliente la respuesta del servicio destino: mismo estado, mismo
     * cuerpo y mismo Content-Type. Con 204 o cuerpo vacío no se escribe nada.
     */
    public static function reenviada(int $estado, string $cuerpo, ?string $tipoContenido, string $recorridoSiguiente): void
    {
        self::encabezadosComunes($estado, $recorridoSiguiente);

        if ($estado === 204 || $cuerpo === '') {
            return;
        }

        header('Content-Type: ' . ($tipoContenido ?: 'application/json'));
        echo $cuerpo;
    }

    private static function encabezadosComunes(int $estado, string $recorridoSiguiente): void
    {
        http_response_code($estado);
        header('X-Request-Id: ' . self::$idPeticion);

        $recorrido = self::NOMBRE_SERVICIO;
        if ($recorridoSiguiente !== '') {
            $recorrido .= '>' . $recorridoSiguiente;
        }
        header('X-Recorrido: ' . $recorrido);
    }
}
