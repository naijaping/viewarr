package io.github.petebutty.viewarr.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Watches the box's HDMI sound output while a film opens, so it can start once a frame-rate switch has finished: the
 * TV switching drops HDMI for a moment, and sound opened before it comes back stayed silent on some TVs / receivers.
 * Only for the film player's start (ui/media/FilmRateStart.kt); it changes nothing itself. [start] / [stop] with
 * the player. Logged as "HdmiSound".
 */
class HdmiSoundGuard(context: Context) {
    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val main = Handler(Looper.getMainLooper())
    private var watching = false
    @Volatile private var lastBackAt = 0L
    @Volatile private var lastGoneAt = 0L

    /** The HDMI sound output has come back since [t]. */
    fun backSince(t: Long) = lastBackAt >= t
    /** It has gone (and maybe come back) since [t]. */
    fun goneSince(t: Long) = lastGoneAt >= t

    private fun isHdmi(d: AudioDeviceInfo) = d.isSink && (d.type == AudioDeviceInfo.TYPE_HDMI || d.type == AudioDeviceInfo.TYPE_HDMI_ARC ||
        (android.os.Build.VERSION.SDK_INT >= 31 && d.type == AudioDeviceInfo.TYPE_HDMI_EARC))

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (removed.any(::isHdmi)) { Log.i(TAG, "hdmi sound output gone"); lastGoneAt = System.currentTimeMillis() }
        }
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            // (Registering reports the outputs already there: only a return after a drop counts.)
            if (lastGoneAt > 0 && lastGoneAt >= lastBackAt && added.any(::isHdmi)) { Log.i(TAG, "hdmi sound output back"); lastBackAt = System.currentTimeMillis() }
        }
    }

    fun start() = main.post {
        if (watching) return@post
        watching = true
        runCatching { audio?.registerAudioDeviceCallback(callback, main) }
    }

    fun stop() = main.post {
        if (!watching) return@post
        watching = false
        runCatching { audio?.unregisterAudioDeviceCallback(callback) }
    }

    private companion object { const val TAG = "HdmiSound" }
}
