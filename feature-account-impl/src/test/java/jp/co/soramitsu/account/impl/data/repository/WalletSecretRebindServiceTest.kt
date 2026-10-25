package jp.co.soramitsu.account.impl.data.repository

import com.google.gson.Gson
import jp.co.soramitsu.account.impl.domain.recovery.WalletIdentityVerifier
import jp.co.soramitsu.account.impl.domain.recovery.WalletKeyIdentity
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.test.runTest
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSecretRebindServiceTest {
    private val storage = HashMapEncryptedPreferences()
    private val verifier = WalletIdentityVerifier(JsonSeedDecoder(Gson()))
    // Public Hardhat account 0, never a real user's material.
    private val privateKey = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    private val publicKey = EthereumKeypairFactory.createWithPrivateKey(Hex.decode(privateKey)).publicKey
    private val identity = WalletKeyIdentity.Ethereum(publicKey, publicKey.ethereumAddressFromPublicKey())
    private val candidate = verifier.verifySeed(identity, privateKey)
    private val quarantine = WalletSecretQuarantine.keyFor(ACTIVE)
    private val marker = WalletPublicIdentityRecovery.keyFor(META_ID, ACTIVE)

    @Test
    fun replacesOneBrokenKeyAndRetainsBothEarlierValuesWithoutTouchingOtherKeys() = runTest {
        storage.putEncryptedString(ACTIVE, "broken-active")
        storage.putEncryptedString(quarantine, "older-quarantine")
        storage.putEncryptedString(marker, WalletPublicIdentityRecovery.MARKER_VALUE)
        storage.putEncryptedString("7:SUBSTRATE_SECRETS", "other-scope")
        storage.putEncryptedString("8:ETHEREUM_SECRETS", "other-wallet")
        storage.putEncryptedString(WalletSecretQuarantine.keyFor("7:ACCESS_SECRETS"), "shared-legacy")
        assertEquals(WalletSecretRebindService.Result.RESTORED, service().rebind(META_ID, candidate))
        assertEquals(candidate.encoded, storage.getDecryptedString(ACTIVE))
        assertEquals("broken-active", storage.getDecryptedString(WalletSecretArchive.keyFor(ACTIVE)))
        assertEquals("older-quarantine", storage.getDecryptedString(WalletSecretArchive.keyFor(ACTIVE, 1)))
        assertFalse(storage.hasKey(quarantine))
        assertFalse(storage.hasKey(marker))
        assertEquals("other-scope", storage.getDecryptedString("7:SUBSTRATE_SECRETS"))
        assertEquals("other-wallet", storage.getDecryptedString("8:ETHEREUM_SECRETS"))
        assertEquals("shared-legacy", storage.getDecryptedString(WalletSecretQuarantine.keyFor("7:ACCESS_SECRETS")))
        assertEquals(1L, storage.walletSecretQuarantineVersion.value)
    }

    @Test
    fun missingKeyRecoveryInvalidatesTheRecoveryInventoryAndIsIdempotent() = runTest {
        assertEquals(WalletSecretRebindService.Result.RESTORED, service().rebind(META_ID, candidate))
        assertEquals(1L, storage.walletSecretQuarantineVersion.value)
        assertEquals(WalletSecretRebindService.Result.ALREADY_USABLE, service().rebind(META_ID, candidate))
        assertEquals(1L, storage.walletSecretQuarantineVersion.value)
        assertFalse(storage.hasKeyWithPrefix(WalletSecretArchive.KEY_PREFIX))
    }

    @Test
    fun validActiveKeyIsKeptWhileStaleScopeMarkersAreRepaired() = runTest {
        storage.putEncryptedString(ACTIVE, candidate.encoded)
        storage.putEncryptedString(quarantine, "old-internal-build-quarantine")
        storage.putEncryptedString(marker, WalletPublicIdentityRecovery.MARKER_VALUE)
        service().rebind(META_ID, candidate)
        assertEquals(candidate.encoded, storage.getDecryptedString(ACTIVE))
        assertEquals("old-internal-build-quarantine", storage.getDecryptedString(WalletSecretArchive.keyFor(ACTIVE)))
        assertFalse(storage.hasKey(quarantine))
        assertFalse(storage.hasKey(marker))
    }

    @Test
    fun identityChangedSincePhraseEntryRejectsTheCandidateBeforeWriting() = runTest {
        storage.putEncryptedString(quarantine, "preserve")
        val changedKeys = EthereumKeypairFactory.createWithPrivateKey(ByteArray(32) { 13 })
        val changed = WalletKeyIdentity.Ethereum(changedKeys.publicKey, changedKeys.publicKey.ethereumAddressFromPublicKey())
        val failure = runCatching { service(reader = WalletKeyIdentityReader { _, _ -> changed }).rebind(META_ID, candidate) }
        assertTrue(failure.isFailure)
        assertEquals("preserve", storage.getDecryptedString(quarantine))
        assertFalse(storage.hasKey(ACTIVE))
        assertFalse(storage.hasKeyWithPrefix(WalletSecretArchive.KEY_PREFIX))
    }

    @Test
    fun concurrentArchiveWriteRejectsTheWholeRepairAndPreservesTheQuarantine() = runTest {
        storage.putEncryptedString(quarantine, "preserve")
        val racing = object : EncryptedPreferences by storage {
            override fun replaceEncryptedStringsDurablyIfStatesMatch(
                expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
                valuesToPut: Map<String, String>, keysToRemove: Set<String>,
                snapshotMoves: List<EncryptedPreferenceSnapshotMove>
            ): Boolean {
                storage.putEncryptedString(WalletSecretArchive.keyFor(ACTIVE), "concurrent-retained-value")
                return storage.replaceEncryptedStringsDurablyIfStatesMatch(expectedStates, valuesToPut, keysToRemove, snapshotMoves)
            }
        }
        val failure = runCatching { service(racing).rebind(META_ID, candidate) }.exceptionOrNull()
        assertTrue(failure is WalletSecretConcurrentMutationException)
        assertEquals("preserve", storage.getDecryptedString(quarantine))
        assertEquals("concurrent-retained-value", storage.getDecryptedString(WalletSecretArchive.keyFor(ACTIVE)))
        assertFalse(storage.hasKey(ACTIVE))
    }

    @Test
    fun unreadableStorageFailsWithoutMovingOrReplacingAnything() = runTest {
        storage.putEncryptedString(quarantine, "preserve")
        val unavailable = object : EncryptedPreferences by storage {
            override fun getDecryptedStringSnapshot(field: String): EncryptedPreferenceSnapshot? {
                if (field == ACTIVE) error("provider unavailable")
                return storage.getDecryptedStringSnapshot(field)
            }
        }
        assertTrue(runCatching { service(unavailable).rebind(META_ID, candidate) }.isFailure)
        assertEquals("preserve", storage.getDecryptedString(quarantine))
        assertFalse(storage.hasKey(ACTIVE))
        assertFalse(storage.hasKeyWithPrefix(WalletSecretArchive.KEY_PREFIX))
    }

    private fun service(
        preferences: EncryptedPreferences = storage,
        reader: WalletKeyIdentityReader = WalletKeyIdentityReader { id, scope ->
            require(id == META_ID && scope == WalletSecretScope.Ethereum)
            identity
        }
    ) = WalletSecretRebindService(preferences, reader, verifier, WalletSecretMutationJournalStore(preferences))

    private companion object {
        const val META_ID = 7L
        const val ACTIVE = "7:ETHEREUM_SECRETS"
    }
}
