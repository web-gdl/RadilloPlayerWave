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
