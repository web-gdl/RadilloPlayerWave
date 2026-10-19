package com.radillo.playerwave.player.crossfade

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * El reproductor que ven la sesión multimedia (app, notificación, volante) y el puente del estéreo.
 * Intercepta los cambios de canción a mano para que el crossfade los prepare **antes** de saltar
 * ([CrossfadeController.requestSkip]); todo lo demás pasa tal cual.
 *
 * Si el crossfade no aplica (apagado, en pausa, ya mezclando), el salto es inmediato como siempre.
 * Cada salto se traduce a su destino concreto (canción siguiente / anterior) al pedirse: así,
 * aunque se ejecute unas décimas después, «anterior» no cambia de sentido por haber pasado los 3 s.
 */
@OptIn(UnstableApi::class)
internal class SkipCrossfadePlayer(
    player: Player,
    private val crossfade: () -> CrossfadeController?,
) : ForwardingPlayer(player) {

    override fun seekToNext() {
        if (wrappedPlayer.hasNextMediaItem()) skip { super.seekToNextMediaItem() } else super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        if (wrappedPlayer.hasNextMediaItem()) skip { super.seekToNextMediaItem() } else super.seekToNextMediaItem()
    }

    override fun seekToPrevious() {
        val p = wrappedPlayer
        // Igual que Player.seekToPrevious(): pasados unos segundos, «anterior» reinicia la canción.
        if (p.hasPreviousMediaItem() && p.currentPosition <= p.maxSeekToPreviousPosition) {
            skip { super.seekToPreviousMediaItem() }
        } else {
            super.seekToPrevious()
        }
    }

    override fun seekToPreviousMediaItem() {
        if (wrappedPlayer.hasPreviousMediaItem()) skip { super.seekToPreviousMediaItem() } else super.seekToPreviousMediaItem()
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (isOtherItem(mediaItemIndex)) skip { super.seekTo(mediaItemIndex, positionMs) } else super.seekTo(mediaItemIndex, positionMs)
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        if (isOtherItem(mediaItemIndex)) skip { super.seekToDefaultPosition(mediaItemIndex) } else super.seekToDefaultPosition(mediaItemIndex)
    }

    private fun isOtherItem(index: Int) = index != wrappedPlayer.currentMediaItemIndex && index in 0 until wrappedPlayer.mediaItemCount

    private inline fun skip(crossinline action: () -> Unit) {
        val handled = crossfade()?.requestSkip { action() } == true
        if (!handled) action()
    }
}
