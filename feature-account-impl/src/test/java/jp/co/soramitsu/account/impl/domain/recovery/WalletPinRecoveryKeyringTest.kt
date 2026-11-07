package jp.co.soramitsu.account.impl.domain.recovery

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.security.SecureRandom
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.data.repository.WalletKeyIdentityReader
import jp.co.soramitsu.account.impl.data.repository.WalletSecretRebindService
import jp.co.soramitsu.account.impl.presentation.recovery.RecoveryTarget
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletPinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.data.storage.envelope.PinVerification
import jp.co.soramitsu.common.data.storage.envelope.WalletPinAuthority
import jp.co.soramitsu.common.data.storage.envelope.WalletPinKdfParameters
import jp.co.soramitsu.common.data.storage.envelope.WalletRootKeyKind
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretEnvelope
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretEnvelopeScope
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretKeyring
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretPayloads
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSealer
import jp.co.soramitsu.common.data.storage.envelope.WalletSecretSession
import jp.co.soramitsu.common.data.storage.pin.WalletSessionLock
import jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import jp.co.soramitsu.testshared.InMemoryKeyringHardwareKey
import jp.co.soramitsu.testshared.InMemoryKeyringPreferences
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.bouncycastle.util.encoders.Hex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** "Forgot PIN?" and recovery once the PIN lives in the wallet keyring (4.3.2). */
class WalletPinRecoveryKeyringTest {
    private val storage = HashMapEncryptedPreferences()
    private val keyringPreferences = InMemoryKeyringPreferences()
    private val hardware = InMemoryKeyringHardwareKey()
    private val keyring = WalletSecretKeyring(keyringPreferences, hardware, SecureRandom(), FAST_KDF)
    private val authority = WalletPinAuthority(storage, keyring, WalletSecretSealer(keyringPreferences, keyring, SecureRandom(), true))
    private val preferences = mockk<Preferences>(relaxed = true)
    private val accounts = mockk<AccountRepository> {
        coEvery { isBiometricEnabled() } returns true
        every { lightMetaAccountsFlow() } returns flowOf(emptyList())
    }
    private val publicKey = EthereumKeypairFactory.createWithPrivateKey(Hex.decode(PRIVATE_KEY)).publicKey
    private val identity = WalletKeyIdentity.Ethereum(publicKey, publicKey.ethereumAddressFromPublicKey())
    private val reader = WalletKeyIdentityReader { id, scope ->
        check(id == 7L && scope == WalletSecretScope.Ethereum)
        identity
    }
    private val target = RecoveryTarget.from(7, WalletSecretScope.Ethereum)
    private val verifier = WalletIdentityVerifier(JsonSeedDecoder(Gson()))
    private val service = WalletPinRecovery(
        authority, keyring, preferences, accounts, reader,
        WalletKeyRecovery(reader, verifier, WalletSecretRebindService(storage, reader, verifier, WalletSecretMutationJournalStore(storage))),
        mockk(relaxed = true),
        { 1_000L }
    )
    private lateinit var sealedValue: String

    @Before
    fun setUp() {
        WalletSessionLock.lock()
        WalletPinHealth.resetForTest()
        // A 4.3.2 wallet: the Ethereum root sealed under SK, with its backup wrap; then the app locks.
        val created = keyring.create(OLD_PIN)
        val encoded = verifier.verifySeed(identity, PRIVATE_KEY).encoded
        val root = WalletSecretPayloads.rootKeys(WalletSecretEnvelopeScope.ETHEREUM_ROOT, encoded).single()
        keyring.addBackupWrap(created, checkNotNull(WalletRootKeyKind.SECP256K1.canonical(root.privateKey)))
        sealedValue = WalletSecretEnvelope.seal(
            created.key,
            WalletSecretEnvelope.Header(created.keyId, WalletSecretEnvelopeScope.ETHEREUM_ROOT, "7:ETHEREUM_SECRETS", publicKey),
            encoded,
            SecureRandom()
        )
        storage.putEncryptedString("7:ETHEREUM_SECRETS", sealedValue)
        created.close()
        WalletSessionLock.lock()
    }

    @After
    fun tearDown() {
        WalletSessionLock.lock()
        WalletPinHealth.resetForTest()
        WalletViewOnlyAccess.pinResetCompleted()
    }

    @Test
    fun `a readable PIN starts recovery only from Forgot PIN`() = runTest {
        assertEquals(PinHealth.HEALTHY, authority.health())
        assertTrue(runCatching { service.begin() }.isFailure)
        assertTrue(service.isActive(service.begin(forgotten = true)))
    }

