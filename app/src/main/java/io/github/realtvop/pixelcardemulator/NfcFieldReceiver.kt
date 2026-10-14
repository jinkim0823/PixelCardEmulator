package io.github.realtvop.pixelcardemulator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings

internal const val ActionRfFieldOn = "com.android.nfc_extras.action.RF_FIELD_ON_DETECTED"
internal const val ActionRfFieldOff = "com.android.nfc_extras.action.RF_FIELD_OFF_DETECTED"

private const val PickerStatePreferences = "automatic_card_picker"
private const val LastLaunchKey = "last_launch_elapsed"
private const val LastSelectionKey = "last_selection_elapsed"
private const val LaunchDebounceMillis = 1_500L
private const val SelectionCooldownMillis = 4_000L

/** Tracks whether one of this app's activities currently gives background launches a visible origin. */
internal object AppVisibility {
    private var startedActivities = 0

    @Synchronized
    fun activityStarted() {
        startedActivities++
    }

    @Synchronized
    fun activityStopped() {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }

    @get:Synchronized
    val hasVisibleActivity: Boolean
        get() = startedActivities > 0
}

/** Opens the restricted card picker when Android reports an external NFC field. */
class NfcFieldReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ActionRfFieldOn) return

        val store = CardStore(context)
        val cards = store.loadCards()
        if (cards.isEmpty()) return

        // A visible activity can start the picker directly. Background and lock-screen launches use
        // the user-granted overlay capability; no overlay window itself is created.
        if (!AppVisibility.hasVisibleActivity && !Settings.canDrawOverlays(context)) return

        val now = SystemClock.elapsedRealtime()
        val preferences = context.getSharedPreferences(PickerStatePreferences, Context.MODE_PRIVATE)
        val lastLaunch = preferences.getLong(LastLaunchKey, 0L)
        val lastSelection = preferences.getLong(LastSelectionKey, 0L)
        if (now >= lastLaunch && now - lastLaunch < LaunchDebounceMillis) return
        if (now >= lastSelection && now - lastSelection < SelectionCooldownMillis) return

        runCatching {
            context.startActivity(
                Intent(context, CardPickerActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                },
            )
        }.onSuccess {
            preferences.edit().putLong(LastLaunchKey, now).apply()
        }
    }

    companion object {
        fun markSelectionCompleted(context: Context) {
            context.getSharedPreferences(PickerStatePreferences, Context.MODE_PRIVATE)
                .edit()
                .putLong(LastSelectionKey, SystemClock.elapsedRealtime())
                .apply()
        }
    }
}
