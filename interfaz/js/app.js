/*
 * Interfaz de gestión de monitores académicos.
 *
 * Este archivo maneja los eventos de la página y la comunicación con el
 * Controlador Java. La interfaz SOLO conoce las rutas relativas /api/...;
 * nunca llama directamente al servicio PHP ni al servicio Python.
 */
'use strict';

// ---------------------------------------------------------------------------
// Referencias a los elementos de la página
// ---------------------------------------------------------------------------

const $ = (id) => document.getElementById(id);

const formulario = $('formulario');
const campoCodigo = $('codigo');
const campoNombre = $('nombre');
const campoCorreo = $('correo');
const campoMateria = $('materia');
const botonGuardar = $('btn-guardar');
const botonCancelar = $('btn-cancelar');
const tituloFormulario = $('titulo-formulario');

// Código del monitor que se está editando; null cuando se está registrando uno nuevo.
let codigoEnEdicion = null;

// ---------------------------------------------------------------------------
// Comunicación con el Controlador Java
// ---------------------------------------------------------------------------

/**
 * Lee el cuerpo de una respuesta sin asumir que es JSON: un 204 llega vacío y
 * un fallo inesperado puede traer una página de error en HTML.
 */
async function leerRespuesta(respuesta) {
  const texto = await respuesta.text();
  let datos = null;
  if (texto.trim() !== '') {
    try {
      datos = JSON.parse(texto);
    } catch (e) {
      datos = null;
    }
  }
  return { ok: respuesta.ok, estado: respuesta.status, datos: datos, texto: texto };
}

/**
 * Hace una petición a la API y actualiza el indicador de recorrido.
 * Siempre devuelve un objeto { ok, estado, datos, texto }; si ni siquiera se
 * pudo contactar al Controlador Java, estado vale 0.
 */
async function llamarApi(metodo, ruta, cuerpo, descripcion) {
  const opciones = { method: metodo, headers: { 'Accept': 'application/json' } };
  if (cuerpo !== undefined) {
    opciones.headers['Content-Type'] = 'application/json';
    opciones.body = JSON.stringify(cuerpo);
  }

  let respuesta;
  try {
    respuesta = await fetch(ruta, opciones);
  } catch (e) {
    mostrarRecorrido(descripcion, '', '', 0);
    return { ok: false, estado: 0, datos: null, texto: '' };
  }

  const resultado = await leerRespuesta(respuesta);
  mostrarRecorrido(
    descripcion,
    respuesta.headers.get('X-Recorrido') || '',
    respuesta.headers.get('X-Request-Id') || '',
    resultado.estado
  );
  return resultado;
}

/** Ruta de un monitor concreto, con el código codificado para la URL. */
function rutaMonitor(codigo) {
  return '/api/monitores/' + encodeURIComponent(codigo);
}

// ---------------------------------------------------------------------------
// Indicador de recorrido: Interfaz → Java → PHP → Python → BD
// ---------------------------------------------------------------------------

function mostrarRecorrido(descripcion, recorrido, idPeticion, estado) {
  // El encabezado llega como "java>php>python": la lista de servicios que atendieron la petición.
  const servicios = recorrido.split('>').map((s) => s.trim().toLowerCase()).filter((s) => s !== '');

  const activos = new Set(['interfaz'].concat(servicios));
  if (activos.has('python')) {
    // Python es el único que habla con la base de datos; si respondió, la petición llegó hasta ella.
    activos.add('bd');
  }

  document.querySelectorAll('#recorrido li').forEach((eslabon) => {
    eslabon.classList.toggle('activo', activos.has(eslabon.dataset.eslabon));
  });

  const textoEstado = estado === 0 ? 'sin respuesta del Controlador Java' : 'HTTP ' + estado;
  $('recorrido-operacion').textContent = descripcion + ' (' + textoEstado + ').';
  $('recorrido-id').textContent = idPeticion ? 'X-Request-Id: ' + idPeticion : '';
}

// ---------------------------------------------------------------------------
// Mensajes de éxito y de error
// ---------------------------------------------------------------------------

const NOMBRES_ORIGEN = {
  java: 'Controlador Java',
  php: 'Servicio PHP',
  python: 'Servicio Python'
};

