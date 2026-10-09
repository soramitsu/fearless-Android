package jp.co.soramitsu.testshared

import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HashMapEncryptedPreferences : EncryptedPreferences {
    private val delegate = mutableMapOf<String, String>()
    private val mutableWalletSecretQuarantineVersion = MutableStateFlow(0L)

    override val walletSecretQuarantineVersion: StateFlow<Long> =
        mutableWalletSecretQuarantineVersion.asStateFlow()

    override fun putEncryptedString(field: String, value: String) {
        delegate[field] = value
    }

    override fun getDecryptedString(field: String): String? = delegate[field]

    override fun hasKey(field: String): Boolean = field in delegate

    override fun removeKey(field: String) {
        delegate.remove(field)
    }

    @Synchronized
    override fun replaceEncryptedStringsDurably(
        valuesToPut: Map<String, String>,
        keysToRemove: Set<String>
    ) {
        require(valuesToPut.keys.intersect(keysToRemove).isEmpty())
        delegate.putAll(valuesToPut)
        keysToRemove.forEach { key -> delegate.remove(key) }
    }

    @Synchronized
    override fun replaceEncryptedStringsDurablyIfStatesMatch(
        expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
        valuesToPut: Map<String, String>,
        keysToRemove: Set<String>,
        snapshotMoves: List<EncryptedPreferenceSnapshotMove>
    ): Boolean {
        val activeMoveCount =
            snapshotMoves.count { it.sourceKey in delegate }
        val replaced =
            super<EncryptedPreferences>
                .replaceEncryptedStringsDurablyIfStatesMatch(
                    expectedStates = expectedStates,
                    valuesToPut = valuesToPut,
                    keysToRemove = keysToRemove,
                    snapshotMoves = snapshotMoves
                )
        if (replaced && activeMoveCount > 0) {
            mutableWalletSecretQuarantineVersion.update { it + 1 }
        }
        return replaced
    }

    @Synchronized
    override fun quarantineEncryptedStringDurably(
        sourceKey: String,
        quarantineKey: String,
        expectedSnapshot: EncryptedPreferenceSnapshot
    ): Boolean {
        require(sourceKey != quarantineKey)
        val sourceValue = delegate[sourceKey]

        if (sourceValue == null && sourceKey !in delegate) {
            check(quarantineKey in delegate)
            return expectedSnapshot.matchesUnencryptedStorageValue(
                checkNotNull(delegate[quarantineKey])
            )
        }
        if (
            !expectedSnapshot.matchesUnencryptedStorageValue(
                checkNotNull(sourceValue)
            )
        ) {
            return false
        }

        val existingQuarantine = delegate[quarantineKey]
        check(existingQuarantine == null || existingQuarantine == sourceValue)
        delegate[quarantineKey] = requireNotNull(sourceValue)
        delegate.remove(sourceKey)
        mutableWalletSecretQuarantineVersion.update { it + 1 }
        return true
    }

    override fun requireDurableStorageHealthy() = Unit
}
