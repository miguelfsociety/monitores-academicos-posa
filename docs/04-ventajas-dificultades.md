# Ventajas y dificultades de una arquitectura distribuida y heterogénea

El análisis parte de lo vivido al construir este sistema: cinco componentes, tres lenguajes (Java, PHP y Python), una base de datos y tres computadores.

## Ventajas

| Ventaja | Cómo se ve en este sistema |
|---|---|
| **Separación de responsabilidades** | Cada componente hace una sola cosa: la interfaz presenta, Java valida y coordina, PHP intermedia, Python persiste y MySQL almacena. Encontrar dónde va un cambio es inmediato. |
| **Bajo acoplamiento** | Cada componente solo conoce al siguiente y solo a través del contrato REST. Java no sabe que detrás del PHP hay Python ni qué base de datos se usa. |
| **Independencia de plataforma y de lenguaje** | Los servicios se entienden por HTTP y JSON, así que cada uno se hizo en el lenguaje asignado sin afectar a los demás. Cada tecnología se usa en lo que hace bien. |
| **Desarrollo en paralelo** | Con el contrato de endpoints definido primero, las tres ramas se construyeron al mismo tiempo, cada una en su propio repositorio de trabajo, y se integraron al final. |
| **Mantenimiento y evolución** | Se puede reemplazar un componente completo (por ejemplo, cambiar MySQL por otro gestor o reescribir el servicio PHP) sin tocar los otros, mientras se respete el contrato. |
| **Transparencia de localización** | Gracias al registro de servicios del PHP, el servicio Python puede cambiar de equipo o de puerto modificando solo una línea de configuración. |
| **Seguridad por niveles** | La base de datos no es visible fuera de PC3. Para llegar a los datos hay que pasar por las validaciones de Java y de Python. |
| **Escalabilidad** | Cada nivel está en su propio computador y podría crecer por separado (más recursos o más instancias) según dónde esté la carga. |
| **Aislamiento de fallas** | Cuando un servicio cae, los demás siguen en pie y responden con un error claro (`502`/`504`) que indica en qué eslabón está el problema, en lugar de que toda la aplicación deje de responder. |
| **Reutilización** | Los servicios Python y PHP pueden ser consumidos por otro cliente (por ejemplo, una aplicación móvil) sin modificarlos. |

## Dificultades

| Dificultad | Cómo se presentó y cómo se manejó |
|---|---|
| **Mayor complejidad** | Una operación simple (guardar un registro) atraviesa cuatro saltos y tres lenguajes. Hubo que escribir y mantener el mismo CRUD tres veces. Se manejó con un contrato único de endpoints para todos. |
| **Latencia** | Cada salto de red añade tiempo; una consulta que en una aplicación monolítica sería una llamada directa aquí son tres peticiones HTTP y una consulta SQL. |
| **Más puntos de falla** | La operación depende de que los tres servicios, la base de datos y la red estén disponibles a la vez. El Servicio PHP, como intermediario, es un punto único de falla y un posible cuello de botella. Se definieron tiempos de espera y respuestas `502`/`504`. |
| **Propagación de errores** | Un error que ocurre en Python (por ejemplo, código duplicado) debe llegar intacto hasta el usuario. Hubo que cuidar que Java y PHP reenviaran el código de estado y el cuerpo sin transformarlos, y acordar un formato único de error con el campo `origen`. |
| **Depuración distribuida** | Los registros (logs) quedan repartidos en tres computadores. Se resolvió con el encabezado `X-Request-Id`, que permite seguir una misma petición en los tres, y con `X-Recorrido`, que muestra hasta dónde llegó. |
| **Consistencia del contrato** | Cada lenguaje tiene sus convenciones (nombres de campos, manejo de valores nulos, códigos de error por defecto). Por ejemplo, FastAPI responde `422` ante datos inválidos y hubo que ajustarlo a `400`; en Java los atributos se escriben distinto que en el JSON acordado. |
| **Validación repartida** | No toda regla puede validarse en el mismo lugar: Java valida la forma de los datos, pero el código duplicado solo lo puede detectar quien tiene los datos (Python y MySQL). Hay que decidir conscientemente dónde va cada regla para no duplicarla ni dejarla sin dueño. |
| **Codificación de caracteres** | Las tildes y la letra ñ deben conservarse entre navegador, tres servicios y la base de datos. Se fijó UTF-8 en todos los saltos y `utf8mb4` en MySQL. |
| **Configuración de red y despliegue** | Cada equipo necesita conocer la IP del siguiente, abrir su puerto en el firewall y estar en una red que permita la comunicación entre equipos. Es la parte que más problemas da al momento de la demostración. Se manejó con archivos `.env`, Docker en cada equipo y un endpoint de salud que revisa toda la cadena. |
| **Entornos distintos** | Mantener JDK, PHP con Apache, Python y MySQL con sus versiones correctas en varios computadores es costoso. Docker lo redujo a un mismo comando en cada equipo, a cambio de tener que aprender y mantener esa herramienta. |
| **Seguridad** | Hay más superficie expuesta: tres servicios escuchando en la red en lugar de uno. En un entorno real habría que añadir autenticación entre servicios, HTTPS y restringir qué equipo puede llamar a cuál. |
| **Pruebas** | Probar un componente aislado exige simular a su vecino, y la prueba de extremo a extremo necesita todo el sistema arriba. |
| **Equipo con más conocimientos** | Se requiere dominar tres lenguajes, sus herramientas y sus formas de manejar HTTP y JSON. |

## Conclusión

Una arquitectura distribuida y heterogénea ofrece flexibilidad, desacoplamiento e independencia tecnológica, pero **no es gratuita**: cambia complejidad dentro del código por complejidad en la comunicación, el despliegue y la operación. Para una aplicación pequeña como este CRUD, una sola aplicación sería más simple y rápida; la distribución se justifica cuando hay equipos distintos trabajando en paralelo, tecnologías que ya existen y deben integrarse, o necesidades de escalar y asegurar cada nivel por separado. En este proyecto el valor está en evidenciar cómo los patrones de Capas, MVC y Broker, junto con el modelo Cliente–Servidor y el estilo REST, permiten que componentes muy diferentes funcionen como un solo sistema.
