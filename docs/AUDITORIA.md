# Auditoría 0.9.0 (17-10-2026)

Revisión completa del código tras los fallos que el usuario encontró en el autoestéreo
JCAC10003 / alps 8227L con la 0.8.2, y rediseño de la interfaz según leyes de UX para uso al volante.

## 1. Fallos reportados: causa real y corrección

| # | Síntoma | Causa | Corrección | Prueba |
|---|---|---|---|---|
| 1 | La app se cierra al volver de «Visualizador» a «Reproduciendo», o al alternar carátula ↔ visualizador | Dos causas posibles, ambas corregidas. **a)** Al cambiar de pantalla una superficie se destruye y otra se crea casi a la vez; el análisis de audio anterior seguía vivo y el nuevo arrancaba en paralelo sobre el mismo analizador (no admite dos hilos) sin manejador de errores: cualquier excepción en `Dispatchers.Default` mata el proceso. **b)** Con `lockHardwareCanvas` el último frame lo termina el hilo de render del sistema de forma asíncrona; en Android 8–9 (lo que suelen llevar estos equipos aunque declaren 11) destruir la superficie en ese instante puede dar un cierre nativo. | a) El análisis nuevo espera (en cadena, sin cancelación) a que termine el anterior y sus errores se registran sin cerrar la app. b) En equipos de gama baja con Android real < 10 se dibuja con CPU; la superficie espera a que el hilo termine antes de liberarse; si aun así hay un cierre durante un cambio de superficie con GPU, el siguiente arranque pasa a CPU solo. Modo forzable en Diagnóstico. | `VisualizerEngineRenderTest` (reinicio sin solapes) |
| 2 | La reproducción en segundo plano no funciona | El reproductor cedía el audio como cualquier app «educada»: en los autoestéreos el escritorio, la radio o el Bluetooth del equipo piden el foco de audio al volver al escritorio y la música se pausaba. | Foco de audio «de coche» (`KeepPlayingAudioFocus`): una pérdida permanente ya no pausa; las breves (navegación, llamadas) pausan o bajan el volumen y la música vuelve sola. Activo por defecto en pantallas de coche; ajuste «Seguir sonando aunque otra app pida el audio». El motivo de cada pausa queda en el registro. | `PlaybackServiceTest` (pérdida permanente, breve y con atenuación) |
| 3 | No se recuerdan aleatorio, repetición, lista ni carpeta | Nadie lo guardaba: el estado vivía solo en el `ExoPlayer` y en `ViewModel`s. | El servicio guarda cola, canción, posición, aleatorio y repetición (JSON atómico, ≤ 5000 canciones) y los restaura al crearse, sin decodificar hasta que se pulse reproducir. La app vuelve a la última sección, pestaña, posición de la lista y carpeta/álbum abiertos. | `PlaybackServiceTest` (reinicio del servicio, archivo dañado, cola enorme) |
| 4 | Tarda mucho en buscar música al abrir | Cada arranque releía MediaStore completo y reescribía **todas** las filas en Room, lo que invalidaba las 5 consultas de la biblioteca; además cada aviso de MediaStore (p. ej. mientras el sistema indexa una USB) relanzaba el escaneo completo. | La caché de Room se muestra al instante. Una huella barata (nº de pistas, id máximo, fecha y tamaño) decide si hace falta escanear; si sí, solo se escriben las diferencias. Avisos agrupados 3 s y receptor de montaje de USB/tarjeta. Botón «Buscar música nueva» (biblioteca y Ajustes) con resultado visible. | `LibrarySyncTest` |

Además, las cuatro correcciones subidas el 16-10 **no compilaban** (vista sin importar, dependencia
`:player → :data` inexistente, API de Media3 inventada) y nunca generaron APK; se revirtieron.

## 2. Otros hallazgos de la auditoría

- **Rendimiento:** la biblioteca repintaba hasta 8 veces al abrir y convertía miles de canciones
  en filas en el hilo principal → ahora una sola emisión construida en segundo plano. La cola
  rehacía todas sus filas dos veces por segundo (cada tic de progreso) → solo cuando cambia.
  «Reproduciendo» y el mini reproductor ya no recargan iconos en cada tic.
