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
| Sin color dinámico (Material You) | La identidad visual debe ser estable y coherente con las escenas. El único acento variable es el de la carátula en reproducción (Palette), opcional y ajustado en saturación/luminosidad para conservar el contraste. |
| Ecualizador con `android.media.audiofx` ligado al `audioSessionId` del ExoPlayer | Es el único ecualizador verificable sin reimplementar DSP. Si el dispositivo no expone bandas, la UI lo dice y no simula nada. El efecto se crea solo cuando está activado; en reposo se sondea la disponibilidad y se libera. |
| Favoritos y listas en Room, no en MediaStore | Las listas de MediaStore están obsoletas desde Android 11 y no admiten favoritos. Tablas `favorites`, `playlists`, `playlist_tracks` (posición explícita) en la misma base de datos que la caché. |
| Temporizador de apagado en el proceso de la app (`SleepTimer`) | Pausa vía `PlaybackController`; sin alarmas ni `WorkManager`. Si el sistema mata el proceso, también para la música, así que no hace falta más. |
| Disposición adaptativa por ancho (≥ 600 dp) | Raíl de navegación lateral y pantalla «Reproduciendo» en dos columnas; medido con `LocalWindowInfo.containerSize`, sin dependencia extra de *window size class*. |
| Hilt + KSP | Inyección estándar de Android; KSP evita kapt. |
| AGP 9.4 / Kotlin 2.4 / Gradle 9.6 / compileSdk 37 | AndroidX actual (Compose 1.12, lifecycle 2.11, core 1.19) exige compileSdk 37 y AGP ≥ 9.1; Hilt ≥ 2.59 exige AGP 9. Kotlin integrado en AGP. |

## Flujo de ajustes y efectos (Fase 3)

```
DataStore "app_settings" ──▶ AppSettingsRepository ──▶ SettingsViewModel ──▶ RadilloApp: RadilloTheme(dark, accent)
                         └─▶ AudioEffectsSettingsRepository ──▶ RadilloApplication.onCreate: AudioEffectsController.apply()
PlaybackService ── audioSessionId ──▶ AudioEffectsHub ──▶ AudioEffectsController (Equalizer/BassBoost) ◀── EqualizerViewModel
PlayerViewModel: carátula actual ──▶ ArtworkAccentExtractor (Palette, caché LRU) ──▶ accent (animado) ──▶ tema
```

## Paquetes

```
com.radillo.playerwave               app: Application (restaura el ecualizador), MainActivity
com.radillo.playerwave.ui            cáscara: tema desde ajustes, barra/raíl, rutas, mini reproductor
com.radillo.playerwave.ui.adaptive   isWideScreen()
com.radillo.playerwave.ui.library    biblioteca (pestañas), colección, lista, búsqueda, hoja de acciones
com.radillo.playerwave.ui.player     reproduciendo (favorito, temporizador, dos columnas), cola, mini reproductor
com.radillo.playerwave.ui.effects    ecualizador
com.radillo.playerwave.ui.settings   ajustes (tema, colores de carátula, acerca de, licencias)
com.radillo.playerwave.ui.visualizer pantalla del visualizador, inmersivo, ajustes
com.radillo.playerwave.di            módulo Hilt del motor de visualización
com.radillo.playerwave.core.model    Track, Album, Artist, Folder, Playlist, AppSettings, AudioEffectsSettings
com.radillo.playerwave.core.ui       ArtworkAccentExtractor; .theme tema, colores, tipografía, formas
com.radillo.playerwave.data.library  MediaStore scanner, Room (v2: pistas, favoritos, listas), repositorio
com.radillo.playerwave.data.collections PlaylistRepository (favoritos y listas)
com.radillo.playerwave.data.settings ajustes del visualizador y de la app (DataStore)
com.radillo.playerwave.player.engine PlaybackController, estado, SleepTimer
com.radillo.playerwave.player.service MediaLibraryService, notificación, publica audioSessionId
com.radillo.playerwave.player.audiofx AudioEffectsHub, AudioEffectsController
com.radillo.playerwave.player.audiotap PcmTapAudioProcessor, TapAudioSource, RenderersFactory
com.radillo.playerwave.visualizer.analysis FFT, bandas, onsets, AudioFrame
com.radillo.playerwave.visualizer.engine Scene, SceneRegistry, params
com.radillo.playerwave.visualizer.scenes CanvasScene, SpectrumBars, Oscilloscope, Rings
com.radillo.playerwave.visualizer.render VisualizerSurface (Compose Canvas)
```
