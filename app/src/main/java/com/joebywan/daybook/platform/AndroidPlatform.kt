package com.joebywan.daybook.platform

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.joebywan.daybook.R
import com.joebywan.daybook.core.Difficulty
import com.joebywan.daybook.core.PuzzleType
import com.joebywan.daybook.core.SolveTone
import com.joebywan.daybook.data.DataStoreKeyValueStore
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.data.preferencesFile
import com.joebywan.daybook.puzzles.PuzzleState
import com.joebywan.daybook.ui.theme.withFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.format.DateTimeFormatter

// The Android half of the platform seam. The shared screens, store and navigation call these
// functions by name; the web build has a file of the same functions, in the same package, at
// web/src/wasmJsMain/kotlin/com/joebywan/daybook/platform/WebPlatform.kt. Keep the two in step —
// a signature added here and not there is a web compile error, which is the point.
//
// Every body here is what the shared code did inline before the web build existed, so Android
// behaves exactly as it did.

/** Today on the device's own calendar and time zone. */
fun currentDate(): LocalDate = java.time.LocalDate.now().toKotlinLocalDate()

/** Wall-clock milliseconds, used only to order saved games by how recently they were left. */
fun currentTimeMillis(): Long = System.currentTimeMillis()

/** A fresh nonce for a random board: a second random game must be a new board. */
fun freshNonce(): Long = System.nanoTime()

private val formatters = HashMap<String, DateTimeFormatter>()

/** [date] in a `DateTimeFormatter` [pattern], in the device's locale. */
fun formatDate(date: LocalDate, pattern: String): String =
    date.toJavaLocalDate().format(formatters.getOrPut(pattern) { DateTimeFormatter.ofPattern(pattern) })

/** A clock reading such as 3:07. */
fun formatClock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** The DataStore file named [name], behind the shared [KeyValueStore] interface. */
@Composable
fun rememberKeyValueStore(name: String): KeyValueStore {
    val context = LocalContext.current
    return remember(name) { DataStoreKeyValueStore(context.preferencesFile(name)) }
}

/** The system back button. */
@Composable
fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

/**
 * An on-screen way back, for platforms without a system back button. Android has one, and the
 * screens deliberately carry no back arrow of their own, so this draws nothing.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun BackButton(onBack: () -> Unit) = Unit

/** Status-bar icon tint to match the theme. Bar colours come from enableEdgeToEdge(). */
@Composable
fun SystemBarsAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
}

/** Whether the stats screen offers a backup export and import. Android has its own backup. */
const val OFFERS_BACKUP: Boolean = false

/** The backup controls on the stats screen; never shown on Android (see [OFFERS_BACKUP]). */
@Composable
fun BackupControls() = Unit

/** A board, generated off the main thread so a slow one never freezes the frame. */
suspend fun generateBoard(puzzle: PuzzleType, seed: Long, difficulty: Difficulty): PuzzleState =
    withContext(Dispatchers.Default) { puzzle.generate(seed, difficulty) }

/** A board already generated and waiting, if the platform keeps any. Android makes each on open. */
@Suppress("UNUSED_PARAMETER")
fun readyBoard(puzzle: PuzzleType, seed: Long, difficulty: Difficulty): PuzzleState? = null

/** Generation is off the main thread, so a spinner on the loading screen keeps turning. */
const val GENERATION_ANIMATES: Boolean = true

/** Settings text under the Sound switch: what else keeps the chime quiet on this platform. */
const val SOLVE_SOUND_NOTE: String =
    "It follows your media volume and stays quiet when your phone is on silent or vibrate."

/** How long a board may take before "Setting out..." appears, so a quick one does not flash it. */
const val LOADING_MESSAGE_DELAY_MS: Long = 150L

/**
 * Called while the home grid is showing. Android generates each board off the main thread when it
 * is opened, which is quick enough, so there is nothing to get ready in advance.
 */
@Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
suspend fun prepareBoards(day: LocalDate, difficulty: Difficulty) = Unit

/** The app's typography in Fredoka (`res/font`, SIL OFL); glyphs it lacks fall back to the system font. */
@Composable
fun platformTypography(base: Typography): Typography = base.withFamily(fredoka)

private val fredoka = FontFamily(
    Font(R.font.fredoka_regular, FontWeight.Normal),
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)

private val soundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private val solveTonePcm by lazy { SolveTone.pcm16() }

/**
 * Plays the solve sound when called, if [enabled]. The tone is synthesised ([SolveTone]) and played
 * through a static `AudioTrack` tagged as game audio, so it follows the media volume. It is skipped
 * when the ringer is on silent or vibrate, built and released off the main thread, and any audio
 * failure (no output, a busy device) is swallowed: a missing chime must never cost a solved puzzle.
 */
@Composable
fun rememberSolveSoundPlayer(enabled: Boolean): () -> Unit {
    val context = LocalContext.current.applicationContext
    return remember(enabled, context) {
        if (!enabled) ({}) else ({ playSolveSound(context) })
    }
}

private fun playSolveSound(context: Context) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
    soundScope.launch {
        var track: AudioTrack? = null
        try {
            val pcm = solveTonePcm
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SolveTone.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            // A static track has no completion callback worth a Looper; the tone's length is known.
            delay(SolveTone.LENGTH * 1000L / SolveTone.SAMPLE_RATE + 150L)
        } catch (_: Exception) {
            // No audio output, or the device refused the track: silence is the right fallback.
        } finally {
            try {
                track?.stop()
            } catch (_: Exception) {
            }
            track?.release()
        }
    }
}
