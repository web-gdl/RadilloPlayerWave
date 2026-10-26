# Publicar RadilloPlayerWave en Google Play: paso a paso

Lo técnico ya está listo en el repositorio: firma, paquete `.aab`, política de privacidad, textos,
icono, gráfico destacado, capturas y respuestas de los formularios (`docs/play/FICHA.md`). Lo que
sigue son los pasos que solo puede hacer el dueño de la cuenta.

## 0. Requisitos de Google (octubre de 2026)

- Cuenta de desarrollador de Google Play: pago único de **25 USD** y verificación de identidad
  (identificación oficial; para cuentas de organización, además número D-U-N-S).
- **Cuentas personales creadas después del 13-nov-2023:** antes de publicar para todos hay que
  hacer una **prueba cerrada con al menos 12 personas durante 14 días seguidos**. Las cuentas de
  organización no tienen este requisito.
- Nivel de API objetivo: desde el 31-ago-2026 se exige Android 16 (API 36). La app apunta a API 37:
  cumple.
- Formato: Android App Bundle (`.aab`), firmado con una clave de subida. Google firma lo que llega
  a los usuarios (Firma de apps de Play).

## 1. Activar la firma propia en GitHub (una sola vez)

La clave de subida va cifrada en `keystore/radillo-release.jks.enc`. Para que GitHub la use:

1. En GitHub abre el repositorio ▸ **Settings ▸ Secrets and variables ▸ Actions**.
2. **New repository secret**. Nombre: `RADILLO_KEYSTORE_PASSWORD`. Valor: la contraseña que te dio
   Claude. **Guárdala también en un lugar seguro**: sin ella no se pueden publicar actualizaciones
   con esta clave (Google permite restablecer la clave de subida, pero tarda días).
3. Haz cualquier cambio en `main` (o pide una versión nueva). Desde entonces:
   - el APK de GitHub se firma con la clave propia (**hay que desinstalar una última vez** la
     versión con firma temporal);
   - cada compilación deja el artefacto **RadilloPlayerWave-google-play-aab** (Actions ▸ la
     ejecución ▸ Artifacts) con el `.aab` para Play.

Nota: el APK de GitHub y el de Play quedan firmados por claves distintas (Google firma el de Play).
Para pasar de uno a otro hay que desinstalar.

## 2. Crear la cuenta y la app

1. <https://play.google.com/console> ▸ crear cuenta (personal u organización), pagar y verificar.
2. **Crear app** con los datos de la sección 1 de `docs/play/FICHA.md`.
3. Completa **Contenido de la app** (privacidad, anuncios, acceso, clasificación, público objetivo,
   seguridad de los datos, servicios en primer plano) con las respuestas de la sección 5.
4. **Ficha de Play Store principal**: textos de la sección 2 e imágenes de `docs/play/`.

## 3. Prueba cerrada (cuentas personales nuevas)

1. **Pruebas ▸ Prueba cerrada ▸ Crear canal** (o usa «Alpha»).
2. **Verificadores**: crea una lista de correos (cuentas de Google) con al menos 12 personas.
3. **Crear versión** ▸ sube el `.aab` ▸ acepta la Firma de apps de Play ▸ notas de la versión
   (puedes copiar `docs/NOVEDADES.md`) ▸ **Enviar a revisión**.
4. Cuando Google la apruebe, comparte el enlace de participación. Cada verificador debe aceptar la
   invitación e instalar la app desde ese enlace, y **seguir inscrito 14 días seguidos**.
5. Pasados los 14 días: **Panel ▸ Solicitar acceso a producción** y contestar el cuestionario
   sobre la prueba.

## 4. Producción

1. **Producción ▸ Crear versión** ▸ sube el `.aab` más reciente.
2. Países: todos o los que elijas. **Enviar a revisión** (de horas a varios días).

## 5. Actualizaciones

Cada versión nueva necesita un `versionCode` mayor (Claude lo sube en cada versión). Descarga el
`.aab` del artefacto de esa compilación y súbelo como versión nueva en Play Console.

## Riesgos conocidos en la revisión

- **Servicio en primer plano de espera del encendido** (`CarStandbyService`): usa el tipo
  `mediaPlayback` sin sonar mientras el auto está apagado. Solo se activa en estéreos Jancar, así
  que en un teléfono de revisión nunca arranca; la declaración lo explica. Si Google lo objeta, se
  puede quitar de la versión de Play.
- **Capturas**: usan canciones y artistas inventados a propósito.
