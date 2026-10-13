package github.realtvop.pixelcardemulator

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.util.UUID

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

    var cards by mutableStateOf(store.loadCards())
        private set

    /** Held as a single id so that at most one card can be active. */
    var activeId by mutableStateOf<String?>(null)
        private set

    /** Independent of [activeId]: the default card does not have to be the active one. */
    var defaultId by mutableStateOf(store.loadDefaultCardId().takeIf { id -> cards.any { it.id == id } })
        private set

    fun setActive(id: String?) {
        activeId = id
    }

    fun toggleActive(id: String) {
        setActive(if (activeId == id) null else id)
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
        cards = cards.map { if (it.id == id) it.copy(name = name, uid = uid) else it }
        store.saveCards(cards)
    }

    fun delete(id: String) {
        cards = cards.filterNot { it.id == id }
        if (activeId == id) activeId = null
        if (defaultId == id) {
            defaultId = null
            store.saveDefaultCardId(null)
        }
        store.saveCards(cards)
    }
}
