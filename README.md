# RadilloPlayerWave

Reproductor de música local para Android con un motor de visualización en tiempo real,
**optimizado para autoestéreos Android genéricos de gama baja** — modelo **JCAC10003**,
plataforma **alps 8227L** (CPU AC822TL / MediaTek MT8227L: Cortex‑A7 de 32 bits, GPU Mali‑450,
1 GB de RAM, pantalla de 1024×600) — y que también funciona en teléfonos y tabletas.

[![Android CI](https://github.com/web-gdl/RadilloPlayerWave/actions/workflows/android.yml/badge.svg)](https://github.com/web-gdl/RadilloPlayerWave/actions/workflows/android.yml)
[![Última versión](https://img.shields.io/github/v/release/web-gdl/RadilloPlayerWave?label=APK)](https://github.com/web-gdl/RadilloPlayerWave/releases/latest)

**Descargar el APK:** <https://github.com/web-gdl/RadilloPlayerWave/releases/latest/download/RadilloPlayerWave.apk>

## Reseña

RadilloPlayerWave nació como un reproductor de MP3 «de los de antes»: tu música, en tu
dispositivo, sin cuentas, sin anuncios, sin telemetría y sin pedir permisos que no necesita (ni
siquiera el micrófono, aunque tenga visualizador). Reproduce lo que haya en la memoria interna o
en una memoria USB, organiza por canciones, álbumes, artistas, carpetas y listas, guarda favoritos,
busca, sigue sonando en segundo plano con controles en la notificación y la pantalla bloqueada
(también cuando el escritorio o la radio del coche piden el audio), recuerda dónde te quedaste,
permite **editar etiquetas y portada** de los MP3 (se guardan dentro del archivo),
tiene temporizador de apagado y usa el ecualizador y el refuerzo de graves del propio dispositivo
cuando existen de verdad (nunca los simula).

Lo que lo distingue es el **visualizador**: analiza el audio que reproduce (FFT en tiempo real
sobre el PCM, sin micrófono) y lo convierte en siete escenas originales — Espectro, Osciloscopio,
Anillos, Partículas, Túnel, Campo de líneas y Ambiente — con paletas, modo inmersivo y ajustes de
sensibilidad, intensidad, velocidad, densidad y sincronía imagen/sonido.

Y lo que lo hace distinto de la mayoría de reproductores modernos es **para quién está
optimizado**: para los autoestéreos Android baratos que vienen en millones de coches —
concretamente el modelo **JCAC10003** sobre la plataforma **alps 8227L (CPU AC822TL, MediaTek)**,
con 1 GB de RAM, procesador Cortex‑A7 de 32 bits, GPU Mali‑450 (solo OpenGL ES 2.0), pantalla
de 1024×600 a 160 dpi y una versión de Android que suele estar «maquillada» en la ROM. En esos
equipos las apps actuales van a tirones o se cierran por falta de memoria. Por eso aquí:

- el APK **release** pesa ~1,4 MB (vistas nativas sin Compose, R8, recursos reducidos, solo audio en el reproductor);
- la app detecta el **nivel de Android real** aunque el sistema declare otro, y no usa nada que no
  exista en el equipo;
- un **perfil de dispositivo** ajusta solo los valores por defecto: visualizador a 30 fps, menos
  partículas y líneas, búferes de audio de 2 MB, carátulas pequeñas, pantalla horizontal fija;
- el visualizador dibuja en un **`SurfaceView` con su propio hilo** y por lotes, así que un
  fotograma lento nunca congela los botones;
- un **gobernador de calidad** mide la fluidez y baja el detalle cuando hace falta;
- una pantalla de **Diagnóstico** muestra RAM, GPU, Android real y fps, el registro de eventos y el
  último cierre inesperado, con el informe copiable (en estos equipos no suele haber `adb`).

La adaptación avanza por fases documentadas en `docs/TARGET_DEVICE.md`: A (cimientos),
B (visualizador en `SurfaceView`) y C (interfaz en vistas nativas para 1024×600, sin Compose)
están hechas; de la D (coche) ya están el escaneo al conectar la USB y, en estéreos Jancar,
las teclas del volante y la vuelta al encender el auto.

> **In English:** a local music player for Android with a real‑time audio visualizer (FFT on the
> player's own PCM, no microphone), tuned for cheap Android car head units — model JCAC10003 on the
> alps 8227L platform (AC822TL / MediaTek MT8227L, 1 GB RAM, Cortex‑A7, Mali‑450, 1024×600). Release
> APK ≈ 1.4 MB (native Views, no Compose), real‑API‑level detection, low‑end device profile, 30 fps cap, quality governor and a
> diagnostics screen. Spanish and English UI.

Nombre provisional: comprobar disponibilidad de marca antes de cualquier uso comercial.

## Capturas (autoestéreo 1024×600)

| Biblioteca | Reproduciendo |
|---|---|
| ![Biblioteca](docs/screenshots/biblioteca.png) | ![Reproduciendo](docs/screenshots/reproduciendo.png) |
| **Visualizador** | **Ajustes** |
| ![Visualizador](docs/screenshots/visualizador.png) | ![Ajustes](docs/screenshots/ajustes.png) |

## Estado

**0.9.0 (auditoría y rediseño).** Corrige los cierres al cambiar entre el visualizador y
«Reproduciendo», sigue sonando en el coche aunque el equipo pida el audio, recuerda cola,
aleatorio, repetición y carpeta, y arranca sin volver a escanear la música. Interfaz rediseñada
para usarla al volante. Detalles en `docs/AUDITORIA.md`.

**0.10.x:** editor de etiquetas y portada (título, artista, álbum, año, pista y portada) que
escribe dentro del MP3 a prueba de cortes de corriente (copia de seguridad, diario, verificación por
CRC y recuperación al arrancar); también en memorias USB.

**0.11.0:** integración con el estéreo (equipos Jancar como el alps 8227L): la app se registra
como fuente de audio del sistema, así que responde a las teclas del volante, se pausa en las
llamadas, no se cierra al apagar el auto y sigue sonando al encenderlo; el estéreo la vuelve a
abrir al arrancar. Con la cámara de reversa el visualizador deja de dibujar. En otros equipos
la app funciona como siempre.

**0.12.0:** crossfade (Ajustes ▸ Reproducción): mezcla el final de una canción con el principio de
la siguiente, de 2 a 12 segundos, sin silencio entre ellas.

**Funciones:** biblioteca de música local (canciones,
álbumes, artistas, carpetas y listas), favoritos, búsqueda, reproducción en segundo plano con
notificación y pantalla bloqueada, cola y mini reproductor, temporizador de apagado, ecualizador y
refuerzo de graves del dispositivo (solo cuando existen de verdad), tema claro/oscuro con acento de
la carátula, disposición adaptada a pantallas anchas, y el visualizador con siete escenas, modo
inmersivo, paletas, ajustes y gobernador de calidad. Pendiente de prueba en el equipo real por
parte de sus usuarios (ver la pantalla Diagnóstico).

**Dispositivo objetivo:** autoestéreo JCAC10003 / alps 8227L (ver arriba). La adaptación se describe
en `docs/TARGET_DEVICE.md` y avanza por fases (A: cimientos ✅ · B: visualizador en SurfaceView ✅ ·
C: UI en Vistas ✅ · D: coche). La app sigue funcionando en teléfonos y tabletas.

**APK:** cada *push* a `main` compila en GitHub Actions un APK **release** (R8, recursos
reducidos) y lo publica en **Releases**: <https://github.com/web-gdl/RadilloPlayerWave/releases/latest>
(archivo `RadilloPlayerWave.apk`; liga directa:
<https://github.com/web-gdl/RadilloPlayerWave/releases/latest/download/RadilloPlayerWave.apk>).
También queda como artefacto de la ejecución (**Actions ▸ ejecución ▸ Artifacts**), con 30 días
de caducidad. Para instalarlo en el equipo: descargar el archivo, abrirlo y aceptar la instalación
desde origen desconocido.

**Firma:** la clave de release viaja cifrada en `keystore/radillo-release.jks.enc` y CI la descifra
con el secreto `RADILLO_KEYSTORE_PASSWORD`. Sin el secreto, el APK se firma con la clave de
depuración (el resumen de cada compilación indica cuál se usó).

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
   ./gradlew :app:assembleRelease
   ```
   El APK queda en `app/build/outputs/apk/release/app-release.apk` (firmado con la clave de
   depuración si no se definen `RADILLO_KEYSTORE_PATH` y `RADILLO_KEYSTORE_PASSWORD`). Para el
   dispositivo objetivo usa siempre el **release**: el debug es varias veces más lento.
4. Instálalo en un dispositivo con depuración USB o arrástralo a un emulador.

Ejecutar las pruebas unitarias:

```bash
./gradlew test
```

## Estructura

```
app/         Actividad única, fragments XML + ViewBinding, navegación propia, recursos (1024×600 y móvil)
core/        Modelos, perfil del dispositivo, carga de carátulas (RGB_565) y acento de carátula
data/        Biblioteca (MediaStore + Room), favoritos y listas (Room), ajustes (SharedPreferences)
player/      ExoPlayer, MediaSession, servicio, tap de PCM (audiotap), efectos (audiofx), temporizador
visualizer/  Análisis (FFT, bandas, onsets), motor, escenas sobre Canvas, SurfaceView con hilo de render
docs/        Arquitectura, dependencias y licencias, limitaciones, auditoría 0.9
```

La regla de dependencias es estricta: `visualizer` nunca depende de `player` ni de Media3.
Ver `docs/ARCHITECTURE.md`.

## Licencia

**Dominio público.** RadilloPlayerWave se entrega sin restricciones bajo
[The Unlicense](https://unlicense.org): cualquiera puede copiarlo, modificarlo, venderlo o
distribuirlo, con o sin atribución, para lo que quiera. Es un regalo para la humanidad.

Las bibliotecas de terceros que incluye el APK (AndroidX, Media3, Kotlin, Dagger Hilt, Coil, Material
Icons) conservan sus propias licencias, todas Apache 2.0; el inventario está en
`docs/DEPENDENCIES.md` y en la pantalla **Ajustes ▸ Licencias de terceros** de la app.
