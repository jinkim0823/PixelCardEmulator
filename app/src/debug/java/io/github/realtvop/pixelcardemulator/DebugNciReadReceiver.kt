package io.github.realtvop.pixelcardemulator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Debug-only, shell/DUMP-protected read-only diagnostic. Never activates or restores a card. */
class DebugNciReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                val store = CardStore(app)
                val uid = store.loadCards().find { it.id == store.loadActiveCardId() }?.uid?.hexToBytes()
                NfcEmulationCoordinator(app).readControllerConfiguration(uid)
            } finally {
                result.finish()
            }
        }.start()
    }
}
