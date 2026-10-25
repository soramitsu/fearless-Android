package jp.co.soramitsu.account.impl.domain.recovery

import com.google.gson.Gson
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.data.repository.WalletKeyIdentityReader
import jp.co.soramitsu.account.impl.data.repository.WalletSecretRebindService
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletKeyRecoveryTest {
    private val storage = HashMapEncryptedPreferences()
    private val verifier = WalletIdentityVerifier(JsonSeedDecoder(Gson()))
    private val publicKey = EthereumKeypairFactory.createWithPrivateKey(Hex.decode(PRIVATE_KEY)).publicKey
    private val identity = WalletKeyIdentity.Ethereum(publicKey, publicKey.ethereumAddressFromPublicKey())
    private val reader = WalletKeyIdentityReader { id, scope ->
        require(id == 7L && scope == WalletSecretScope.Ethereum)
        identity
    }

    @Test
    fun matchingPhraseAndPrivateKeyRestoreTheSameDurableAccountWithoutCreatingAWallet() = runTest {
        val recovery = service(reader)
        assertEquals(WalletSecretRebindService.Result.RESTORED,
            recovery.restore(7, identity.scope, ImportMode.MnemonicPhrase, PHRASE))
        val original = storage.getDecryptedString(ACTIVE)
        assertEquals(WalletSecretRebindService.Result.ALREADY_USABLE,
            recovery.restore(7, identity.scope, ImportMode.RawSeed, PRIVATE_KEY))
        assertEquals(original, storage.getDecryptedString(ACTIVE))
    }

    @Test
    fun wrongPhraseLeavesExactOldQuarantineAndActiveNamespaceUntouched() = runTest {
        storage.putEncryptedString(WalletSecretQuarantine.keyFor(ACTIVE), "original-opaque-value")
        val failure = runCatching {
            service(reader).restore(7, identity.scope, ImportMode.MnemonicPhrase,
                "bottom drive obey lake curtain smoke basket hold race lonely fit walk")
        }.exceptionOrNull()
        assertTrue(failure is WalletRecoveryIdentityMismatch)
        assertFalse(storage.hasKey(ACTIVE))
        assertEquals("original-opaque-value", storage.getDecryptedString(WalletSecretQuarantine.keyFor(ACTIVE)))
    }

    @Test
    fun cancellingWhileIdentityIsLoadingCannotCommitAKeyLater() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val waitingReader = WalletKeyIdentityReader { _, _ ->
            started.complete(Unit)
            release.await()
            identity
        }
        val attempt = async { service(waitingReader).restore(7, identity.scope, ImportMode.RawSeed, PRIVATE_KEY) }
        started.await()
        attempt.cancel()
        release.complete(Unit)
        attempt.join()
        assertFalse(storage.hasKey(ACTIVE))
    }

    @Test
    fun unsupportedWholeWalletModesCannotBeUsedToRebindOneKey() = runTest {
        for (mode in listOf(ImportMode.Google, ImportMode.Preinstalled)) {
            assertTrue(runCatching { service(reader).restore(7, identity.scope, mode, PRIVATE_KEY) }.isFailure)
            assertFalse(storage.hasKey(ACTIVE))
        }
    }

    private fun service(identities: WalletKeyIdentityReader) = WalletKeyRecovery(
        identities, verifier, WalletSecretRebindService(storage, identities, verifier, WalletSecretMutationJournalStore(storage))
    )

    private companion object {
        const val ACTIVE = "7:ETHEREUM_SECRETS"
        const val PHRASE = "test test test test test test test test test test test junk"
        const val PRIVATE_KEY = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    }
}
