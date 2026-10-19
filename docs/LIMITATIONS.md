# Limitaciones conocidas y compatibilidad

## Compatibilidad de Android

| Versión | API | Estado |
|---|---|---|
| Android 7.0 – 7.1 | 24 – 25 | Soportado. Icono legacy (PNG), sin icono adaptativo. |
| Android 8.0 – 12L | 26 – 32 | Soportado. Permiso `READ_EXTERNAL_STORAGE` para la biblioteca (Fase 1). |
| Android 13+ | 33+ | Soportado. Permisos `READ_MEDIA_AUDIO` y `POST_NOTIFICATIONS` (Fase 1). |
| Android 14+ | 34+ | Soportado. Servicio en primer plano de tipo `mediaPlayback` (Fase 1). |

Por debajo de API 24 no se da soporte: Media3 exige API 23 y la cuota de usuarios no justifica
el coste de mantenimiento.

## Editor de etiquetas y portada

- Solo MP3 (ID3). Se escribe ID3v2.3, la versión que entienden todos los reproductores; las
  etiquetas 2.2 y 2.4 se convierten al guardar. Los marcos no editados se conservan, salvo
  comentarios o letras en UTF-8 de una etiqueta 2.4 (poco comunes), que se descartan.
- Se comprueba que el contenido sea MPEG de verdad (dos tramas válidas seguidas): un archivo
  «.mp3» que en realidad es AAC/MP4, WAV, FLAC u Ogg no se toca. Tampoco una etiqueta que no se
  pueda leer entera (se perderían marcos).
- Si la etiqueta nueva cabe en el hueco de la anterior, solo se reescribe ese hueco y el audio no
  se mueve; si no, se reescribe el archivo con 8 KB de relleno para que las siguientes ediciones
  sí quepan.
- Antes de tocar la canción se guarda (sincronizada a disco) una copia de seguridad y un diario
  con el CRC del antes y del después. Si la escritura falla se restaura al momento; si se corta la
  corriente, el siguiente arranque termina o deshace la edición. Mientras quede una edición sin
  cerrar (p. ej. con la USB retirada) no se permite otra. Hace falta espacio libre para dos copias.
- Si la canción está sonando, el reproductor la suelta mientras se reescribe y la retoma en el
  mismo punto.
- Escribir en una **memoria USB** requiere conceder su carpeta raíz una vez (selector de carpetas
  del sistema). Si el equipo no tiene selector de carpetas, solo se puede editar en la memoria
  interna (Android ≤ 9 con el permiso de almacenamiento).
- La miniatura que Android guarda por álbum puede tardar en renovarse; la app muestra la portada
  nueva al momento con una copia propia.

## Visualizaciones

- El análisis de audio solo cubre lo que reproduce esta app. Capturar audio de otras
  aplicaciones exigiría `RECORD_AUDIO` y la API de captura de reproducción (Android 10+),
  que además solo captura apps que lo permitan explícitamente. Queda fuera de alcance.
- Si un dispositivo fuerza offload de audio o no entrega PCM al `AudioProcessor`, las escenas
  pasan a modo de respaldo (animación no sincronizada) y la UI lo indica.
- Con salida Bluetooth puede haber un desfase de 100-300 ms entre imagen y sonido: el motor lo
  compensa alineando por posición de reproducción y, además, el ajuste «Sincronía imagen/sonido»
  permite corregirlo a mano (−300..+800 ms).
- El análisis asigna arrays nuevos por frame (~9 KB a 60 Hz) en el hilo de análisis; el render no
  asigna memoria por frame. Si en un dispositivo concreto se observa jank, el primer sospechoso es
  este punto (se puede pasar a doble buffer).
- Si una canción cambia por transición automática (sin seek), la cadena de audio no se vacía y las
  marcas temporales dejan de coincidir con la nueva posición: el motor pasa al respaldo por
  latencia estimada hasta el siguiente seek o pista. Visualmente es imperceptible salvo con
  latencias de Bluetooth muy variables.
- El tap de PCM solo recibe audio de 16 bits o float; las fuentes de alta resolución se convierten
  a 16 bits antes de la cadena (comportamiento por defecto del sink), por lo que siempre hay datos.
