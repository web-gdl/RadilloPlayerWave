### Novedades de la 0.19.0

**«Reproduciendo» rediseñada**

- **Favorito en la portada:** corazón en la esquina superior derecha. En silueta si la canción no es
  favorita; un toque lo rellena en rojo y la agrega a favoritos, otro toque la quita. La ficha
  «Favorito» de abajo se quitó.
- **Portada y visualizador con toques:**
  - 1 toque: cambia entre portada y visualizador;
  - 2 toques (o deslizar a los lados): cambia de visualizador;
  - 3 toques: visualizador a pantalla completa. Ahí, 1 toque regresa al reproductor y deslizar
    cambia de visualizador.
  Para distinguir uno, dos y tres toques, el efecto de un toque tarda unas tres décimas de segundo.
- **Engrane en el visualizador:** con el visualizador a la vista, el engrane de la esquina superior
  derecha abre sus ajustes. Por eso «Visualizador» ya no está en la barra superior.
- **Progreso como onda de sonido:** la barra de progreso dibuja el volumen de la canción a lo largo
  de toda su duración. Se calcula en segundo plano la primera vez que suena cada canción (se ve una
  barra normal mientras tanto) y se guarda para las siguientes.
- **Un solo color activo:** reproducir, progreso, aleatorio, repetir y crossfade usan el color de la
  portada, igual que la barra superior. Aleatorio y repetir activos llevan además fondo y un punto,
  para que se note con sol en la pantalla.
- **Botones más grandes** (76 px de zona táctil), tiempos a los lados del progreso en letra más
  grande, «Sigue: …» bajo el artista y «⋮» como botón redondo.
- **Favoritos de la barra superior** con icono propio (lista con corazón).
- En el teléfono en vertical, las secciones de la barra inferior caben completas.

![Reproduciendo con portada](https://raw.githubusercontent.com/web-gdl/RadilloPlayerWave/main/docs/screenshots/reproduciendo.png)

![Reproduciendo con visualizador](https://raw.githubusercontent.com/web-gdl/RadilloPlayerWave/main/docs/screenshots/reproduciendo-visualizador.png)

![Visualizador a pantalla completa](https://raw.githubusercontent.com/web-gdl/RadilloPlayerWave/main/docs/screenshots/visualizador.png)

(Capturas del autoestéreo, 1024×534. El naranja es un ejemplo: el color cambia con la portada de cada canción.)

### Novedades de la 0.18.0

- **Barra superior con color dinámico:** la sección activa (Biblioteca, Reproduciendo, Visualizador…)
  usa el mismo color que el botón de reproducir, tomado de la portada de la canción que suena. También
  la barra de progreso del mini reproductor. Si el color es muy oscuro (o muy claro en el tema claro) se
  ajusta para que siempre se distinga.
- **Botón «Favoritos» en la barra** (entre Biblioteca y Reproduciendo): reproduce tu lista de
  favoritos desde el principio, respeta el aleatorio y abre «Reproduciendo». Si esa lista ya está
  sonando, solo abre «Reproduciendo»; si aún no tienes favoritos, te avisa.
- **Buscar:** el teclado se esconde solo 1,5 s después de la última letra para dejar ver los
  resultados (tocar el campo lo muestra de nuevo).


### Novedades de la 0.17.0

- **Favoritos y listas a salvo al reinstalar:**
  - cada vez que cambian, se guarda una copia en `Documentos/RadilloPlayerWave/listas-y-favoritos.json`
    (fuera de la app, así que no se borra al desinstalar);
  - al instalar de nuevo se recuperan solos en cuanto la app encuentra la música, aunque el sistema
    haya cambiado los números internos de las canciones;
  - si alguna canción está en una USB que no está puesta, queda pendiente y se agrega sola al
    ponerla;
  - **Ajustes ▸ Biblioteca:** «Exportar favoritos y listas» guarda una copia donde quieras e
    «Importar» la recupera (en Android 11 o más nuevo es la forma de recuperarlos tras reinstalar).
- **Actualizar sin desinstalar:** desde la 0.16.0 todas las versiones van firmadas con la misma
  clave: la nueva se instala encima y conserva todo. Además, la base de datos ya no puede
  borrarse sola en una actualización futura.

### Novedades de la 0.16.1

- **Marquesina en títulos y artistas largos:** si el título de la canción o el artista no caben, el
  texto se desplaza solo, de forma continua, en «Reproduciendo», en el mini reproductor y en el
  visualizador. Los textos cortos se quedan quietos.

### Novedades de la 0.16.0

- **Visualizador sin tirones ni congelones** (sobre todo «Espectro» a pantalla completa en el estéreo):
  - si el equipo no da abasto, la calidad se ajusta en unos 4 segundos (antes podía tardar más de
    40 s de tirones, o no ajustarse nunca);
  - el dibujo ya no acapara la pantalla: los toques y el cambio a pantalla completa responden
    aunque un frame tarde;
  - en equipos modestos, la pantalla completa se dibuja con la mitad de píxeles y el sistema la
    amplía: se ve algo más suave y va fluida;
  - en «Espectro», al bajar la calidad se apaga primero el halo de fondo, que es lo más pesado de
    dibujar (más de la mitad del frame, según las mediciones).
- **Cambiar de visualizador desde «Reproduciendo»:** con el visualizador en el recuadro de la
  carátula, **doble toque** o **deslizar** a los lados cambia de visualizador (aparece su nombre) y
  **un toque** vuelve a la carátula.