function mostrarMensaje(tipo, texto, detalles) {
  const caja = $('mensaje');
  caja.className = 'mensaje ' + tipo;
  $('mensaje-texto').textContent = texto;

  const lista = $('mensaje-detalles');
  lista.replaceChildren();
  (detalles || []).forEach((detalle) => {
    const item = document.createElement('li');
    item.textContent = String(detalle);
    lista.appendChild(item);
  });

  caja.hidden = false;
}

function ocultarMensaje() {
  $('mensaje').hidden = true;
}

/** Texto genérico para cuando la respuesta de error no trae el JSON esperado. */
function textoPorEstado(estado) {
  switch (estado) {
    case 0: return 'No se pudo contactar al Controlador Java. Verifique que el servidor esté encendido.';
    case 400: return 'Los datos enviados no son válidos.';
    case 404: return 'No se encontró el recurso solicitado.';
    case 405: return 'La operación no está permitida.';
    case 409: return 'Ya existe un registro con ese código.';
    case 502: return 'Uno de los servicios de la cadena no está disponible.';
    case 503: return 'La base de datos no está disponible.';
    case 504: return 'Uno de los servicios de la cadena no respondió a tiempo.';
    default: return 'Ocurrió un error inesperado.';
  }
}

/** Muestra el error de una respuesta usando el formato { error, detalles, origen }. */
function mostrarError(resultado, accion) {
  const datos = resultado.datos && typeof resultado.datos === 'object' ? resultado.datos : {};

  let texto = accion + ': ';
  texto += typeof datos.error === 'string' && datos.error !== '' ? datos.error : textoPorEstado(resultado.estado);

  const notas = [];
  if (resultado.estado !== 0) {
    notas.push('HTTP ' + resultado.estado);
  }
  if (typeof datos.origen === 'string' && datos.origen !== '') {
    notas.push('detectado por: ' + (NOMBRES_ORIGEN[datos.origen] || datos.origen));
  }
  if (notas.length > 0) {
    texto += ' (' + notas.join(', ') + ')';
  }

  mostrarMensaje('error', texto, Array.isArray(datos.detalles) ? datos.detalles : []);
}

// ---------------------------------------------------------------------------
// Materias (selector del formulario)
// ---------------------------------------------------------------------------

function opcion(valor, texto) {
  const elemento = document.createElement('option');
  elemento.value = valor;
  elemento.textContent = texto;
  return elemento;
}

async function cargarMaterias() {
  const resultado = await llamarApi('GET', '/api/materias', undefined, 'Cargar materias');

  campoMateria.replaceChildren();
  if (!resultado.ok || !Array.isArray(resultado.datos)) {
    campoMateria.appendChild(opcion('', 'No se pudieron cargar las materias'));
    mostrarError(resultado, 'No se pudieron cargar las materias');
    return false;
  }

  campoMateria.appendChild(opcion('', 'Seleccione una materia'));
  resultado.datos.forEach((materia) => {
    campoMateria.appendChild(opcion(String(materia.id), materia.codigo + ' - ' + materia.nombre));
  });
  return true;
}

// ---------------------------------------------------------------------------
// Tabla de monitores
// ---------------------------------------------------------------------------

function nombreMateria(monitor) {
  if (monitor.materia && typeof monitor.materia === 'object') {
    return monitor.materia.nombre;
  }
  return 'Materia ' + monitor.materia_id;
}

function celda(texto) {
  const td = document.createElement('td');
  td.textContent = texto;
  return td;
}

function botonDeFila(texto, clase, alHacerClic) {
  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = 'boton pequeno ' + clase;
  boton.textContent = texto;
  boton.addEventListener('click', alHacerClic);
  return boton;
}

function pintarTabla(monitores) {
  const cuerpo = $('tabla-cuerpo');
  cuerpo.replaceChildren();

  monitores.forEach((monitor) => {
    const fila = document.createElement('tr');
    fila.appendChild(celda(monitor.codigo));
    fila.appendChild(celda(monitor.nombre_completo));
    fila.appendChild(celda(monitor.correo));
    fila.appendChild(celda(nombreMateria(monitor)));

    const acciones = document.createElement('td');
    acciones.className = 'celda-acciones';
    acciones.appendChild(botonDeFila('Editar', 'secundario', () => iniciarEdicion(monitor)));
    acciones.appendChild(botonDeFila('Eliminar', 'peligro', () => eliminarMonitor(monitor)));
    fila.appendChild(acciones);

    cuerpo.appendChild(fila);
  });

  $('tabla-vacia').hidden = monitores.length > 0;
}