- Las escenas se dibujan con `android.graphics.Canvas` en un `SurfaceView` con hilo propio. Si el
  Android real es anterior a 8.0 (o `lockHardwareCanvas` falla), el render es por software: en un
  Cortex-A7 a 1024×600 conviene entonces el tope de 30 fps y densidad baja. La escena de terreno 3D
  (OpenGL ES 2.0 en Mali-450) queda como opcional al final.
- El **gobernador de calidad** solo puede medir la fluidez del propio bucle de render: si el
  dispositivo va lento por otra app o por ahorro de energía, también reducirá el detalle. Asume
  pantallas de al menos 60 Hz; en una pantalla forzada a 30 Hz bajará la calidad al mínimo sin
  necesidad (se puede desactivar en los ajustes del visualizador). El detalle se recupera despacio
  a propósito (varios segundos sin tirones) para no oscilar.
- Las escenas **Partículas** y **Campo de líneas** son las más costosas en pantallas grandes con
  densidad alta (hasta 800 partículas o ~3 000 segmentos por frame); el gobernador las reduce
  primero. **Ambiente** usa mezcla aditiva (`BlendMode.Plus`); en Android 7–8 (API 24–27) se
  traduce a `PorterDuff.ADD`, con un resultado equivalente.

## Reproducción (Fase 1)

- Formatos: los que decodifica el dispositivo vía MediaCodec más los extractores de Media3
  (MP3, AAC/M4A, FLAC, OGG/Opus, WAV, etc.). No se incluyen decodificadores por software adicionales.
- Las pistas de menos de 5 s se excluyen del escaneo (tonos y notificaciones mal etiquetadas).
- Las carátulas se leen de `content://media/external/audio/albumart/<id>`; si MediaStore no la
  ha indexado, se muestra el marcador propio.
- La cola se muestra en orden de reproducción lineal; con "aleatorio" activo el orden real lo
  decide ExoPlayer.

## Crossfade (0.12.0)

- La mezcla usa un segundo reproductor efímero solo para el final de la canción que se va; el
  visualizador sigue a la canción que entra.
- Al cambiar de canción a mano la mezcla dura como mucho 3 s. Desde la 0.13.2 el salto ocurre
  cuando la mezcla está lista (unas décimas después de pulsar; como mucho 1,5 s, y si no, salto
  normal): mientras tanto sigue sonando la canción actual, sin silencio. Pulsar varias veces
  seguidas salta sin mezclar. Elegir una canción nueva desde la biblioteca no mezcla: reemplaza
  la cola.
- No se mezcla con «repetir una», al final de la cola sin repetición, ni en canciones de menos de
  dos veces la mezcla más 5 s. Pausar en plena mezcla la corta.
- En un equipo con poca CPU, durante esos segundos se decodifican dos canciones a la vez.

## Estéreos Jancar (0.11.0)

- La integración (teclas del volante, pausas del sistema, seguir al encender, app de memoria) solo
  se activa si el equipo tiene el servicio `com.jancar.services`. Se dedujo del análisis de las
  apps de fábrica, no de documentación del fabricante: otras versiones del firmware podrían
  comportarse distinto. La pantalla Diagnóstico dice si se conectó y qué órdenes llegaron.
- El widget de música del lanzador de fábrica no muestra la canción de la app (solo pinta la
  música de fábrica y el Bluetooth) y, al tocarlo, abre la app de fábrica, que entonces se queda
  con el audio. Es comportamiento fijo del lanzador.
- Si el usuario cierra la app (desde «recientes» con la música en pausa, o desde la barra de
  estado), suelta la fuente y el estéreo ya no la abrirá al arrancar.
- Al arrancar el estéreo la música sigue solo si estaba sonando (lo decide el último play o pausa
  del usuario, no las pausas del sistema).
- Con el auto apagado y la música en espera, la app mantiene un aviso «Música en espera» en primer
  plano para que Android no la cierre; desaparece al seguir sonando. Si al encender el estéreo no
  manda «reanudar», la app reanuda sola a los 5 s.
- Si la app ya no estaba abierta al encender (el equipo se reinició), se reabre al recibir el aviso
  de arranque del sistema o de la llave. El aviso de la llave solo llega a una app cerrada si el
  estéreo lo envía así; el de arranque llega siempre que el equipo arranque de cero.
