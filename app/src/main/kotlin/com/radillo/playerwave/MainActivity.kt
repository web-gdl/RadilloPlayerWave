package com.radillo.playerwave

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.radillo.playerwave.core.device.DeviceProfile
import com.radillo.playerwave.core.model.ThemeMode
import com.radillo.playerwave.core.model.Track
import com.radillo.playerwave.core.ui.ArtworkLoader
import com.radillo.playerwave.data.permissions.AudioPermission
import com.radillo.playerwave.data.settings.AppSettingsRepository
import com.radillo.playerwave.data.settings.PrefsAppSettingsRepository
import com.radillo.playerwave.databinding.ActivityMainBinding
import com.radillo.playerwave.player.engine.PlaybackState
import com.radillo.playerwave.player.jancar.JancarProtocol
import com.radillo.playerwave.player.service.BootResume
import com.radillo.playerwave.ui.ChromeMode
import com.radillo.playerwave.ui.Navigator
import com.radillo.playerwave.ui.Tab
import com.radillo.playerwave.ui.common.subtitle
import com.radillo.playerwave.ui.common.visibleIf
import com.radillo.playerwave.ui.library.CollectionDetailFragment
import com.radillo.playerwave.ui.library.LastCollection
import com.radillo.playerwave.ui.library.LibraryFragment
import com.radillo.playerwave.ui.player.NowPlayingFragment
import com.radillo.playerwave.ui.player.PlayerViewModel
import com.radillo.playerwave.ui.visualizer.VisualizerFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Actividad única con vistas nativas: navegación (raíl lateral en pantallas anchas, barra inferior
 * en móviles), mini reproductor y un contenedor de fragments. Sin Compose, sin AppCompat.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity(), Navigator {

    @Inject lateinit var profile: DeviceProfile
    @Inject lateinit var appSettings: AppSettingsRepository

    private val playerViewModel: PlayerViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private var currentTab = Tab.LIBRARY
    private var appliedTheme = ThemeMode.SYSTEM
    private var lastMiniArt: String? = null

    private val uiPrefs by lazy { getSharedPreferences("ui_state", MODE_PRIVATE) }
    private var lastMiniPlaying: Boolean? = null
    private var lastMiniTrack: Track? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * Aplica el tema elegido antes de crear las vistas. En «según el sistema», las pantallas de coche
     * usan el oscuro: muchos equipos nunca activan el modo noche y una interfaz blanca deslumbra.
     */
    override fun attachBaseContext(newBase: Context) {
        val mode = PrefsAppSettingsRepository.readThemeMode(newBase)
        appliedTheme = mode
        val night = when (mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> if (DeviceProfile.detect(newBase).fixedLandscape) true else null
        }
        if (night == null) {
            super.attachBaseContext(newBase)
        } else {
            val config = Configuration(newBase.resources.configuration)
            val flag = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or flag
            super.attachBaseContext(newBase.createConfigurationContext(config))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientation(appSettings.snapshot().lockLandscape ?: profile.fixedLandscape)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.navLibrary.setOnClickListener { openTab(Tab.LIBRARY) }
        binding.navNowPlaying.setOnClickListener { openTab(Tab.NOW_PLAYING) }
        binding.navVisualizer.setOnClickListener { openTab(Tab.VISUALIZER) }
        // Barra superior de pantallas anchas: Buscar y Ajustes siempre a mano (en el móvil están en la biblioteca).
        binding.topSearch?.setOnClickListener { push(com.radillo.playerwave.ui.library.SearchFragment()) }
        binding.topSettings?.setOnClickListener { push(com.radillo.playerwave.ui.settings.SettingsFragment()) }
        binding.miniPlayer.miniOpen.setOnClickListener { openTab(Tab.NOW_PLAYING) }
        binding.miniPlayer.miniPlay.setOnClickListener { playerViewModel.togglePlayPause() }
        binding.miniPlayer.miniNext.setOnClickListener { playerViewModel.next() }
        binding.miniPlayer.miniPrevious.setOnClickListener { playerViewModel.previous() }

        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    if (f.id == R.id.content) updateChrome()
                }
            },
            false,
        )

        if (savedInstanceState == null) {
            // Se vuelve a la última sección usada (en el coche, casi siempre «Reproduciendo») y, en la
            // biblioteca, a la carpeta, álbum o artista que estuviera abierto.
            openTab(Tab.entries.firstOrNull { it.name == uiPrefs.getString(KEY_LAST_TAB, null) } ?: Tab.LIBRARY)
            requestNotificationPermissionOnce()
            handleBootMemory(intent)
        } else {
            currentTab = Tab.entries.getOrElse(savedInstanceState.getInt(KEY_TAB)) { Tab.LIBRARY }
        }
        updateNavSelection()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { playerViewModel.state.collect(::renderMiniPlayer) }
                launch {
                    appSettings.settings.map { it.themeMode }.distinctUntilChanged().collect { mode ->
                        if (mode != appliedTheme) recreate()
                    }
                }
                launch {
                    appSettings.settings
                        .map { it.lockLandscape ?: profile.fixedLandscape }
                        .distinctUntilChanged()
                        .collect(::applyOrientation)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleBootMemory(intent)
    }

    /**
     * El estéreo Jancar abre la última app de música al arrancar con el extra `boot_memory=true`.
     * Se avisa al servicio para que la música siga si estaba sonando. Se ignora si la actividad
     * se reabre desde «recientes» con el mismo intent.
     */
    private fun handleBootMemory(intent: Intent?) {
        intent ?: return
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        @Suppress("DEPRECATION")
        val value = intent.extras?.get(JancarProtocol.EXTRA_BOOT_MEMORY)?.toString() ?: return
        if (!value.equals("true", ignoreCase = true)) return
        intent.removeExtra(JancarProtocol.EXTRA_BOOT_MEMORY)
        com.radillo.playerwave.core.diagnostics.EventLog.log("El estéreo abrió la app al arrancar")
        BootResume.request()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab.ordinal)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ArtworkLoader.get(this).trim()
    }

    // ---- Navigator ----

    /**
     * Abre una sección de primer nivel. La biblioteca recuerda la carpeta, álbum o artista abierto:
     * al volver a ella desde otra sección (o al abrir la app) se reabre. Volver a tocar «Biblioteca»
     * estando ya en ella lleva a la raíz.
     */
    override fun openTab(tab: Tab) {
        val fm = supportFragmentManager
        if (fm.isStateSaved) return
        val atRoot = fm.backStackEntryCount == 0
        val hasContent = fm.findFragmentById(R.id.content) != null
        if (tab == currentTab && atRoot && hasContent) return
        val switching = tab != currentTab || !hasContent
        // Al vaciar la pila la biblioteca puede reanudarse un instante y borrar la carpeta guardada.
        val savedCollection = LastCollection.read(this)
        if (!atRoot) fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val fragment = when (tab) {
            Tab.LIBRARY -> LibraryFragment()
            Tab.NOW_PLAYING -> NowPlayingFragment()
            Tab.VISUALIZER -> VisualizerFragment.newInstance()
        }
        fm.beginTransaction().setReorderingAllowed(true).replace(R.id.content, fragment).commit()
        currentTab = tab
        uiPrefs.edit().putString(KEY_LAST_TAB, tab.name).apply()
        updateNavSelection()
        when {
            savedCollection == null -> Unit
            tab != Tab.LIBRARY -> LastCollection.save(this, savedCollection.kind, savedCollection.key, savedCollection.title)
            switching -> push(CollectionDetailFragment.newInstance(savedCollection.kind, savedCollection.key, savedCollection.title))
            else -> LastCollection.clear(this)
        }
    }

    override fun push(fragment: Fragment) {
        val fm = supportFragmentManager
        if (fm.isStateSaved) return
        fm.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun back() {
        onBackPressedDispatcher.onBackPressed()
    }

    // ---- Chrome ----

    private fun updateChrome() {
        val current = supportFragmentManager.findFragmentById(R.id.content) as? ChromeMode
        binding.navBar.visibleIf(current?.hidesNavigation != true)
        binding.navDivider?.visibleIf(current?.hidesNavigation != true)
        renderMiniPlayer(playerViewModel.state.value)
    }

    private fun updateNavSelection() {
        binding.navLibrary.isSelected = currentTab == Tab.LIBRARY
        binding.navNowPlaying.isSelected = currentTab == Tab.NOW_PLAYING
        binding.navVisualizer.isSelected = currentTab == Tab.VISUALIZER
    }

    private fun renderMiniPlayer(state: PlaybackState) {
        val mini = binding.miniPlayer
        val track = state.currentTrack
        val current = supportFragmentManager.findFragmentById(R.id.content) as? ChromeMode
        val visible = track != null && current?.hidesMiniPlayer != true
        mini.root.visibleIf(visible)
        if (track == null) return
        // Los textos solo al cambiar de canción: el estado llega dos veces por segundo con el progreso.
        if (lastMiniTrack != track) {
            lastMiniTrack = track
            mini.miniTitle.text = track.title
            mini.miniSubtitle.text = track.subtitle(this)
        }
        mini.miniProgress.progress = if (state.durationMs > 0) (state.positionMs * 1000L / state.durationMs).toInt().coerceIn(0, 1000) else 0
        if (lastMiniPlaying != state.isPlaying) {
            lastMiniPlaying = state.isPlaying
            mini.miniPlay.setImageResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            mini.miniPlay.contentDescription = getString(if (state.isPlaying) R.string.action_pause else R.string.action_play)
        }
        if (lastMiniArt != track.coverUri) {
            lastMiniArt = track.coverUri
            ArtworkLoader.get(this).load(mini.miniArt, track.coverUri, mini.miniArt.layoutParams.width, R.drawable.tile_note)
        }
    }

    private fun applyOrientation(lockLandscape: Boolean) {
        val wanted = if (lockLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (requestedOrientation != wanted) requestedOrientation = wanted
    }

    /** `POST_NOTIFICATIONS` (Android 13+) se pide una sola vez; la app funciona igual sin él. */
    private fun requestNotificationPermissionOnce() {
        val permission = AudioPermission.notificationPermissionOrNull() ?: return
        if (uiPrefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return
        uiPrefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
        notificationPermission.launch(permission)
    }

    private companion object {
        const val KEY_TAB = "tab"
        const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
        const val KEY_LAST_TAB = "last_tab"
    }
}