/** Vuelve a pedir la lista completa. Devuelve true si se pudo cargar. */
async function cargarMonitores() {
  const resultado = await llamarApi('GET', '/api/monitores', undefined, 'Listar monitores');
  if (!resultado.ok || !Array.isArray(resultado.datos)) {
    mostrarError(resultado, 'No se pudo cargar la lista de monitores');
    return false;
  }
  pintarTabla(resultado.datos);
  return true;
}

// ---------------------------------------------------------------------------
// Formulario: registrar y editar
// ---------------------------------------------------------------------------

const FORMATO_CORREO = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

function marcarCampo(campo, idError, mensaje) {
  campo.classList.toggle('invalido', mensaje !== '');
  $(idError).textContent = mensaje;
}

/**
 * Validación en el cliente. Es solo una ayuda para el usuario: la validación
 * que cuenta la hace el Controlador Java.
 */
function validarFormulario() {
  const codigo = campoCodigo.value.trim();
  const nombre = campoNombre.value.trim();
  const correo = campoCorreo.value.trim();

  let errorCodigo = '';
  if (codigo === '') {
    errorCodigo = 'El código es obligatorio.';
  } else if (/\s/.test(codigo)) {
    errorCodigo = 'El código no puede contener espacios.';
  }

  let errorCorreo = '';
  if (correo === '') {
    errorCorreo = 'El correo es obligatorio.';
  } else if (!FORMATO_CORREO.test(correo)) {
    errorCorreo = 'Escriba un correo válido, por ejemplo nombre@ucaldas.edu.co.';
  }

  marcarCampo(campoCodigo, 'error-codigo', errorCodigo);
  marcarCampo(campoNombre, 'error-nombre', nombre === '' ? 'El nombre completo es obligatorio.' : '');
  marcarCampo(campoCorreo, 'error-correo', errorCorreo);
  marcarCampo(campoMateria, 'error-materia', campoMateria.value === '' ? 'Seleccione una materia.' : '');

  return formulario.querySelectorAll('.invalido').length === 0;
}

function limpiarValidacion() {
  marcarCampo(campoCodigo, 'error-codigo', '');
  marcarCampo(campoNombre, 'error-nombre', '');
  marcarCampo(campoCorreo, 'error-correo', '');
  marcarCampo(campoMateria, 'error-materia', '');
}

function iniciarEdicion(monitor) {
  codigoEnEdicion = monitor.codigo;

  campoCodigo.value = monitor.codigo;
  campoCodigo.disabled = true; // el código identifica al monitor y no se modifica
  campoNombre.value = monitor.nombre_completo;
  campoCorreo.value = monitor.correo;
  campoMateria.value = String(monitor.materia_id);

  tituloFormulario.textContent = 'Editar monitor ' + monitor.codigo;
  botonGuardar.textContent = 'Guardar cambios';
  botonCancelar.hidden = false;

  limpiarValidacion();
  ocultarMensaje();
  tituloFormulario.scrollIntoView({ behavior: 'smooth', block: 'start' });
  campoNombre.focus({ preventScroll: true });
}

/** Deja el formulario listo para registrar un monitor nuevo. */
function terminarEdicion() {
  codigoEnEdicion = null;
  formulario.reset();
  campoCodigo.disabled = false;
  tituloFormulario.textContent = 'Registrar monitor';
  botonGuardar.textContent = 'Registrar';
  botonCancelar.hidden = true;
  limpiarValidacion();
}

