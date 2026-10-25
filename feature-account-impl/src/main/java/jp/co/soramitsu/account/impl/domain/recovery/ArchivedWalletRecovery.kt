package jp.co.soramitsu.account.impl.domain.recovery

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import javax.inject.Inject
import jp.co.soramitsu.account.impl.data.repository.WalletSecretMutationCoordinator
import jp.co.soramitsu.common.data.secrets.ArchivedWalletMaterialDecoder
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.utils.v4r2tonAddress
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.util.encoders.Hex

/** In-memory authenticated archive access. No material enters navigation or saved state. */
class ArchivedWalletRecovery @Inject constructor(
    private val preferences: EncryptedPreferences,
    private val coordinator: WalletSecretMutationCoordinator
) {
    data class Address(val scope: WalletSecretScope, val text: String)

    class Record(
        val key: String,
        val addresses: List<Address>,
        val unprovedParts: Int,
        val readable: Boolean,
        internal val snapshot: EncryptedPreferenceSnapshot?
    ) {
        val canPurge: Boolean get() = snapshot != null && WalletSecretArchive.sourceKeyOf(key) != null

        override fun toString(): String = "ArchivedWalletRecord(readable=$readable)"
    }

    suspend fun read(): List<Record> = withContext(Dispatchers.IO) {
        preferences.keysWithPrefixes(setOf(WalletSecretArchive.KEY_PREFIX), 8192, 1024, 1_048_576,
            failOnOversizedMatch = true).sorted().mapNotNull { key ->
            val source = WalletSecretArchive.sourceKeyOf(key).orEmpty()
            // PIN history belongs to PIN recovery, never the wallet-material UI.
            if (source == "pin_code" || source.startsWith("pin_code:")) return@mapNotNull null
            try {
                val snapshot = preferences.getDecryptedStringSnapshot(key)
                val decoded = ArchivedWalletMaterialDecoder().decode(source, snapshot?.takeIf { it.hasDecryptedValue }?.plaintext.orEmpty())
                var unprovedParts = decoded.unprovedParts
                val addresses = decoded.candidates.mapNotNull { candidate ->
                    try { Address(candidate.scope, when (candidate.scope) {
                        WalletSecretScope.Substrate -> candidate.accountId.toAddress(42)
                        WalletSecretScope.Ethereum -> "0x" + Hex.toHexString(candidate.accountId)
                        WalletSecretScope.Ton -> candidate.publicKey.v4r2tonAddress(isTestnet = false)
                        is WalletSecretScope.ChainAccount -> error("Archive candidates are independent wallet roots")
                    }) } catch (failure: CancellationException) { throw failure }
                    catch (_: Exception) { unprovedParts++; null }
                }
                Record(key, addresses, unprovedParts, snapshot?.hasDecryptedValue == true, snapshot)
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { Record(key, emptyList(), 1, false, null) }
        }
    }

    suspend fun restore(record: Record, scope: WalletSecretScope, name: String): Long = withContext(Dispatchers.IO) {
        require(record.addresses.any { it.scope == scope })
        coordinator.restoreArchived(record.key, checkNotNull(record.snapshot), scope, name)
    }

    suspend fun purge(record: Record) = withContext(Dispatchers.IO) {
        coordinator.purgeArchived(record.key, checkNotNull(record.snapshot))
    }

    /** The complete original plaintext, including unknown/malformed fields; no decoded-field projection. */
    fun exactExport(record: Record): String {
        require(record.readable && record.snapshot?.hasDecryptedValue == true)
        val data = JsonObject().apply {
            addProperty("format", "fearless-retained-wallet-data-v1")
            addProperty("storageKey", record.key)
            addProperty("storedData", checkNotNull(record.snapshot).plaintext)
        }
        return GsonBuilder().setPrettyPrinting().create().toJson(data)
    }
}
