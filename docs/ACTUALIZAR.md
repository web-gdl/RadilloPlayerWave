# Actualizar RadilloPlayerWave sin perder favoritos ni listas

## Lo normal: instalar encima

Desde la **0.16.0** todas las versiones de GitHub van firmadas con la misma clave. Descarga el APK
nuevo, ábrelo y toca **Actualizar**: no hace falta desinstalar y se conserva todo (favoritos,
listas, cola, posición y ajustes).

Si Android dice «La app no está instalada» o «conflicto con un paquete existente», el equipo tiene
una versión **anterior a la 0.16.0**, firmada con una clave temporal. Android no deja actualizar entre
claves distintas; hay que desinstalar **una sola vez**.

## Si tienes que desinstalar

**Desde la 0.17.0** la app copia sola los favoritos y las listas en
`Documentos/RadilloPlayerWave/listas-y-favoritos.json`:

- **Android 10 o anterior** (como el estéreo alps 8227L, Android 8.1): desinstala, instala la nueva,
  da el permiso de música y espera a que aparezca la biblioteca. Favoritos y listas vuelven solos.
- **Android 11 o más nuevo:** el sistema no deja a la app nueva leer ese archivo por su cuenta.
  Tras instalar: **Ajustes ▸ Biblioteca ▸ Importar favoritos y listas** y elige el archivo en
  Documentos ▸ RadilloPlayerWave.

**Con una versión anterior a la 0.17.0** (todavía sin copia automática) y que obliga a desinstalar:
no hay forma de sacar las listas, porque están en los datos privados de la app y la versión vieja
no sabe exportarlas. Desinstala, instala la 0.17.0 o posterior y, desde entonces, la copia ya
existe para la próxima vez.

## Si una canción no aparece

La copia guarda la ruta del archivo, el título, el artista y la duración. Si una canción está en una
USB que no está puesta, queda pendiente y se agrega sola al ponerla. Si cambiaste el nombre del
archivo y también sus etiquetas, la app no la puede reconocer.
