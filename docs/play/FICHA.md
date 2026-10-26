# Ficha de Google Play: textos y respuestas listos para copiar

Todo lo que pide Play Console, en el orden en que lo pide. Las imágenes están en esta misma carpeta.

## 1. Datos de la app (Crear app)

| Campo | Valor |
|---|---|
| Nombre de la app | RadilloPlayerWave |
| Idioma predeterminado | Español (Latinoamérica) – es-419 |
| App o juego | App |
| Gratis o de pago | Gratis (no se puede cambiar después a de pago) |
| Categoría | Música y audio |
| Correo de contacto | *(tu correo público de desarrollador)* |
| Sitio web | https://github.com/web-gdl/RadilloPlayerWave |
| Política de privacidad | https://github.com/web-gdl/RadilloPlayerWave/blob/main/docs/PRIVACY.md |

## 2. Ficha principal (es-419)

**Nombre (≤ 30):** RadilloPlayerWave

**Descripción breve (≤ 80):**
Música local con visualizador, pensada para el estéreo Android de tu auto

**Descripción completa (≤ 4000):**

```
RadilloPlayerWave es un reproductor de música local, ligero y sin publicidad, hecho para los estéreos Android de auto (pantallas de 1024×600 y equipos de 1 GB de RAM) y que también funciona en teléfonos y tabletas.

MÚSICA LOCAL, SIN INTERNET
• Reproduce la música de tu dispositivo y de memorias USB: MP3, AAC/M4A, FLAC, OGG/Opus y WAV.
• Biblioteca por canciones, álbumes, artistas, carpetas y listas; favoritos y búsqueda.
• Recuerda la cola, la canción, la posición, el aleatorio y la repetición.
• Sin cuentas, sin anuncios, sin rastreo: la app ni siquiera tiene acceso a internet.

HECHA PARA EL AUTO
• Botones grandes y pantallas pensadas para usarse de un vistazo.
• Sigue sonando aunque el escritorio o la radio del estéreo pidan el audio.
• En estéreos compatibles: teclas del volante, pausa en llamadas, sigue donde iba al apagar y encender el auto, y muestra la canción en el widget del escritorio.
• Abre el ecualizador del propio equipo, con balance y fader.

CROSSFADE
• Mezcla el final de una canción con el principio de la siguiente (de 2 a 12 segundos), también al cambiar de canción a mano.

VISUALIZADOR
• Siete escenas en tiempo real (espectro, anillos, túnel, partículas y más) que reaccionan a la música, con paletas, ajustes y calidad automática para equipos modestos.

EDITOR DE ETIQUETAS Y PORTADAS
• Cambia título, artista, álbum, año, número de pista y portada dentro del propio MP3, a prueba de cortes de corriente.

Código abierto (dominio público, The Unlicense): https://github.com/web-gdl/RadilloPlayerWave
```

## 3. Ficha en inglés (en-US, opcional)

**Short description:** Local music player with visualizers, made for your car's Android stereo

**Full description:**

```
RadilloPlayerWave is a lightweight, ad-free local music player built for Android car stereos (1024×600 screens, 1 GB of RAM) that also works on phones and tablets.

• Plays music from the device and USB drives: MP3, AAC/M4A, FLAC, OGG/Opus, WAV.
• Library by songs, albums, artists, folders and playlists; favorites and search. Remembers your queue and position.
• No accounts, no ads, no tracking — the app has no internet access at all.
• Made for the car: big controls, keeps playing when the stereo's launcher or radio takes audio focus; on compatible head units, steering wheel keys, pause on calls, resume after the ignition cycle and now-playing info on the home screen widget. Opens the head unit's own equalizer (balance and fader).
• Crossfade (2–12 s), also on manual skips.
• Seven real-time visualizer scenes with palettes and automatic quality.
• MP3 tag and cover editor with power-cut-safe writes.

Open source (public domain, The Unlicense): https://github.com/web-gdl/RadilloPlayerWave
```

## 4. Recursos gráficos

| Recurso | Archivo |
|---|---|
| Icono 512×512 | `icono-512.png` |
| Gráfico destacado 1024×500 | `grafico-destacado-1024x500.png` |
| Capturas de teléfono (2–8) | carpeta `telefono/` |
| Capturas de tableta de 7" y 10" | carpeta `tablet/` (las pantallas del estéreo, ampliadas) |

Las capturas usan canciones y artistas inventados: Play rechaza fichas con nombres de artistas u
obras reales.

## 5. Contenido de la app (Política ▸ Contenido de la app)

**Política de privacidad:** la URL de arriba.

**Anuncios:** No, la app no contiene anuncios.

**Acceso a la app:** Toda la funcionalidad está disponible sin restricciones (no hay inicio de sesión).

**Clasificación de contenido** (cuestionario IARC): categoría «Todas las demás apps» (no juego,
no red social). Responde **No** a todo: violencia, sexo, lenguaje, drogas, apuestas, compras
digitales, interacción entre usuarios, compartir ubicación, contenido generado por usuarios.
Resultado esperado: «Apto para todo público» (E / PEGI 3 / Clasificación A).

**Público objetivo:** 18 años o más (también puedes marcar 13+). **No** marques menores de 13: la
app no está pensada para niños y eso evita los requisitos de la política de familias.

**Seguridad de los datos:**
- ¿Recopila o comparte alguno de los tipos de datos requeridos? → **No**.
- (Al responder «No» ya no pregunta cifrado ni eliminación.)
- Motivo, por si lo piden: la app no tiene permiso de internet; los archivos de audio se leen
  solo en el dispositivo; el registro de diagnóstico es local y solo se comparte si el usuario
  lo copia.

**App de noticias / salud / financiera / gubernamental:** No.

**Servicios en primer plano** (Android 14+, tipo `mediaPlayback`):
- Selecciona **Reproducción de contenido multimedia**.
- Descripción para pegar:
  ```
  RadilloPlayerWave es un reproductor de música. Usa un servicio en primer plano de tipo
  mediaPlayback mientras el usuario reproduce música (con notificación de controles), para que
  la reproducción continúe con la pantalla apagada o con otra app delante. En estéreos de auto
  compatibles, un segundo servicio del mismo tipo mantiene la sesión de reproducción mientras el
  auto está apagado con la música en pausa por el sistema, para reanudarla al volver a encenderlo;
  termina en cuanto la música sigue o el usuario la pausa.
  ```
- Video que piden: graba con el teléfono 20–30 s: abrir la app, reproducir una canción, salir al
  escritorio y mostrar la notificación con los controles mientras sigue sonando.

**Permisos sensibles:** ninguno de los restringidos (no usa `MANAGE_EXTERNAL_STORAGE`,
`QUERY_ALL_PACKAGES`, SMS, llamadas ni ubicación). `READ_MEDIA_AUDIO` no requiere declaración.
