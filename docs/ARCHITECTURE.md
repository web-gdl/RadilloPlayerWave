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

## Flujo de audio → visual (implementado en la Fase 2)

```
ExoPlayer (hilo de reproducción)
  └─ PcmTapAudioProcessor.queueInput()  ── copia little-endian → float, sin bloquear ──▶ PcmRingBuffer (SPSC)
                                                                                           │ señal (Channel CONFLATED)
Hilo de análisis (Dispatchers.Default, solo con una superficie en primer plano)            ▼
  TapAudioSource.blocks → AudioAnalyzer: ventana Hann 2048 → FFT radix-2 → magnitudes → bandas log (64)
  → escala dB (−60..0) → AGC lento → OnsetDetector (flujo espectral, umbral adaptativo)
  → suavizado ataque/liberación → bass/mid/treble, RMS, pico, formas de onda L/R
  ──▶ FrameHistory (frame + instante de llegada, ~3 s)
                                                                                           │
Hilo de render (Compose: withFrameNanos → VisualizerEngine.onFrame)                        ▼
  PlaybackClock.positionUs() + desplazamiento manual → FrameHistory.select():
     por marca temporal (misma pista) o por instante de llegada − latencia estimada (cambio de pista)
  → Scene.update(frame, dt) → Scene.draw(DrawScope)   (buffers preasignados; fundido entre escenas con saveLayer)
```

Estados del motor (`DataState`): `LIVE` (audio real alineado), `IDLE` (pausa: el último
frame decae), `SYNTHETIC` (reproduciendo sin PCM fiable: movimiento ambiental y aviso en la UI).

Marcas temporales: `AudioProcessor.StreamMetadata.positionOffsetUs` fija la referencia en cada
`flush` (seek); dentro de una pista los frames se eligen por posición exacta y, de paso, se
mide la latencia real del `AudioTrack`, que sirve de respaldo cuando las marcas dejan de ser
comparables (transición automática entre pistas).

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
com.radillo.playerwave.ui.player     reproductor, cola, mini reproductor
com.radillo.playerwave.ui.visualizer pantalla del visualizador, inmersivo, ajustes
com.radillo.playerwave.di            módulo Hilt del motor de visualización
com.radillo.playerwave.core.model    Track, Album, Artist, Folder, Playlist
com.radillo.playerwave.core.ui.theme tema, colores, tipografía, formas
com.radillo.playerwave.data.library  MediaStore scanner, Room, repositorio
com.radillo.playerwave.data.settings ajustes del visualizador (DataStore)
com.radillo.playerwave.player.engine PlaybackController, estado
com.radillo.playerwave.player.service MediaLibraryService, notificación
com.radillo.playerwave.player.audiotap PcmTapAudioProcessor, TapAudioSource, RenderersFactory
com.radillo.playerwave.visualizer.analysis FFT, bandas, onsets, AudioFrame
com.radillo.playerwave.visualizer.engine Scene, SceneRegistry, params
com.radillo.playerwave.visualizer.scenes CanvasScene, SpectrumBars, Oscilloscope, Rings
com.radillo.playerwave.visualizer.render VisualizerSurface (Compose Canvas)
```
