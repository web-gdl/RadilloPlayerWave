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
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.radillo.playerwave.R
import com.radillo.playerwave.ui.common.Row
import com.radillo.playerwave.ui.common.RowAdapter
import com.radillo.playerwave.ui.common.setupList
import com.radillo.playerwave.visualizer.analysis.AudioSource
import com.radillo.playerwave.visualizer.analysis.AudioSourceStatus
import com.radillo.playerwave.visualizer.analysis.PcmBlock
import com.radillo.playerwave.visualizer.analysis.PlaybackClock
import com.radillo.playerwave.visualizer.engine.DefaultScenes
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
 * Capturas de las pantallas principales en el autoestéreo (1024×600, 160 dpi, tema oscuro) y en un
 * móvil. No comprueban píxeles: CI las publica como artefacto para revisar el diseño sin equipo.
 *
 * Los ids se buscan por nombre para que la prueba sobreviva a cambios de los layouts: si un id no
 * existe, ese detalle simplemente no se rellena.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class ScreenshotTest {

    private val outDir = File("build/outputs/screenshots").apply { mkdirs() }

    private val songs = listOf(
        "Bohemian Rhapsody" to "Queen · A Night at the Opera",
        "Hotel California" to "Eagles · Hotel California",
        "La Bamba" to "Ritchie Valens · La Bamba",
        "Rayando el sol" to "Maná · Falta amor",
        "De música ligera" to "Soda Stereo · Canción animal",
        "Oye cómo va" to "Santana · Abraxas",
        "Clandestino" to "Manu Chao · Clandestino",
        "Lamento boliviano" to "Enanitos Verdes · Big Bang",
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
        listOf("navLibrary", "navNowPlaying", "navVisualizer").forEach { root.find(it)?.isSelected = it == selectedNav }
        val miniRoot = root.find("miniPlayer")
        miniRoot?.visibility = if (mini) View.VISIBLE else View.GONE
        miniRoot?.text("miniTitle", "Hotel California")
        miniRoot?.text("miniSubtitle", "Eagles · Hotel California")
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
        v.text("libraryCount", "1248 canciones")
        return v
    }

    private fun nowPlaying(ctx: Context): View {
        val v = LayoutInflater.from(ctx).inflate(R.layout.fragment_now_playing, null, false)
        v.show("emptyText", false)
        v.text("trackTitle", "Hotel California")
        v.text("trackSubtitle", "Eagles · Hotel California")
        v.text("position", "2:41")
        v.text("duration", "6:30")
        v.text("queueInfo", "A continuación: La Bamba")
        (v.find("seek") as? SeekBar)?.progress = 420
        (v.find("art") as? ImageView)?.setImageResource(R.drawable.tile_note)
        (v.find("btnPlay") as? ImageView)?.setImageResource(R.drawable.ic_pause)
        v.find("btnShuffle")?.isActivated = true
        v.find("btnFavorite")?.isActivated = true
        (v.find("btnFavoriteIcon") as? ImageView)?.setImageResource(R.drawable.ic_favorite)
        return v
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

    private fun visualizer(ctx: Context): View {
        val v = LayoutInflater.from(ctx).inflate(R.layout.fragment_visualizer, null, false)
        v.background = null
        v.find("surface")?.visibility = View.INVISIBLE
        v.text("trackTitle", "Hotel California")
        v.text("trackSubtitle", "Eagles")
        v.show("status", false)
        v.text("sceneName", "Espectro")
        (v.find("btnPlay") as? ImageView)?.setImageResource(R.drawable.ic_pause)
        return v
    }

    private fun settings(ctx: Context): View = LayoutInflater.from(ctx).inflate(R.layout.fragment_settings, null, false).apply {
        find("themeSystem")?.isSelected = true
        (find("switchKeepPlaying") as? android.widget.Switch)?.isChecked = true
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
        val ctx = themed(night)
        capture(shell(ctx, library(ctx), "navLibrary", mini = true), 1024, 600, "car_library_$suffix")
        capture(shell(ctx, nowPlaying(ctx), "navNowPlaying", mini = false), 1024, 600, "car_now_playing_$suffix")
        val railWidth = ctx.resources.getDimensionPixelSize(R.dimen.nav_rail)
        val shellView = shell(ctx, visualizer(ctx), "navVisualizer", mini = false)
        val scene = sceneBitmap(DefaultScenes.DEFAULT_ID, 1024 - railWidth, 600)
        val underlay = Bitmap.createBitmap(1024, 600, Bitmap.Config.ARGB_8888).also { Canvas(it).drawBitmap(scene, railWidth.toFloat(), 0f, null) }
        (shellView.find("root") ?: shellView).background = null
        shellView.find("mainColumn")?.background = null
        capture(shellView, 1024, 600, "car_visualizer_$suffix", underlay)
        capture(shell(ctx, settings(ctx), "navLibrary", mini = true), 1024, 600, "car_settings_$suffix")
        capture(shell(ctx, albumDetail(ctx), "navLibrary", mini = true), 1024, 600, "car_folder_$suffix")
    }

    @Test
    @Config(qualifiers = "es-w1024dp-h600dp-land-mdpi")
    fun carHeadUnit() {
        carScreens(night = true, suffix = "dark")
        carScreens(night = false, suffix = "light")
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-port-xxhdpi")
    fun phone() {
        val ctx = themed(night = true)
        val w = 1080
        val h = 2340
        capture(shell(ctx, library(ctx), "navLibrary", mini = true), w, h, "phone_library_dark")
        capture(shell(ctx, nowPlaying(ctx), "navNowPlaying", mini = false), w, h, "phone_now_playing_dark")
        capture(shell(ctx, visualizer(ctx), "navVisualizer", mini = false), w, h, "phone_visualizer_dark")
    }
}
