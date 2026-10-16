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
- Las escenas usan Compose Canvas; la escena de terreno 3D (OpenGL ES) llega en la Fase 5.
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
- En pantallas anchas (≥ 600 dp) se usa un raíl de navegación lateral y «Reproduciendo» pasa a
  dos columnas; no hay disposición específica para plegables con bisagra.

## Estado por fase

- **Fase 0:** esqueleto; compilado en CI.
- **Fase 1:** biblioteca y reproducción; compilado y probado en CI.
- **Fase 2 (MVP):** motor de visualización y escenas Espectro/Osciloscopio/Anillos;
  compilado y probado en CI (FFT, bandas, suavizado/AGC/onsets, anillo PCM, analizador,
  historial de frames, procesador de audio con Robolectric).
- **Fase 3 (0.4.0):** favoritos, listas, búsqueda, temporizador de apagado, ecualizador,
  ajustes (tema, colores de carátula) y disposición adaptativa; compilado y probado en CI
  (repositorio de favoritos/listas con Room en memoria y Robolectric).
- **Fase 4 (actual, 0.5.0):** escenas Partículas, Túnel, Campo de líneas y Ambiente, y gobernador
  de calidad; compilado y probado en CI (gobernador con frames lentos/rápidos/pausas y pantallas
  de 120 Hz; ciclo de vida y simulación de las siete escenas con frames reales, sintéticos y
  tamaños extremos).
- Pendiente de prueba manual en dispositivo por parte del usuario (sincronía, rendimiento de
  cada escena, comportamiento del gobernador, ecualizador en hardware real).
