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
- Las escenas usan Compose Canvas; las escenas 3D (OpenGL ES) llegan en la Fase 5.

## Reproducción (Fase 1)

- Formatos: los que decodifica el dispositivo vía MediaCodec más los extractores de Media3
  (MP3, AAC/M4A, FLAC, OGG/Opus, WAV, etc.). No se incluyen decodificadores por software adicionales.
- Las pistas de menos de 5 s se excluyen del escaneo (tonos y notificaciones mal etiquetadas).
- Las carátulas se leen de `content://media/external/audio/albumart/<id>`; si MediaStore no la
  ha indexado, se muestra el marcador propio.
- Sin ecualizador todavía (Fase 3, solo si es verificable en el dispositivo).
- La cola se muestra en orden de reproducción lineal; con "aleatorio" activo el orden real lo
  decide ExoPlayer.

## Estado por fase

- **Fase 0:** esqueleto; compilado en CI.
- **Fase 1:** biblioteca y reproducción; compilado y probado en CI.
- **Fase 2 (actual, MVP):** motor de visualización y escenas Espectro/Osciloscopio/Anillos;
  compilado y probado en CI (FFT, bandas, suavizado/AGC/onsets, anillo PCM, analizador,
  historial de frames, procesador de audio con Robolectric).
- Pendiente de prueba manual en dispositivo por parte del usuario (sincronía, rendimiento).