    @Test
    fun `a forgotten PIN is replaced after a backup proof opens SK, and no value is resealed`() = runTest {
        val id = service.begin(forgotten = true)
        service.verifyWallet(id, target, ImportMode.RawSeed, PRIVATE_KEY)
        assertTrue(service.isAuthorized(id))
        // The sealed key was opened and judged usable, so it stays byte for byte.
        assertEquals(sealedValue, storage.getDecryptedString("7:ETHEREUM_SECRETS"))
        assertFalse(WalletSecretSession.isOpen())

        service.saveNewPin(id, NEW_PIN)

        assertEquals(WalletSecretSession.Origin.BACKUP_PROOF, WalletSecretSession.origin())
        WalletSessionLock.lock()
        assertEquals(PinVerification.WRONG, authority.verify(OLD_PIN))
        assertEquals(PinVerification.CORRECT, authority.verify(NEW_PIN))
        assertEquals(sealedValue, storage.getDecryptedString("7:ETHEREUM_SECRETS"))
    }

    @Test
    fun `a lost hardware key is recovered by the backup proof`() = runTest {
        hardware.loseAll()
        assertEquals(PinHealth.UNREADABLE, authority.health())
        val id = service.begin()
        service.verifyWallet(id, target, ImportMode.RawSeed, PRIVATE_KEY)
        service.saveNewPin(id, NEW_PIN)
        WalletSessionLock.lock()
        assertEquals(PinHealth.HEALTHY, authority.health())
        assertEquals(PinVerification.CORRECT, authority.verify(NEW_PIN))
    }

    @Test
    fun `a backup whose root has no wrap never authorizes a new PIN`() = runTest {
        val other = "01".repeat(32)
        val otherPublic = EthereumKeypairFactory.createWithPrivateKey(Hex.decode(other)).publicKey
        val otherIdentity = WalletKeyIdentity.Ethereum(otherPublic, otherPublic.ethereumAddressFromPublicKey())
        val otherReader = WalletKeyIdentityReader { _, _ -> otherIdentity }
        val otherService = WalletPinRecovery(
            authority, keyring, preferences, accounts, otherReader,
            WalletKeyRecovery(otherReader, verifier, WalletSecretRebindService(storage, otherReader, verifier, WalletSecretMutationJournalStore(storage))),
            mockk(relaxed = true),
            { 1_000L }
        )
        val id = otherService.begin(forgotten = true)
        assertThrows(WalletPinRecoveryKeyUnavailableException::class.java) {
            kotlinx.coroutines.runBlocking { otherService.verifyWallet(id, target, ImportMode.RawSeed, other) }
        }
        assertFalse(otherService.isAuthorized(id))
        assertTrue(runCatching { otherService.saveNewPin(id, NEW_PIN) }.isFailure)
        assertEquals(PinVerification.CORRECT, authority.verify(OLD_PIN))
    }

    @Test
    fun `strong biometrics authorize only with the vault PIN that opens the PIN wrap`() = runTest {
        val id = service.begin(forgotten = true)
        assertTrue(runCatching { service.strongBiometricSucceeded(id) }.isFailure)
        assertTrue(runCatching { service.strongBiometricSucceeded(id, "000000") }.isFailure)
        assertFalse(service.isAuthorized(id))
        service.strongBiometricSucceeded(id, OLD_PIN)
        assertTrue(service.isAuthorized(id))
        service.saveNewPin(id, NEW_PIN)
        WalletSessionLock.lock()
        assertEquals(PinVerification.CORRECT, authority.verify(NEW_PIN))
    }

    @Test
    fun `a PIN changed elsewhere invalidates an open recovery`() = runTest {
        val id = service.begin(forgotten = true)
        service.verifyWallet(id, target, ImportMode.RawSeed, PRIVATE_KEY)
        assertEquals(PinVerification.CORRECT, authority.verify(OLD_PIN))
        authority.save("111111")
        WalletSessionLock.lock()
        assertTrue(runCatching { service.saveNewPin(id, NEW_PIN) }.isFailure)
        assertEquals(PinVerification.CORRECT, authority.verify("111111"))
    }

    private companion object {
        const val OLD_PIN = "271828"
        const val NEW_PIN = "314159"
        const val PRIVATE_KEY = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
        val FAST_KDF = WalletPinKdfParameters(memoryKiB = 64, iterations = 1, parallelism = 1)
    }
}
