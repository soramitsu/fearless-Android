package jp.co.soramitsu.common.data.storage.envelope

import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.EmergencyRecoveryAccessResult
import jp.co.soramitsu.common.data.secrets.WalletEmergencyRecoveryReader
import jp.co.soramitsu.common.data.secrets.WalletEmergencyRecoverySession
import jp.co.soramitsu.common.data.secrets.v1.SourceInternal
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.storage.encrypt.PinHealth
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealth
import jp.co.soramitsu.common.data.storage.pin.PinAttemptClock
import jp.co.soramitsu.common.data.storage.pin.WalletEmergencyAuthenticator
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.runBlocking
import org.bouncycastle.util.encoders.Hex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Upgrade from 4.3.1 storage: the legacy `pin_code`, every wallet-secret namespace stored without
 * an envelope, and values that must never be sealed. Synthetic vectors only.
 */
class WalletSecretUpgradeMigrationTest {

    @Before
    fun setUp() {
        resetEnvelopeGlobals()
        WalletSecureStorageHealth.resetForTest()
    }

    @After
    fun tearDown() {
        resetEnvelopeGlobals()
        WalletSecureStorageHealth.resetForTest()
    }

    private object V04SigningData : Schema<V04SigningData>() {
        val PrivateKey by byteArray()
        val PublicKey by byteArray()
        val Nonce by byteArray().optional()
    }

    private val chainAccountKey = "8:${Hex.toHexString(SyntheticSecrets.bytes(80, 20))}:ACCESS_SECRETS"

    /** Every allowlisted namespace as 4.3.1 stored it, with the original plaintext. */
    private val legacySecrets: Map<String, String> = linkedMapOf(
        "7:SUBSTRATE_SECRETS" to SyntheticSecrets.substrateSr25519(1),
        "7:ETHEREUM_SECRETS" to SyntheticSecrets.ethereum(2),
        "7:TON_SECRETS" to SyntheticSecrets.ton(3),
        "8:ACCESS_SECRETS" to MetaAccountSecrets(
            substrateKeyPair = Sr25519Keypair(
                privateKey = SyntheticSecrets.bytes(4),
                publicKey = SyntheticSecrets.bytes(104),
                nonce = SyntheticSecrets.bytes(204)
            ),
            entropy = SyntheticSecrets.bytes(304, 16),
            ethereumKeypair = Keypair(SyntheticSecrets.bytes(105, 64), SyntheticSecrets.bytes(5)),
            tonKeypair = Keypair(SyntheticSecrets.bytes(106), SyntheticSecrets.bytes(6))
        ).toHexString(),
        chainAccountKey to SyntheticSecrets.chainAccount(8),
        "security_source_5Synthetic1" to SourceInternal.toHexString(SourceInternal { source ->
            source[SourceInternal.Type] = "MNEMONIC"
            source[SourceInternal.PrivateKey] = SyntheticSecrets.bytes(9)
            source[SourceInternal.PublicKey] = SyntheticSecrets.bytes(109)
            source[SourceInternal.Nonce] = SyntheticSecrets.bytes(209)
            source[SourceInternal.Seed] = SyntheticSecrets.bytes(309)
            source[SourceInternal.Mnemonic] = "synthetic words"
            source[SourceInternal.DerivationPath] = null
        }),
        "private_5Synthetic2" to V04SigningData { data ->
            data[V04SigningData.PrivateKey] = SyntheticSecrets.bytes(10)
            data[V04SigningData.PublicKey] = SyntheticSecrets.bytes(110)
            data[V04SigningData.Nonce] = SyntheticSecrets.bytes(210)
        }.toHexString(),
        "seed_5Synthetic2" to "0x" + Hex.toHexString(SyntheticSecrets.bytes(11)),
        WalletSecretQuarantine.keyFor("9:SUBSTRATE_SECRETS") to SyntheticSecrets.substrateSr25519(12),
        WalletSecretArchive.keyFor("10:SUBSTRATE_SECRETS") to SyntheticSecrets.substrateSr25519(13),
        WalletSecretArchive.keyFor("10:ETHEREUM_SECRETS", 2) to SyntheticSecrets.ethereum(14)
    )

