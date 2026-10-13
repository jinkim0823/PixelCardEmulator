package github.realtvop.pixelcardemulator

import android.app.Application
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.util.UUID
import java.util.concurrent.Executors

private const val NfcStatePreferences = "nfc_emulation_state"
private const val OemRestorePendingKey = "oem_restore_pending"

data class EmulatorUiState(
    val busy: Boolean = false,
    val status: String? = null,
    val permissionRequired: Boolean = false,
    val error: String? = null,
)

private sealed interface PendingAction {
    data class Activate(val card: SavedCard, val previousCard: SavedCard?) : PendingAction
    data class Deactivate(val deleteCardId: String? = null) : PendingAction
    data object RestoreAfterRestart : PendingAction
}

/** A saved card. [uid] is uppercase hex without separators. */
data class SavedCard(
    val name: String,
    val uid: String,
    val id: String = UUID.randomUUID().toString(),
) {
    init {
        require(uid.length / 2 in SupportedUidSizes && uid.length % 2 == 0) { "Unsupported UID length" }
        require(uid.all { it in "0123456789ABCDEF" }) { "UID must be uppercase hexadecimal" }
    }

    /** UID length in bytes: 4, 7 or 10. */
    val uidSize: Int get() = uid.length / 2
}

val MockCards = listOf(
    SavedCard("Office", "9C4E21B7"),
    SavedCard("Home", "04A2247B915E80"),
    SavedCard("Gym", "5A17C3F0"),
    SavedCard("Parking garage", "08F13D6A22B94C7E0151"),
)

class CardsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = CardStore(application)
    private val emulator = OemNfcCardEmulator(application)
    private val nfcState = application.getSharedPreferences(NfcStatePreferences, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingAction: PendingAction? = null

    var cards by mutableStateOf(store.loadCards())
        private set

    /** Held as a single id so that at most one card can be active. */
    var activeId by mutableStateOf<String?>(null)
        private set

    /** Independent of [activeId]: the default card does not have to be the active one. */
    var defaultId by mutableStateOf(store.loadDefaultCardId().takeIf { id -> cards.any { it.id == id } })
        private set

    var emulatorUiState by mutableStateOf(EmulatorUiState())
        private set

    val grantCommand: String
        get() = "adb shell pm grant ${getApplication<Application>().packageName} " +
            "android.permission.WRITE_SECURE_SETTINGS"

    init {
        if (nfcState.getBoolean(OemRestorePendingKey, false)) {
            runAction(PendingAction.RestoreAfterRestart)
        } else {
            defaultId?.let { id -> cards.find { it.id == id } }?.let { runAction(PendingAction.Activate(it, null)) }
        }
    }

    fun setActive(id: String?) {
        activeId = id
    }

    fun toggleActive(id: String) {
        if (emulatorUiState.busy) return
        if (activeId == id) {
            runAction(PendingAction.Deactivate())
        } else {
            val card = cards.find { it.id == id } ?: return
            runAction(PendingAction.Activate(card, cards.find { it.id == activeId }))
        }
    }

    fun toggleDefault(id: String) {
        defaultId = if (defaultId == id) null else id
        store.saveDefaultCardId(defaultId)
    }

    fun add(name: String, uid: String) {
        cards = cards + SavedCard(name, uid)
        store.saveCards(cards)
    }

    fun update(id: String, name: String, uid: String) {
        val previous = cards.find { it.id == id }
        cards = cards.map { if (it.id == id) it.copy(name = name, uid = uid) else it }
        store.saveCards(cards)
        if (activeId == id && !emulatorUiState.busy) {
            cards.find { it.id == id }?.let { runAction(PendingAction.Activate(it, previous)) }
        }
    }

    fun delete(id: String) {
        if (activeId == id) {
            if (!emulatorUiState.busy) runAction(PendingAction.Deactivate(deleteCardId = id))
            return
        }
        deleteStoredCard(id)
    }

    fun refreshPermission() {
        if (hasSecureSettingsPermission() && pendingAction != null && !emulatorUiState.busy) {
            emulatorUiState = emulatorUiState.copy(permissionRequired = false)
            pendingAction?.let(::runAction)
        }
    }

    fun dismissPermissionPrompt() {
        emulatorUiState = emulatorUiState.copy(permissionRequired = false)
    }

    fun dismissError() {
        emulatorUiState = emulatorUiState.copy(error = null)
    }

    override fun onCleared() {
        executor.shutdownNow()
        super.onCleared()
    }

    private fun deleteStoredCard(id: String) {
        cards = cards.filterNot { it.id == id }
        if (activeId == id) activeId = null
        if (defaultId == id) {
            defaultId = null
            store.saveDefaultCardId(null)
        }
        store.saveCards(cards)
    }

    private fun runAction(action: PendingAction) {
        pendingAction = action
        if (!hasSecureSettingsPermission()) {
            emulatorUiState = EmulatorUiState(permissionRequired = true)
            return
        }
        if (!emulator.isSupported) {
            emulatorUiState = EmulatorUiState(error = "UID emulation is not supported by this NFC system")
            pendingAction = null
            return
        }

        emulatorUiState = EmulatorUiState(
            busy = true,
            status = when (action) {
                is PendingAction.Activate -> "Activating ${action.card.name}…"
                is PendingAction.Deactivate -> "Restoring NFC defaults…"
                PendingAction.RestoreAfterRestart -> "Recovering NFC after restart…"
            },
        )
        executor.execute { executeAction(action) }
    }

    private fun executeAction(action: PendingAction) {
        try {
            when (action) {
                is PendingAction.Activate -> executeActivation(action)
                is PendingAction.Deactivate -> {
                    emulator.deactivate()
                    saveRestorePending(false)
                    mainHandler.post {
                        setActive(null)
                        action.deleteCardId?.let(::deleteStoredCard)
                        finishAction()
                    }
                }
                PendingAction.RestoreAfterRestart -> {
                    emulator.deactivate()
                    saveRestorePending(false)
                    mainHandler.post {
                        setActive(null)
                        finishAction()
                        defaultId?.let { id -> cards.find { it.id == id } }?.let {
                            runAction(PendingAction.Activate(it, null))
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            mainHandler.post {
                emulatorUiState = EmulatorUiState(error = error.message ?: "NFC operation failed")
                pendingAction = null
            }
        }
    }

    private fun executeActivation(action: PendingAction.Activate) {
        saveRestorePending(true)
        try {
            emulator.activate(action.card.uid.hexToBytes())
            mainHandler.post {
                setActive(action.card.id)
                finishAction()
            }
        } catch (activationError: Throwable) {
            val rollbackError = runCatching {
                if (action.previousCard != null) {
                    emulator.activate(action.previousCard.uid.hexToBytes())
                    if (action.previousCard.id == action.card.id) {
                        mainHandler.post {
                            cards = cards.map { card ->
                                if (card.id == action.previousCard.id) action.previousCard else card
                            }
                            store.saveCards(cards)
                        }
                    }
                } else {
                    emulator.deactivate()
                    saveRestorePending(false)
                }
            }.exceptionOrNull()
            if (rollbackError != null) {
                activationError.addSuppressed(rollbackError)
            }
            throw activationError
        }
    }

    private fun finishAction() {
        emulatorUiState = EmulatorUiState()
        pendingAction = null
    }

    private fun saveRestorePending(pending: Boolean) {
        check(nfcState.edit().putBoolean(OemRestorePendingKey, pending).commit()) {
            "Could not save NFC recovery state"
        }
    }

    private fun hasSecureSettingsPermission(): Boolean =
        getApplication<Application>().checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
}
