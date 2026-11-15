<?php
declare(strict_types=1);

/*
 * Front controller del Servicio PHP (Broker).
 *
 * Toda petición entra por aquí (ver .htaccess). El flujo es:
 *   1. Identificar la petición (X-Request-Id).
 *   2. Buscar la ruta en la tabla de rutas            -> 404 / 405 si no existe.
 *   3. Localizar en el registro el servicio destino   -> 404 si no está registrado.
 *   4. Revisar que el cuerpo de POST/PUT sea JSON     -> 400 sin llamar al destino.
 *   5. Reenviar la petición y devolver la respuesta   -> 502 / 504 si el destino falla.
 *
 * Aquí no se validan reglas de negocio: eso le corresponde al Controlador Java
 * y al Servicio Python.
 */

require_once __DIR__ . '/../src/Respuesta.php';
require_once __DIR__ . '/../src/Rutas.php';
require_once __DIR__ . '/../src/RegistroServicios.php';
require_once __DIR__ . '/../src/Reenviador.php';

$metodo = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$ruta = explode('?', $_SERVER['REQUEST_URI'] ?? '/', 2)[0];
$consulta = $_SERVER['QUERY_STRING'] ?? '';

$idPeticion = obtenerIdPeticion();
Respuesta::iniciar($idPeticion);

/** Escribe una línea en el log por cada petición atendida. */
$registrar = static function (string $destino, int $estado) use ($idPeticion, $metodo, $ruta): void {
    error_log(sprintf('[%s] %s %s -> servicio=%s estado=%d', $idPeticion, $metodo, $ruta, $destino, $estado));
};

// Cualquier aviso o excepción no prevista termina en un JSON 500 con origen "php".
set_error_handler(static function (int $nivel, string $mensaje, string $archivo, int $linea): bool {
    throw new ErrorException($mensaje, 0, $nivel, $archivo, $linea);
});
set_exception_handler(static function (Throwable $excepcion) use ($registrar): void {
    error_log('Error interno: ' . $excepcion->getMessage() . ' en ' . $excepcion->getFile() . ':' . $excepcion->getLine());
    if (!headers_sent()) {
        Respuesta::error(500, 'Error interno del servicio PHP');
    }
    $registrar('-', 500);
});

// --- 2. Tabla de rutas ---------------------------------------------------
$resolucion = Rutas::resolver($metodo, $ruta);

if ($resolucion['resultado'] === Rutas::NO_ENCONTRADA) {
    Respuesta::error(404, 'Ruta no encontrada: ' . $ruta);
    $registrar('-', 404);
    exit;
}

if ($resolucion['resultado'] === Rutas::METODO_NO_PERMITIDO) {
    $permitidos = implode(', ', $resolucion['permitidos']);
    header('Allow: ' . $permitidos);
    Respuesta::error(405, 'Método ' . $metodo . ' no permitido para ' . $ruta, ['Métodos permitidos: ' . $permitidos]);
    $registrar('-', 405);
    exit;
}

$registro = new RegistroServicios(require __DIR__ . '/../config/servicios.php');
$reenviador = new Reenviador();
$recurso = $resolucion['recurso'];

// --- Caso propio: salud en cascada --------------------------------------
if ($recurso === 'health') {
    $estado = responderSalud($registro, $reenviador, $idPeticion);
    $registrar('monitores', $estado);
    exit;
}

// --- 3. Registro de servicios -------------------------------------------
$urlBase = $registro->localizar($recurso);
if ($urlBase === null) {
    Respuesta::error(404, 'No hay un servicio registrado para el recurso "' . $recurso . '"');
    $registrar($recurso, 404);
    exit;
}

// --- 4. Cuerpo de la petición -------------------------------------------
$cuerpo = (string) file_get_contents('php://input');

if ($metodo === 'POST' || $metodo === 'PUT') {
    if (trim($cuerpo) === '') {
        Respuesta::error(400, 'El cuerpo de la petición está vacío; se esperaba un JSON');
        $registrar($recurso, 400);
        exit;
    }
    if (!json_validate($cuerpo)) {
        Respuesta::error(400, 'El cuerpo de la petición no es un JSON válido');
        $registrar($recurso, 400);
        exit;
    }
}

// --- 5. Reenvío al servicio destino -------------------------------------
$url = $urlBase . Rutas::rutaDestino($recurso, $resolucion['codigo'], $consulta);
$resultado = $reenviador->enviar($metodo, $url, $cuerpo, $_SERVER['CONTENT_TYPE'] ?? null, $idPeticion);

if ($resultado['fallo'] === Reenviador::FALLO_TIEMPO) {
    Respuesta::error(504, 'El servicio de ' . $recurso . ' no respondió a tiempo', [$resultado['detalle']]);
    $registrar($recurso, 504);
    exit;
}

if ($resultado['fallo'] !== null) {
    Respuesta::error(502, 'El servicio de ' . $recurso . ' no está disponible', [$resultado['detalle']]);
    $registrar($recurso, 502);
    exit;
}

Respuesta::reenviada(
    $resultado['estado'],
    $resultado['cuerpo'],
    $resultado['encabezados']['content-type'] ?? null,
    $resultado['encabezados']['x-recorrido'] ?? ''
);
$registrar($recurso, $resultado['estado']);

// ------------------------------------------------------------------------
// Funciones de apoyo
// ------------------------------------------------------------------------

/**
 * Usa el X-Request-Id que llega (normalmente lo genera Java). Si no llega, o
 * trae caracteres extraños, se genera uno nuevo.
 */
function obtenerIdPeticion(): string
{
    $recibido = trim($_SERVER['HTTP_X_REQUEST_ID'] ?? '');
    if (preg_match('/^[A-Za-z0-9._-]{1,100}\z/', $recibido) === 1) {
        return $recibido;
    }

    // UUID versión 4 a partir de 16 bytes aleatorios.
    $bytes = random_bytes(16);
    $bytes[6] = chr((ord($bytes[6]) & 0x0f) | 0x40);
    $bytes[8] = chr((ord($bytes[8]) & 0x3f) | 0x80);

    return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($bytes), 4));
}

/**
 * GET /api/health: el PHP responde por sí mismo y consulta al servicio
 * siguiente (se usa la URL del servicio "monitores"). Devuelve el estado HTTP
 * con el que respondió.
 */
function responderSalud(RegistroServicios $registro, Reenviador $reenviador, string $idPeticion): int
{
    $siguiente = null;
    $recorrido = '';
    $siguienteBien = false;

    $urlBase = $registro->localizar('monitores');
    if ($urlBase !== null) {
        $resultado = $reenviador->enviar('GET', $urlBase . '/api/health', '', null, $idPeticion);
        if ($resultado['fallo'] === null) {
            // Se deja lo recibido aunque venga con error, para ver dónde se rompió la cadena.
            $siguiente = json_decode($resultado['cuerpo'], true);
            $recorrido = $resultado['encabezados']['x-recorrido'] ?? '';
            $siguienteBien = $resultado['estado'] === 200 && is_array($siguiente);
        }
    }

    $estado = $siguienteBien ? 200 : 502;
    Respuesta::json($estado, [
        'servicio'  => 'php',
        'estado'    => $siguienteBien ? 'ok' : 'error',
        'siguiente' => $siguiente,
    ], $recorrido);

    return $estado;
}
