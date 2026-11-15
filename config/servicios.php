<?php
declare(strict_types=1);

/*
 * Configuración del registro de servicios del Broker.
 *
 * Cada recurso de la API tiene su propia entrada con la URL base del servidor
 * que lo atiende. Las URL se leen de variables de entorno en ejecución
 * (docker compose + archivo .env), de modo que cambiar un servicio de equipo
 * o de puerto no obliga a tocar el código ni a reconstruir la imagen.
 *
 * En el despliegue del proyecto las dos entradas apuntan al mismo servidor
 * Python (PC3), pero podrían estar en servidores distintos.
 */

return [
    'monitores' => getenv('SERVICIO_MONITORES_URL') ?: '',
    'materias'  => getenv('SERVICIO_MATERIAS_URL') ?: '',
];