- Un escritorio que no sea el de fábrica solo muestra la canción si lee las fuentes de terceros
  (tipo 20) que publica el servicio del estéreo, y solo mientras la app está registrada (sonando o
  en espera del encendido).

## Favoritos, listas, ajustes y efectos (Fase 3)

- **Ecualizador y refuerzo de graves** usan los efectos del propio dispositivo
  (`android.media.audiofx`) sobre la sesión de audio del reproductor. Hace falta haber iniciado la
  reproducción al menos una vez para que exista la sesión; hasta entonces la pantalla lo indica.
  Si el dispositivo no ofrece un `Equalizer` operativo, la pantalla dice que no está disponible:
  no se simula nada. El número de bandas, su rango (normalmente ±15 dB) y los nombres de los
  preajustes los decide el fabricante (suelen venir en inglés).
- Los efectos se aplican al `audioSessionId` del ExoPlayer de esta app; no afectan a otras apps.
  Con salida Bluetooth algunos dispositivos aplican su propio procesado y el efecto puede ser
  menos perceptible.
- **Favoritos y listas** se guardan en la base de datos local de la app (Room), no en MediaStore:
  no se comparten con otros reproductores y se pierden al desinstalar la app. Durante el
  desarrollo la base de datos usa *migración destructiva*: al cambiar el esquema entre versiones
  se borran favoritos y listas (la caché de pistas se regenera sola). Antes de la versión 1.0
  se añadirán migraciones reales.
- El **temporizador de apagado** vive en el proceso de la app: si el sistema mata el proceso
  también se detiene la música, así que no hace falta persistirlo. Pausa (no cierra la app).
- El **acento de la carátula** se extrae con Palette de una miniatura de ~96 px y se ajusta en
  saturación y luminosidad para mantener el contraste; en carátulas muy oscuras o grises el acento
  se parece al color base del tema. Se puede desactivar en Ajustes.
- La **búsqueda** compara título, artista y álbum por subcadena, con un máximo de 200 resultados (sin acentos normalizados ni
  búsqueda fonética).
- En pantallas anchas (≥ 600 dp de ancho) las secciones van en una barra superior con Buscar y
  Ajustes, y «Reproduciendo» pasa a dos columnas (desde la 0.12); no hay disposición específica
  para plegables con bisagra.

## Estado por fase

- **Fase 0:** esqueleto; compilado en CI.
- **Fase 1:** biblioteca y reproducción; compilado y probado en CI.
- **Fase 2 (MVP):** motor de visualización y escenas Espectro/Osciloscopio/Anillos;
  compilado y probado en CI (FFT, bandas, suavizado/AGC/onsets, anillo PCM, analizador,
  historial de frames, procesador de audio con Robolectric).
- **Fase 3 (0.4.0):** favoritos, listas, búsqueda, temporizador de apagado, ecualizador,
  ajustes (tema, colores de carátula) y disposición adaptativa; compilado y probado en CI
  (repositorio de favoritos/listas con Room en memoria y Robolectric).
- **Fase 4 (0.5.0):** escenas Partículas, Túnel, Campo de líneas y Ambiente, y gobernador
  de calidad; compilado y probado en CI.
- **Adaptación al autoestéreo** (`docs/TARGET_DEVICE.md`): Fase A (0.6.0, release firmado y
  reducido, perfil del dispositivo, diagnóstico, tope de fps) y Fase B (0.7.0, visualizador en
  `SurfaceView` con hilo propio y escenas sobre Canvas) y Fase C (0.8.0, interfaz en vistas
  nativas, sin Compose ni Coil ni DataStore; APK de 1,4 MB) compiladas y probadas en CI; Fase D
  (coche) pendiente.
- Fase C: favoritos, listas y ajustes guardados con versiones anteriores se pierden al actualizar
  (los ajustes pasaron de DataStore a SharedPreferences). La interfaz en vistas no tiene todavía
  el tinte de interfaz completo con el color de la carátula: el acento se aplica al botón de
  reproducir y a la barra de progreso.
- Pendiente de prueba manual en dispositivo por parte del usuario (sincronía, rendimiento de
  cada escena, comportamiento del gobernador, ecualizador en hardware real).
