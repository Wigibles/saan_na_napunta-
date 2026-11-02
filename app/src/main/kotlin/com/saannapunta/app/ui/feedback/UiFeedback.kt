package com.saannapunta.app.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.saannapunta.app.R

/** UI sounds rendered by tools/make_sfx.py (earshot-style physical synthesis). */
enum class Sfx(@RawRes val resId: Int) {
    TAP(R.raw.sfx_tap),
    SELECT(R.raw.sfx_select),
    TOGGLE_ON(R.raw.sfx_toggle_on),
    TOGGLE_OFF(R.raw.sfx_toggle_off),
    STEP_NEXT(R.raw.sfx_step_next),
    STEP_BACK(R.raw.sfx_step_back),
    SUCCESS(R.raw.sfx_success),
    COMPLETE(R.raw.sfx_complete),
    ERROR(R.raw.sfx_error),
    DELETE(R.raw.sfx_delete),
    GRANTED(R.raw.sfx_granted)
}

/**
 * App-wide sound player. Sounds are short, quiet sonification cues: they never play while the phone
 * is on silent or vibrate, and the user can turn them off in Settings.
 */
object SoundFx {
    private const val VOLUME = 0.55f
    private const val MIN_REPEAT_MS = 60L

    @Volatile
    var enabled: Boolean = true

    private var pool: SoundPool? = null
    private val soundIds = mutableMapOf<Sfx, Int>()
    private val loaded = mutableSetOf<Int>()
    private val lastPlayed = mutableMapOf<Sfx, Long>()
    private var audioManager: AudioManager? = null

    @Synchronized
    fun init(context: Context) {
        if (pool != null) return
        val app = context.applicationContext
        audioManager = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val newPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        newPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(this) { loaded.add(sampleId) }
        }
        for (sfx in Sfx.entries) {
            soundIds[sfx] = newPool.load(app, sfx.resId, 1)
        }
        pool = newPool
    }

    @Synchronized
    fun play(sfx: Sfx) {
        if (!enabled) return
        if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val now = SystemClock.elapsedRealtime()
        if (now - (lastPlayed[sfx] ?: 0L) < MIN_REPEAT_MS) return
        val id = soundIds[sfx] ?: return
        if (id !in loaded) return
        lastPlayed[sfx] = now
        pool?.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }
}

/** Pairs each sound with the matching system haptic so taps feel physical even with sound off. */
class UiFeedback internal constructor(private val view: View) {

    fun tap() = fire(Sfx.TAP, HapticFeedbackConstants.KEYBOARD_TAP)

    fun select() = fire(Sfx.SELECT, HapticFeedbackConstants.CLOCK_TICK)

    fun toggle(on: Boolean) = fire(
        if (on) Sfx.TOGGLE_ON else Sfx.TOGGLE_OFF,
        when {
            Build.VERSION.SDK_INT >= 34 && on -> HapticFeedbackConstants.TOGGLE_ON
            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.TOGGLE_OFF
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
    )

    fun next() = fire(Sfx.STEP_NEXT, HapticFeedbackConstants.KEYBOARD_TAP)

    fun back() = fire(Sfx.STEP_BACK, HapticFeedbackConstants.KEYBOARD_TAP)

    fun success() = fire(Sfx.SUCCESS, confirmHaptic())

    fun complete() = fire(Sfx.COMPLETE, confirmHaptic())

    fun granted() = fire(Sfx.GRANTED, confirmHaptic())

    fun delete() = fire(Sfx.DELETE, HapticFeedbackConstants.LONG_PRESS)

    fun error() = fire(
        Sfx.ERROR,
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    )

    private fun confirmHaptic(): Int =
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP

    private fun fire(sfx: Sfx, haptic: Int) {
        view.performHapticFeedback(haptic)
        SoundFx.play(sfx)
    }
}

@Composable
fun rememberUiFeedback(): UiFeedback {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view) {
        SoundFx.init(context)
        UiFeedback(view)
    }
}
