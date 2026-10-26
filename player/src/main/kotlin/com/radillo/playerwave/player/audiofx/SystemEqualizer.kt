package com.radillo.playerwave.player.audiofx

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import com.radillo.playerwave.core.diagnostics.EventLog
import java.util.Locale

/**
 * Ecualizador **del sistema** (la app ya no trae uno propio).
 *
 * En el estéreo es la app de audio del equipo (ecualizador, balance izquierda/derecha y
 * adelante/atrás sobre todo el audio). Cómo se encuentra, por orden:
 * 1. la app que el usuario eligió ([choose]);
 * 2. las de las apps de fábrica Jancar (`com.jancar.audiosettings`, `com.jancar.settings`);
 * 3. una app instalada cuyo nombre o paquete parezca un ecualizador ([candidates]), si hay una sola;
 * 4. el panel estándar de Android ([AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL]).
 *
 * Si nada funciona, [open] devuelve `false` y la UI deja elegir la app a mano. Cada intento queda
 * en el registro de Diagnóstico.
 *
 * Además se anuncia la sesión de audio del reproductor a los ecualizadores que actúan por sesión
 * ([onSessionId]), como hace cualquier reproductor de Android.
 */
object SystemEqualizer {

    /** App de ecualizador instalada (o la elegida), para mostrarla y abrirla. */
    data class App(val label: String, val component: ComponentName)

    /** Candidata a ecualizador: [likely] si su nombre o paquete lo parece. */
    data class Candidate(val app: App, val likely: Boolean)

    private val KNOWN_TARGETS = listOf(
        ComponentName("com.jancar.audiosettings", "com.jancar.audiosettings.ui.activity.MainActivity"),
        ComponentName("com.jancar.settings", "com.jancar.settings.activity.AudioSettingActivity"),
    )
    private const val JANCAR_AUDIO_PACKAGE = "com.jancar.audiosettings"

    /** Palabras que delatan una app de ecualizador o de audio del equipo. */
    private val KEYWORDS = listOf(
        "ecualiz", "equaliz", "audiosetting", "audio_setting", "soundsetting", "sound_setting",
        "sonido", "sound", "audio", "dsp", "音效", "均衡", "声音",
    )

    private const val PREFS = "system_equalizer"
    private const val KEY_COMPONENT = "component"

    @Volatile private var sessionId = 0

    /**
     * Abre el ecualizador del sistema. Devuelve `false` si no encontró ninguno que se pudiera
     * abrir (la UI ofrece entonces elegirlo con [candidates] y [choose]).
     */
    fun open(context: Context): Boolean {
        chosen(context)?.let { app ->
            if (start(context, Intent().setComponent(app.component), "elegida ${app.component.flattenToShortString()}")) return true
        }
        for (target in KNOWN_TARGETS) {
            if (start(context, Intent().setComponent(target), target.flattenToShortString())) return true
        }
        context.packageManager.getLaunchIntentForPackage(JANCAR_AUDIO_PACKAGE)?.let {
            if (start(context, it, "lanzador de $JANCAR_AUDIO_PACKAGE")) return true
        }
        val likely = candidates(context).filter { it.likely }
        if (likely.size == 1) {
            val app = likely.single().app
            if (start(context, Intent(Intent.ACTION_MAIN).setComponent(app.component), "detectada ${app.component.flattenToShortString()}")) {
                return true
            }
        }
        val panel = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        if (start(context, panel, "panel de Android")) return true
        EventLog.log("Ecualizador: no se encontró ninguno que abrir (${likely.size} posibles)")
        return false
    }

    /**
     * Apps con icono en el escritorio, primero las que parecen ecualizador o audio del equipo.
     * Excluye esta misma app.
     */
    fun candidates(context: Context): List<Candidate> {
        val pm = context.packageManager
        val launchers = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        } catch (_: Exception) {
            emptyList()
        }
        return launchers
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                val label = info.loadLabel(pm)?.toString().orEmpty().ifBlank { info.activityInfo.packageName }
                val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                val text = (label + " " + component.packageName + " " + component.className).lowercase(Locale.ROOT)
                Candidate(App(label, component), KEYWORDS.any { it in text })
            }
            .distinctBy { it.app.component }
            .sortedWith(compareBy<Candidate>({ !it.likely }, { it.app.label.lowercase(Locale.ROOT) }))
    }

    /** Guarda la app elegida por el usuario como ecualizador. */
    fun choose(context: Context, app: App) {
        prefs(context).edit().putString(KEY_COMPONENT, app.component.flattenToString()).apply()
        EventLog.log("Ecualizador: elegido ${app.label} (${app.component.flattenToShortString()})")
    }

    /** App elegida por el usuario, si sigue instalada. */
    fun chosen(context: Context): App? {
        val component = prefs(context).getString(KEY_COMPONENT, null)?.let(ComponentName::unflattenFromString) ?: return null
        val pm = context.packageManager
        val label = try {
            pm.getActivityInfo(component, 0).loadLabel(pm).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return App(label, component)
    }

    /** Resumen para el informe de Diagnóstico. */
    fun summary(context: Context): String {
        val pm = context.packageManager
        val known = KNOWN_TARGETS.filter { Intent().setComponent(it).resolveActivity(pm) != null }
            .joinToString { it.flattenToShortString() }.ifEmpty { "ninguna de fábrica" }
        val likely = candidates(context).filter { it.likely }
            .joinToString { "${it.app.label} (${it.app.component.packageName})" }.ifEmpty { "ninguna" }
        val chosen = chosen(context)?.let { "${it.label} (${it.component.flattenToShortString()})" } ?: "automático"
        return "elegida: $chosen · de fábrica: $known · posibles: $likely"
    }

    private fun start(context: Context, intent: Intent, what: String): Boolean {
        if (intent.resolveActivity(context.packageManager) == null) return false
        return try {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            EventLog.log("Ecualizador: abierto ($what)")
            true
        } catch (e: Exception) {
            // Típico: la actividad existe pero no está exportada (solo la abren apps del sistema).
            EventLog.log("Ecualizador: no se pudo abrir $what (${e.javaClass.simpleName})")
            false
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * El servicio de reproducción avisa del `audioSessionId` del reproductor (0 al cerrarse): se
     * cierra la sesión anterior y se abre la nueva ante los ecualizadores del sistema.
     */
    fun onSessionId(context: Context, id: Int) {
        val previous = sessionId
        if (previous == id) return
        sessionId = id
        if (previous > 0) broadcast(context, AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, previous)
        if (id > 0) broadcast(context, AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, id)
    }

    private fun broadcast(context: Context, action: String, session: Int) {
        try {
            context.sendBroadcast(
                Intent(action)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, session)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC),
            )
        } catch (_: Exception) {
            // Sin ecualizador del sistema que escuche, no pasa nada.
        }
    }
}
