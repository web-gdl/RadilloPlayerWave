# Inventario de dependencias y licencias

Versiones verificadas el 15-10-2026 contra las páginas oficiales de *release notes*.
Ninguna dependencia es GPL/LGPL. Todas son Apache-2.0 salvo que se indique.

| Dependencia | Versión | Licencia | Uso |
|---|---|---|---|
| Android Gradle Plugin | 8.13.2 | Apache-2.0 | Build |
| Gradle | 8.14.3 | Apache-2.0 | Build |
| Kotlin / KGP / plugin Compose | 2.2.21 | Apache-2.0 | Lenguaje |
| KSP | 2.2.21-2.0.5 | Apache-2.0 | Procesado de anotaciones |
| Dagger Hilt | 2.60.1 | Apache-2.0 | Inyección de dependencias |
| androidx.hilt (hilt-navigation-compose) | 1.4.0 | Apache-2.0 | ViewModels en Compose |
| Compose BOM | 2026.09.00 (Compose 1.12.1) | Apache-2.0 | UI |
| material-icons-extended | 1.7.8 | Apache-2.0 | Iconos Material (ya no está en el BOM) |
| androidx.core | 1.19.1 | Apache-2.0 | Base |
| androidx.activity | 1.13.0 | Apache-2.0 | Activity + Compose |
| androidx.lifecycle | 2.11.0 | Apache-2.0 | ViewModel, servicio |
| androidx.navigation | 2.10.2 | Apache-2.0 | Navegación Compose |
| androidx.datastore | 1.2.1 | Apache-2.0 | Preferencias |
| androidx.room | 2.8.5 | Apache-2.0 | Caché de biblioteca |
| androidx.media3 (exoplayer, session, common) | 1.11.1 | Apache-2.0 | Reproducción |
| androidx.palette | 1.0.0 | Apache-2.0 | Color dominante de carátulas |
| Coil 3 (coil-compose) | 3.6.3 | Apache-2.0 | Carga de carátulas |
| kotlinx.coroutines | 1.11.0 | Apache-2.0 | Concurrencia |
| JUnit 4 | 4.13.2 | EPL-1.0 | Tests (solo en tiempo de test, no se redistribuye) |
| Truth | 1.4.4 | Apache-2.0 | Tests |
| androidx.test (core, ext-junit, espresso) | 1.7.0 / 1.3.0 / 3.7.0 | Apache-2.0 | Tests |
| Robolectric | 4.17 | MIT | Tests |

## Recursos gráficos

| Recurso | Origen | Licencia |
|---|---|---|
| `ic_launcher_foreground.xml` y PNG legacy | Creados para este proyecto | Propio |
| Iconos de navegación | Material Icons (androidx) | Apache-2.0 |
| Tipografía | Fuente sans-serif del sistema | Del sistema |

## Puntos que requieren revisión legal

- Disponibilidad de la marca "RadilloPlayerWave" en los mercados previstos.
- Si se distribuye en Google Play, cumplir con la declaración de datos y permisos.
- Al añadir cualquier dependencia nueva, registrarla aquí antes de usarla.