    /** Encrypted values that are never wallet secrets. */
    private val neverSealed: Map<String, String> = linkedMapOf(
        TonConnectStorageKeys.legacy("cd".repeat(32)) to "0x" + Hex.toHexString(SyntheticSecrets.bytes(15, 96)),
        WalletPublicIdentityRecovery.keyFor(7, "7:SUBSTRATE_SECRETS") to WalletPublicIdentityRecovery.MARKER_VALUE
    )

    private fun legacyStorage(): MapSharedPreferences {
        val shared = MapSharedPreferences()
        legacySecrets.forEach { (key, value) -> shared.values[key] = "v2:test:$value" }
        neverSealed.forEach { (key, value) -> shared.values[key] = "v2:test:$value" }
        shared.values["pin_code"] = "v2:test:$PIN"
        shared.values[WalletSecretArchive.keyFor("pin_code")] = "v2:test:12x"
        // A value the device key cannot read stays exactly as it is.
        shared.values["11:SUBSTRATE_SECRETS"] = "not-a-ciphertext"
        shared.values["auth_type"] = "PINCODE"
        return shared
    }

    private fun assertEveryOriginalOpens(harness: EnvelopeStorageHarness) {
        for ((key, original) in legacySecrets) {
            val inner = checkNotNull(harness.inner(key)) { "$key disappeared" }
            assertTrue("$key is sealed", WalletSecretEnvelope.isSealed(inner))
            assertEquals("$key opens to its original", original, WalletSecretEnvelopes.open(inner))
        }
        neverSealed.forEach { (key, value) -> assertEquals(key, value, harness.inner(key)) }
        assertEquals("not-a-ciphertext", harness.shared.values["11:SUBSTRATE_SECRETS"])
        assertEquals("PINCODE", harness.shared.values["auth_type"])
    }

    @Test
    fun `the first unlock on 4_3_2 seals every wallet secret and retires the legacy PIN`() = runBlocking {
        val harness = EnvelopeStorageHarness(shared = legacyStorage())
        assertEquals(PinHealth.HEALTHY, harness.authority.health())
        assertEquals(PinVerification.WRONG, harness.authority.verify("000000"))
        assertFalse(harness.keyring.exists())

        assertEquals(PinVerification.CORRECT, harness.authority.verify(PIN))
        assertTrue(harness.keyring.exists())
        assertEquals(WalletSecretSession.Origin.NEW_KEYRING, WalletSecretSession.origin())

        val report = harness.migration.run()
        assertEquals(legacySecrets.size, report.sealed)
        assertTrue(report.legacyPinRetired)
        assertEveryOriginalOpens(harness)
        assertFalse(harness.shared.values.containsKey("pin_code"))
        assertFalse(harness.shared.values.containsKey(WalletSecretArchive.keyFor("pin_code")))

        // The PIN wrap is now the only verifier.
        WalletSecretSession.clear()
        assertEquals(PinHealth.HEALTHY, harness.authority.health())
        assertEquals(PinVerification.WRONG, harness.authority.verify("000000"))
        assertFalse(WalletSecretSession.isOpen())
        assertEquals(PinVerification.CORRECT, harness.authority.verify(PIN))
        assertEquals(WalletSecretSession.Origin.PIN_WRAP, WalletSecretSession.origin())

        // Running again changes nothing.
        val before = harness.shared.values.toMap()
        val again = harness.migration.run()
        assertEquals(0, again.sealed)
        assertEquals(legacySecrets.size, again.alreadySealed)
        assertEquals(before, harness.shared.values.toMap())
    }

    @Test
    fun `every sealed root has a backup wrap that opens SK`() = runBlocking {
        val harness = EnvelopeStorageHarness(shared = legacyStorage())
        harness.authority.verify(PIN)
        harness.migration.run()
        val sk = checkNotNull(harness.authority.sessionKey())
        val activeRoots = legacySecrets.keys.filter { WalletSecretSealPolicy.classify(it)?.location == WalletSecretSealPolicy.Location.ACTIVE }
        var proved = 0
        for (key in activeRoots) {
            val target = checkNotNull(WalletSecretSealPolicy.classify(key))
            for (root in WalletSecretPayloads.rootKeys(target.scope, checkNotNull(legacySecrets[key]))) {
                assertNotNull(harness.keyring.openWithBackup(listOf(checkNotNull(root.kind.canonical(root.privateKey)))))
                proved++
            }
        }
        // 3 V3 roots, 3 keys in the ACCESS payload, the chain account, V1 and V0.4 signing data.
        assertEquals(9, proved)
        sk.close()
    }

