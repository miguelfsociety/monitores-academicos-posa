<?php
declare(strict_types=1);

/**
 * Reenviador de peticiones (cliente HTTP con cURL).
 *
 * Responsabilidad: enviar al servicio destino la misma petición que recibió el
 * PHP (método, cuerpo crudo y encabezados de trazabilidad) y devolver lo que
 * el servicio respondió, sin interpretarlo.
 */
final class Reenviador
{
    public const FALLO_CONEXION = 'conexion';
    public const FALLO_TIEMPO = 'tiempo';

    private const SEGUNDOS_CONEXION = 3;
    private const SEGUNDOS_RESPUESTA = 10;

    /**
     * @return array{fallo: ?string, detalle: string, estado: int, cuerpo: string, encabezados: array<string, string>}
     *   "fallo" es null si el servicio respondió (con cualquier estado HTTP),
     *   FALLO_TIEMPO si no respondió a tiempo y FALLO_CONEXION en los demás casos.
     *   Los nombres de "encabezados" vienen en minúsculas.
     */
    public function enviar(string $metodo, string $url, string $cuerpo, ?string $tipoContenido, string $idPeticion): array
    {
        $encabezadosPeticion = [
            'Accept: application/json',
            'X-Request-Id: ' . $idPeticion,
            // Evita que cURL espere un "100 Continue" antes de mandar el cuerpo.
            'Expect:',
        ];

        $encabezadosRespuesta = [];

        $curl = curl_init($url);
        curl_setopt_array($curl, [
            CURLOPT_CUSTOMREQUEST  => $metodo,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_CONNECTTIMEOUT => self::SEGUNDOS_CONEXION,
            CURLOPT_TIMEOUT        => self::SEGUNDOS_RESPUESTA,
            // Se guardan los encabezados de la respuesta para leer Content-Type y X-Recorrido.
            CURLOPT_HEADERFUNCTION => static function ($curl, string $linea) use (&$encabezadosRespuesta): int {
                $partes = explode(':', $linea, 2);
                if (count($partes) === 2) {
                    $encabezadosRespuesta[strtolower(trim($partes[0]))] = trim($partes[1]);
                }

                return strlen($linea);
            },
        ]);

        if ($cuerpo !== '') {
            // El cuerpo se manda tal cual llegó, sin decodificarlo ni volverlo a armar.
            curl_setopt($curl, CURLOPT_POSTFIELDS, $cuerpo);
            $encabezadosPeticion[] = 'Content-Type: ' . ($tipoContenido ?: 'application/json');
        }
        curl_setopt($curl, CURLOPT_HTTPHEADER, $encabezadosPeticion);

        $cuerpoRespuesta = curl_exec($curl);
        $numeroError = curl_errno($curl);
        $textoError = curl_error($curl);
        $estado = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        curl_close($curl);

        if ($cuerpoRespuesta === false) {
            return [
                'fallo'       => $numeroError === CURLE_OPERATION_TIMEDOUT ? self::FALLO_TIEMPO : self::FALLO_CONEXION,
                'detalle'     => $textoError,
                'estado'      => 0,
                'cuerpo'      => '',
                'encabezados' => [],
            ];
        }

        return [
            'fallo'       => null,
            'detalle'     => '',
            'estado'      => $estado,
            'cuerpo'      => (string) $cuerpoRespuesta,
            'encabezados' => $encabezadosRespuesta,
        ];
    }
}
