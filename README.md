# RadilloPlayerWave

Reproductor de música local para Android centrado en visualizaciones reactivas al audio.
Nombre provisional: comprobar disponibilidad de marca antes de cualquier uso comercial.

**Estado: Fase 0 (esqueleto).** La app abre con tres destinos vacíos. La biblioteca y la
reproducción llegan en la Fase 1; las escenas del visualizador, en la Fase 2.

## Requisitos

| Herramienta | Versión |
|---|---|
| Android Studio | Rabbit 1 (2026.2.1) o Quail 4 (2026.1.4): las únicas que admiten AGP 9.4 |
| JDK | 17 o superior (el JBR 21 que incluye Android Studio sirve) |
| Android SDK | Platform 37 y Build-Tools 36.0.0 (Android Studio los descarga al sincronizar) |
| Gradle | 9.6.0 (lo descarga el *wrapper*; no hay que instalarlo) |

## Abrir y compilar

1. Descomprime el proyecto y ábrelo en Android Studio con **File ▸ Open…** seleccionando la carpeta `RadilloPlayerWave`.
2. Acepta la sincronización de Gradle. La primera vez descarga el SDK que falte y las dependencias (varios minutos).
3. **Build ▸ Build App Bundle(s) / APK(s) ▸ Build APK(s)**, o desde terminal:

   ```bash
   ./gradlew :app:assembleDebug
   ```
   El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
4. Instálalo en un dispositivo con depuración USB o arrástralo a un emulador.

Ejecutar las pruebas unitarias:

```bash
./gradlew test
```

## Estructura

```
app/         Ensamblaje: Application (Hilt), MainActivity, navegación, recursos
core/        Modelos de dominio, tema (claro/oscuro), tipografía, formas
data/        Biblioteca (MediaStore + Room), ajustes (DataStore)         [Fase 1]
player/      ExoPlayer, MediaSession, servicio, tap de PCM               [Fase 1-2]
visualizer/  Análisis (FFT, bandas, onsets), motor de escenas, render   [Fase 2+]
docs/        Arquitectura, dependencias y licencias, limitaciones
```

La regla de dependencias es estricta: `visualizer` nunca depende de `player` ni de Media3.
Ver `docs/ARCHITECTURE.md`.