    @Test
    fun `a migration interrupted after any write resumes without losing a value`() {
        for (applied in listOf(true, false)) {
            var commit = 1
            while (true) {
                resetEnvelopeGlobals()
                WalletSecureStorageHealth.resetForTest()
                val shared = legacyStorage()
                shared.commitsBeforeDeath = commit - 1
                shared.fatalCommitApplied = applied
                val first = EnvelopeStorageHarness(shared = shared)
                runCatching {
                    first.authority.verify(PIN)
                    runBlocking { first.migration.run() }
                }
                val died = shared.dead
                // A new process: storage (and with it the PIN-wrap factor) survives, nothing in memory does.
                resetEnvelopeGlobals()
                WalletSecureStorageHealth.resetForTest()
                shared.commitsBeforeDeath = null
                val second = EnvelopeStorageHarness(shared = shared)
                assertEquals("commit $commit", PinHealth.HEALTHY, second.authority.health())
                assertEquals("commit $commit", PinVerification.CORRECT, second.authority.verify(PIN))
                val report = runBlocking { second.migration.run() }
                assertTrue("commit $commit", report.legacyPinRetired || !second.shared.values.containsKey("pin_code"))
                assertEveryOriginalOpens(second)
                if (!died) break
                commit++
            }
            assertTrue("the first process wrote at least 10 times", commit > 10)
        }
    }

    @Test
    fun `a sealed value that does not read back is put back as it was and sealing stops`() = runBlocking {
        val harness = EnvelopeStorageHarness(shared = legacyStorage())
        assertEquals(PinVerification.CORRECT, harness.authority.verify(PIN))
        // The store hands back another value for the sealed TON root from its second read on.
        val reads = mutableMapOf<String, Int>()
        org.mockito.kotlin.whenever(harness.crypto.decrypt(org.mockito.kotlin.any())).thenAnswer { call ->
            val ciphertext = call.getArgument<String>(0)
            val inner = ciphertext.takeIf { it.startsWith("v2:test:") }?.removePrefix("v2:test:").orEmpty()
            val count = checkNotNull(reads.merge(ciphertext, 1, Int::plus))
            val tonRoot = WalletSecretEnvelope.isSealed(inner) &&
                WalletSecretEnvelope.readHeader(inner).keyName == "7:TON_SECRETS"
            if (tonRoot && count >= 2) "fse1:changed" else inner
        }

        val report = harness.migration.run()

        assertTrue(report.stoppedOnMismatch)
        assertFalse(report.legacyPinRetired)
        assertEquals(legacySecrets.getValue("7:TON_SECRETS"), harness.inner("7:TON_SECRETS"))
        assertTrue(harness.shared.values.containsKey("pin_code"))

        // A store that reads back what it was given: the next unlock finishes the job.
        org.mockito.kotlin.whenever(harness.crypto.decrypt(org.mockito.kotlin.any())).thenAnswer { call ->
            call.getArgument<String>(0).takeIf { it.startsWith("v2:test:") }?.removePrefix("v2:test:").orEmpty()
        }
        val again = harness.migration.run()
        assertTrue(again.legacyPinRetired)
        assertEveryOriginalOpens(harness)
    }