- **Prevención de errores:** borrar una lista no pedía confirmación → ahora sí.
- **Diagnóstico sin adb:** `EventLog` guarda arranques, pausas y su motivo, errores de
  reproducción, escaneos y la traza del último cierre inesperado. Todo aparece en Diagnóstico y en
  el informe que se copia.
- **Reproducir tras restaurar:** reproducir con la cola restaurada prepara el reproductor si está
  en reposo y vuelve al principio si había terminado.
- **CI:** `ScreenshotTest` genera capturas de 1024×600 (claro/oscuro) y de móvil en cada
  compilación y las sube a la rama `ci-capturas`.

## 3. Rediseño de la interfaz (leyes de UX aplicadas)

| Pantalla | Antes | Ahora | Principio |
|---|---|---|---|
| Biblioteca | Título «Biblioteca» repetido, pestañas con aspecto de botón y dos filas de controles (solo 4 canciones visibles) | Pestañas de texto subrayadas en lugar del título; una sola cabecera con número de canciones, actualizar y Aleatorio/Reproducir | Jakob (patrón de pestañas conocido), Von Restorff (un solo botón destacado), economía de espacio |
| Biblioteca | La canción que suena solo cambiaba de color | Fondo propio + icono de ecualizador | Región común (Gestalt), accesible sin depender del color |
| Biblioteca | Botón de actualizar ambiguo en la barra | Junto al número de canciones y con resultado («3 canciones nuevas») | Proximidad, visibilidad del estado del sistema |
| Reproduciendo | Carátula de 300 dp, cinco botones de transporte casi iguales, iconos sueltos sin texto | Carátula de 380 dp; reproducir 104 dp > anterior/siguiente 80 dp > aleatorio/repetir 64 dp; acciones con icono y texto; «A continuación: …»; barra de progreso gruesa | Fitts (tamaño según uso), reconocer en vez de recordar, visibilidad del estado |
| Visualizador | Siete botones de escena siempre visibles tapando la imagen | Nombre de la escena con flechas grandes; la lista completa al tocar el nombre; controles que se ocultan solos mientras suena | Hick (menos opciones a la vista), revelación progresiva, Fitts |
| Visualizador | Reproducir pequeño y distinto de «Reproduciendo» | Mismos controles y tamaños que en «Reproduciendo» | Consistencia |
| Mini reproductor | Sin progreso ni «anterior» | Progreso fino y anterior/reproducir/siguiente | Visibilidad del estado, Fitts |
| Ajustes | Tema en tres filas de radio; lo importante al final | Selector de una fila; secciones por frecuencia de uso (reproducción, biblioteca, apariencia, dispositivo) | Hick, ley de Tesler (lo complejo, al final) |
| Toda la app | Acciones sin confirmación visible | Avisos breves al añadir a la cola, favoritos o listas | Umbral de Doherty / retroalimentación |
| Toda la app | Al abrir, siempre en Biblioteca > Canciones | Vuelve a la última sección, pestaña, posición y carpeta | Memoria del usuario, menor carga cognitiva |

Se mantienen los criterios de conducción de las fases anteriores: objetivos táctiles ≥ 64 dp en
el coche, texto ≥ 20 sp, tema oscuro por defecto, sin animaciones de pantalla.

## 4. Pruebas

68 pruebas en CI (JUnit + Robolectric), 9 nuevas en esta versión: reinicio del análisis sin
solapes; persistencia de la cola (reinicio del servicio, archivo dañado, recorte); foco de audio
de coche; diferencia de biblioteca, huella y resultado del escaneo. Además, la inflación de todos
los layouts en móvil y 1024×600 (claro/oscuro) y las capturas de cada pantalla.

Pendiente de confirmar en el equipo real: que no haya cierres al cambiar de pantalla (si los hay,
Diagnóstico ▸ «Último cierre inesperado» tendrá la traza) y que la música siga al volver al
escritorio.
