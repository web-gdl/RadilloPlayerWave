package com.radillo.playerwave.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.radillo.playerwave.R
import com.radillo.playerwave.data.waveform.WaveformShape
import com.radillo.playerwave.databinding.FragmentNowPlayingBinding
import com.radillo.playerwave.ui.player.NowPlayingFragment
import com.radillo.playerwave.ui.common.AccentColors
import com.radillo.playerwave.ui.common.AccentPainter
import com.radillo.playerwave.ui.common.AppAccent
import com.radillo.playerwave.ui.common.Row
import com.radillo.playerwave.ui.common.RowAdapter
import com.radillo.playerwave.ui.common.setupList
import com.radillo.playerwave.visualizer.analysis.AudioSource
import com.radillo.playerwave.visualizer.analysis.AudioSourceStatus
import com.radillo.playerwave.visualizer.analysis.PcmBlock
import com.radillo.playerwave.visualizer.analysis.PlaybackClock
import com.radillo.playerwave.ui.visualizer.sceneLabelRes
import com.radillo.playerwave.visualizer.analysis.AnalyzerConfig
import com.radillo.playerwave.visualizer.analysis.AudioFrame
import com.radillo.playerwave.visualizer.engine.DefaultScenes
import com.radillo.playerwave.visualizer.engine.Palettes
import com.radillo.playerwave.visualizer.engine.SceneParams
import com.radillo.playerwave.visualizer.engine.VisualizerEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Capturas de las pantallas principales en el autoestéreo (1024×534 útiles, 160 dpi, tema oscuro) y en un
 * móvil. No comprueban píxeles: CI las publica como artefacto para revisar el diseño sin equipo.
 *
 * Los ids se buscan por nombre para que la prueba sobreviva a cambios de los layouts: si un id no
 * existe, ese detalle simplemente no se rellena.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class ScreenshotTest {

    private companion object {
        /** Acento de ejemplo (el que saldría de la portada de muestra). */
        const val SAMPLE_ACCENT = 0xFFFF7A3D.toInt()
    }

    private val outDir = File("build/outputs/screenshots").apply { mkdirs() }

    // Canciones y artistas inventados: las capturas también van a la ficha de Google Play, donde no
    // deben aparecer nombres de artistas ni obras reales.
    private val songs = listOf(
        "Luces de carretera" to "Los Kilómetros · Ruta nocturna",
        "Kilómetro cero" to "Marea Alta · Mapa de sal",
        "Ciudad en espejo" to "Nube Norte · Reflejos",
        "Sol de mediodía" to "Arena Roja · Verano largo",
        "Frecuencia modulada" to "Radio Faro · Onda corta",
        "Volver al mar" to "Brisa Sur · Puerto",
        "Señales" to "Los Kilómetros · Ruta nocturna",
        "Camino de vuelta" to "Marea Alta · Mapa de sal",
    )

    private fun themed(night: Boolean): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return ContextThemeWrapper(app.createConfigurationContext(config), R.style.Theme_RadilloPlayerWave)
    }

    private fun View.find(name: String): View? {
        val id = resources.getIdentifier(name, "id", context.packageName)
        return if (id == 0) null else findViewById(id)
    }

    private fun View.text(name: String, value: CharSequence) {
        (find(name) as? TextView)?.text = value
    }

    private fun View.show(name: String, visible: Boolean) {
        find(name)?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun sampleRows(highlight: Int = 1) = songs.mapIndexed { i, (t, s) ->
        Row(key = "t$i", title = t, subtitle = s, artUri = null, highlighted = i == highlight)
    }

    /** Monta la actividad (raíl + mini reproductor) con [content] dentro del contenedor. */
    private fun shell(ctx: Context, content: View, selectedNav: String, mini: Boolean): View {
        val root = LayoutInflater.from(ctx).inflate(R.layout.activity_main, null, false)
        (root.find("content") as ViewGroup).addView(content, FrameLayout.LayoutParams(-1, -1))
        listOf("navLibrary", "navNowPlaying", "topSearch", "topSettings").forEach { root.find(it)?.isSelected = it == selectedNav }
        // Color dinámico como en la app: acento de una portada de ejemplo (naranja), en el mismo orden que MainActivity.
        val wide = root.find("topSearch") != null
        AccentColors.styleNav(
            context = ctx,
            sections = (if (wide) listOf("navLibrary", "navNowPlaying") else listOf("navLibrary", "navFavorites", "navNowPlaying"))
                .mapNotNull { root.find(it) as? ViewGroup },
            search = root.find("topSearch") as? ViewGroup,
            favorites = root.find("navFavorites") as ViewGroup,
            favoritesIcon = root.find("navFavoritesIcon") as ImageView,
            settings = root.find("topSettings") as? ImageView,
            miniProgress = root.find("miniPlayer")!!.find("miniProgress") as android.widget.ProgressBar,
            accent = SAMPLE_ACCENT,
            wide = wide,
        )
        // Un solo color en toda la app (0.23.0), como hace MainActivity.
        AppAccent.set(AccentColors.visible(SAMPLE_ACCENT, ctx.getColor(R.color.window_background)))
        AccentPainter.paint(root, AppAccent.current(ctx))
        val miniRoot = root.find("miniPlayer")
        miniRoot?.visibility = if (mini) View.VISIBLE else View.GONE
        miniRoot?.text("miniTitle", "Luces de carretera")
        miniRoot?.text("miniSubtitle", "Los Kilómetros · Ruta nocturna")
        (miniRoot?.find("miniArt") as? ImageView)?.setImageResource(R.drawable.tile_note)
        (miniRoot?.find("miniPlay") as? ImageView)?.setImageResource(R.drawable.ic_pause)
        (miniRoot?.find("miniProgress") as? android.widget.ProgressBar)?.progress = 420
        return root
    }

    private fun capture(view: View, width: Int, height: Int, name: String, underlay: Bitmap? = null) {
        val wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        repeat(3) { // varias pasadas: RecyclerView crea sus filas en el layout
            view.measure(wSpec, hSpec)
            view.layout(0, 0, width, height)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (underlay != null) canvas.drawBitmap(underlay, 0f, 0f, null)
        view.draw(canvas)
        FileOutputStream(File(outDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun library(ctx: Context): View {
        val v = LayoutInflater.from(ctx).inflate(R.layout.fragment_library, null, false)
        val adapter = RowAdapter(onClick = {}, onMore = {})
        (v.find("list") as RecyclerView).setupList(adapter)
        adapter.submitList(sampleRows())
        v.find("tabSongs")?.isSelected = true
        // Como en LibraryFragment: en pantallas anchas Buscar y Ajustes van en la barra superior.
        if (ctx.resources.getBoolean(R.bool.top_bar_actions)) {
            v.show("btnSearch", false)
            v.show("btnSettings", false)
        }
        v.text("libraryCount", "1248 canciones")
        return v
    }

    /** «Reproduciendo» como lo deja el fragmento: acento de ejemplo, onda de sonido y esquina del recuadro. */
    private fun nowPlaying(ctx: Context, favorite: Boolean = false, visualizer: Bitmap? = null): View {
        val v = LayoutInflater.from(ctx).inflate(R.layout.fragment_now_playing, null, false)
        v.show("emptyText", false)
        v.text("trackTitle", "Luces de carretera")
        v.text("trackSubtitle", "Los Kilómetros · Ruta nocturna")
        v.text("position", "2:41")
        v.text("duration", "6:30")
        v.text("queueInfo", "Sigue: Kilómetro cero")
        val b = FragmentNowPlayingBinding.bind(v)
        b.seek.fraction = 0.42f
        b.seek.peaks = sampleWaveform()
        b.artFrame.clipToOutline = true
        b.btnPlay.setImageResource(R.drawable.ic_pause)
        b.btnShuffle.isActivated = true
        b.btnCrossfade.isActivated = true
        b.btnCrossfadeLabel.text = ctx.getString(R.string.np_crossfade_on, 6)
        NowPlayingFragment.applyAccent(b, SAMPLE_ACCENT)
        NowPlayingFragment.renderFavorite(b, favorite)
        if (visualizer != null) {
            // El SurfaceView no se puede capturar: la escena va como imagen en el mismo recuadro.
            b.art.setImageBitmap(visualizer)
            b.btnFavorite.visibility = View.GONE
            b.btnVisualizerSettings.visibility = View.VISIBLE
            b.artSceneLabel.text = "Barras LED"
            b.artSceneLabel.visibility = View.VISIBLE
        } else {
            b.art.setImageBitmap(sampleCover(b.artFrame.layoutParams.width))
        }
        return v
    }

    /** Portada de ejemplo (degradado), de la que saldría el acento naranja. */
    private fun sampleCover(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(0xFFFF7A3D.toInt(), 0xFFB3204F.toInt(), 0xFF3A1050.toInt()),
                floatArrayOf(0f, 0.6f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        Canvas(bitmap).drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        return bitmap
    }

    /** Onda inventada pero fija (misma captura en cada ejecución). */
    private fun sampleWaveform(): ByteArray {
        var seed = 7L
        val levels = FloatArray(WaveformShape.BUCKETS) { i ->
            seed = seed * 16807L % 2147483647L
            val random = seed / 2147483647f
            val envelope = 0.35f + 0.65f * kotlin.math.abs(kotlin.math.sin(i / 9f)) * (0.6f + 0.4f * kotlin.math.sin(i / 23f + 1f))
            envelope * (0.45f + 0.55f * random)
        }
        return WaveformShape.normalize(levels.map { it * it }.toFloatArray())!!
    }

    private class FakeSource : AudioSource {
        override val blocks: Flow<PcmBlock> = emptyFlow()
        override val status: StateFlow<AudioSourceStatus> = MutableStateFlow(AudioSourceStatus.UNAVAILABLE)
    }

    private class FakeClock : PlaybackClock {
        var us = 0L
        override fun positionUs(): Long = us
        override fun isPlaying(): Boolean = true
    }

    /** Dibuja unos segundos de la escena (con datos sintéticos) en un bitmap. */
    private fun sceneBitmap(sceneId: String, width: Int, height: Int): Bitmap {
        val clock = FakeClock()
        val engine = VisualizerEngine(FakeSource(), clock, DefaultScenes.registry())
        engine.setScene(sceneId)
        engine.onResize(width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        var now = 1_000_000_000L
        repeat(90) {
            engine.render(canvas, now)
            now += 33_333_333L
            clock.us += 33_333L
        }
        return bitmap
    }

    /**
     * La escena [sceneId] tras unos segundos de «música» de prueba (graves fuertes que bajan hacia
     * los agudos, golpes periódicos y una onda estéreo), como en el autoestéreo (densidad 0,6).
     */
    private fun musicBitmap(sceneId: String, width: Int, height: Int, paletteId: String = Palettes.AURORA.id): Bitmap {
        val config = AnalyzerConfig()
        val random = kotlin.random.Random(21)
        val scene = DefaultScenes.registry().create(sceneId)!!
        scene.onAttach(SceneParams(deviceScale = 0.6f, paletteId = paletteId))
        scene.onResize(width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dt = 1f / 30f
        repeat(360) { f -> // 12 s: el terreno tarda ~11 s en llenarse del horizonte al frente
            val t = f * dt
            val beat = if (f % 15 == 0) 1f else 0f
            val bands = FloatArray(config.bandCount) { i ->
                val x = i.toFloat() / (config.bandCount - 1)
                val shape = 0.95f * kotlin.math.exp(-x * 1.9f) + 0.12f
                val wobble = 0.75f + 0.35f * kotlin.math.sin(i * 0.7f + t * 3.1f)
                (shape * wobble + 0.15f * random.nextFloat() + 0.15f * beat * (1f - x)).coerceIn(0f, 1f)
            }
            val wave = { phase: Float, gain: Float ->
                FloatArray(config.waveformSize) { k ->
                    val u = k.toFloat() / config.waveformSize
                    gain * (0.55f * kotlin.math.sin(u * 37.7f + phase) + 0.28f * kotlin.math.sin(u * 106.8f + phase * 1.7f) +
                        0.12f * kotlin.math.sin(u * 257.6f + phase * 0.6f))
                }
            }
            val frame = AudioFrame(
                spectrum = FloatArray(config.spectrumSize) { random.nextFloat() * 0.4f },
                bands = bands,
                bass = 0.75f + 0.2f * beat, mid = 0.5f, treble = 0.35f, rms = 0.6f, peak = 0.9f, onset = 0.9f * beat,
                waveformLeft = wave(t * 2f, 1f),
                waveformRight = wave(t * 2f + 1.4f, 0.8f),
                timestampUs = (t * 1_000_000).toLong(), synthetic = false,
            )
            scene.update(frame, dt)
        }
        canvas.drawColor(Palettes.byId(paletteId).background)
        scene.draw(canvas)
        scene.onDetach()
        return bitmap
    }

    /** Pantalla completa: solo la imagen (va de fondo en la captura) y el nombre del visualizador. */
    private fun visualizer(ctx: Context, label: String = "Barras LED"): View {
        val v = LayoutInflater.from(ctx).inflate(R.layout.fragment_visualizer, null, false)
        v.background = null
        v.find("surface")?.visibility = View.INVISIBLE
        v.show("status", false)
        v.text("sceneLabel", label)
        v.show("sceneLabel", true)
        return v
    }

    private fun settings(ctx: Context): View = LayoutInflater.from(ctx).inflate(R.layout.fragment_settings, null, false).apply {
        find("themeSystem")?.isSelected = true
        (find("switchKeepPlaying") as? android.widget.Switch)?.isChecked = true
    }

    private fun tagEditor(ctx: Context): View {
        val v = LayoutInflater.from(ctx).inflate(R.layout.fragment_tag_editor, null, false)
        v.text("fieldTitle", "Rayando el sol")
        v.text("fieldArtist", "Maná")
        v.text("fieldAlbum", "Falta amor")
        v.text("fieldYear", "1990")
        v.text("fieldTrack", "7")
        (v.find("cover") as? ImageView)?.setImageResource(R.drawable.tile_album)
        return v
    }

    private fun albumDetail(ctx: Context): View {
        val v = LayoutInflater.from(ctx).inflate(R.layout.fragment_track_list, null, false)
        v.text("title", "Música USB")
        v.text("listCount", "8 canciones")
        v.show("empty", false)
        val adapter = RowAdapter(onClick = {}, onMore = {})
        (v.find("list") as RecyclerView).setupList(adapter)
        adapter.submitList(sampleRows(highlight = 3))
        return v
    }

    private fun carScreens(night: Boolean, suffix: String) {
        // Espacio útil real del estéreo: 1024×534 (la barra del sistema ocupa ~66 px arriba).
        val w = 1024
        val h = 534
        val ctx = themed(night)
        capture(shell(ctx, library(ctx), "navLibrary", mini = true), w, h, "car_library_$suffix")
        capture(shell(ctx, nowPlaying(ctx), "navNowPlaying", mini = false), w, h, "car_now_playing_$suffix")
        capture(shell(ctx, nowPlaying(ctx, favorite = true), "navNowPlaying", mini = false), w, h, "car_now_playing_favorite_$suffix")
        val artSize = ctx.resources.getDimensionPixelSize(R.dimen.np_art)
        val inArt = musicBitmap(DefaultScenes.DEFAULT_ID, artSize, artSize)
        capture(shell(ctx, nowPlaying(ctx, visualizer = inArt), "navNowPlaying", mini = false), w, h, "car_now_playing_visualizer_$suffix")
        capture(visualizer(ctx), w, h, "car_visualizer_$suffix", musicBitmap(DefaultScenes.DEFAULT_ID, w, h))
        capture(shell(ctx, settings(ctx), "topSettings", mini = true), w, h, "car_settings_$suffix")
        capture(visualizerSettings(ctx), 640, h, "car_visualizer_settings_$suffix")
        capture(shell(ctx, albumDetail(ctx), "navLibrary", mini = true), w, h, "car_folder_$suffix")
        capture(shell(ctx, tagEditor(ctx), "navLibrary", mini = false), w, h, "car_tag_editor_$suffix")
    }

    @Test
    @Config(qualifiers = "es-w1024dp-h534dp-land-mdpi")
    fun carHeadUnit() {
        carScreens(night = true, suffix = "dark")
        carScreens(night = false, suffix = "light")
    }

    /** Diálogo de ajustes del visualizador (0.23.0): escena, color, sensibilidad y avanzados plegados. */
    private fun visualizerSettings(ctx: Context): View {
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_visualizer_settings, null, false)
        fun chips(container: String, labels: List<String>, selected: Int) {
            val parent = v.find(container) as ViewGroup
            labels.forEachIndexed { i, label ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_chip, parent, false) as TextView).apply {
                    text = label
                    isSelected = i == selected
                    parent.addView(this)
                }
            }
        }
        chips("sceneChips", DefaultScenes.registry().ids.map { ctx.getString(sceneLabelRes(it)) }, 0)
        chips("paletteChips", (listOf("cover") + Palettes.all.map { it.id }).map { ctx.getString(com.radillo.playerwave.ui.visualizer.paletteLabelRes(it)) }, 0)
        (v.find("seekSensitivity") as android.widget.SeekBar).progress = 300
        v.text("valSensitivity", "1.00×")
        (v.find("switchKeepScreenOn") as android.widget.Switch).isChecked = true
        val frame = FrameLayout(ctx).apply { setBackgroundColor(ctx.getColor(R.color.surface)) }
        frame.addView(v, FrameLayout.LayoutParams(-1, -2))
        AccentPainter.paint(frame, AppAccent.current(ctx))
        return frame
    }

    /** Las cuatro escenas a pantalla completa en el área útil del estéreo (car_scene_<id>.png). */
    @Test
    @Config(qualifiers = "es-w1024dp-h534dp-land-mdpi")
    fun carScenes() {
        val ctx = themed(night = true)
        val w = 1024
        val h = 534
        for (id in DefaultScenes.registry().ids) {
            val label = ctx.getString(sceneLabelRes(id))
            capture(visualizer(ctx, label), w, h, "car_scene_$id", musicBitmap(id, w, h))
        }
        // Paleta «Portada» con tres carátulas distintas (naranja, azul, verde): car_cover_<escena>_<color>.png
        for (color in intArrayOf(0xFFFF8A3D.toInt(), 0xFF2B7BFF.toInt(), 0xFF3DDC84.toInt())) {
            val paletteId = Palettes.coverId(color)
            for (id in listOf("modern_bars", "radial", "oscilloscope", "terrain")) {
                val label = ctx.getString(sceneLabelRes(id))
                capture(visualizer(ctx, label), w, h, "car_cover_${id}_${paletteId.removePrefix("cover-")}", musicBitmap(id, w, h, paletteId))
            }
        }
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-port-xxhdpi")
    fun phone() {
        val ctx = themed(night = true)
        val w = 1080
        val h = 2340
        capture(shell(ctx, library(ctx), "navLibrary", mini = true), w, h, "phone_library_dark")
        capture(shell(ctx, nowPlaying(ctx), "navNowPlaying", mini = false), w, h, "phone_now_playing_dark")
        capture(visualizer(ctx), w, h, "phone_visualizer_dark", sceneBitmap(DefaultScenes.DEFAULT_ID, w, h))
        capture(shell(ctx, tagEditor(ctx), "navLibrary", mini = false), w, h, "phone_tag_editor_dark")
    }
}
