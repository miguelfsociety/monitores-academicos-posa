# Diagrama de secuencia: registro de un monitor

Muestra el recorrido completo de la operación **RF1 – Registrar monitores**, desde que el usuario pulsa "Registrar" hasta que ve el resultado, incluyendo los dos casos de error del requisito **RF6** (registro incompleto y código duplicado).

![Diagrama de secuencia del registro](diagramas/secuencia-registro.png)

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant I as Interfaz<br/>(HTML/CSS/JS)
    participant J as Controlador Java<br/>(PC1 :8080)
    participant P as Servicio PHP<br/>(PC2 :8081)
    participant Y as Servicio Python<br/>(PC3 :8000)
    participant B as MySQL<br/>(PC3)

    U->>I: Diligencia el formulario y pulsa "Registrar"
    I->>I: Revisa campos obligatorios y formato de correo
    I->>J: POST /api/monitores (JSON)
    J->>J: Asigna X-Request-Id y valida la información básica

    alt Datos incompletos o inválidos
        J-->>I: 400 Bad Request {error, detalles, origen: "java"}
        I-->>U: Muestra los campos por corregir
    else Datos válidos
        J->>P: POST /api/monitores (JSON, X-Request-Id)
        P->>P: Busca el servicio "monitores" en el registro
        P->>Y: POST /api/monitores (JSON, X-Request-Id)
        Y->>Y: Valida el cuerpo recibido
        Y->>B: SELECT materia y monitor por código
        B-->>Y: Resultado

        alt El código ya existe
            Y-->>P: 409 Conflict {error, origen: "python"}
            P-->>J: 409 Conflict (misma respuesta)
            J-->>I: 409 Conflict (misma respuesta)
            I-->>U: "Ya existe un monitor con ese código"
        else Código disponible
            Y->>B: INSERT INTO monitores
            B-->>Y: Fila insertada
            Y-->>P: 201 Created (monitor, X-Recorrido: python)
            P-->>J: 201 Created (monitor, X-Recorrido: php>python)
            J-->>I: 201 Created (monitor, X-Recorrido: java>php>python)
            I->>J: GET /api/monitores (recarga la tabla por la misma cadena)
            J-->>I: 200 OK (lista)
            I-->>U: Mensaje de éxito, tabla actualizada y recorrido
        end
    end
```

## Descripción del flujo

### Flujo principal (registro exitoso)

1. El usuario llena el formulario (código, nombre completo, correo y materia) y pulsa **Registrar**.
2. La interfaz revisa que no haya campos vacíos y que el correo tenga formato válido. Es solo una ayuda para el usuario; la validación que cuenta es la de los servicios.
3. La interfaz envía `POST /api/monitores` al Controlador Java con los datos en JSON.
4. El Controlador Java asigna un identificador a la petición (`X-Request-Id`) y valida la información básica: campos obligatorios, longitudes y formato.
5. Java envía la petición al Servicio PHP.
6. El Servicio PHP busca en su registro qué servidor atiende el recurso `monitores` y reenvía la petición al Servicio Python sin modificarla.
7. El Servicio Python valida el cuerpo, verifica que la materia exista y que el código no esté registrado.
8. Python inserta el registro en MySQL.
9. La respuesta `201 Created` con el monitor creado regresa por el mismo camino: Python → PHP → Java → Interfaz. Cada servicio agrega su nombre al encabezado `X-Recorrido`.
10. La interfaz muestra el mensaje de éxito, vuelve a consultar la lista (por la misma cadena) y actualiza la tabla.

### Flujo alterno A: registro incompleto o inválido

El Controlador Java detecta el problema en el paso 4 y responde `400 Bad Request` con el detalle de cada campo. **La petición no llega al PHP ni al Python**: el recorrido termina en `java`.

### Flujo alterno B: código duplicado

Los datos son válidos, así que la petición llega hasta el Servicio Python. Allí se detecta que el código ya existe (verificación previa y llave primaria de la tabla) y se responde `409 Conflict`. PHP y Java devuelven esa misma respuesta sin alterarla y la interfaz muestra el mensaje al usuario.

### Fallas de comunicación

Si el servicio siguiente no está disponible, el componente que lo detecta responde `502 Bad Gateway` (o `504 Gateway Timeout` si no contestó a tiempo) con el mismo formato de error, y la interfaz informa en qué punto de la cadena se presentó la falla.