async function guardarMonitor(evento) {
  evento.preventDefault();
  ocultarMensaje();
  if (!validarFormulario()) {
    return;
  }

  const datos = {
    nombre_completo: campoNombre.value.trim(),
    correo: campoCorreo.value.trim(),
    materia_id: Number(campoMateria.value)
  };

  const editando = codigoEnEdicion !== null;
  botonGuardar.disabled = true;

  let resultado;
  if (editando) {
    resultado = await llamarApi('PUT', rutaMonitor(codigoEnEdicion), datos, 'Actualizar monitor ' + codigoEnEdicion);
  } else {
    datos.codigo = campoCodigo.value.trim();
    resultado = await llamarApi('POST', '/api/monitores', datos, 'Registrar monitor ' + datos.codigo);
  }
  botonGuardar.disabled = false;

  if (!resultado.ok) {
    mostrarError(resultado, editando ? 'No se pudo actualizar el monitor' : 'No se pudo registrar el monitor');
    return;
  }

  const codigo = editando ? codigoEnEdicion : datos.codigo;
  const exito = editando
    ? 'Monitor ' + codigo + ' actualizado correctamente.'
    : 'Monitor ' + codigo + ' registrado correctamente.';
  // El recorrido que debe quedar a la vista es el de guardar, no el de recargar la lista.
  const recorridoGuardado = capturarRecorrido();

  terminarEdicion();
  if (await cargarMonitores()) {
    mostrarMensaje('exito', exito);
  }
  restaurarRecorrido(recorridoGuardado);
}

// ---------------------------------------------------------------------------
// Eliminar
// ---------------------------------------------------------------------------

async function eliminarMonitor(monitor) {
  const confirmado = window.confirm(
    '¿Eliminar al monitor ' + monitor.codigo + ' (' + monitor.nombre_completo + ')?\nEsta acción no se puede deshacer.'
  );
  if (!confirmado) {
    return;
  }

  ocultarMensaje();
  const resultado = await llamarApi('DELETE', rutaMonitor(monitor.codigo), undefined, 'Eliminar monitor ' + monitor.codigo);
  const recorridoEliminar = capturarRecorrido();

  if (resultado.ok) {
    if (codigoEnEdicion === monitor.codigo) {
      terminarEdicion();
    }
    if (await cargarMonitores()) {
      mostrarMensaje('exito', 'Monitor ' + monitor.codigo + ' eliminado correctamente.');
    }
  } else {
    mostrarError(resultado, 'No se pudo eliminar el monitor');
    if (resultado.estado === 404) {
      // Otro usuario ya lo había eliminado: se refresca la tabla sin tapar el mensaje.
      const refresco = await llamarApi('GET', '/api/monitores', undefined, 'Listar monitores');
      if (refresco.ok && Array.isArray(refresco.datos)) {
        pintarTabla(refresco.datos);
      }
    }
  }
  restaurarRecorrido(recorridoEliminar);
}

// ---------------------------------------------------------------------------
// Conservar el recorrido de la operación principal
// ---------------------------------------------------------------------------

function capturarRecorrido() {
  return {
    activos: Array.from(document.querySelectorAll('#recorrido li.activo')).map((e) => e.dataset.eslabon),
    operacion: $('recorrido-operacion').textContent,
    id: $('recorrido-id').textContent
  };
}

function restaurarRecorrido(copia) {
  document.querySelectorAll('#recorrido li').forEach((eslabon) => {
    eslabon.classList.toggle('activo', copia.activos.indexOf(eslabon.dataset.eslabon) !== -1);
  });
  $('recorrido-operacion').textContent = copia.operacion;
  $('recorrido-id').textContent = copia.id;
}

// ---------------------------------------------------------------------------
// Consultar un monitor por código
// ---------------------------------------------------------------------------

async function buscarMonitor(evento) {
  evento.preventDefault();
  ocultarMensaje();

  const codigo = $('buscar-codigo').value.trim();
  const cajaResultado = $('resultado');
  const sinResultado = $('resultado-vacio');
  cajaResultado.hidden = true;
  sinResultado.hidden = true;

  if (codigo === '') {
    sinResultado.textContent = 'Escriba el código del monitor que desea consultar.';
    sinResultado.hidden = false;
    return;
  }

  const resultado = await llamarApi('GET', rutaMonitor(codigo), undefined, 'Consultar monitor ' + codigo);

  if (resultado.ok && resultado.datos && typeof resultado.datos === 'object') {
    const monitor = resultado.datos;
    $('res-codigo').textContent = monitor.codigo;
    $('res-nombre').textContent = monitor.nombre_completo;
    $('res-correo').textContent = monitor.correo;
    $('res-materia').textContent = nombreMateria(monitor);
    cajaResultado.hidden = false;
    return;
  }

  if (resultado.estado === 404) {
    sinResultado.textContent = 'No existe un monitor con el código "' + codigo + '".';
    sinResultado.hidden = false;
  }
  mostrarError(resultado, 'No se pudo consultar el monitor');
}

