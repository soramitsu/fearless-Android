package jp.co.soramitsu.common.data.storage.envelope

import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealth
import jp.co.soramitsu.common.data.storage.encrypt.readWalletSecretOrQuarantine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WalletSecretSealingWriteAndReadTest {

    private lateinit var harness: EnvelopeStorageHarness

    @Before
    fun setUp() {
        resetEnvelopeGlobals()
        WalletSecureStorageHealth.resetForTest()
        harness = EnvelopeStorageHarness()
    }

    @After
    fun tearDown() {
        resetEnvelopeGlobals()
        WalletSecureStorageHealth.resetForTest()
    }

    private fun unlockNewKeyring() {
        harness.authority.save(PIN)
        assertTrue(WalletSecretSession.isOpen())
    }

    @Test
    fun `a write while unlocked is sealed, with its backup wrap stored first`() {
        unlockNewKeyring()
        val value = SyntheticSecrets.substrateSr25519(1)
        harness.storage.putEncryptedString("5:SUBSTRATE_SECRETS", value)

        val inner = checkNotNull(harness.inner("5:SUBSTRATE_SECRETS"))
        assertTrue(WalletSecretEnvelope.isSealed(inner))
        assertEquals(value, WalletSecretEnvelopes.open(inner))
        // The snapshot is the outer plaintext, so compare-and-set stays byte exact.
        assertEquals(inner, harness.storage.getDecryptedStringSnapshot("5:SUBSTRATE_SECRETS")?.plaintext)
        val root = WalletSecretPayloads.rootKeys(WalletSecretEnvelopeScope.SUBSTRATE_ROOT, value).single()
        val canonical = checkNotNull(root.kind.canonical(root.privateKey))
        assertNotNull(harness.keyring.openWithBackup(listOf(canonical)))
    }

    @Test
    fun `a write while locked is stored as it is`() {
        unlockNewKeyring()
        WalletSecretSession.clear()
        val value = SyntheticSecrets.ton(2)
        harness.storage.putEncryptedString("5:TON_SECRETS", value)
        assertEquals(value, harness.inner("5:TON_SECRETS"))
    }

    @Test
    fun `a value longer than any reader accepts is written as it is`() {
        unlockNewKeyring()
        val huge = "0x" + "00".repeat(WalletSecretSealer.MAX_SEALABLE_PLAINTEXT_CHARS / 2)
        harness.storage.putEncryptedString("5:SUBSTRATE_SECRETS", huge)

        assertEquals(huge, harness.inner("5:SUBSTRATE_SECRETS"))
        assertFalse(harness.sealer.isSealable(huge))
        assertFalse(harness.sealer.isSealable(""))
        assertFalse(harness.sealer.isSealable(checkNotNull(harness.inner("5:SUBSTRATE_SECRETS")).let { "fse1:$it" }))
        assertTrue(harness.sealer.isSealable("0x00"))
    }

    @Test
    fun `values outside the allowlist are never sealed`() {
        unlockNewKeyring()
        val names = listOf(TonConnectStorageKeys.legacy("ab".repeat(32)), "wallet_secret_mutation_journal_v1", "some_other_value")
        names.forEach { harness.storage.putEncryptedString(it, "0x00112233445566778899") }
        names.forEach { assertEquals("0x00112233445566778899", harness.inner(it)) }
    }

    @Test
    fun `the kill switch stops new sealing but sealed values still open`() {
        unlockNewKeyring()
        harness.storage.putEncryptedString("5:ETHEREUM_SECRETS", SyntheticSecrets.ethereum(3))
        harness.preferences.putBoolean(WalletSecretSealingSwitch.REMOTE_VETO_KEY, false)
        harness.storage.putEncryptedString("6:ETHEREUM_SECRETS", SyntheticSecrets.ethereum(4))

        assertTrue(WalletSecretEnvelope.isSealed(harness.inner("5:ETHEREUM_SECRETS")))
        assertEquals(SyntheticSecrets.ethereum(4), harness.inner("6:ETHEREUM_SECRETS"))
        assertEquals(SyntheticSecrets.ethereum(3), WalletSecretEnvelopes.open(checkNotNull(harness.inner("5:ETHEREUM_SECRETS"))))
    }

    @Test
    fun `the compiled kill switch stops new sealing and keyring creation`() {
        val off = EnvelopeStorageHarness(compiledSealing = false)
        off.authority.save(PIN)
        assertFalse(off.keyring.exists())
        assertFalse(WalletSecretSession.isOpen())
        off.storage.putEncryptedString("5:SUBSTRATE_SECRETS", SyntheticSecrets.substrateSr25519(5))
        assertEquals(SyntheticSecrets.substrateSr25519(5), off.inner("5:SUBSTRATE_SECRETS"))
        assertEquals(jp.co.soramitsu.common.data.storage.encrypt.PinHealth.HEALTHY, off.authority.health())
    }

    @Test
    fun `a sealed store read opens while unlocked`() {
        unlockNewKeyring()
        val value = SyntheticSecrets.substrateSr25519(6)
        harness.storage.putEncryptedString("5:SUBSTRATE_SECRETS", value)

        @Suppress("DEPRECATION")
        val read = checkNotNull(SubstrateSecretStore(harness.storage).get(5))
        assertArrayEquals(SyntheticSecrets.bytes(106), read[SubstrateSecrets.SubstrateKeypair][jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema.PublicKey])
    }

    @Test
    fun `a sealed read while locked throws and never quarantines`() {
        unlockNewKeyring()
        val value = SyntheticSecrets.substrateSr25519(7)
        harness.storage.putEncryptedString("5:SUBSTRATE_SECRETS", value)
        val stored = harness.shared.values["5:SUBSTRATE_SECRETS"]
        WalletSecretSession.clear()

        @Suppress("DEPRECATION")
        assertThrows(WalletSecretLockedException::class.java) { SubstrateSecretStore(harness.storage).get(5) }
        assertThrows(WalletSecretLockedException::class.java) {
            harness.storage.readWalletSecretOrQuarantine<String>("5:SUBSTRATE_SECRETS") { error("corrupt") }
        }
        assertFalse(harness.storage.hasKey(WalletSecretQuarantine.keyFor("5:SUBSTRATE_SECRETS")))
        assertEquals(stored, harness.shared.values["5:SUBSTRATE_SECRETS"])
    }

    @Test
    fun `a sealed chain-account read while locked keeps the value`() {
        unlockNewKeyring()
        val accountId = SyntheticSecrets.bytes(9, 20)
        val key = "5:${org.bouncycastle.util.encoders.Hex.toHexString(accountId)}:ACCESS_SECRETS"
        harness.storage.putEncryptedString(key, SyntheticSecrets.chainAccount(9))
        val stored = harness.shared.values[key]
        WalletSecretSession.clear()

        assertThrows(WalletSecretLockedException::class.java) {
            runBlocking { SecretStoreV2(harness.storage).getChainAccountSecrets(5, accountId) }
        }
        assertFalse(harness.storage.hasKey(WalletSecretQuarantine.keyFor(key)))
        assertEquals(stored, harness.shared.values[key])
    }

    @Test
    fun `a sealed value whose inner payload is corrupt is quarantined only with SK, by exact move`() {
        unlockNewKeyring()
        harness.storage.putEncryptedString("5:TON_SECRETS", "0x0011")
        val stored = harness.shared.values["5:TON_SECRETS"]
        assertTrue(WalletSecretEnvelope.isSealed(harness.inner("5:TON_SECRETS")))

        assertThrows(WalletRecoveryRequiredException::class.java) {
            harness.storage.readWalletSecretOrQuarantine("5:TON_SECRETS") { error("corrupt payload") }
        }
        assertEquals(stored, harness.shared.values[WalletSecretQuarantine.keyFor("5:TON_SECRETS")])
    }

    private companion object {
        const val PIN = "135790"
    }
}
