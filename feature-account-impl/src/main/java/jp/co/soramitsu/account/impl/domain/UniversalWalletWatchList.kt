package jp.co.soramitsu.account.impl.domain

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice.Arrival
import jp.co.soramitsu.account.api.domain.UniversalWalletSlot
import jp.co.soramitsu.common.data.storage.Preferences

/**
 * The no-password accounts that a password wallet's slots left for their password-protected
 * ones after a probe read them empty: by Switch, or when an import or a later enrolment took
 * the password. A deposit can land after that read, so the notice keeps checking both
 * accounts of each entry; entries go only with the wallet.
 */
class UniversalWalletWatchList(private val preferences: Preferences) {

    class Entry(
        val slot: UniversalWalletSlot,
        /** The chain row of the slot; null for the TON root. */
        val chainId: String?,
        val oldKey: ByteArray,
        val newKey: ByteArray,
        /** What the last check saw at the address the slot does not use. */
        val arrival: Arrival? = null
    ) {
        fun withArrival(arrival: Arrival?) = Entry(slot, chainId, oldKey, newKey, arrival)
    }

    fun entries(metaId: Long): List<Entry> {
        val stored = preferences.getString(key(metaId)) ?: return emptyList()
        return try {
            JsonParser.parseString(stored).asJsonArray.map { decode(it.asJsonObject) }
        } catch (_: Exception) {
            // Public keys only; an unreadable list watches nothing rather than failing the wallet.
            emptyList()
        }
    }

    /** Adds or replaces the entry of [entry]'s slot; the rest of the wallet's list is kept. */
    @Synchronized
    fun put(metaId: Long, entry: Entry) {
        val entries = entries(metaId).filterNot { it.slot == entry.slot } + entry
        val array = JsonArray()
        entries.forEach { array.add(encode(it)) }
        preferences.putString(key(metaId), array.toString())
    }

    @Synchronized
    fun forget(metaId: Long) = preferences.putString(key(metaId), null)

    private fun encode(entry: Entry) = JsonObject().apply {
        addProperty(SLOT, entry.slot.name)
        entry.chainId?.let { addProperty(CHAIN_ID, it) }
        addProperty(OLD_KEY, entry.oldKey.toHex())
        addProperty(NEW_KEY, entry.newKey.toHex())
        entry.arrival?.let { arrival ->
            addProperty(ARRIVAL, arrival.kind.name)
            arrival.balance?.let { addProperty(BALANCE, it) }
        }
    }

    private fun decode(json: JsonObject) = Entry(
        slot = UniversalWalletSlot.valueOf(json[SLOT].asString),
        chainId = json[CHAIN_ID]?.asString,
        oldKey = json[OLD_KEY].asString.fromHex(),
        newKey = json[NEW_KEY].asString.fromHex(),
        arrival = json[ARRIVAL]?.asString?.let { Arrival(Arrival.Kind.valueOf(it), json[BALANCE]?.asString) }
    )

    private fun key(metaId: Long) = "$KEY_PREFIX$metaId"

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun String.fromHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val KEY_PREFIX = "universal_wallet_watch.v1."
        const val SLOT = "slot"
        const val CHAIN_ID = "chainId"
        const val OLD_KEY = "oldKey"
        const val NEW_KEY = "newKey"
        const val ARRIVAL = "arrival"
        const val BALANCE = "balance"
    }
}
