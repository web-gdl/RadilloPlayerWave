# RadilloPlayerWave

Reproductor de música local para Android centrado en visualizaciones reactivas al audio.
Nombre provisional: comprobar disponibilidad de marca antes de cualquier uso comercial.

**Estado: Fase 4 (0.5.0).** Biblioteca de música local (canciones, álbumes, artistas, carpetas y
listas), favoritos, búsqueda, reproducción en segundo plano con notificación y pantalla bloqueada,
cola y mini reproductor, temporizador de apagado, ecualizador y refuerzo de graves del
dispositivo (solo cuando existen de verdad), tema claro/oscuro con acento de la carátula,
disposición adaptada a pantallas anchas, y el **visualizador**: análisis de audio en tiempo real
(sin micrófono) con siete escenas originales — Espectro, Osciloscopio, Anillos, Partículas, Túnel,
Campo de líneas y Ambiente —, modo inmersivo, paletas, ajustes de sensibilidad, intensidad,
velocidad, densidad y sincronía, y un gobernador de calidad que reduce el detalle cuando el
dispositivo no mantiene la fluidez.

**APK:** cada *push* a `main` compila en GitHub Actions y publica el APK de depuración en
**Releases**: <https://github.com/web-gdl/RadilloPlayerWave/releases/latest> (archivo
`RadilloPlayerWave-debug.apk`; liga directa:
<https://github.com/web-gdl/RadilloPlayerWave/releases/latest/download/RadilloPlayerWave-debug.apk>).
También queda como artefacto de la ejecución (**Actions ▸ ejecución ▸ Artifacts**), con 30 días
de caducidad. Mientras el repositorio sea privado hace falta tener la sesión de GitHub iniciada.

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
core/        Modelos de dominio, tema (claro/oscuro, acento de carátula), tipografía, formas
data/        Biblioteca (MediaStore + Room), favoritos y listas (Room), ajustes (DataStore)
player/      ExoPlayer, MediaSession, servicio, tap de PCM (audiotap), efectos (audiofx), temporizador
visualizer/  Análisis (FFT, bandas, onsets), motor de escenas, escenas, render
docs/        Arquitectura, dependencias y licencias, limitaciones
```

La regla de dependencias es estricta: `visualizer` nunca depende de `player` ni de Media3.
Ver `docs/ARCHITECTURE.md`.
