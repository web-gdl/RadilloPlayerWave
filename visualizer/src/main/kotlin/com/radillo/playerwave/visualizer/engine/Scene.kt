package com.radillo.playerwave.visualizer.engine

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.radillo.playerwave.visualizer.analysis.AudioFrame

/** Backend de render que necesita una escena. */
enum class RenderBackend { CANVAS, OPENGL_ES }

/**
 * Parámetros comunes ajustables por el usuario. Cada escena puede ignorar los que no apliquen.
 */
data class SceneParams(
    val sensitivity: Float = 1f,     // 0.25 .. 3
    val intensity: Float = 1f,       // 0 .. 2
    val speed: Float = 1f,           // 0.25 .. 3
    val density: Float = 1f,         // 0.25 .. 2 (partículas, barras, líneas)
    val paletteId: String = "aurora",
)

/**
 * Contrato de una escena. Las escenas no conocen al reproductor: solo reciben
 * [AudioFrame]s y el tiempo transcurrido.
 *
 * Ciclo: [onAttach] → ([onResize] | [update] | [draw])* → [onDetach].
 * [update] y [draw] se llaman en el hilo de render; no deben asignar memoria por frame.
 */
interface Scene {
    val id: String
    val displayName: String
    val backend: RenderBackend

    fun onAttach(params: SceneParams)
    fun onParamsChanged(params: SceneParams)
    fun onResize(widthPx: Int, heightPx: Int)
    fun update(frame: AudioFrame, deltaSeconds: Float)
    fun onDetach()

    /** Render para escenas [RenderBackend.CANVAS]. Las escenas GL ignoran este método. */
    fun draw(scope: DrawScope)
}

/** Fábrica registrada en el [SceneRegistry]; permite crear escenas perezosamente. */
fun interface SceneFactory {
    fun create(): Scene
}

/**
 * Registro de escenas disponibles. Añadir una escena es registrar una fábrica aquí;
 * ni el motor de reproducción ni la UI de navegación cambian.
 */
class SceneRegistry(entries: List<Pair<String, SceneFactory>> = emptyList()) {
    private val factories = LinkedHashMap<String, SceneFactory>().apply {
        entries.forEach { (id, factory) -> put(id, factory) }
    }

    val ids: List<String> get() = factories.keys.toList()

    fun register(id: String, factory: SceneFactory) {
        factories[id] = factory
    }

    fun create(id: String): Scene? = factories[id]?.create()
}
