package io.github.realtvop.pixelcardemulator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import java.util.concurrent.Executors

/** Lock-screen-capable card chooser with the management controls removed. */
class CardPickerActivity : ComponentActivity() {
    private val store by lazy { CardStore(this) }
    private val emulator by lazy { NfcEmulationCoordinator(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()

        setContent {
            PixelCardTheme {
                val cards = remember { store.loadCards() }
                val defaultId = remember { store.loadDefaultCardId() }
                var activeId by remember { mutableStateOf(store.loadActiveCardId()) }
                var busyCardId by remember { mutableStateOf<String?>(null) }
                var error by remember { mutableStateOf<String?>(null) }

                CardListScreen(
                    cards = cards,
                    activeId = activeId,
                    busyCardId = busyCardId,
                    defaultId = defaultId,
                    onToggleActive = select@{ id ->
                        if (busyCardId != null) return@select
                        if (id == activeId) {
                            return@select
                        }
                        val selected = cards.find { it.id == id } ?: return@select
                        val previous = cards.find { it.id == activeId }
                        busyCardId = selected.id
                        executor.execute {
                            val failure = switchCard(selected, previous)
                            mainHandler.post {
                                busyCardId = null
                                if (failure == null) {
                                    activeId = selected.id
                                    NfcFieldReceiver.markSelectionCompleted(this@CardPickerActivity)
                                } else {
                                    error = failure.message ?: getString(R.string.nfc_operation_failed)
                                }
                            }
                        }
                    },
                    onToggleDefault = {},
                    onAddCard = {},
                    onEditCard = {},
                    managementEnabled = false,
                )

                error?.let { message ->
                    AlertDialog(
                        onDismissRequest = { error = null },
                        title = { Text(stringResource(R.string.nfc_operation_failed)) },
                        text = { Text(message) },
                        confirmButton = {
                            TextButton(onClick = { error = null }) { Text(stringResource(R.string.ok)) }
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.activityStarted()
    }

    override fun onStop() {
        AppVisibility.activityStopped()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        emulator.setActivity(this)
    }

    override fun onPause() {
        emulator.setActivity(null)
        super.onPause()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun switchCard(selected: SavedCard, previous: SavedCard?): Throwable? {
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            return NfcEmulationException(getString(R.string.permission_required))
        }
        if (!emulator.isSupported) {
            return NfcEmulationException(getString(R.string.uid_emulation_unsupported))
        }

        return try {
            emulator.activate(selected.uid.hexToBytes())
            store.saveActiveCardId(selected.id)
            null
        } catch (activationError: Throwable) {
            val rollbackError = runCatching {
                if (previous != null) {
                    emulator.activate(previous.uid.hexToBytes())
                    store.saveActiveCardId(previous.id)
                } else {
                    emulator.deactivate()
                    store.saveActiveCardId(null)
                }
            }.exceptionOrNull()
            rollbackError?.let(activationError::addSuppressed)
            activationError
        }
    }
}
