# Arquitectura

## Módulos y regla de dependencias

```
app ──▶ core, data, player, visualizer
data ──▶ core
player ──▶ core, visualizer      (implementa AudioSource con un AudioProcessor de ExoPlayer)
visualizer ──▶ core              (NUNCA player ni Media3)
core ──▶ (solo AndroidX/Compose)
```

`visualizer` define las interfaces `AudioSource`, `AudioFrame`, `Scene` y `SceneRegistry`.
`player` las implementa o las alimenta. Añadir una escena nueva es crear una clase que
implemente `Scene` y registrarla; el motor de reproducción no cambia.

## Flujo de audio → visual

```
ExoPlayer (hilo de reproducción)
  └─ PcmTapAudioProcessor.queueInput()  ── copia sin bloquear ──▶ RingBuffer
                                                                      │
Hilo de análisis (Dispatchers.Default)                                ▼
  ventana Hann 2048 → FFT radix-2 → magnitudes → bandas log → RMS/pico → onsets
  → suavizado attack/release → AGC lento ──▶ StateFlow<AudioFrame>
                                                                      │
Hilo de render (Compose frame clock / GL thread)                      ▼
  Scene.update(frame, dt) → Scene.draw(...)   (sin asignaciones por frame)
```

Si el tap no entrega datos, `AudioSource.status` pasa a `UNAVAILABLE` y el motor emite
frames `synthetic = true`; las escenas degradan a animación lenta y la UI lo indica.

## Decisiones técnicas

| Decisión | Motivo |
|---|---|
| Tap de PCM vía `AudioProcessor` en vez de `android.media.audiofx.Visualizer` | `Visualizer` exige `RECORD_AUDIO` incluso para la propia sesión y devuelve datos degradados o nulos en muchos dispositivos. El tap no necesita permisos y entrega el PCM exacto. |
| Offload de audio deshabilitado | Con offload/passthrough los `AudioProcessor` no reciben PCM. |
| FFT propia en Kotlin | Evita dependencias de DSP con licencias que revisar; 2048 puntos a 60 Hz es trivial. |
| Compose Canvas para escenas 2D; OpenGL ES 3.0 para 3D | Canvas basta para el MVP y simplifica el ciclo de vida; GL se añade cuando lo exige la escena. |
| `MediaLibraryService` de Media3 | Notificación, pantalla bloqueada, auriculares y Bluetooth sin código propio. |
| Sin color dinámico (Material You) | La identidad visual debe ser estable y coherente con las escenas. |
| Hilt + KSP | Inyección estándar de Android; KSP evita kapt. |
| AGP 9.4 / Kotlin 2.4 / Gradle 9.6 / compileSdk 37 | AndroidX actual (Compose 1.12, lifecycle 2.11, core 1.19) exige compileSdk 37 y AGP ≥ 9.1; Hilt ≥ 2.59 exige AGP 9. Kotlin integrado en AGP. |

## Paquetes previstos

```
com.radillo.playerwave               app: Application, MainActivity
com.radillo.playerwave.ui            cáscara de navegación
com.radillo.playerwave.ui.library    pantallas de biblioteca               [Fase 1]
com.radillo.playerwave.ui.nowplaying reproductor                            [Fase 1]
com.radillo.playerwave.ui.visualizer pantalla inmersiva y ajustes           [Fase 2]
com.radillo.playerwave.core.model    Track, Album, Artist, Folder, Playlist
com.radillo.playerwave.core.ui.theme tema, colores, tipografía, formas
com.radillo.playerwave.data.library  MediaStore scanner, Room, repositorio  [Fase 1]
com.radillo.playerwave.data.settings DataStore                              [Fase 1]
com.radillo.playerwave.player.engine PlaybackController, estado
com.radillo.playerwave.player.service MediaLibraryService, notificación     [Fase 1]
com.radillo.playerwave.player.audiotap PcmTapAudioProcessor                 [Fase 2]
com.radillo.playerwave.visualizer.analysis FFT, bandas, onsets, AudioFrame
com.radillo.playerwave.visualizer.engine Scene, SceneRegistry, params
com.radillo.playerwave.visualizer.scenes escenas concretas                  [Fase 2+]
com.radillo.playerwave.visualizer.render Canvas / GL                        [Fase 2+]
```