// ---------------------------------------------------------------------------
// Probar conexión: estado de cada eslabón de la cadena
// ---------------------------------------------------------------------------

const ESLABONES_SALUD = [
  { clave: 'java', nombre: 'Controlador Java' },
  { clave: 'php', nombre: 'Servicio PHP' },
  { clave: 'python', nombre: 'Servicio Python' },
  { clave: 'bd', nombre: 'Base de datos' }
];

/**
 * Recorre la respuesta anidada de /api/health ({servicio, estado, siguiente})
 * y devuelve el estado de cada eslabón: "ok", "error", "sin respuesta" o
 * "desconocido" (cuando la cadena se rompió antes de llegar a él).
 */
function interpretarSalud(resultado) {
  const estados = { java: 'desconocido', php: 'desconocido', python: 'desconocido', bd: 'desconocido' };

  if (resultado.estado === 0 || !resultado.datos || typeof resultado.datos !== 'object') {
    estados.java = 'sin respuesta';
    return estados;
  }

  const orden = ['java', 'php', 'python'];
  let nodo = resultado.datos;
  while (nodo && typeof nodo === 'object') {
    const servicio = String(nodo.servicio || '').toLowerCase();
    if (orden.indexOf(servicio) !== -1) {
      estados[servicio] = String(nodo.estado || 'desconocido');
    }
    if (typeof nodo.bd === 'string') {
      estados.bd = nodo.bd;
    }
    if (nodo.siguiente === null) {
      // El servicio no recibió respuesta del que tiene detrás.
      const posicion = orden.indexOf(servicio);
      if (posicion !== -1 && posicion + 1 < orden.length) {
        estados[orden[posicion + 1]] = 'sin respuesta';
      }
    }
    nodo = nodo.siguiente;
  }
  return estados;
}

async function probarConexion() {
  const boton = $('btn-salud');
  boton.disabled = true;
  const resultado = await llamarApi('GET', '/api/health', undefined, 'Probar conexión');
  boton.disabled = false;

  const estados = interpretarSalud(resultado);
  const lista = $('salud');
  lista.replaceChildren();

  ESLABONES_SALUD.forEach((eslabon) => {
    const estado = estados[eslabon.clave];
    const item = document.createElement('li');
    item.className = estado === 'ok' ? 'ok' : (estado === 'desconocido' ? 'desconocido' : 'fallo');

    const nombre = document.createElement('strong');
    nombre.textContent = eslabon.nombre;
    item.appendChild(nombre);
    item.appendChild(document.createTextNode(estado));
    lista.appendChild(item);
  });
  lista.hidden = false;

  if (resultado.ok) {
    mostrarMensaje('exito', 'Conexión correcta: todos los servicios de la cadena respondieron.');
  } else {
    mostrarMensaje('error', 'La cadena de servicios no está completa (' +
      (resultado.estado === 0 ? 'sin respuesta del Controlador Java' : 'HTTP ' + resultado.estado) +
      '). Revise el estado de cada eslabón.');
  }
}

// ---------------------------------------------------------------------------
// Inicio
// ---------------------------------------------------------------------------

formulario.addEventListener('submit', guardarMonitor);
botonCancelar.addEventListener('click', () => {
  terminarEdicion();
  ocultarMensaje();
});
$('form-busqueda').addEventListener('submit', buscarMonitor);
$('btn-salud').addEventListener('click', probarConexion);
$('btn-recargar').addEventListener('click', async () => {
  ocultarMensaje();
  await cargarMonitores();
});
$('mensaje-cerrar').addEventListener('click', ocultarMensaje);

(async function iniciar() {
  const materiasOk = await cargarMaterias();
  // Si las materias fallaron, el mensaje de error ya está en pantalla y se intenta igual la lista.
  const monitoresOk = await cargarMonitores();
  if (materiasOk && monitoresOk) {
    ocultarMensaje();
  }
})();
