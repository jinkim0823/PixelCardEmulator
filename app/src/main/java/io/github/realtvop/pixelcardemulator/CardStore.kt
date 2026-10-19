package io.github.realtvop.pixelcardemulator

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

private const val PreferencesName = "office_cards"
private const val CardsKey = "cards"
private const val DefaultCardKey = "default_card"
private const val ActiveCardKey = "active_card"

/** Synchronous persistence for the small card catalog. */
class CardStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun loadCards(): List<SavedCard> = runCatching {
        val array = JSONArray(preferences.getString(CardsKey, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val uid = Base64.decode(item.getString("uid"), Base64.NO_WRAP)
                if (uid.size in SupportedUidSizes) {
                    add(
                        SavedCard(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            uid = uid.toHex(),
                            profile = CardProfile.fromStorage(item.optString("profile", "default")),
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    fun saveCards(cards: List<SavedCard>) {
        val array = JSONArray().apply {
            cards.forEach { card ->
                put(
                    JSONObject()
                        .put("id", card.id)
                        .put("name", card.name)
                        .put("profile", card.profile.storageId)
                        .put("uid", Base64.encodeToString(card.uid.hexToBytes(), Base64.NO_WRAP)),
                )
            }
        }
        check(preferences.edit().putString(CardsKey, array.toString()).commit()) {
            "Could not save cards"
        }
    }

    fun loadDefaultCardId(): String? = preferences.getString(DefaultCardKey, null)

    fun saveDefaultCardId(id: String?) {
        check(preferences.edit().putString(DefaultCardKey, id).commit()) {
            "Could not save the default card"
        }
    }

    fun loadActiveCardId(): String? = preferences.getString(ActiveCardKey, null)

    fun saveActiveCardId(id: String?) {
        check(preferences.edit().putString(ActiveCardKey, id).commit()) {
            "Could not save the active card"
        }
    }
}

val SupportedUidSizes = setOf(4, 7, 10)

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex value must contain complete bytes" }
    return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}

fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02X".format(byte) }