    @Test
    fun `keys owned by a pending mutation journal are left to that journal`() = runBlocking {
        val harness = EnvelopeStorageHarness(shared = legacyStorage())
        // A creation staged by 4.3.1 that its replay has not finished yet.
        harness.journal.stage(
            jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.Journal(
                operationId = "123e4567-e89b-42d3-a456-426614174000",
                operation = jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.Operation.CREATE,
                metaId = 77,
                beforeImage = null,
                afterImage = jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.PublicAfterImage(
                    name = "Pending", substratePublicKeyHex = Hex.toHexString(SyntheticSecrets.bytes(177)),
                    substrateAccountIdHex = Hex.toHexString(SyntheticSecrets.bytes(277)),
                    substrateCryptoType = jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.SubstrateCryptoType.SR25519,
                    ethereumPublicKeyHex = null, ethereumAddressHex = null, tonPublicKeyHex = null, isSelected = false,
                    position = 3, isBackedUp = false, googleBackupAddress = null, initialized = true
                ),
                selectedMetaIdAfterDelete = null,
                chainAccountIdsHex = emptySet(),
                secretKeysToPut = setOf("77:SUBSTRATE_SECRETS"),
                secretKeysToRemove = emptySet()
            ),
            mapOf("77:SUBSTRATE_SECRETS" to SyntheticSecrets.substrateSr25519(77))
        )
        harness.authority.verify(PIN)
        val report = harness.migration.run()

        assertEquals(SyntheticSecrets.substrateSr25519(77), harness.inner("77:SUBSTRATE_SECRETS"))
        assertEquals(legacySecrets.size, report.sealed)
        assertFalse("the PIN stays until the journal is reconciled", report.legacyPinRetired)
        assertTrue(harness.shared.values.containsKey("pin_code"))
    }

    @Test
    fun `an unreadable mutation journal stops all sealing`() = runBlocking {
        val shared = legacyStorage()
        shared.values["wallet_secret_mutation_journal_v1"] = "v2:test:{\"version\":1}"
        val harness = EnvelopeStorageHarness(shared = shared)
        harness.authority.verify(PIN)
        val report = harness.migration.run()
        assertEquals(0, report.sealed)
        assertFalse(report.legacyPinRetired)
        assertTrue(shared.values.containsKey("pin_code"))
        legacySecrets.forEach { (key, value) -> assertEquals(value, harness.inner(key)) }
    }

    @Test
    fun `a PIN change rewraps SK without touching any sealed value`() = runBlocking {
        val harness = EnvelopeStorageHarness(shared = legacyStorage())
        harness.authority.verify(PIN)
        harness.migration.run()
        val sealedValues = legacySecrets.keys.associateWith { harness.shared.values[it] }

        harness.authority.save("246802")

        legacySecrets.keys.forEach { assertEquals(it, sealedValues[it], harness.shared.values[it]) }
        WalletSecretSession.clear()
        assertEquals(PinVerification.WRONG, harness.authority.verify(PIN))
        assertEquals(PinVerification.CORRECT, harness.authority.verify("246802"))
        assertEveryOriginalOpens(harness)
    }

    @Test
    fun `emergency export reveals every secret with the PIN and leaves the session locked`() = runBlocking {
        val harness = EnvelopeStorageHarness(shared = legacyStorage())
        harness.authority.verify(PIN)
        harness.migration.run()
        WalletSecretSession.clear()

        val clock = PinAttemptClock(wallClock = { 1_800_000_000_000L }, sinceBoot = { 1_000L })
        val authenticator = WalletEmergencyAuthenticator(harness.preferences, harness.authority, clock)
        val reader = WalletEmergencyRecoveryReader(harness.storage)
        val session = WalletEmergencyRecoverySession(authenticator, reader)

        assertTrue(session.unlock("000000") is EmergencyRecoveryAccessResult.Refused)
        val page = (session.unlock(PIN) as EmergencyRecoveryAccessResult.Page).page
        assertFalse(WalletSecretSession.isOpen())
        val keys = page.keys.map { it.storageKey }.toMutableList()
        var next = page.nextStorageKey
        while (next != null) {
            val more = session.listPage(next)
            keys += more.keys.map { it.storageKey }
            next = more.nextStorageKey
        }
        for (key in legacySecrets.keys) {
            assertTrue("$key listed", key in keys)
            val entry = session.readKey(key)
            assertTrue("$key readable", entry.readable)
            assertTrue("$key has fields", entry.fields.isNotEmpty())
            assertFalse("$key is not shown sealed", entry.fields.any { WalletSecretEnvelope.isSealed(it.value) })
        }
        // The substrate private key of wallet 7 is shown as it was stored.
        val substrate = session.readKey("7:SUBSTRATE_SECRETS")
        assertTrue(substrate.fields.any { it.value == "0x" + Hex.toHexString(SyntheticSecrets.bytes(1)) })
        assertFalse(WalletSecretSession.isOpen())
    }

