package github.realtvop.pixelcardemulator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.util.UUID

/** A saved card. [uid] is uppercase hex without separators and is never shown once saved. */
data class SavedCard(
    val name: String,
    val uid: String,
    val id: String = UUID.randomUUID().toString(),
) {
    /** UID length in bytes: 4, 7 or 10. */
    val uidSize: Int get() = uid.length / 2
}

val MockCards = listOf(
    SavedCard("Office", "9C4E21B7"),
    SavedCard("Home", "04A2247B915E80"),
    SavedCard("Gym", "5A17C3F0"),
    SavedCard("Parking garage", "08F13D6A22B94C7E0151"),
)

/** In-memory mock state. Nothing here touches NFC or storage, so it resets when the process dies. */
class CardsViewModel : ViewModel() {
    var cards by mutableStateOf(MockCards)
        private set

    /** Held as a single id so that at most one card can be active. */
    var activeId by mutableStateOf<String?>(MockCards[0].id)
        private set

    /** Independent of [activeId]: the default card does not have to be the active one. */
    var defaultId by mutableStateOf<String?>(MockCards[1].id)
        private set

    fun toggleActive(id: String) {
        activeId = if (activeId == id) null else id
    }

    fun toggleDefault(id: String) {
        defaultId = if (defaultId == id) null else id
    }

    fun add(name: String, uid: String) {
        cards = cards + SavedCard(name, uid)
    }

    fun update(id: String, name: String, uid: String) {
        cards = cards.map { if (it.id == id) it.copy(name = name, uid = uid) else it }
    }

    fun delete(id: String) {
        cards = cards.filterNot { it.id == id }
        if (activeId == id) activeId = null
        if (defaultId == id) defaultId = null
    }
}
