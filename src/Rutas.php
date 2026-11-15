<?php
declare(strict_types=1);

/**
 * Tabla de rutas del servicio PHP.
 *
 * Responsabilidad: declarar los endpoints que expone el PHP (los mismos del
 * contrato REST) y decidir si una petición corresponde a alguno de ellos.
 * No contiene reglas de negocio: solo método + patrón de ruta.
 */
final class Rutas
{
    public const ENCONTRADA = 'encontrada';
    public const NO_ENCONTRADA = 'no_encontrada';
    public const METODO_NO_PERMITIDO = 'metodo_no_permitido';

    /** Tabla explícita: método + patrón, idéntica al contrato. */
    private const TABLA = [
        ['GET',    '/api/monitores'],
        ['GET',    '/api/monitores/{codigo}'],
        ['POST',   '/api/monitores'],
        ['PUT',    '/api/monitores/{codigo}'],
        ['DELETE', '/api/monitores/{codigo}'],
        ['GET',    '/api/materias'],
        ['GET',    '/api/health'],
    ];

    /**
     * Busca la ruta en la tabla.
     *
     * @return array{resultado: string, recurso?: string, codigo?: ?string, permitidos?: string[]}
     *   - ENCONTRADA: incluye "recurso" (segmento posterior a /api) y "codigo" (o null).
     *   - METODO_NO_PERMITIDO: la ruta existe pero no con ese método; incluye "permitidos".
     *   - NO_ENCONTRADA: la ruta no está en la tabla.
     */
    public static function resolver(string $metodo, string $ruta): array
    {
        $permitidos = [];

        foreach (self::TABLA as [$metodoTabla, $patron]) {
            $coincidencias = [];
            if (preg_match(self::aExpresionRegular($patron), $ruta, $coincidencias) !== 1) {
                continue;
            }
            if ($metodoTabla !== $metodo) {
                $permitidos[] = $metodoTabla;
                continue;
            }

            // El recurso es el segmento que sigue a /api: /api/monitores/M001 -> "monitores".
            $segmentos = explode('/', $patron);

            return [
                'resultado' => self::ENCONTRADA,
                'recurso'   => $segmentos[2],
                'codigo'    => isset($coincidencias[1]) ? rawurldecode($coincidencias[1]) : null,
            ];
        }

        if ($permitidos !== []) {
            return ['resultado' => self::METODO_NO_PERMITIDO, 'permitidos' => $permitidos];
        }

        return ['resultado' => self::NO_ENCONTRADA];
    }

    /**
     * Arma la ruta que se le pide al servicio destino. El código se vuelve a
     * codificar para que viaje bien en la URL y se conserva el query string.
     */
    public static function rutaDestino(string $recurso, ?string $codigo, string $consulta): string
    {
        $ruta = '/api/' . $recurso;
        if ($codigo !== null) {
            $ruta .= '/' . rawurlencode($codigo);
        }
        if ($consulta !== '') {
            $ruta .= '?' . $consulta;
        }

        return $ruta;
    }

    /**
     * Convierte un patrón de la tabla en expresión regular:
     * "/api/monitores/{codigo}" -> "#^/api/monitores/([^/]+)$#".
     */
    private static function aExpresionRegular(string $patron): string
    {
        $expresion = preg_quote($patron, '#');
        $expresion = str_replace('\{codigo\}', '([^/]+)', $expresion);

        // \z y no $: así un salto de línea al final de la ruta no cuenta como coincidencia.
        return '#^' . $expresion . '\z#';
    }
}