    @Test
    fun `without the PIN a sealed value is listed as unreadable, never as its envelope`() = runBlocking {
        val harness = EnvelopeStorageHarness(shared = legacyStorage())
        harness.authority.verify(PIN)
        harness.migration.run()
        WalletSecretSession.clear()
        val entry = WalletEmergencyRecoveryReader(harness.storage).readKey("7:TON_SECRETS")
        assertFalse(entry.readable)
        assertTrue(entry.fields.isEmpty())
    }

    @Test
    fun `with sealing switched off nothing is sealed and the legacy PIN keeps working`() = runBlocking {
        val harness = EnvelopeStorageHarness(compiledSealing = false, shared = legacyStorage())
        assertEquals(PinVerification.CORRECT, harness.authority.verify(PIN))
        assertFalse(harness.keyring.exists())
        val report = harness.migration.run()
        assertEquals(0, report.sealed)
        legacySecrets.forEach { (key, value) -> assertEquals(value, harness.inner(key)) }
        assertTrue(harness.shared.values.containsKey("pin_code"))
    }

    @Test
    fun `switching sealing off after migration keeps every sealed value readable`() = runBlocking {
        val harness = EnvelopeStorageHarness(shared = legacyStorage())
        harness.authority.verify(PIN)
        harness.migration.run()
        harness.preferences.putBoolean(WalletSecretSealingSwitch.REMOTE_VETO_KEY, false)
        WalletSecretSession.clear()
        assertEquals(PinVerification.CORRECT, harness.authority.verify(PIN))
        harness.migration.run()
        assertEveryOriginalOpens(harness)
    }

    @Test
    fun `a lost or corrupt factor after sealing needs a backup proof, which opens SK and gets a new factor`() = runBlocking {
        for (damage in listOf(EnvelopeStorageHarness::loseFactor, EnvelopeStorageHarness::corruptFactor)) {
            resetEnvelopeGlobals()
            val harness = EnvelopeStorageHarness(shared = legacyStorage())
            harness.authority.verify(PIN)
            harness.migration.run()
            WalletSecretSession.clear()
            damage(harness)
            val damagedFactor = harness.factorValues()

            assertEquals(PinHealth.UNREADABLE, harness.authority.health())
            assertEquals(PinVerification.UNREADABLE, harness.authority.verify(PIN))
            assertTrue(harness.authority.recoveryNeedsKeyMaterial())
            val root = WalletSecretPayloads.rootKeys(WalletSecretEnvelopeScope.ETHEREUM_ROOT, checkNotNull(legacySecrets["7:ETHEREUM_SECRETS"])).single()
            val recovered = checkNotNull(harness.keyring.openWithBackup(WalletRootKeyKind.entries.mapNotNull { it.canonical(root.privateKey) }))
            val snapshot = harness.authority.captureResetSnapshot()
            harness.authority.saveRecovered("112233", snapshot, recovered, forgotten = false)
            recovered.close()

            assertEquals(WalletSecretSession.Origin.BACKUP_PROOF, WalletSecretSession.origin())
            // The damaged factor is left as it was; the new PIN wrap names a new one.
            assertTrue(harness.factorValues().entries.containsAll(damagedFactor.entries))
            assertEquals(damagedFactor.size + 1, harness.factorValues().size)
            WalletSecretSession.clear()
            assertEquals(PinHealth.HEALTHY, harness.authority.health())
            assertEquals(PinVerification.CORRECT, harness.authority.verify("112233"))
            assertEveryOriginalOpens(harness)
        }
    }

    @Test
    fun `a first PIN on a new install lives only in the keyring`() = runBlocking {
        val harness = EnvelopeStorageHarness()
        // Onboarding wrote the first wallet before any PIN existed.
        harness.storage.putEncryptedString("1:SUBSTRATE_SECRETS", SyntheticSecrets.substrateSr25519(20))
        assertEquals(PinHealth.ABSENT, harness.authority.health())
        harness.authority.save(PIN)
        assertFalse(harness.shared.values.containsKey("pin_code"))
        assertEquals(PinHealth.HEALTHY, harness.authority.health())
        harness.migration.run()
        assertTrue(WalletSecretEnvelope.isSealed(harness.inner("1:SUBSTRATE_SECRETS")))
        assertNull(harness.shared.values["pin_code"])
    }

    private companion object {
        const val PIN = "975310"
    }
}
