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

## Estado por fase

- **Fase 0 (actual):** la app arranca con tres destinos vacíos. No hay reproducción.
- El proyecto se ha generado y revisado sin acceso a un SDK de Android en el entorno de
  desarrollo del asistente; la primera compilación la realiza el usuario en Android Studio.
  Cualquier error de sincronización se corrige antes de pasar a la Fase 1.
