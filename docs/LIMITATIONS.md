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
- Con salida Bluetooth puede haber un desfase de 100-300 ms entre imagen y sonido; se añade
  un ajuste de sincronía manual en la Fase 2.

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
- **Fase 1 (actual):** biblioteca y reproducción; compilado y probado en CI (unitarias +
  Robolectric para permisos, mapeo de MediaItem y ciclo de vida del servicio).
- Pendiente de prueba manual en dispositivo por parte del usuario.
