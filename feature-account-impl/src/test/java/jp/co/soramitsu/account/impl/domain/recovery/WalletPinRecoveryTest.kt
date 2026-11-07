package jp.co.soramitsu.account.impl.domain.recovery

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.data.repository.WalletKeyIdentityReader
import jp.co.soramitsu.account.impl.data.repository.WalletSecretRebindService
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.data.storage.envelope.WalletKeyringHardwareKey
import jp.co.soramitsu.common.data.storage.envelope.WalletPinAuthority
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretKeyring
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSealer
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.bouncycastle.util.encoders.Hex
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class WalletPinRecoveryTest {
    private val storage = HashMapEncryptedPreferences().also { it.putEncryptedString("pin_code", OLD_PIN) }
    private val preferences = mockk<Preferences>(relaxed = true)
    private val accounts = mockk<AccountRepository> {
        coEvery { isBiometricEnabled() } returns true
        every { lightMetaAccountsFlow() } returns flowOf(emptyList())
    }
    private var clock = 1_000L
    private val publicKey = EthereumKeypairFactory.createWithPrivateKey(Hex.decode(PRIVATE_KEY)).publicKey
    private var identity = WalletKeyIdentity.Ethereum(publicKey, publicKey.ethereumAddressFromPublicKey())
    private val reader = WalletKeyIdentityReader { id, scope ->
        check(id == 7L && scope == WalletSecretScope.Ethereum)
        identity
    }
    private val target = RecoveryTarget.from(7, WalletSecretScope.Ethereum)
    private val verifier = WalletIdentityVerifier(JsonSeedDecoder(Gson()))
    // Sealing compiled off: no keyring exists, so these cover the legacy (4.3.1) PIN record.
    private val keyringPreferences = jp.co.soramitsu.testshared.InMemoryKeyringPreferences()
    private val keyring = WalletSecretKeyring(keyringPreferences, UnavailableHardwareKey, java.security.SecureRandom())
    private fun authorityFor(encrypted: jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences) =
        WalletPinAuthority(encrypted, keyring, WalletSecretSealer(keyringPreferences, keyring, java.security.SecureRandom(), compiledSwitch = false))
    private fun service(
        keyRecovery: WalletKeyRecovery? = null,
        identities: WalletKeyIdentityReader = reader,
        vault: jp.co.soramitsu.account.impl.data.biometric.BiometricPinVault = FakeVault(enrolled = false)
    ) = WalletPinRecovery(
        authorityFor(storage), keyring, preferences, accounts, identities,
        keyRecovery ?: WalletKeyRecovery(identities, verifier,
            WalletSecretRebindService(storage, identities, verifier, WalletSecretMutationJournalStore(storage))), vault, { clock })

    @After fun cleanup() { WalletViewOnlyAccess.pinResetCompleted() }

    @Test fun unreadablePinBeginsUnprivilegedAndCannotBeResetBeforeProof() = runTest {
        val service = service()
        val id = service.begin()
        assertFalse(service.isAuthorized(id))
        assertTrue(service.isActive(id))
        assertTrue(service.choices(id).isEmpty())
        assertTrue(runCatching { service.saveNewPin(id, "123456") }.isFailure)
        assertEquals(OLD_PIN, storage.getDecryptedString("pin_code"))
    }

    @Test fun healthyAndAbsentPinsCannotAcquireARecoveryGrant() = runTest {
        storage.putEncryptedString("pin_code", "123456")
        assertTrue(runCatching { service().begin() }.isFailure)
        storage.removeKey("pin_code")
        assertTrue(runCatching { service().begin() }.isFailure)
    }

    @Test fun enrolledStrongBiometricsAllowOneResetAndRetainTheOriginalRecordExactly() = runTest {
        val service = service()
        val id = service.begin()
        service.strongBiometricSucceeded(id)
        assertTrue(service.isAuthorized(id))
        WalletViewOnlyAccess.enter()
        service.saveNewPin(id, "123456")
        assertEquals("123456", storage.getDecryptedString("pin_code"))
        assertEquals(OLD_PIN, storage.getDecryptedString(WalletSecretArchive.keyFor("pin_code", 0)))
        assertFalse(service.isAuthorized(id))
        assertFalse(WalletViewOnlyAccess.isEnabled())
        assertTrue(runCatching { service.saveNewPin(id, "654321") }.isFailure)
        verify {
            preferences.removeField("prefs_pin_failed_attempts")
            preferences.removeField("prefs_pin_delay_start_since_boot_ms")
            preferences.removeField("prefs_pin_delay_start_wall_clock_ms")
            preferences.removeField("prefs_pin_lock_until_ms")
        }
    }

    @Test fun previouslyDisabledBiometricsCannotAuthorizeReset() = runTest {
        coEvery { accounts.isBiometricEnabled() } returns false
        val service = service()
        val id = service.begin()
        assertTrue(runCatching { service.strongBiometricSucceeded(id) }.isFailure)
        assertFalse(service.isAuthorized(id))
    }

    @Test fun canceledAndReplacedAndExpiredGrantsCannotAuthorizeOrWrite() = runTest {
        val service = service()
        val old = service.begin()
        val current = service.begin()
        assertTrue(runCatching { service.strongBiometricSucceeded(old) }.isFailure)
        service.cancel(old)
        service.strongBiometricSucceeded(current)
        assertTrue(service.isAuthorized(current))
        clock += 600_000L
        assertFalse(service.isActive(current))
        assertFalse(service.isAuthorized(current))
        assertTrue(runCatching { service.saveNewPin(current, "123456") }.isFailure)
        val fresh = service.begin()
        service.cancel(fresh)
        assertTrue(runCatching { service.choices(fresh) }.isFailure)
        assertEquals(OLD_PIN, storage.getDecryptedString("pin_code"))
    }

    @Test fun aChangedPinInvalidatesProofAndCannotBeOverwritten() = runTest {
        val service = service()
        val id = service.begin()
        service.strongBiometricSucceeded(id)
        storage.putEncryptedString("pin_code", "654321")
        assertTrue(runCatching { service.saveNewPin(id, "123456") }.isFailure)
        assertTrue(runCatching { service.strongBiometricSucceeded(id) }.isFailure)
        assertEquals("654321", storage.getDecryptedString("pin_code"))
        assertFalse(storage.hasKey(WalletSecretArchive.keyFor("pin_code", 0)))
    }

    @Test fun realMatchingWalletBackupAuthorizesResetWithoutRetainingItInTheGrant() = runTest {
        val service = service()
        val id = service.begin()
        service.verifyWallet(id, target, ImportMode.RawSeed, PRIVATE_KEY)
        assertTrue(service.isAuthorized(id))
        service.saveNewPin(id, "123456")
        assertEquals("123456", storage.getDecryptedString("pin_code"))
        assertTrue(storage.hasKey("7:ETHEREUM_SECRETS"))
    }

    @Test fun wrongWalletBackupNeverAuthorizesReset() = runTest {
        val service = service()
        val id = service.begin()
        assertTrue(runCatching { service.verifyWallet(id, target, ImportMode.RawSeed, "01".repeat(32)) }.isFailure)
        assertFalse(service.isAuthorized(id))
        assertEquals(OLD_PIN, storage.getDecryptedString("pin_code"))
        assertFalse(storage.hasKey("7:ETHEREUM_SECRETS"))
    }

    @Test fun replacedPublicIdentityInvalidatesAnOtherwiseMatchingBackupProof() = runTest {
        val service = service()
        val id = service.begin()
        service.verifyWallet(id, target, ImportMode.RawSeed, PRIVATE_KEY)
        val different = EthereumKeypairFactory.createWithPrivateKey(Hex.decode("01".repeat(32))).publicKey
        identity = WalletKeyIdentity.Ethereum(different, different.ethereumAddressFromPublicKey())
        assertTrue(runCatching { service.saveNewPin(id, "123456") }.isFailure)
        assertEquals(OLD_PIN, storage.getDecryptedString("pin_code"))
    }

    @Test fun returningOldKeyCannotUseAStaleUnreadablePinGrantToReplaceANowHealthyPin() = runTest {
        var readable = false
        val effectiveStorage = object : jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences by storage {
            override fun getPinHealth() = if (readable) jp.co.soramitsu.common.data.storage.encrypt.PinHealth.HEALTHY
                else jp.co.soramitsu.common.data.storage.encrypt.PinHealth.UNREADABLE
        }
        val service = WalletPinRecovery(
            authorityFor(effectiveStorage), keyring, preferences, accounts, reader, mockk(), FakeVault(enrolled = false), { clock }
        )
        val id = service.begin()
        service.strongBiometricSucceeded(id)
        readable = true // Original typed ciphertext remains byte-identical.
        assertTrue(runCatching { service.saveNewPin(id, "123456") }.isFailure)
        assertEquals(OLD_PIN, storage.getDecryptedString("pin_code"))
        assertFalse(storage.hasKey(WalletSecretArchive.keyFor("pin_code", 0)))
    }

    @Test fun choicesSkipMalformedIdentitiesWithoutHidingAnIndependentUsableKey() = runTest {
        val wallet = mockk<jp.co.soramitsu.account.api.domain.model.LightMetaAccount> {
            every { id } returns 7L
            every { name } returns "Public fixture"
            every { universalWalletChainAccounts } returns emptyMap()
        }
        every { accounts.lightMetaAccountsFlow() } returns flowOf(listOf(wallet))
        val service = service()
        val id = service.begin()
        assertEquals(listOf(WalletPinRecovery.Choice("Public fixture", target)), service.choices(id))
    }

    @Test fun cancellationWhileVerifyingCannotAuthorizeALateResult() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val recovery = mockk<WalletKeyRecovery> {
            coEvery { prove(any(), any(), any(), any(), any(), any()) } returns
                VerifiedWalletSecret(WalletSecretScope.Ethereum, "0x00")
            coEvery { rebind(any(), any(), any()) } coAnswers {
                entered.complete(Unit)
                release.await()
                WalletSecretRebindService.Result.ALREADY_USABLE
            }
        }
        val service = service(recovery)
        val id = service.begin()
        val job = async { service.verifyWallet(id, target, ImportMode.RawSeed, PRIVATE_KEY) }
        entered.await()
        job.cancel()
        release.complete(Unit)
        job.join()
        assertFalse(service.isAuthorized(id))
        assertEquals(OLD_PIN, storage.getDecryptedString("pin_code"))
    }

    @Test fun anEnrolledVaultWithoutAKeyringAuthorizesOnlyWithItsPin() = runTest {
        val service = service(vault = FakeVault(enrolled = true))
        val id = service.begin()
        assertTrue(runCatching { service.strongBiometricSucceeded(id) }.isFailure)
        assertTrue(runCatching { service.strongBiometricSucceeded(id, "not a PIN") }.isFailure)
        assertFalse(service.isAuthorized(id))
        // The stored PIN is unreadable, so the vault's PIN is the proof.
        service.strongBiometricSucceeded(id, "123456")
        assertTrue(service.isAuthorized(id))
    }

    @Test fun anEnrolledVaultMustHoldTheReadablePinItRecovers() = runTest {
        storage.putEncryptedString("pin_code", "135790")
        val service = service(vault = FakeVault(enrolled = true))
        val id = service.begin(forgotten = true)
        assertTrue(runCatching { service.strongBiometricSucceeded(id, "246802") }.isFailure)
        assertFalse(service.isAuthorized(id))
        service.strongBiometricSucceeded(id, "135790")
        assertTrue(service.isAuthorized(id))
    }

    private class FakeVault(private val enrolled: Boolean) : jp.co.soramitsu.account.impl.data.biometric.BiometricPinVault {
        override fun isEnrolled() = enrolled
        override fun enroll(pin: String) = Unit
        override fun prepareUnlock() = jp.co.soramitsu.account.impl.data.biometric.BiometricPinVault.UnlockCipher.Unavailable
        override fun decryptPin(authenticated: javax.crypto.Cipher): String? = null
        override fun clear() = Unit
        override fun wasInvalidated() = false
        override fun acknowledgeInvalidation() = Unit
    }

    /** No keyring is ever created here, so the hardware key is never reached. */
    private object UnavailableHardwareKey : WalletKeyringHardwareKey {
        override fun create(): String = error("No keyring in legacy recovery tests")
        override fun hmac(alias: String, data: ByteArray): ByteArray = error("No keyring in legacy recovery tests")
        override fun exists(alias: String): Boolean = false
    }

    private companion object {
        const val OLD_PIN = "opaque-original-pin-record"
        const val PRIVATE_KEY = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    }
}
