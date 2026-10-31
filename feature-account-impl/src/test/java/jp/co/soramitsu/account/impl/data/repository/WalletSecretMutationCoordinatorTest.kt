package jp.co.soramitsu.account.impl.data.repository

import com.google.gson.JsonParser
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryStateIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.SubstratePasswordRule
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.WalletCustodyLocal
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.ton.api.pk.PrivateKeyEd25519

class WalletSecretMutationCoordinatorTest {

    private lateinit var database: FakeWalletMutationDatabase
    private lateinit var preferences: FaultInjectingEncryptedPreferences
    private lateinit var journalStore: WalletSecretMutationJournalStore
    private lateinit var identifiers: FakeWalletMutationIdentifierSource
    private lateinit var coordinator: WalletSecretMutationCoordinator

    @Before
    fun setUp() {
        database = FakeWalletMutationDatabase()
        preferences = FaultInjectingEncryptedPreferences()
        journalStore = WalletSecretMutationJournalStore(preferences)
        identifiers = FakeWalletMutationIdentifierSource(
            metaIds = listOf(77L),
            operationIds = canonicalOperationIds()
        )
        coordinator = coordinator()
    }

    @Test
    fun addTonPreservesAllExistingRootsAndMetadataAndRejectsReplacement() = runBlocking {
        val existing = fullSecretFixtures().prototype.copyForTest(id = 10, tonPublicKey = null)
        database.put(existing)
        val phrase = MnemonicCreator.fromEntropy(ByteArray(16)).words
        val derived = jp.co.soramitsu.common.utils.TonKeyDerivation.deriveAccount(phrase)
        val encoded = TonSecrets(phrase.encodeToByteArray(), jp.co.soramitsu.common.data.Keypair(derived.publicKey, derived.privateKey)).toHexString()
        val after = existing.copyForTest(tonPublicKey = derived.publicKey)
        coordinator.addTon(existing, after, encoded)
        val stored = requireNotNull(database.account(10))
        assertArrayEquals(existing.substratePublicKey, stored.substratePublicKey)
        assertArrayEquals(existing.ethereumPublicKey, stored.ethereumPublicKey)
        assertArrayEquals(derived.publicKey, stored.tonPublicKey)
        assertEquals(existing.name, stored.name)
        assertEquals(existing.position, stored.position)
        assertEquals(existing.isBackedUp, stored.isBackedUp)
        assertEquals(encoded, preferences.value("10:TON_SECRETS"))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        expectCoordinatorFailure { coordinator.addTon(stored, stored, encoded) }
        assertEquals(encoded, preferences.value("10:TON_SECRETS"))
    }

    @Test
    fun addTonRejectsOrphanedSecretsAndUnrelatedMetadataChanges() = runBlocking {
        val existing = substratePrototype(substrateFixture(1), isSelected = true).copyForTest(id = 10)
        val ton = tonFixture(2)
        database.put(existing)
        val after = existing.copyForTest(tonPublicKey = ton.publicKey)
        expectCoordinatorFailure {
            coordinator.addTon(existing, after.copyForTest(isBackedUp = !existing.isBackedUp), ton.encoded)
        }
        assertTrue(preferences.keys().isEmpty())
        preferences.putEncryptedString("10:TON_SECRETS", "preserved orphan")
        expectCoordinatorFailure { coordinator.addTon(existing, after, ton.encoded) }
        assertEquals("preserved orphan", preferences.value("10:TON_SECRETS"))
        assertNull(database.account(10)?.tonPublicKey)
    }

    @Test
    fun addTonBindsAPasswordWalletsUniversalKeyOnlyWithItsStoredPassword() = runBlocking {
        val existing = substratePrototype(substrateFixture(1), isSelected = true).copyForTest(id = 10)
        database.put(existing)
        val protected = jp.co.soramitsu.common.utils.TonKeyDerivation.deriveAccount(PASSWORD_PHRASE, "TREZOR")
        val after = existing.copyForTest(tonPublicKey = protected.publicKey)

        preferences.putEncryptedString("10:SUBSTRATE_SECRETS", passwordSubstrateSecret("//polkadot"))
        expectCoordinatorFailure { coordinator.addTon(existing, after, universalTonSecret(protected)) }
        assertNull(database.account(10)?.tonPublicKey)

        preferences.putEncryptedString("10:SUBSTRATE_SECRETS", passwordSubstrateSecret("//polkadot///TREZOR"))
        coordinator.addTon(existing, after, universalTonSecret(protected))
        assertArrayEquals(protected.publicKey, database.account(10)?.tonPublicKey)
        assertEquals(universalTonSecret(protected), preferences.value("10:TON_SECRETS"))
    }

    @Test
    fun replaceUniversalTonSwapsTheNoPasswordRootForThePasswordOne() = runBlocking {
        val (before, legacy, protected) = passwordWalletWithLegacyTon()
        val after = before.copyForTest(tonPublicKey = protected.publicKey)

        coordinator.replaceUniversalTon(before, after, universalTonSecret(protected))

        val stored = requireNotNull(database.account(10))
        assertArrayEquals(protected.publicKey, stored.tonPublicKey)
        assertArrayEquals(before.substratePublicKey, stored.substratePublicKey)
        assertEquals(before.name, stored.name)
        assertEquals(before.isBackedUp, stored.isBackedUp)
        assertEquals(universalTonSecret(protected), preferences.value("10:TON_SECRETS"))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertFalse(legacy.publicKey.contentEquals(protected.publicKey))
        // The password key gives way only to its no-password counterpart, never to another key.
        val other = jp.co.soramitsu.common.utils.TonKeyDerivation.deriveAccount(PASSWORD_PHRASE, "other")
        expectCoordinatorFailure {
            coordinator.replaceUniversalTon(stored, stored.copyForTest(tonPublicKey = other.publicKey), universalTonSecret(other))
        }
        assertEquals(universalTonSecret(protected), preferences.value("10:TON_SECRETS"))
    }

    @Test
    fun replaceUniversalTonMovesBackWhenFundsReachTheOldAddress() = runBlocking {
        val (before, legacy, protected) = passwordWalletWithLegacyTon()
        coordinator.replaceUniversalTon(before, before.copyForTest(tonPublicKey = protected.publicKey), universalTonSecret(protected))
        val switched = requireNotNull(database.account(10))

        coordinator.replaceUniversalTon(switched, before, universalTonSecret(legacy))

        assertArrayEquals(legacy.publicKey, database.account(10)?.tonPublicKey)
        assertEquals(universalTonSecret(legacy), preferences.value("10:TON_SECRETS"))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun replaceUniversalTonWaitsForATonConnectSaveCancelledBeforeItsRowInsert() = runBlocking {
        val (before, legacy, protected) = passwordWalletWithLegacyTon()
        // A connection save staged its journal and stopped before inserting its row.
        preferences.putEncryptedString(TonConnectStorageKeys.MUTATION_JOURNAL_KEY, "pending-ton-connect-save")

        val refused = expectCoordinatorFailure {
            coordinator.replaceUniversalTon(before, before.copyForTest(tonPublicKey = protected.publicKey), universalTonSecret(protected))
        }

        assertEquals(WalletMutationFailureReason.CONNECTED_APPS, refused.reason)
        assertArrayEquals(legacy.publicKey, database.account(10)?.tonPublicKey)
        assertEquals(universalTonSecret(legacy), preferences.value("10:TON_SECRETS"))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun replaceUniversalTonRefusesAnythingButTheSameWordsPasswordKey() = runBlocking {
        val (before, legacy, protected) = passwordWalletWithLegacyTon()
        val other = jp.co.soramitsu.common.utils.TonKeyDerivation.deriveAccount(PASSWORD_PHRASE, "other")
        val otherWords = jp.co.soramitsu.common.utils.TonKeyDerivation.deriveAccount(OTHER_PHRASE, "TREZOR")
        val attempts = listOf(
            before.copyForTest(tonPublicKey = other.publicKey) to universalTonSecret(other),
            before.copyForTest(tonPublicKey = otherWords.publicKey) to universalTonSecret(otherWords, OTHER_PHRASE),
            before.copyForTest(tonPublicKey = protected.publicKey) to universalTonSecret(other)
        )
        for ((after, encoded) in attempts) {
            expectCoordinatorFailure { coordinator.replaceUniversalTon(before, after, encoded) }
            assertArrayEquals(legacy.publicKey, database.account(10)?.tonPublicKey)
            assertEquals(universalTonSecret(legacy), preferences.value("10:TON_SECRETS"))
        }

        // A wallet without a password has no protected key to switch to.
        preferences.putEncryptedString("10:SUBSTRATE_SECRETS", passwordSubstrateSecret("//polkadot"))
        expectCoordinatorFailure {
            coordinator.replaceUniversalTon(before, before.copyForTest(tonPublicKey = protected.publicKey), universalTonSecret(protected))
        }
        assertEquals(universalTonSecret(legacy), preferences.value("10:TON_SECRETS"))
    }

    @Test
    fun replaceUniversalTonWaitsForConnectedTonAppsAndReplaysAfterInterruption() = runBlocking {
        val (before, legacy, protected) = passwordWalletWithLegacyTon()
        val after = before.copyForTest(tonPublicKey = protected.publicKey)
        database.tonConnectionOwners += 10L to TonConnectionSecretOwner(
            clientId = "ab".repeat(16),
            url = "https://dapp.example/connect",
            source = "WEB"
        )
        val refused = expectCoordinatorFailure { coordinator.replaceUniversalTon(before, after, universalTonSecret(protected)) }
        assertEquals(WalletMutationFailureReason.CONNECTED_APPS, refused.reason)
        assertEquals(universalTonSecret(legacy), preferences.value("10:TON_SECRETS"))
        assertArrayEquals(legacy.publicKey, database.account(10)?.tonPublicKey)

        database.tonConnectionOwners.clear()
        database.failBeforeTransactionNumber = 3
        expectFailure<InjectedDatabaseFailure> { coordinator.replaceUniversalTon(before, after, universalTonSecret(protected)) }
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertArrayEquals(legacy.publicKey, database.account(10)?.tonPublicKey)

        coordinator().reconcilePendingMutation()
        assertArrayEquals(protected.publicKey, database.account(10)?.tonPublicKey)
        assertEquals(universalTonSecret(protected), preferences.value("10:TON_SECRETS"))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    private fun passwordWalletWithLegacyTon(): Triple<MetaAccountLocal, jp.co.soramitsu.common.utils.TonKeyDerivation.TonAccount,
        jp.co.soramitsu.common.utils.TonKeyDerivation.TonAccount> {
        val legacy = jp.co.soramitsu.common.utils.TonKeyDerivation.deriveAccount(PASSWORD_PHRASE)
        val protected = jp.co.soramitsu.common.utils.TonKeyDerivation.deriveAccount(PASSWORD_PHRASE, "TREZOR")
        val before = substratePrototype(substrateFixture(1), isSelected = true)
            .copyForTest(id = 10, tonPublicKey = legacy.publicKey)
        database.put(before)
        // As 4.2.x stored it: the path keeps the password, the TON root ignored it.
        preferences.putEncryptedString("10:SUBSTRATE_SECRETS", passwordSubstrateSecret("//polkadot///TREZOR"))
        preferences.putEncryptedString("10:TON_SECRETS", universalTonSecret(legacy))
        return Triple(before, legacy, protected)
    }

    private fun passwordSubstrateSecret(path: String): String {
        val mnemonic = MnemonicCreator.fromWords(PASSWORD_PHRASE)
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey(1))
        return SubstrateSecrets(
            substrateKeyPair = keypair,
            entropy = mnemonic.entropy,
            seed = privateKey(1),
            substrateDerivationPath = path
        ).toHexString()
    }

    private fun universalTonSecret(
        account: jp.co.soramitsu.common.utils.TonKeyDerivation.TonAccount,
        phrase: String = PASSWORD_PHRASE
    ): String = TonSecrets(
        phrase.encodeToByteArray(),
        jp.co.soramitsu.common.data.Keypair(account.publicKey, account.privateKey)
    ).toHexString()

    @Test
    fun tonAdditionCannotShadowDedicatedAccountsBeforeStagingOrDuringReplay() = runBlocking {
        val existing = substratePrototype(substrateFixture(1), isSelected = true).copyForTest(id = 10)
        val ton = tonFixture(2)
        database.put(existing)
        val after = existing.copyForTest(tonPublicKey = ton.publicKey)
        val aliases = listOf("ton-mainnet", "ton:mainnet", "TON:MAINNET", "-239", "-3")
        for (alias in aliases) {
            database.accountChainIds[10] = listOf(alias)
            expectCoordinatorFailure { coordinator.addTon(existing, after, ton.encoded) }
            assertTrue(preferences.keys().isEmpty())
            assertNull(database.account(10)?.tonPublicKey)
        }
        database.accountChainIds.clear()
        database.failBeforeTransactionNumber = aliases.size + 2
        expectFailure<InjectedDatabaseFailure> { coordinator.addTon(existing, after, ton.encoded) }
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertNull(database.account(10)?.tonPublicKey)
        database.accountChainIds[10] = listOf("-239")
        expectCoordinatorFailure { coordinator().reconcilePendingMutation() }
        assertNull(database.account(10)?.tonPublicKey)
        assertEquals(listOf("-239"), database.accountChainIds[10])
    }

    @Test
    fun committedTonAdditionCompletesCleanupWithoutTouchingNewerDedicatedAccounts() = runBlocking {
        val existing = substratePrototype(substrateFixture(1), isSelected = true).copyForTest(id = 10)
        val ton = tonFixture(2)
        database.put(existing)
        preferences.enqueueDurableFault(DurableFault.NONE, DurableFault.BEFORE)
        expectCoordinatorFailure {
            coordinator.addTon(existing, existing.copyForTest(tonPublicKey = ton.publicKey), ton.encoded)
        }
        assertArrayEquals(ton.publicKey, database.account(10)?.tonPublicKey)
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        database.accountChainIds[10] = listOf("-239")
        coordinator().reconcilePendingMutation()
        assertEquals(listOf("-239"), database.accountChainIds[10])
        assertEquals(1, database.events.count { it.name == "update:10" })
        assertEquals(ton.encoded, preferences.value("10:TON_SECRETS"))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun interruptedTonAdditionReplaysWithoutLosingNewerMetadata() = runBlocking {
        val existing = substratePrototype(substrateFixture(1), isSelected = true).copyForTest(id = 10)
        val ton = tonFixture(2)
        database.put(existing)
        database.failBeforeTransactionNumber = 2
        expectFailure<InjectedDatabaseFailure> {
            coordinator.addTon(existing, existing.copyForTest(tonPublicKey = ton.publicKey), ton.encoded)
        }
        assertNull(database.account(10)?.tonPublicKey)
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        database.put(existing.copyForTest(name = "Renamed", isBackedUp = true))
        coordinator().reconcilePendingMutation()
        val restored = requireNotNull(database.account(10))
        assertEquals("Renamed", restored.name)
        assertTrue(restored.isBackedUp)
        assertArrayEquals(ton.publicKey, restored.tonPublicKey)
        assertArrayEquals(existing.substratePublicKey, restored.substratePublicKey)
        assertEquals(ton.encoded, preferences.value("10:TON_SECRETS"))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun createStagesAllSecretsBeforePublishingExactAllocatedWallet() = runBlocking {
        database.put(metaAccount(id = 1, position = 4, isSelected = true))
        val fixtures = fullSecretFixtures()
        val prototype = fixtures.prototype.copyForTest(
            id = 0,
            position = 999,
            isSelected = true
        )

        val result = coordinator.create(
            prototype = prototype,
            substrateSecretPlaintext = fixtures.substrate.encoded,
            ethereumSecretPlaintext = fixtures.ethereum.encoded,
            tonSecretPlaintext = fixtures.ton.encoded
        )

        assertEquals(77L, result)
        val stored = requireNotNull(database.account(77))
        assertEquals(5, stored.position)
        assertEquals(setOf(77L), database.selectedIds())
        assertEquals(0L, prototype.id)
        assertEquals(999, prototype.position)
        assertEquals(fixtures.substrate.encoded, preferences.value("77:SUBSTRATE_SECRETS"))
        assertEquals(fixtures.ethereum.encoded, preferences.value("77:ETHEREUM_SECRETS"))
        assertEquals(fixtures.ton.encoded, preferences.value("77:TON_SECRETS"))
        assertEquals(
            WalletCustodyProvenance.Kind.SIGNED,
            WalletCustodyProvenance.classify(stored, emptyList(), database.custody(77))
        )
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertEquals(
            listOf("stage", "insert:77", "select:77", "clear"),
            preferencesAndDatabaseEvents()
        )
    }

    @Test
    fun createUnselectedWalletPreservesExistingSelection() = runBlocking {
        database.put(metaAccount(id = 1, position = 8, isSelected = true))
        val fixture = substrateFixture(1)
        val prototype = substratePrototype(fixture, isSelected = false, position = 0)

        coordinator.create(prototype, fixture.encoded, null, null)

        assertEquals(setOf(1L), database.selectedIds())
        assertEquals(9, database.account(77)?.position)
        assertFalse(requireNotNull(database.account(77)).isSelected)
    }

    @Test
    fun createRejectsWrongSubstratePrivateKeyWithoutAnyWrite() = runBlocking {
        val publicFixture = substrateFixture(1)
        val wrongSecret = substrateFixture(2)

        val failure = expectCoordinatorFailure {
            coordinator.create(
                substratePrototype(publicFixture),
                wrongSecret.encoded,
                null,
                null
            )
        }

        assertEquals(WalletMutationFailureReason.SECRET_BINDING_FAILED, failure.reason)
        assertTrue(database.accounts().isEmpty())
        assertNull(database.custody(77))
        assertTrue(preferences.keys().isEmpty())
    }

    @Test
    fun createRejectsMalformedAndTrailingScaleWithoutAnyWrite() = runBlocking {
        val fixture = substrateFixture(1)
        listOf(
            "not-hex",
            fixture.encoded.uppercase(),
            fixture.encoded + "00"
        ).forEach { malformed ->
            val localPreferences = FaultInjectingEncryptedPreferences()
            val localDatabase = FakeWalletMutationDatabase()
            val localCoordinator = coordinator(
                database = localDatabase,
                preferences = localPreferences,
                identifiers = FakeWalletMutationIdentifierSource(
                    metaIds = listOf(77L),
                    operationIds = canonicalOperationIds()
                )
            )

            val failure = expectCoordinatorFailure {
                localCoordinator.create(
                    substratePrototype(fixture),
                    malformed,
                    null,
                    null
                )
            }

            assertEquals(WalletMutationFailureReason.SECRET_BINDING_FAILED, failure.reason)
            assertTrue(localDatabase.accounts().isEmpty())
            assertTrue(localPreferences.keys().isEmpty())
        }
    }

    @Test
    fun createRejectsWrongEthereumAddressWithoutAnyWrite() = runBlocking {
        val ethereum = ethereumFixture(1)
        val prototype = ethereumPrototype(ethereum).copyForTest(
            ethereumAddress = byteArrayOf(9).repeatToSize(20)
        )

        val failure = expectCoordinatorFailure {
            coordinator.create(prototype, null, ethereum.encoded, null)
        }

        assertEquals(WalletMutationFailureReason.SECRET_BINDING_FAILED, failure.reason)
        assertTrue(database.accounts().isEmpty())
        assertTrue(preferences.keys().isEmpty())
    }

    @Test
    fun createRejectsWrongTonPrivateKeyWithoutAnyWrite() = runBlocking {
        val publicFixture = tonFixture(1)
        val wrongSecret = tonFixture(2)

        val failure = expectCoordinatorFailure {
            coordinator.create(
                tonPrototype(publicFixture),
                null,
                null,
                wrongSecret.encoded
            )
        }

        assertEquals(WalletMutationFailureReason.SECRET_BINDING_FAILED, failure.reason)
        assertTrue(database.accounts().isEmpty())
        assertTrue(preferences.keys().isEmpty())
    }

    @Test
    fun createAcceptsKeypairOnlyJsonRepresentations() = runBlocking {
        val substratePrivate = privateKey(1)
        val substrateKeypair = EthereumKeypairFactory.createWithPrivateKey(substratePrivate)
        val substrate = SubstrateFixture(
            publicKey = substrateKeypair.publicKey,
            accountId = substrateKeypair.publicKey.substrateAccountId(),
            encoded = SubstrateSecrets(substrateKeyPair = substrateKeypair).toHexString()
        )
        val ethereumPrivate = privateKey(2)
        val ethereumKeypair = EthereumKeypairFactory.createWithPrivateKey(ethereumPrivate)
        val ethereum = EthereumFixture(
            publicKey = ethereumKeypair.publicKey,
            address = ethereumKeypair.publicKey.ethereumAddressFromPublicKey(),
            encoded = EthereumSecrets(ethereumKeypair = ethereumKeypair).toHexString()
        )
        val prototype = substratePrototype(substrate).copyForTest(
            ethereumPublicKey = ethereum.publicKey,
            ethereumAddress = ethereum.address
        )

        coordinator.create(prototype, substrate.encoded, ethereum.encoded, null)

        assertNotNull(database.account(77))
    }

    @Test
    fun createRejectsMismatchedSubstrateRawSeedBeforeStaging() = runBlocking {
        val fixture = substrateFixture(1)
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey(1))
        val mismatched = SubstrateSecrets(
            substrateKeyPair = keypair,
            seed = privateKey(2)
        ).toHexString()

        val failure = expectCoordinatorFailure {
            coordinator.create(substratePrototype(fixture), mismatched, null, null)
        }

        assertEquals(WalletMutationFailureReason.SECRET_BINDING_FAILED, failure.reason)
        assertTrue(preferences.keys().isEmpty())
        assertTrue(database.accounts().isEmpty())
    }

    @Test
    fun createAcceptsAndBindsSubstrateEntropySeedAndDerivationPath() = runBlocking {
        val entropy = ByteArray(16) { it.toByte() }
        val mnemonic = MnemonicCreator.fromEntropy(entropy)
        val path = "//7//8///password"
        val decodedPath = SubstrateJunctionDecoder.decode(path)
        val seed = SubstrateSeedFactory.deriveSeed32(
            mnemonicWords = mnemonic.words,
            password = decodedPath.password
        ).seed
        val keypair = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.ECDSA,
            seed = seed,
            junctions = decodedPath.junctions
        )
        val fixture = SubstrateFixture(
            publicKey = keypair.publicKey,
            accountId = keypair.publicKey.substrateAccountId(),
            encoded = SubstrateSecrets(
                substrateKeyPair = keypair,
                entropy = entropy,
                seed = seed,
                substrateDerivationPath = path
            ).toHexString()
        )

        coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)

        assertNotNull(database.account(77))
    }

    // substrate-password-vectors.json default24-unicode: an import may open the account the browser
    // extension 4.2 made from this password (SubstratePasswordRule.NFC).
    @Test
    fun createBindsAWalletImportedWithAnEarlierExtensionsPasswordRule() = runBlocking {
        val fixture = earlierRuleFixture(seedRule = SubstratePasswordRule.NFC, entropyPhrase = UNICODE_PHRASE)

        coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)

        assertNotNull(database.account(77))
        assertEquals(fixture.encoded, preferences.value("77:SUBSTRATE_SECRETS"))
    }

    @Test
    fun createRejectsEntropyThatNoPasswordRuleBindsToTheSeed() = runBlocking {
        val fixture = earlierRuleFixture(seedRule = SubstratePasswordRule.NFC, entropyPhrase = PASSWORD_PHRASE)

        val failure = expectCoordinatorFailure {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }

        assertEquals(WalletMutationFailureReason.SECRET_BINDING_FAILED, failure.reason)
        assertTrue(preferences.keys().isEmpty())
        assertTrue(database.accounts().isEmpty())
    }

    private fun earlierRuleFixture(seedRule: SubstratePasswordRule, entropyPhrase: String): SubstrateFixture {
        val path = "//polkadot///p\u00e4ssw\u00f6rd \uff26\uff37"
        val decodedPath = SubstrateJunctionDecoder.decode(path)
        val seed = SubstrateSeedFactory.deriveSeed32(UNICODE_PHRASE, decodedPath.password, seedRule).seed
        val keypair = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.ECDSA,
            seed = seed,
            junctions = decodedPath.junctions
        )
        return SubstrateFixture(
            publicKey = keypair.publicKey,
            accountId = keypair.publicKey.substrateAccountId(),
            encoded = SubstrateSecrets(
                substrateKeyPair = keypair,
                entropy = MnemonicCreator.fromWords(entropyPhrase).entropy,
                seed = seed,
                substrateDerivationPath = path
            ).toHexString()
        )
    }

    @Test
    fun createRejectsSubstrateEntropyOrPathThatDoesNotRecoverKeypair() = runBlocking {
        val entropy = ByteArray(16) { it.toByte() }
        val mnemonic = MnemonicCreator.fromEntropy(entropy)
        val path = "//7///password"
        val decodedPath = SubstrateJunctionDecoder.decode(path)
        val seed = SubstrateSeedFactory.deriveSeed32(
            mnemonicWords = mnemonic.words,
            password = decodedPath.password
        ).seed
        val keypair = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.ECDSA,
            seed = seed,
            junctions = decodedPath.junctions
        )
        val prototype = substratePrototype(
            SubstrateFixture(
                publicKey = keypair.publicKey,
                accountId = keypair.publicKey.substrateAccountId(),
                encoded = ""
            )
        )
        val invalidPayloads = listOf(
            SubstrateSecrets(
                substrateKeyPair = keypair,
                entropy = entropy.clone().apply { this[0] = (this[0] + 1).toByte() },
                seed = seed,
                substrateDerivationPath = path
            ).toHexString(),
            SubstrateSecrets(
                substrateKeyPair = keypair,
                entropy = entropy,
                seed = seed,
                substrateDerivationPath = "//8///password"
            ).toHexString()
        )

        invalidPayloads.forEach { invalid ->
            val localPreferences = FaultInjectingEncryptedPreferences()
            val failure = expectCoordinatorFailure {
                coordinator(
                    database = FakeWalletMutationDatabase(),
                    preferences = localPreferences,
                    identifiers = FakeWalletMutationIdentifierSource(
                        metaIds = listOf(77),
                        operationIds = canonicalOperationIds()
                    )
                ).create(prototype, invalid, null, null)
            }
            assertEquals(WalletMutationFailureReason.SECRET_BINDING_FAILED, failure.reason)
            assertTrue(localPreferences.keys().isEmpty())
        }
    }

    @Test
    fun createRejectsMismatchedEthereumRawSeedBeforeStaging() = runBlocking {
        val fixture = ethereumFixture(1)
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey(1))
        val mismatched = EthereumSecrets(
            seed = privateKey(2),
            ethereumKeypair = keypair
        ).toHexString()

        val failure = expectCoordinatorFailure {
            coordinator.create(ethereumPrototype(fixture), null, mismatched, null)
        }

        assertEquals(WalletMutationFailureReason.SECRET_BINDING_FAILED, failure.reason)
        assertTrue(preferences.keys().isEmpty())
    }

    @Test
    fun createAcceptsAndBindsEthereumEntropyPrivateSeedAndPath() = runBlocking {
        val entropy = ByteArray(16) { (it + 1).toByte() }
        val mnemonic = MnemonicCreator.fromEntropy(entropy)
        val path = "//44//60//0/0/7"
        val decodedPath = BIP32JunctionDecoder.decode(path)
        val bip32Seed = EthereumSeedFactory.deriveSeed32(
            mnemonicWords = mnemonic.words,
            password = decodedPath.password
        ).seed
        val keypair = EthereumKeypairFactory.generate(bip32Seed, decodedPath.junctions)
        val fixture = EthereumFixture(
            publicKey = keypair.publicKey,
            address = keypair.publicKey.ethereumAddressFromPublicKey(),
            encoded = EthereumSecrets(
                entropy = entropy,
                seed = keypair.privateKey,
                ethereumKeypair = keypair,
                ethereumDerivationPath = path
            ).toHexString()
        )

        coordinator.create(ethereumPrototype(fixture), null, fixture.encoded, null)

        assertNotNull(database.account(77))
    }

    @Test
    fun createRejectsEthereumEntropyOrPathThatDoesNotRecoverKeypair() = runBlocking {
        val entropy = ByteArray(16) { (it + 1).toByte() }
        val mnemonic = MnemonicCreator.fromEntropy(entropy)
        val path = "//44//60//0/0/7"
        val decodedPath = BIP32JunctionDecoder.decode(path)
        val bip32Seed = EthereumSeedFactory.deriveSeed32(
            mnemonicWords = mnemonic.words,
            password = decodedPath.password
        ).seed
        val keypair = EthereumKeypairFactory.generate(bip32Seed, decodedPath.junctions)
        val fixture = EthereumFixture(
            publicKey = keypair.publicKey,
            address = keypair.publicKey.ethereumAddressFromPublicKey(),
            encoded = ""
        )
        val invalid = EthereumSecrets(
            entropy = entropy,
            seed = keypair.privateKey,
            ethereumKeypair = keypair,
            ethereumDerivationPath = "//44//60//0/0/8"
        ).toHexString()

        val failure = expectCoordinatorFailure {
            coordinator.create(ethereumPrototype(fixture), null, invalid, null)
        }

        assertEquals(WalletMutationFailureReason.SECRET_BINDING_FAILED, failure.reason)
        assertTrue(preferences.keys().isEmpty())
    }

    @Test
    fun createRejectsTonMnemonicThatDoesNotRecoverStoredKeypair() = runBlocking {
        val fixture = tonFixture(1)
        val differentMnemonic = TonSecrets.read(tonFixture(2).encoded)[TonSecrets.Seed]
        val decoded = TonSecrets.read(fixture.encoded)
        val mismatched = TonSecrets(
            seed = differentMnemonic,
            tonKeypair = jp.co.soramitsu.common.data.Keypair(
                privateKey = decoded[TonSecrets.PrivateKey],
                publicKey = decoded[TonSecrets.PublicKey]
            )
        ).toHexString()

        val failure = expectCoordinatorFailure {
            coordinator.create(tonPrototype(fixture), null, null, mismatched)
        }

        assertEquals(WalletMutationFailureReason.SECRET_BINDING_FAILED, failure.reason)
        assertTrue(preferences.keys().isEmpty())
    }

    @Test
    fun createRejectsInvalidUtf8TonMnemonicBeforeStaging() = runBlocking {
        val fixture = tonFixture(1)
        val decoded = TonSecrets.read(fixture.encoded)
        val invalid = TonSecrets(
            seed = byteArrayOf(0xc3.toByte(), 0x28),
            tonKeypair = jp.co.soramitsu.common.data.Keypair(
                privateKey = decoded[TonSecrets.PrivateKey],
                publicKey = decoded[TonSecrets.PublicKey]
            )
        ).toHexString()

        val failure = expectCoordinatorFailure {
            coordinator.create(tonPrototype(fixture), null, null, invalid)
        }

        assertEquals(WalletMutationFailureReason.SECRET_BINDING_FAILED, failure.reason)
        assertTrue(preferences.keys().isEmpty())
    }

    @Test
    fun createRejectsIdentityOwnedByAnotherWalletBeforeStaging() = runBlocking {
        val fixture = substrateFixture(1)
        database.put(
            substratePrototype(fixture, isSelected = true)
                .copyForTest(id = 1, position = 1)
        )

        val failure = expectCoordinatorFailure {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }

        assertEquals(WalletMutationFailureReason.IDENTITY_CONFLICT, failure.reason)
        assertEquals(setOf(1L), database.accounts().map(MetaAccountLocal::id).toSet())
        assertTrue(preferences.keys().isEmpty())
    }

    @Test
    fun stagedCreateIdentityConflictRemainsARecoveryStateConflict() = runBlocking {
        val fixture = substrateFixture(1)
        preferences.enqueueDurableFault(DurableFault.AFTER)
        val stageFailure = expectCoordinatorFailure {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }
        assertEquals(WalletMutationFailureReason.DURABILITY_UNKNOWN, stageFailure.reason)
        database.put(
            substratePrototype(fixture, isSelected = true)
                .copyForTest(id = 1, position = 1)
        )

        val replayFailure = expectCoordinatorFailure {
            coordinator().reconcilePendingMutation()
        }

        assertEquals(WalletMutationFailureReason.STATE_CONFLICT, replayFailure.reason)
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertNull(database.account(77))
    }

    @Test
    fun createSkipsInvalidOccupiedRepeatedOrphanedAndDeletedIdCandidates() = runBlocking {
        val fixture = substrateFixture(1)
        database.put(metaAccount(id = 5, position = 1, isSelected = true))
        preferences.putEncryptedString("6:abcd:ACCESS_SECRETS", "orphan")
        preferences.putEncryptedString(WalletSecretArchive.keyFor("8:SUBSTRATE_SECRETS"), "deleted wallet material")
        identifiers = FakeWalletMutationIdentifierSource(
            metaIds = listOf(0, -1, 5, 5, 6, 8, 7),
            operationIds = canonicalOperationIds()
        )
        coordinator = coordinator()

        val result = coordinator.create(
            substratePrototype(fixture),
            fixture.encoded,
            null,
            null
        )

        assertEquals(7L, result)
        assertNotNull(database.account(7))
        assertEquals("orphan", preferences.value("6:abcd:ACCESS_SECRETS"))
        assertEquals("deleted wallet material", preferences.value(WalletSecretArchive.keyFor("8:SUBSTRATE_SECRETS")))
    }

    @Test
    fun createSkipsQuarantinedOrphanNamespace() = runBlocking {
        val fixture = substrateFixture(1)
        preferences.putEncryptedString(
            WalletSecretQuarantine.keyFor("8:abcd:ACCESS_SECRETS"),
            "orphan"
        )
        identifiers = FakeWalletMutationIdentifierSource(
            metaIds = listOf(8, 9),
            operationIds = canonicalOperationIds()
        )
        coordinator = coordinator()

        assertEquals(
            9L,
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        )
    }

    @Test
    fun createSkipsPortableCohortReservedWalletId() = runBlocking {
        val fixture = substrateFixture(1)
        preferences.putEncryptedString(PortableWalletCohortJournalStore.reservationKey(8L), "reserved")
        identifiers = FakeWalletMutationIdentifierSource(
            metaIds = listOf(8, 9),
            operationIds = canonicalOperationIds()
        )
        coordinator = coordinator()

        assertEquals(
            9L,
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        )
        assertNotNull(database.account(9))
        assertEquals("reserved", preferences.value(PortableWalletCohortJournalStore.reservationKey(8L)))
    }

    @Test
    fun createRejectsPortableCohortJournalEvenIfItsIdMarkerIsMissing() = runBlocking {
        val fixture = substrateFixture(1)
        preferences.putEncryptedString(PortableWalletCohortJournalStore.JOURNAL_KEY, "unreconciled")
        identifiers = FakeWalletMutationIdentifierSource(
            metaIds = listOf(8),
            operationIds = canonicalOperationIds()
        )
        coordinator = coordinator()

        val failure = assertThrows(WalletSecretMutationCoordinatorException::class.java) {
            runBlocking { coordinator.create(substratePrototype(fixture), fixture.encoded, null, null) }
        }
        assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
        assertNull(database.account(8))
        assertEquals("unreconciled", preferences.value(PortableWalletCohortJournalStore.JOURNAL_KEY))
    }

    @Test
    fun createFailsClosedWhenIdAllocationIsExhausted() = runBlocking {
        val fixture = substrateFixture(1)
        database.put(metaAccount(id = 5, position = 1, isSelected = true))
        identifiers = FakeWalletMutationIdentifierSource(
            metaIds = List(
                WalletSecretMutationCoordinator.MAX_META_ID_ALLOCATION_ATTEMPTS
            ) { 5L },
            operationIds = canonicalOperationIds()
        )
        coordinator = coordinator()

        val failure = expectCoordinatorFailure {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }

        assertEquals(
            WalletMutationFailureReason.IDENTIFIER_ALLOCATION_EXHAUSTED,
            failure.reason
        )
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertNull(database.account(77))
    }

    @Test
    fun createRejectsInvalidOperationIdBeforeStaging() = runBlocking {
        val fixture = substrateFixture(1)
        identifiers = FakeWalletMutationIdentifierSource(
            metaIds = listOf(77),
            operationIds = listOf("not-a-uuid")
        )
        coordinator = coordinator()

        val failure = expectCoordinatorFailure {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }

        assertEquals(WalletMutationFailureReason.INVALID_ARGUMENT, failure.reason)
        assertTrue(preferences.keys().isEmpty())
        assertTrue(database.accounts().isEmpty())
    }

    @Test
    fun createReconcilesAfterProcessDeathBeforeDatabaseTransaction() = runBlocking {
        val fixture = substrateFixture(1)
        database.failBeforeTransactionNumber = 2

        expectFailure<InjectedDatabaseFailure> {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }

        assertNull(database.account(77))
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        coordinator().reconcilePendingMutation()
        assertNotNull(database.account(77))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun createRollsBackAndReconcilesWhenInsertFailsAfterMutation() = runBlocking {
        val fixture = substrateFixture(1)
        database.failOperationAfter = DatabaseOperation.INSERT

        expectFailure<InjectedDatabaseFailure> {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }

        assertNull(database.account(77))
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        coordinator().reconcilePendingMutation()
        assertNotNull(database.account(77))
    }

    @Test
    fun createRollsBackAndReconcilesWhenTransactionDiesBeforeCommit() = runBlocking {
        val fixture = substrateFixture(1)
        database.failAfterTransactionNumber = 2

        expectFailure<InjectedDatabaseFailure> {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }

        assertNull(database.account(77))
        coordinator().reconcilePendingMutation()
        assertNotNull(database.account(77))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun createReconcilesExactAfterImageWhenJournalClearFailsBeforeWrite() = runBlocking {
        val fixture = substrateFixture(1)
        preferences.enqueueDurableFault(DurableFault.NONE, DurableFault.BEFORE)

        val failure = expectCoordinatorFailure {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }

        assertEquals(WalletMutationFailureReason.DURABILITY_UNKNOWN, failure.reason)
        assertNotNull(database.account(77))
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        coordinator().reconcilePendingMutation()
        assertEquals(1, database.accounts().count { it.id == 77L })
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun createReplayPreservesNewerMetadataAndSelectionAfterRoomCommit() = runBlocking {
        val fixture = substrateFixture(1)
        preferences.enqueueDurableFault(DurableFault.NONE, DurableFault.BEFORE)

        expectCoordinatorFailure {
            coordinator.create(
                substratePrototype(fixture, isSelected = true),
                fixture.encoded,
                null,
                null
            )
        }
        val evolved = requireNotNull(database.account(77)).copyForTest(
            name = "Renamed after commit",
            isSelected = false,
            position = 91,
            isBackedUp = true,
            googleBackupAddress = "newer@example.test",
            initialized = true
        )
        database.put(evolved)
        database.put(metaAccount(id = 88, position = 92, isSelected = true))

        coordinator().reconcilePendingMutation()

        val stored = requireNotNull(database.account(77))
        assertEquals("Renamed after commit", stored.name)
        assertEquals(91, stored.position)
        assertTrue(stored.isBackedUp)
        assertEquals("newer@example.test", stored.googleBackupAddress)
        assertTrue(stored.initialized)
        assertEquals(setOf(88L), database.selectedIds())
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun createToleratesAmbiguousClearThatActuallyCommitted() = runBlocking {
        val fixture = substrateFixture(1)
        preferences.enqueueDurableFault(DurableFault.NONE, DurableFault.AFTER)

        val failure = expectCoordinatorFailure {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }

        assertEquals(WalletMutationFailureReason.DURABILITY_UNKNOWN, failure.reason)
        assertNotNull(database.account(77))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        coordinator().reconcilePendingMutation()
        assertEquals(1, database.accounts().count { it.id == 77L })
    }

    @Test
    fun createRecoversAmbiguousStageThatActuallyCommitted() = runBlocking {
        val fixture = substrateFixture(1)
        preferences.enqueueDurableFault(DurableFault.AFTER)

        val failure = expectCoordinatorFailure {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }

        assertEquals(WalletMutationFailureReason.DURABILITY_UNKNOWN, failure.reason)
        assertNull(database.account(77))
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        coordinator().reconcilePendingMutation()
        assertNotNull(database.account(77))
    }

    @Test
    fun createStageFailureBeforeCommitPublishesNothing() = runBlocking {
        val fixture = substrateFixture(1)
        preferences.enqueueDurableFault(DurableFault.BEFORE)

        val failure = expectCoordinatorFailure {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }

        assertEquals(WalletMutationFailureReason.DURABILITY_UNKNOWN, failure.reason)
        assertNull(database.account(77))
        assertTrue(preferences.keys().isEmpty())
    }

    @Test
    fun processWideMutexSerializesConcurrentCreatePositions() = runBlocking {
        identifiers = FakeWalletMutationIdentifierSource(
            metaIds = listOf(100, 101),
            operationIds = canonicalOperationIds()
        )
        coordinator = coordinator()
        val first = substrateFixture(1)
        val second = substrateFixture(2)

        val ids = listOf(
            async {
                coordinator.create(substratePrototype(first), first.encoded, null, null)
            },
            async {
                coordinator.create(substratePrototype(second), second.encoded, null, null)
            }
        ).awaitAll()

        assertEquals(setOf(100L, 101L), ids.toSet())
        assertEquals(setOf(1, 2), database.accounts().map(MetaAccountLocal::position).toSet())
    }

    @Test
    fun addEvmPublishesExactAllowedTransitionAndSecret() = runBlocking {
        val substrate = substrateFixture(1)
        val ethereum = ethereumFixture(2)
        val existing = substratePrototype(substrate, isSelected = true)
            .copyForTest(id = 10, position = 3, isBackedUp = false)
        val after = existing.copyForTest(
            ethereumPublicKey = ethereum.publicKey,
            ethereumAddress = ethereum.address,
            isBackedUp = true
        )
        database.put(existing)
        database.insertCustody(WalletCustodyProvenance.watchMarker(existing, emptyList()))

        val result = coordinator.addEvm(existing, after, ethereum.encoded)

        assertEquals(10L, result)
        val stored = requireNotNull(database.account(10))
        assertArrayEquals(ethereum.publicKey, stored.ethereumPublicKey)
        assertArrayEquals(ethereum.address, stored.ethereumAddress)
        assertTrue(stored.isBackedUp)
        assertArrayEquals(existing.substratePublicKey, stored.substratePublicKey)
        assertEquals(setOf(10L), database.selectedIds())
        assertEquals(ethereum.encoded, preferences.value("10:ETHEREUM_SECRETS"))
        assertNull(database.custody(10)) // Mixed watch root + signed EVM is UNKNOWN, never WATCH.
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun addEvmRejectsUnrelatedFieldChangeBeforeStaging() = runBlocking {
        val substrate = substrateFixture(1)
        val ethereum = ethereumFixture(2)
        val existing = substratePrototype(substrate, isSelected = true)
            .copyForTest(id = 10, position = 3)
        database.put(existing)
        val invalidAfter = existing.copyForTest(
            name = "changed",
            ethereumPublicKey = ethereum.publicKey,
            ethereumAddress = ethereum.address
        )

        val failure = expectCoordinatorFailure {
            coordinator.addEvm(existing, invalidAfter, ethereum.encoded)
        }

        assertEquals(WalletMutationFailureReason.INVALID_ARGUMENT, failure.reason)
        assertNull(preferences.value("10:ETHEREUM_SECRETS"))
        assertEquals(existing.name, database.account(10)?.name)
    }

    @Test
    fun addEvmRejectsStaleBeforeImageBeforeStaging() = runBlocking {
        val substrate = substrateFixture(1)
        val ethereum = ethereumFixture(2)
        val existing = substratePrototype(substrate, isSelected = true)
            .copyForTest(id = 10, position = 3)
        database.put(existing.copyForTest(name = "database-newer"))
        val after = existing.copyForTest(
            ethereumPublicKey = ethereum.publicKey,
            ethereumAddress = ethereum.address
        )

        val failure = expectCoordinatorFailure {
            coordinator.addEvm(existing, after, ethereum.encoded)
        }

        assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
        assertTrue(preferences.keys().isEmpty())
    }

    @Test
    fun addEvmRejectsEthereumIdentityOwnedByAnotherWallet() = runBlocking {
        val substrate = substrateFixture(1)
        val ethereum = ethereumFixture(2)
        val existing = substratePrototype(substrate, isSelected = true)
            .copyForTest(id = 10, position = 3)
        database.put(existing)
        database.put(
            ethereumPrototype(ethereum).copyForTest(id = 11, position = 4)
        )
        val after = existing.copyForTest(
            ethereumPublicKey = ethereum.publicKey,
            ethereumAddress = ethereum.address
        )

        val failure = expectCoordinatorFailure {
            coordinator.addEvm(existing, after, ethereum.encoded)
        }

        assertEquals(WalletMutationFailureReason.IDENTITY_CONFLICT, failure.reason)
        assertNull(preferences.value("10:ETHEREUM_SECRETS"))
    }

    @Test
    fun addEvmRollsBackUpdateAndReconcilesAfterProcessDeath() = runBlocking {
        val substrate = substrateFixture(1)
        val ethereum = ethereumFixture(2)
        val existing = substratePrototype(substrate, isSelected = true)
            .copyForTest(id = 10, position = 3)
        val after = existing.copyForTest(
            ethereumPublicKey = ethereum.publicKey,
            ethereumAddress = ethereum.address
        )
        database.put(existing)
        database.failOperationAfter = DatabaseOperation.UPDATE

        expectFailure<InjectedDatabaseFailure> {
            coordinator.addEvm(existing, after, ethereum.encoded)
        }

        assertNull(database.account(10)?.ethereumAddress)
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        coordinator().reconcilePendingMutation()
        assertArrayEquals(ethereum.address, database.account(10)?.ethereumAddress)
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun reconcileAddEvmAppliesAuthorizedBackupTransitionAfterInterruptedStageAndRename() = runBlocking {
        val substrate = substrateFixture(1)
        val ethereum = ethereumFixture(2)
        val existing = substratePrototype(substrate, isSelected = true)
            .copyForTest(id = 10, position = 3, isBackedUp = false)
        val after = existing.copyForTest(
            ethereumPublicKey = ethereum.publicKey,
            ethereumAddress = ethereum.address,
            isBackedUp = true
        )
        database.put(existing)
        database.failBeforeTransactionNumber = 2
        expectFailure<InjectedDatabaseFailure> {
            coordinator.addEvm(existing, after, ethereum.encoded)
        }
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertNull(database.account(10)?.ethereumAddress)
        database.put(existing.copyForTest(name = "renamed before restart replay"))

        coordinator().reconcilePendingMutation()

        val stored = requireNotNull(database.account(10))
        assertEquals("renamed before restart replay", stored.name)
        assertTrue(stored.isBackedUp)
        assertArrayEquals(ethereum.publicKey, stored.ethereumPublicKey)
        assertArrayEquals(ethereum.address, stored.ethereumAddress)
        assertEquals(setOf(10L), database.selectedIds())
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun reconcileAddEvmPreservesNewerBackupFlagWhenJournalDoesNotChangeIt() = runBlocking {
        val substrate = substrateFixture(1)
        val ethereum = ethereumFixture(2)
        val existing = substratePrototype(substrate, isSelected = true)
            .copyForTest(id = 10, position = 3)
        val after = existing.copyForTest(
            ethereumPublicKey = ethereum.publicKey,
            ethereumAddress = ethereum.address
        )
        database.put(existing)
        database.failBeforeTransactionNumber = 2
        expectFailure<InjectedDatabaseFailure> {
            coordinator.addEvm(existing, after, ethereum.encoded)
        }
        database.put(
            existing.copyForTest(
                name = "newer",
                position = 44,
                isBackedUp = true,
                googleBackupAddress = "newer@example.test",
                initialized = true
            )
        )

        coordinator().reconcilePendingMutation()

        val stored = requireNotNull(database.account(10))
        assertEquals("newer", stored.name)
        assertEquals(44, stored.position)
        assertTrue(stored.isBackedUp)
        assertEquals("newer@example.test", stored.googleBackupAddress)
        assertTrue(stored.initialized)
        assertArrayEquals(ethereum.publicKey, stored.ethereumPublicKey)
        assertArrayEquals(ethereum.address, stored.ethereumAddress)
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun addEvmKeepsJournalWhenAuthorizedBackupTransitionIsNotCommitted() = runBlocking {
        val substrate = substrateFixture(1)
        val ethereum = ethereumFixture(2)
        val existing = substratePrototype(substrate, isSelected = true)
            .copyForTest(id = 10, position = 3, isBackedUp = false)
        val after = existing.copyForTest(
            ethereumPublicKey = ethereum.publicKey,
            ethereumAddress = ethereum.address,
            isBackedUp = true
        )
        database.put(existing)
        database.dropIsBackedUpOnNextUpdate = true

        val failure = expectCoordinatorFailure {
            coordinator.addEvm(existing, after, ethereum.encoded)
        }

        assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
        val stored = requireNotNull(database.account(10))
        assertFalse(stored.isBackedUp)
        assertNull(stored.ethereumAddress)
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun addEvmReplayAfterClearFailureIsIdempotentForAuthorizedBackupTransition() = runBlocking {
        val substrate = substrateFixture(1)
        val ethereum = ethereumFixture(2)
        val existing = substratePrototype(substrate, isSelected = true)
            .copyForTest(id = 10, position = 3, isBackedUp = false)
        val after = existing.copyForTest(
            ethereumPublicKey = ethereum.publicKey,
            ethereumAddress = ethereum.address,
            isBackedUp = true
        )
        database.put(existing)
        preferences.enqueueDurableFault(DurableFault.NONE, DurableFault.BEFORE)
        expectCoordinatorFailure {
            coordinator.addEvm(existing, after, ethereum.encoded)
        }
        assertTrue(requireNotNull(database.account(10)).isBackedUp)
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))

        val restarted = coordinator()
        restarted.reconcilePendingMutation()
        restarted.reconcilePendingMutation()

        val stored = requireNotNull(database.account(10))
        assertTrue(stored.isBackedUp)
        assertArrayEquals(ethereum.publicKey, stored.ethereumPublicKey)
        assertArrayEquals(ethereum.address, stored.ethereumAddress)
        assertEquals(1, database.events.count { it.name == "update:10" })
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun reconcileAddEvmStillRejectsCryptographicallyDifferentCurrentWallet() = runBlocking {
        val substrate = substrateFixture(1)
        val changedSubstrate = substrateFixture(3)
        val ethereum = ethereumFixture(2)
        val existing = substratePrototype(substrate, isSelected = true)
            .copyForTest(id = 10, position = 3)
        val after = existing.copyForTest(
            ethereumPublicKey = ethereum.publicKey,
            ethereumAddress = ethereum.address
        )
        database.put(existing)
        database.failBeforeTransactionNumber = 2
        expectFailure<InjectedDatabaseFailure> {
            coordinator.addEvm(existing, after, ethereum.encoded)
        }
        database.put(
            existing.copyForTest(
                substratePublicKey = changedSubstrate.publicKey,
                substrateAccountId = changedSubstrate.accountId
            )
        )

        val failure = expectCoordinatorFailure {
            coordinator().reconcilePendingMutation()
        }

        assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertNull(database.account(10)?.ethereumAddress)
    }

    @Test
    fun addEvmReplayPreservesMetadataAndSelectionChangedAfterRoomCommit() = runBlocking {
        val substrate = substrateFixture(1)
        val ethereum = ethereumFixture(2)
        val existing = substratePrototype(substrate, isSelected = true)
            .copyForTest(id = 10, position = 3)
        val after = existing.copyForTest(
            ethereumPublicKey = ethereum.publicKey,
            ethereumAddress = ethereum.address,
            isBackedUp = true
        )
        database.put(existing)
        preferences.enqueueDurableFault(DurableFault.NONE, DurableFault.BEFORE)
        expectCoordinatorFailure {
            coordinator.addEvm(existing, after, ethereum.encoded)
        }
        val evolved = requireNotNull(database.account(10)).copyForTest(
            name = "renamed after add",
            isSelected = false,
            position = 40,
            isBackedUp = false,
            initialized = true
        )
        database.put(evolved)
        database.put(metaAccount(id = 11, position = 41, isSelected = true))

        coordinator().reconcilePendingMutation()

        val stored = requireNotNull(database.account(10))
        assertEquals("renamed after add", stored.name)
        assertEquals(40, stored.position)
        assertFalse(stored.isBackedUp)
        assertTrue(stored.initialized)
        assertArrayEquals(ethereum.publicKey, stored.ethereumPublicKey)
        assertEquals(setOf(11L), database.selectedIds())
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun reconcileRejectsWrongStagedSecretWithoutPublishingWallet() = runBlocking {
        val fixture = substrateFixture(1)
        val wrong = substrateFixture(2)
        database.failBeforeTransactionNumber = 2
        expectFailure<InjectedDatabaseFailure> {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }
        preferences.putEncryptedString("77:SUBSTRATE_SECRETS", wrong.encoded)

        val failure = expectCoordinatorFailure {
            coordinator().reconcilePendingMutation()
        }

        assertEquals(WalletMutationFailureReason.SECRET_BINDING_FAILED, failure.reason)
        assertNull(database.account(77))
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun reconcileMalformedStoredJournalFailsClosedWithoutClearingIt() = runBlocking {
        preferences.putEncryptedString(
            WalletSecretMutationJournalStore.JOURNAL_KEY,
            "{}"
        )

        val failure = expectCoordinatorFailure {
            coordinator.reconcilePendingMutation()
        }

        assertEquals(WalletMutationFailureReason.RECOVERY_REQUIRED, failure.reason)
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertTrue(database.accounts().isEmpty())
    }

    @Test
    fun multipleSelectedDatabaseRowsFailClosedBeforeAnyMutation() = runBlocking {
        database.put(metaAccount(id = 1, position = 1, isSelected = true))
        database.put(metaAccount(id = 2, position = 2, isSelected = true))
        val fixture = substrateFixture(1)

        val failure = expectCoordinatorFailure {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }

        assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
        assertTrue(preferences.keys().isEmpty())
    }

    @Test
    fun nonEmptyDatabaseWithoutSelectionFailsClosedBeforeAnyMutation() = runBlocking {
        database.put(metaAccount(id = 1, position = 1, isSelected = false))
        val fixture = substrateFixture(1)

        val failure = expectCoordinatorFailure {
            coordinator.create(substratePrototype(fixture), fixture.encoded, null, null)
        }

        assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
        assertTrue(preferences.keys().isEmpty())
        assertEquals(setOf(1L), database.accounts().map(MetaAccountLocal::id).toSet())
    }

    @Test
    fun deletionRefusesAFullArchiveBeforeRemovingTheWalletOrChangingSelection() = runBlocking {
        val target = substratePrototype(substrateFixture(1), isSelected = true).copyForTest(id = 50, position = 0)
        database.put(target)
        val activeKey = "50:SUBSTRATE_SECRETS"
        preferences.putEncryptedString(activeKey, "active material")
        repeat(WalletSecretArchive.MAX_SLOTS) { slot ->
            preferences.putEncryptedString(WalletSecretArchive.keyFor(activeKey, slot), "retained material $slot")
        }
        assertThrows(WalletRecoveryStateIntegrityException::class.java) { runBlocking { coordinator.delete(50) } }
        assertEquals(journalImage(target), journalImage(checkNotNull(database.account(50))))
        assertEquals(setOf(50L), database.selectedIds())
        assertEquals("active material", preferences.value(activeKey))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun deleteSelectedWalletPromotesDeterministicSuccessorAndRemovesExactKeys() = runBlocking {
        val substrate = substrateFixture(1)
        val target = substratePrototype(substrate, isSelected = true)
            .copyForTest(id = 50, position = 0)
        database.put(target)
        database.put(metaAccount(id = 20, position = 2))
        database.put(metaAccount(id = 10, position = 2))
        val chainIds = listOf(
            byteArrayOf(2).repeatToSize(32),
            byteArrayOf(1).repeatToSize(20)
        )
        database.chainAccountIds[50] = chainIds.map(ByteArray::clone).toMutableList()
        database.assetMetaIds += 50
        val beforeImage = journalImage(target)
        val chainIdsHex = chainIds.map(::hex).toSet()
        val removalKeys = journalStore.deletionSecretKeys(50, beforeImage, chainIdsHex)
        removalKeys.forEach { preferences.putEncryptedString(it, "secret") }
        preferences.putEncryptedString("unrelated", "keep")

        coordinator.delete(50)

        assertNull(database.account(50))
        assertEquals(setOf(10L), database.selectedIds())
        assertFalse(50L in database.assetMetaIds)
        assertTrue(removalKeys.none(preferences::hasKey))
        assertEquals("secret", preferences.value(WalletSecretArchive.keyFor("50:SUBSTRATE_SECRETS")))
        assertEquals("keep", preferences.value("unrelated"))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun deleteRemovesExactLegacyActiveAndQuarantineKeysForTargetAccountId() = runBlocking {
        val fixture = substrateFixture(1)
        val target = substratePrototype(fixture, isSelected = true)
            .copyForTest(id = 50, position = 0)
        database.put(target)
        val address0 = fixture.accountId.toAddress(0)
        val address42 = fixture.accountId.toAddress(42)
        val unrelatedAddress = ByteArray(32) { 9 }.toAddress(0)
        val ownedLegacyKeys = setOf(
            "security_source_$address0",
            "private_$address42",
            "seed_$address0",
            "entropy_$address42",
            "derivation_$address0",
            "wallet_secret_quarantine:security_source_$address42",
            "wallet_secret_quarantine:private_$address0"
        )
        val unrelatedKeys = setOf(
            "security_source_$unrelatedAddress",
            "private_private_$address0",
            "unrelated"
        )
        (ownedLegacyKeys + unrelatedKeys).forEach {
            preferences.putEncryptedString(it, "secret")
        }

        coordinator.delete(50)

        assertTrue(ownedLegacyKeys.none(preferences::hasKey))
        assertEquals("secret", preferences.value(WalletSecretArchive.keyFor("security_source_$address0")))
        assertEquals("secret", preferences.value(WalletSecretArchive.keyFor("security_source_$address42")))
        assertTrue(unrelatedKeys.all(preferences::hasKey))
        assertNull(database.account(50))
    }

    @Test
    fun deleteReplayRemovesScopedAndUnownedLegacyTonConnectSecrets() =
        runBlocking {
            database.put(metaAccount(id = 50, position = 0, isSelected = true))
            val owner = TonConnectionSecretOwner(
                clientId = "ab".repeat(16),
                url = "https://target.example/connect",
                source = "WEB"
            )
            database.tonConnectionOwners += 50L to owner
            val scopedKey = TonConnectStorageKeys.scoped(
                metaId = 50,
                url = owner.url,
                source = owner.source
            )
            val legacyKey = TonConnectStorageKeys.legacy(owner.clientId)
            val exactKeys = setOf(
                scopedKey,
                WalletSecretQuarantine.keyFor(scopedKey),
                legacyKey,
                WalletSecretQuarantine.keyFor(legacyKey)
            )
            exactKeys.forEach {
                preferences.putEncryptedString(it, "exact-ton-secret")
            }
            database.failOperationAfter = DatabaseOperation.DELETE

            expectFailure<InjectedDatabaseFailure> {
                coordinator.delete(50)
            }

            assertNotNull(database.account(50))
            assertTrue(exactKeys.all(preferences::hasKey))
            assertTrue(
                preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
            )

            coordinator().reconcilePendingMutation()

            assertNull(database.account(50))
            assertTrue(exactKeys.none(preferences::hasKey))
            assertFalse(
                preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
            )
        }

    @Test
    fun deleteRemovesOrphanScopedTonConnectActiveSecret() = runBlocking {
        database.put(metaAccount(id = 50, position = 0, isSelected = true))
        val orphan = TonConnectStorageKeys.scoped(
            metaId = 50,
            url = "https://orphan.example/active",
            source = "WEB"
        )
        preferences.putEncryptedString(orphan, "orphan-ton-secret")

        coordinator.delete(50)

        assertNull(database.account(50))
        assertFalse(preferences.hasKey(orphan))
        assertFalse(
            preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
        )
    }

    @Test
    fun deleteRemovesOrphanScopedTonConnectQuarantine() = runBlocking {
        database.put(metaAccount(id = 50, position = 0, isSelected = true))
        val activeKey = TonConnectStorageKeys.scoped(
            metaId = 50,
            url = "https://orphan.example/quarantine",
            source = "QR"
        )
        val orphanQuarantine = WalletSecretQuarantine.keyFor(activeKey)
        preferences.putEncryptedString(
            orphanQuarantine,
            "orphan-quarantined-ton-secret"
        )

        coordinator.delete(50)

        assertNull(database.account(50))
        assertFalse(preferences.hasKey(orphanQuarantine))
    }

    @Test
    fun deleteReplayRemovesMixedDatabaseAndOrphanTonConnectInventory() =
        runBlocking {
            database.put(metaAccount(id = 50, position = 0, isSelected = true))
            val owner = TonConnectionSecretOwner(
                clientId = "12".repeat(16),
                url = "https://database.example/connect",
                source = "WEB"
            )
            database.tonConnectionOwners += 50L to owner
            val databaseScoped = TonConnectStorageKeys.scoped(
                metaId = 50,
                url = owner.url,
                source = owner.source
            )
            val legacy = TonConnectStorageKeys.legacy(owner.clientId)
            val orphanActive = TonConnectStorageKeys.scoped(
                metaId = 50,
                url = "https://orphan.example/one",
                source = "QR"
            )
            val orphanQuarantine = WalletSecretQuarantine.keyFor(
                TonConnectStorageKeys.scoped(
                    metaId = 50,
                    url = "https://orphan.example/two",
                    source = "WEB"
                )
            )
            val exactKeys = setOf(
                databaseScoped,
                WalletSecretQuarantine.keyFor(databaseScoped),
                legacy,
                WalletSecretQuarantine.keyFor(legacy),
                orphanActive,
                orphanQuarantine
            )
            exactKeys.forEach {
                preferences.putEncryptedString(it, "exact-ton-secret")
            }
            database.failOperationAfter = DatabaseOperation.DELETE

            expectFailure<InjectedDatabaseFailure> {
                coordinator.delete(50)
            }

            assertNotNull(database.account(50))
            assertTrue(exactKeys.all(preferences::hasKey))
            assertTrue(
                preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
            )

            coordinator().reconcilePendingMutation()

            assertNull(database.account(50))
            assertTrue(exactKeys.none(preferences::hasKey))
            assertFalse(
                preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
            )
        }

    @Test
    fun orphanScopedInventoryIsIsolatedByExactWalletPrefix() = runBlocking {
        database.put(metaAccount(id = 5, position = 0, isSelected = true))
        database.put(metaAccount(id = 50, position = 1))
        val targetActive = TonConnectStorageKeys.scoped(
            metaId = 5,
            url = "https://target.example/orphan",
            source = "WEB"
        )
        val targetQuarantine = WalletSecretQuarantine.keyFor(targetActive)
        val survivorActive = TonConnectStorageKeys.scoped(
            metaId = 50,
            url = "https://survivor.example/orphan",
            source = "WEB"
        )
        val survivorQuarantine = WalletSecretQuarantine.keyFor(survivorActive)
        setOf(
            targetActive,
            targetQuarantine,
            survivorActive,
            survivorQuarantine
        ).forEach {
            preferences.putEncryptedString(it, "exact-ton-secret")
        }

        coordinator.delete(5)

        assertNull(database.account(5))
        assertNotNull(database.account(50))
        assertFalse(preferences.hasKey(targetActive))
        assertFalse(preferences.hasKey(targetQuarantine))
        assertTrue(preferences.hasKey(survivorActive))
        assertTrue(preferences.hasKey(survivorQuarantine))
    }

    @Test
    fun malformedScopedPrefixMatchesFailBeforeDeletionJournalMutation() =
        runBlocking {
            database.put(metaAccount(id = 50, position = 0, isSelected = true))
            val activePrefix = TonConnectStorageKeys.scopedPrefix(50)
            val invalidKeys = listOf(
                activePrefix + "not-a-canonical-sha256",
                WalletSecretQuarantine.keyFor(
                    activePrefix + "not-a-quarantined-canonical-sha256"
                ),
                activePrefix + "x".repeat(300)
            )

            invalidKeys.forEach { invalidKey ->
                preferences.putEncryptedString(invalidKey, "hostile-ton-secret")

                val failure = expectCoordinatorFailure {
                    coordinator.delete(50)
                }

                assertEquals(
                    WalletMutationFailureReason.STATE_CONFLICT,
                    failure.reason
                )
                assertNotNull(database.account(50))
                assertTrue(preferences.hasKey(invalidKey))
                assertFalse(
                    preferences.hasKey(
                        WalletSecretMutationJournalStore.JOURNAL_KEY
                    )
                )
                preferences.removeKey(invalidKey)
            }
        }

    @Test
    fun scopedInventoryProviderFailureIsSideEffectFreeAndRetryable() =
        runBlocking {
            database.put(metaAccount(id = 50, position = 0, isSelected = true))
            val orphan = TonConnectStorageKeys.scoped(
                metaId = 50,
                url = "https://orphan.example/provider-retry",
                source = "WEB"
            )
            preferences.putEncryptedString(orphan, "orphan-ton-secret")
            preferences.keysWithPrefixesFailure =
                WalletSecureStorageUnavailableException(
                    "simulated preference provider failure"
                )

            expectFailure<WalletSecureStorageUnavailableException> {
                coordinator.delete(50)
            }

            assertNotNull(database.account(50))
            assertTrue(preferences.hasKey(orphan))
            assertFalse(
                preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
            )

            coordinator.delete(50)

            assertNull(database.account(50))
            assertFalse(preferences.hasKey(orphan))
        }

    @Test
    fun scopedInventoryOverflowFailsClosedAndRetriesAfterBoundIsRestored() =
        runBlocking {
            database.put(metaAccount(id = 50, position = 0, isSelected = true))
            val maximumKeys =
                TonConnectStorageKeys.MAX_CONNECTIONS_PER_WALLET * 2
            val orphanKeys = (0..maximumKeys).map { index ->
                TonConnectStorageKeys.scoped(
                    metaId = 50,
                    url = "https://orphan.example/overflow/$index",
                    source = "WEB"
                )
            }.toSet()
            assertEquals(maximumKeys + 1, orphanKeys.size)
            orphanKeys.forEach {
                preferences.putEncryptedString(it, "orphan-ton-secret")
            }

            val overflow = expectCoordinatorFailure {
                coordinator.delete(50)
            }

            assertEquals(
                WalletMutationFailureReason.STATE_CONFLICT,
                overflow.reason
            )
            assertNotNull(database.account(50))
            assertTrue(orphanKeys.all(preferences::hasKey))
            assertFalse(
                preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
            )

            val removedOverflow = orphanKeys.last()
            preferences.removeKey(removedOverflow)
            coordinator.delete(50)

            assertNull(database.account(50))
            assertTrue(
                orphanKeys
                    .filterNot { it == removedOverflow }
                    .none(preferences::hasKey)
            )
        }

    @Test
    fun deletePreservesExactCaseLegacyTonSecretOwnedBySurvivingWallet() =
        runBlocking {
            database.put(metaAccount(id = 50, position = 0, isSelected = true))
            database.put(metaAccount(id = 51, position = 1))
            val clientId = "aB".repeat(16)
            val target = TonConnectionSecretOwner(
                clientId = clientId,
                url = "https://target.example/connect",
                source = "WEB"
            )
            val survivor = TonConnectionSecretOwner(
                clientId = clientId,
                url = "https://survivor.example/connect",
                source = "QR"
            )
            database.tonConnectionOwners += 50L to target
            database.tonConnectionOwners += 51L to survivor
            val targetScoped = TonConnectStorageKeys.scoped(
                metaId = 50,
                url = target.url,
                source = target.source
            )
            val survivorScoped = TonConnectStorageKeys.scoped(
                metaId = 51,
                url = survivor.url,
                source = survivor.source
            )
            val legacy = TonConnectStorageKeys.legacy(clientId)
            val targetKeys = setOf(
                targetScoped,
                WalletSecretQuarantine.keyFor(targetScoped)
            )
            val retainedKeys = setOf(
                survivorScoped,
                WalletSecretQuarantine.keyFor(survivorScoped),
                legacy,
                WalletSecretQuarantine.keyFor(legacy)
            )
            (targetKeys + retainedKeys).forEach {
                preferences.putEncryptedString(it, "exact-ton-secret")
            }

            coordinator.delete(50)

            assertTrue(targetKeys.none(preferences::hasKey))
            assertTrue(retainedKeys.all(preferences::hasKey))
            assertNull(database.account(50))
            assertNotNull(database.account(51))
            assertEquals(
                listOf(51L),
                database.tonConnectionOwners.map { it.first }
            )
        }

    @Test
    fun deleteRejectsMalformedOrOversizedTonRowsBeforeJournalMutation() =
        runBlocking {
            listOf(
                "https://invalid.example/\uD800",
                "x".repeat(TonConnectStorageKeys.MAX_URL_CHARS + 1)
            ).forEachIndexed { index, invalidUrl ->
                val metaId = 50L + index
                database.put(
                    metaAccount(
                        id = metaId,
                        position = index,
                        isSelected = index == 0
                    )
                )
                database.tonConnectionOwners += metaId to
                    TonConnectionSecretOwner(
                        clientId = "cd".repeat(16),
                        url = invalidUrl,
                        source = "WEB"
                    )

                val failure = expectCoordinatorFailure {
                    coordinator.delete(metaId)
                }

                assertEquals(
                    WalletMutationFailureReason.STATE_CONFLICT,
                    failure.reason
                )
                assertNotNull(database.account(metaId))
                assertFalse(
                    preferences.hasKey(
                        WalletSecretMutationJournalStore.JOURNAL_KEY
                    )
                )
            }
        }

    @Test
    fun deleteRejectsMoreThanBoundedTonRowsAndPendingTonJournal() =
        runBlocking {
            database.put(metaAccount(id = 50, position = 0, isSelected = true))
            repeat(TonConnectStorageKeys.MAX_CONNECTIONS_PER_WALLET + 1) {
                index ->
                database.tonConnectionOwners += 50L to
                    TonConnectionSecretOwner(
                        clientId = "ef".repeat(16),
                        url = "https://target.example/connect/$index",
                        source = "WEB"
                    )
            }

            val overflow = expectCoordinatorFailure {
                coordinator.delete(50)
            }

            assertEquals(
                WalletMutationFailureReason.STATE_CONFLICT,
                overflow.reason
            )
            assertNotNull(database.account(50))
            assertFalse(
                preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
            )

            database.tonConnectionOwners.clear()
            preferences.putEncryptedString(
                TonConnectStorageKeys.MUTATION_JOURNAL_KEY,
                "pending-ton-mutation"
            )
            val pending = expectCoordinatorFailure {
                coordinator.delete(50)
            }

            assertEquals(
                WalletMutationFailureReason.RECOVERY_REQUIRED,
                pending.reason
            )
            assertNotNull(database.account(50))
            assertFalse(
                preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
            )
        }

    @Test
    fun deleteFailsBeforeDiscoveryWhenSamePublicIdentityHasDifferentSs58Material() =
        runBlocking {
            val fixture = substrateFixture(31)
            val target = substratePrototype(fixture, isSelected = true)
                .copyForTest(id = 50, position = 0)
            val survivor = substratePrototype(fixture)
                .copyForTest(id = 51, position = 1)
            database.put(target)
            database.put(survivor)
            val address0 = fixture.accountId.toAddress(0)
            val address42 = fixture.accountId.toAddress(42)
            val exactValues = mapOf(
                "security_source_$address0" to "target-or-survivor-v1",
                "private_$address42" to "target-or-survivor-v04"
            )
            exactValues.forEach(preferences::putEncryptedString)

            val failure = expectCoordinatorFailure {
                coordinator.delete(50)
            }
            coordinator().reconcilePendingMutation()

            assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
            assertNotNull(database.account(50))
            assertNotNull(database.account(51))
            exactValues.forEach { (key, value) ->
                assertEquals(value, preferences.value(key))
            }
            assertFalse(
                preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
            )
        }

    @Test
    fun deleteFailsBeforeDiscoveryWhenAnotherWalletClaimsTargetAccountId() =
        runBlocking {
            val targetFixture = substrateFixture(37)
            val survivorFixture = substrateFixture(41)
            val target = substratePrototype(targetFixture, isSelected = true)
                .copyForTest(id = 50, position = 0)
            val survivor = substratePrototype(survivorFixture)
                .copyForTest(
                    id = 51,
                    position = 1,
                    substrateAccountId = targetFixture.accountId
                )
            database.put(target)
            database.put(survivor)
            val legacyKey =
                "security_source_${targetFixture.accountId.toAddress(0)}"
            preferences.putEncryptedString(legacyKey, "retain-exact-survivor")

            val failure = expectCoordinatorFailure {
                coordinator.delete(50)
            }
            coordinator().reconcilePendingMutation()

            assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
            assertEquals("retain-exact-survivor", preferences.value(legacyKey))
            assertNotNull(database.account(50))
            assertNotNull(database.account(51))
            assertFalse(
                preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
            )
        }

    @Test
    fun deleteFailsBeforeDiscoveryWhenTargetAccountIdDoesNotMatchPublicKey() =
        runBlocking {
            val fixture = substrateFixture(43)
            val mismatchedAccountId = fixture.accountId.clone().also {
                it[0] = (it[0].toInt() xor 0x01).toByte()
            }
            val target = substratePrototype(fixture, isSelected = true)
                .copyForTest(
                    id = 50,
                    position = 0,
                    substrateAccountId = mismatchedAccountId
                )
            database.put(target)
            val legacyKey = "private_${mismatchedAccountId.toAddress(42)}"
            preferences.putEncryptedString(legacyKey, "retain-mismatched-target")

            val failure = expectCoordinatorFailure {
                coordinator.delete(50)
            }
            coordinator().reconcilePendingMutation()

            assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
            assertEquals("retain-mismatched-target", preferences.value(legacyKey))
            assertNotNull(database.account(50))
            assertFalse(
                preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
            )
        }

    @Test
    fun deleteRemovesOrphanedV2ChainSecretsWithoutTouchingPrefixCollisions() = runBlocking {
        val target = metaAccount(id = 50, position = 0, isSelected = true)
        database.put(target)
        val orphanAccountIdHex = "ab".repeat(32)
        val orphanKey = "50:$orphanAccountIdHex:ACCESS_SECRETS"
        val quarantinedOrphanKey = WalletSecretQuarantine.keyFor(orphanKey)
        val unrelated = setOf(
            "500:$orphanAccountIdHex:ACCESS_SECRETS",
            "50:${orphanAccountIdHex.uppercase()}:ACCESS_SECRETS",
            "50:unrelated"
        )
        (setOf(orphanKey, quarantinedOrphanKey) + unrelated).forEach {
            preferences.putEncryptedString(it, "secret")
        }

        coordinator.delete(50)

        assertFalse(preferences.hasKey(orphanKey))
        assertFalse(preferences.hasKey(quarantinedOrphanKey))
        assertTrue(unrelated.all(preferences::hasKey))
        assertNull(database.account(50))
    }

    @Test
    fun deleteNonSelectedWalletPreservesSelectedWallet() = runBlocking {
        database.put(metaAccount(id = 1, position = 9, isSelected = true))
        database.put(metaAccount(id = 2, position = 0, isSelected = false))

        coordinator.delete(2)

        assertNull(database.account(2))
        assertEquals(setOf(1L), database.selectedIds())
    }

    @Test
    fun firstWalletIsSelectedEvenWhenPrototypeWasUnselected() = runBlocking {
        val fixture = substrateFixture(1)

        val metaId = coordinator.create(
            prototype = substratePrototype(fixture, isSelected = false),
            substrateSecretPlaintext = fixture.encoded,
            ethereumSecretPlaintext = null,
            tonSecretPlaintext = null
        )

        assertEquals(setOf(metaId), database.selectedIds())
        assertTrue(requireNotNull(database.account(metaId)).isSelected)
    }

    @Test
    fun deleteMissingWalletFailsWithoutJournal() = runBlocking {
        val failure = expectCoordinatorFailure {
            coordinator.delete(404)
        }

        assertEquals(WalletMutationFailureReason.INVALID_ARGUMENT, failure.reason)
        assertTrue(preferences.keys().isEmpty())
    }

    @Test
    fun deleteRollsBackAndReconcilesWhenDatabaseDiesAfterMutation() = runBlocking {
        database.put(metaAccount(id = 1, position = 0, isSelected = true))
        database.put(metaAccount(id = 2, position = 1))
        database.failOperationAfter = DatabaseOperation.DELETE

        expectFailure<InjectedDatabaseFailure> {
            coordinator.delete(1)
        }

        assertNotNull(database.account(1))
        assertEquals(setOf(1L), database.selectedIds())
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        coordinator().reconcilePendingMutation()
        assertNull(database.account(1))
        assertEquals(setOf(2L), database.selectedIds())
    }

    @Test
    fun deleteReplayCompletesCleanTargetAbsentStateAndIsIdempotent() = runBlocking {
        val target = metaAccount(id = 1, position = 0, isSelected = true)
        database.put(target)
        database.put(metaAccount(id = 2, position = 1))
        val removalKeys = journalStore.deletionSecretKeys(
            metaId = 1,
            beforeImage = journalImage(target),
            chainAccountIdsHex = emptySet()
        )
        removalKeys.forEach { preferences.putEncryptedString(it, "secret") }
        preferences.enqueueDurableFault(DurableFault.NONE, DurableFault.BEFORE)

        val failure = expectCoordinatorFailure {
            coordinator.delete(1)
        }

        assertEquals(WalletMutationFailureReason.DURABILITY_UNKNOWN, failure.reason)
        assertNull(database.account(1))
        assertTrue(removalKeys.all(preferences::hasKey))
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        coordinator().reconcilePendingMutation()
        assertTrue(removalKeys.none(preferences::hasKey))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))

        coordinator().reconcilePendingMutation()

        assertNull(database.account(1))
        assertEquals(setOf(2L), database.selectedIds())
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun deleteReplayRejectsOrphanedAssetsAndKeepsJournal() = runBlocking {
        val target = metaAccount(id = 1, position = 0, isSelected = true)
        database.put(target)
        database.put(metaAccount(id = 2, position = 1))
        val removalKeys = journalStore.deletionSecretKeys(
            metaId = 1,
            beforeImage = journalImage(target),
            chainAccountIdsHex = emptySet()
        )
        removalKeys.forEach { preferences.putEncryptedString(it, "secret") }
        preferences.enqueueDurableFault(DurableFault.NONE, DurableFault.BEFORE)
        expectCoordinatorFailure {
            coordinator.delete(1)
        }
        database.assetMetaIds += 1

        val failure = expectCoordinatorFailure {
            coordinator().reconcilePendingMutation()
        }

        assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
        assertNull(database.account(1))
        assertTrue(1L in database.assetMetaIds)
        assertTrue(removalKeys.all(preferences::hasKey))
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun deleteReplayFinalizesAfterLaterHealthySelectionChange() = runBlocking {
        database.put(metaAccount(id = 1, position = 0, isSelected = true))
        database.put(metaAccount(id = 2, position = 1))
        database.put(metaAccount(id = 3, position = 2))
        preferences.enqueueDurableFault(DurableFault.NONE, DurableFault.BEFORE)
        expectCoordinatorFailure {
            coordinator.delete(1)
        }
        database.forceSelect(3)

        coordinator().reconcilePendingMutation()

        assertEquals(setOf(3L), database.selectedIds())
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun deleteReplayToleratesMetadataAndSelectionDrift() = runBlocking {
        database.put(metaAccount(id = 1, position = 0, isSelected = true))
        database.put(metaAccount(id = 2, position = 1))
        database.put(metaAccount(id = 3, position = 2))
        database.failBeforeTransactionNumber = 2
        expectFailure<InjectedDatabaseFailure> {
            coordinator.delete(1)
        }
        database.put(
            requireNotNull(database.account(1)).copyForTest(
                name = "renamed while deletion was pending",
                isSelected = false,
                position = 99,
                isBackedUp = true,
                googleBackupAddress = "newer@example.test",
                initialized = true
            )
        )
        database.forceSelect(3)

        coordinator().reconcilePendingMutation()

        assertNull(database.account(1))
        assertEquals(setOf(3L), database.selectedIds())
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun deleteReplayComputesSuccessorFromCurrentOrdering() = runBlocking {
        database.put(metaAccount(id = 1, position = 0, isSelected = true))
        database.put(metaAccount(id = 2, position = 1))
        database.put(metaAccount(id = 3, position = 2))
        database.failBeforeTransactionNumber = 2
        expectFailure<InjectedDatabaseFailure> {
            coordinator.delete(1)
        }
        database.put(requireNotNull(database.account(2)).copyForTest(position = 100))
        database.put(requireNotNull(database.account(3)).copyForTest(position = 0))

        coordinator().reconcilePendingMutation()

        assertNull(database.account(1))
        assertEquals(setOf(3L), database.selectedIds())
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun deleteReplayRejectsChangedCryptographicIdentityAndKeepsJournal() = runBlocking {
        database.put(metaAccount(id = 1, position = 0, isSelected = true))
        database.failBeforeTransactionNumber = 2
        expectFailure<InjectedDatabaseFailure> {
            coordinator.delete(1)
        }
        database.put(
            requireNotNull(database.account(1)).copyForTest(
                tonPublicKey = byteArrayOf(9).repeatToSize(32)
            )
        )

        val failure = expectCoordinatorFailure {
            coordinator().reconcilePendingMutation()
        }

        assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
        assertNotNull(database.account(1))
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun deleteReplayRejectsNonEmptyDatabaseWithoutSelectionAndKeepsJournal() = runBlocking {
        database.put(metaAccount(id = 1, position = 0, isSelected = true))
        database.put(metaAccount(id = 2, position = 1))
        preferences.enqueueDurableFault(DurableFault.NONE, DurableFault.BEFORE)
        expectCoordinatorFailure {
            coordinator.delete(1)
        }
        database.forceNoSelection()

        val failure = expectCoordinatorFailure {
            coordinator().reconcilePendingMutation()
        }

        assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
        assertNull(database.account(1))
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun deleteReplayRejectsChangedChainAccountsAndKeepsJournal() = runBlocking {
        database.put(metaAccount(id = 1, position = 0, isSelected = true))
        database.chainAccountIds[1] = mutableListOf(byteArrayOf(1).repeatToSize(32))
        database.failBeforeTransactionNumber = 2
        expectFailure<InjectedDatabaseFailure> {
            coordinator.delete(1)
        }
        database.chainAccountIds[1] = mutableListOf(byteArrayOf(2).repeatToSize(32))

        val failure = expectCoordinatorFailure {
            coordinator().reconcilePendingMutation()
        }

        assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
        assertNotNull(database.account(1))
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun archiveRestoreAcceptsAllReleasedTonTextVariantsWithoutChangingRetainedBytes() = runBlocking {
        val resource = checkNotNull(javaClass.getResourceAsStream("/wallet-secret-vectors/12-validator-acceptance.json"))
        val vectors = resource.reader().use { JsonParser.parseReader(it).asJsonObject }
            .getAsJsonArray("vectors").map { it.asJsonObject }.filter { it["store"].asString == "TonSecrets(V3)" }
        assertEquals(24, vectors.size)
        for (vector in vectors) {
            setUp()
            val encoded = vector["stored"].asString
            val key = WalletSecretArchive.keyFor("7:TON_SECRETS")
            preferences.putEncryptedString(key, encoded)
            val snapshot = checkNotNull(preferences.getDecryptedStringSnapshot(key))
            val id = coordinator.restoreArchived(key, snapshot, WalletSecretScope.Ton, "Restored fixture")
            assertEquals(77L, id)
            assertEquals(encoded, preferences.value(key))
            assertEquals(encoded, preferences.value("77:TON_SECRETS"))
            assertFalse(checkNotNull(database.account(id)).isBackedUp)
            assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        }
    }

    @Test
    fun archiveRestoreReplaysAfterRoomFailureAndOrdinaryCreateStillRejectsBackupDrift() = runBlocking {
        val fixture = substrateFixture(9)
        val decoded = SubstrateSecrets.read(fixture.encoded)
        decoded[SubstrateSecrets.Seed] = ByteArray(32) { 11 }
        val historical = decoded.toHexString()
        val key = WalletSecretArchive.keyFor("7:SUBSTRATE_SECRETS")
        preferences.putEncryptedString(key, historical)
        val snapshot = checkNotNull(preferences.getDecryptedStringSnapshot(key))
        val prototype = substratePrototype(fixture)
        expectFailure<WalletSecretMutationCoordinatorException> {
            coordinator.create(prototype, historical, null, null)
        }
        // Give the recovery a fresh deterministic id after the rejected new-wallet preparation.
        identifiers = FakeWalletMutationIdentifierSource(listOf(78L), canonicalOperationIds())
        coordinator = coordinator()
        database.failOperationAfter = DatabaseOperation.INSERT
        expectFailure<Exception> {
            coordinator.restoreArchived(key, snapshot, WalletSecretScope.Substrate, "Restored fixture")
        }
        assertEquals(WalletSecretMutationJournalStore.Operation.RESTORE_ARCHIVE, journalStore.load()?.operation)
        coordinator.reconcilePendingMutation()
        assertEquals(historical, preferences.value("78:SUBSTRATE_SECRETS"))
        assertEquals(historical, preferences.value(key))
        assertNotNull(database.account(78))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun archiveRestoreRefusesAChangedPreviewAndPurgeRemovesOnlyTheConfirmedRecord() = runBlocking {
        val key = WalletSecretArchive.keyFor("7:ETHEREUM_SECRETS")
        val other = WalletSecretArchive.keyFor("8:ETHEREUM_SECRETS")
        preferences.putEncryptedString(key, ethereumFixture(1).encoded)
        preferences.putEncryptedString(other, "unreadable retained value")
        val stale = checkNotNull(preferences.getDecryptedStringSnapshot(key))
        preferences.putEncryptedString(key, ethereumFixture(2).encoded)
        expectFailure<IllegalStateException> {
            coordinator.restoreArchived(key, stale, WalletSecretScope.Ethereum, "Restored fixture")
        }
        expectFailure<IllegalStateException> { coordinator.purgeArchived(key, stale) }
        val current = checkNotNull(preferences.getDecryptedStringSnapshot(key))
        coordinator.purgeArchived(key, current)
        assertFalse(preferences.hasKey(key))
        assertEquals("unreadable retained value", preferences.value(other))
        assertNull(database.account(77))
    }

    @Test
    fun cancellationDuringPreviewRecheckCannotStartAnArchiveMutation() = runBlocking {
        val key = WalletSecretArchive.keyFor("7:ETHEREUM_SECRETS")
        preferences.putEncryptedString(key, ethereumFixture(1).encoded)
        val snapshot = checkNotNull(preferences.getDecryptedStringSnapshot(key))
        val operation = Job()
        val storage = object : EncryptedPreferences by preferences {
            override fun getDecryptedStringSnapshot(field: String): EncryptedPreferenceSnapshot? {
                if (field == key) operation.cancel()
                return preferences.getDecryptedStringSnapshot(field)
            }
        }
        val restoring = WalletSecretMutationCoordinator(database, WalletSecretMutationJournalStore(storage), storage, identifiers, Unit)
        expectFailure<CancellationException> {
            withContext(operation) { restoring.restoreArchived(key, snapshot, WalletSecretScope.Ethereum, "Restored fixture") }
        }
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertFalse(preferences.hasKey("77:ETHEREUM_SECRETS"))
        assertNull(database.account(77))
        assertEquals(snapshot.plaintext, preferences.value(key))
    }

    @Test
    fun purgeCannotTargetAnActiveWalletKey() = runBlocking {
        val key = "7:ETHEREUM_SECRETS"
        preferences.putEncryptedString(key, ethereumFixture(1).encoded)
        val snapshot = checkNotNull(preferences.getDecryptedStringSnapshot(key))
        expectFailure<IllegalArgumentException> { coordinator.purgeArchived(key, snapshot) }
        assertTrue(preferences.hasKey(key))
    }

    private fun coordinator(
        database: FakeWalletMutationDatabase = this.database,
        preferences: FaultInjectingEncryptedPreferences = this.preferences,
        identifiers: FakeWalletMutationIdentifierSource = this.identifiers
    ): WalletSecretMutationCoordinator {
        return WalletSecretMutationCoordinator(
            database = database,
            journalStore = WalletSecretMutationJournalStore(preferences),
            encryptedPreferences = preferences,
            identifierSource = identifiers,
            testOnly = Unit
        )
    }

    private fun preferencesAndDatabaseEvents(): List<String> {
        return (preferences.events + database.events)
            .sortedBy(Event::sequence)
            .map(Event::name)
    }

    private fun substrateFixture(lastPrivateByte: Int): SubstrateFixture {
        val privateKey = privateKey(lastPrivateByte)
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey)
        return SubstrateFixture(
            publicKey = keypair.publicKey,
            accountId = keypair.publicKey.substrateAccountId(),
            encoded = SubstrateSecrets(
                substrateKeyPair = keypair,
                seed = privateKey
            ).toHexString()
        )
    }

    private fun ethereumFixture(lastPrivateByte: Int): EthereumFixture {
        val privateKey = privateKey(lastPrivateByte)
        val keypair = EthereumKeypairFactory.createWithPrivateKey(privateKey)
        return EthereumFixture(
            publicKey = keypair.publicKey,
            address = keypair.publicKey.ethereumAddressFromPublicKey(),
            encoded = EthereumSecrets(
                seed = privateKey,
                ethereumKeypair = keypair
            ).toHexString()
        )
    }

    private fun tonFixture(variant: Int): TonFixture {
        val mnemonic = MnemonicCreator.fromEntropy(
            ByteArray(16).apply { this[lastIndex] = variant.toByte() }
        ).words
        val tonSeed = org.ton.mnemonic.Mnemonic.toSeed(mnemonic.split(' '))
        val privateKey = PrivateKeyEd25519(tonSeed)
        val publicKey = privateKey.publicKey()
        return TonFixture(
            publicKey = publicKey.key.toByteArray(),
            encoded = TonSecrets(
                seed = mnemonic.encodeToByteArray(),
                tonKeypair = jp.co.soramitsu.common.data.Keypair(
                    privateKey = privateKey.key.toByteArray(),
                    publicKey = publicKey.key.toByteArray()
                )
            ).toHexString()
        )
    }

    private fun fullSecretFixtures(): FullSecretFixtures {
        val substrate = substrateFixture(1)
        val ethereum = ethereumFixture(2)
        val ton = tonFixture(3)
        return FullSecretFixtures(
            substrate = substrate,
            ethereum = ethereum,
            ton = ton,
            prototype = MetaAccountLocal(
                substratePublicKey = substrate.publicKey,
                substrateCryptoType = CryptoType.ECDSA,
                substrateAccountId = substrate.accountId,
                ethereumPublicKey = ethereum.publicKey,
                ethereumAddress = ethereum.address,
                tonPublicKey = ton.publicKey,
                name = "Full wallet",
                isSelected = true,
                position = 0,
                isBackedUp = false,
                googleBackupAddress = null,
                initialized = false
            )
        )
    }

    private fun substratePrototype(
        fixture: SubstrateFixture,
        isSelected: Boolean = false,
        position: Int = 0
    ): MetaAccountLocal {
        return MetaAccountLocal(
            substratePublicKey = fixture.publicKey,
            substrateCryptoType = CryptoType.ECDSA,
            substrateAccountId = fixture.accountId,
            ethereumPublicKey = null,
            ethereumAddress = null,
            tonPublicKey = null,
            name = "Substrate wallet",
            isSelected = isSelected,
            position = position,
            isBackedUp = false,
            googleBackupAddress = null,
            initialized = false
        )
    }

    private fun ethereumPrototype(fixture: EthereumFixture): MetaAccountLocal {
        return MetaAccountLocal(
            substratePublicKey = null,
            substrateCryptoType = null,
            substrateAccountId = null,
            ethereumPublicKey = fixture.publicKey,
            ethereumAddress = fixture.address,
            tonPublicKey = null,
            name = "Ethereum wallet",
            isSelected = false,
            position = 0,
            isBackedUp = false,
            googleBackupAddress = null,
            initialized = false
        )
    }

    private fun tonPrototype(fixture: TonFixture): MetaAccountLocal {
        return MetaAccountLocal(
            substratePublicKey = null,
            substrateCryptoType = null,
            substrateAccountId = null,
            ethereumPublicKey = null,
            ethereumAddress = null,
            tonPublicKey = fixture.publicKey,
            name = "TON wallet",
            isSelected = false,
            position = 0,
            isBackedUp = false,
            googleBackupAddress = null,
            initialized = false
        )
    }

    private fun metaAccount(
        id: Long,
        position: Int,
        isSelected: Boolean = false
    ): MetaAccountLocal {
        return MetaAccountLocal(
            substratePublicKey = null,
            substrateCryptoType = null,
            substrateAccountId = null,
            ethereumPublicKey = null,
            ethereumAddress = null,
            tonPublicKey = byteArrayOf(id.toByte()).repeatToSize(32),
            name = "Wallet $id",
            isSelected = isSelected,
            position = position,
            isBackedUp = false,
            googleBackupAddress = null,
            initialized = false
        ).apply {
            this.id = id
        }
    }

    private fun journalImage(account: MetaAccountLocal) =
        WalletSecretMutationJournalStore.PublicAfterImage(
            name = account.name,
            substratePublicKeyHex = account.substratePublicKey?.let(::hex),
            substrateAccountIdHex = account.substrateAccountId?.let(::hex),
            substrateCryptoType = account.substrateCryptoType?.let {
                WalletSecretMutationJournalStore.SubstrateCryptoType.valueOf(it.name)
            },
            ethereumPublicKeyHex = account.ethereumPublicKey?.let(::hex),
            ethereumAddressHex = account.ethereumAddress?.let(::hex),
            tonPublicKeyHex = account.tonPublicKey?.let(::hex),
            isSelected = account.isSelected,
            position = account.position,
            isBackedUp = account.isBackedUp,
            googleBackupAddress = account.googleBackupAddress,
            initialized = account.initialized
        )

    private suspend fun expectCoordinatorFailure(
        block: suspend () -> Unit
    ): WalletSecretMutationCoordinatorException {
        return expectFailure(block)
    }

    private suspend inline fun <reified T : Throwable> expectFailure(
        noinline block: suspend () -> Unit
    ): T {
        try {
            block()
        } catch (failure: Throwable) {
            if (failure is T) return failure
            throw AssertionError(
                "Expected ${T::class.java.name}, got ${failure::class.java.name}",
                failure
            )
        }
        fail("Expected ${T::class.java.name}")
        error("unreachable")
    }

    private data class SubstrateFixture(
        val publicKey: ByteArray,
        val accountId: ByteArray,
        val encoded: String
    )

    private data class EthereumFixture(
        val publicKey: ByteArray,
        val address: ByteArray,
        val encoded: String
    )

    private data class TonFixture(
        val publicKey: ByteArray,
        val encoded: String
    )

    private data class FullSecretFixtures(
        val substrate: SubstrateFixture,
        val ethereum: EthereumFixture,
        val ton: TonFixture,
        val prototype: MetaAccountLocal
    )

    private companion object {
        const val PASSWORD_PHRASE =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val OTHER_PHRASE = "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
        const val UNICODE_PHRASE = "abandon amount liar amount expire adjust cage candy arch gather drum bullet " +
            "absurd math era live bid rhythm alien crouch range attend journey unaware"
    }
}

private class FakeWalletMutationIdentifierSource(
    metaIds: List<Long>,
    operationIds: List<String>
) : WalletMutationIdentifierSource {

    private val metaIds = ArrayDeque(metaIds)
    private val operationIds = ArrayDeque(operationIds)

    @Synchronized
    override fun nextMetaIdCandidate(): Long {
        return metaIds.removeFirstOrNull()
            ?: error("No deterministic meta id remains")
    }

    @Synchronized
    override fun nextOperationId(): String {
        return operationIds.removeFirstOrNull()
            ?: error("No deterministic operation id remains")
    }
}

private enum class DurableFault {
    NONE,
    BEFORE,
    AFTER
}

private class FaultInjectingEncryptedPreferences : EncryptedPreferences {

    private val values = linkedMapOf<String, String>()
    private val durableFaults = ArrayDeque<DurableFault>()
    val events = mutableListOf<Event>()
    var keysWithPrefixesFailure: Throwable? = null

    @Synchronized
    fun enqueueDurableFault(vararg faults: DurableFault) {
        durableFaults.addAll(faults)
    }

    @Synchronized
    fun keys(): Set<String> = values.keys.toSet()

    @Synchronized
    fun value(key: String): String? = values[key]

    @Synchronized
    override fun putEncryptedString(field: String, value: String) {
        values[field] = value
    }

    @Synchronized
    override fun getDecryptedString(field: String): String? = values[field]

    @Synchronized
    override fun hasKey(field: String): Boolean = field in values

    @Synchronized
    override fun hasKeyWithPrefix(prefix: String): Boolean {
        return values.keys.any { it.startsWith(prefix) }
    }

    @Synchronized
    override fun keysWithPrefixes(
        prefixes: Set<String>,
        maxResultCount: Int,
        maxKeyBytes: Int,
        maxTotalKeyBytes: Int,
        failOnOversizedMatch: Boolean
    ): Set<String> {
        keysWithPrefixesFailure?.let { failure ->
            keysWithPrefixesFailure = null
            throw failure
        }
        var totalBytes = 0
        return values.keys
            .filter { key ->
                if (!prefixes.any(key::startsWith)) {
                    false
                } else {
                    val withinBound = key.encodeToByteArray().size <= maxKeyBytes
                    check(withinBound || !failOnOversizedMatch)
                    withinBound
                }
            }
            .sorted()
            .onEach {
                check(totalBytes <= maxTotalKeyBytes - it.encodeToByteArray().size)
                totalBytes += it.encodeToByteArray().size
            }
            .also { check(it.size <= maxResultCount) }
            .toSet()
    }

    @Synchronized
    override fun removeKey(field: String) {
        values.remove(field)
    }

    @Synchronized
    override fun replaceEncryptedStringsDurably(
        valuesToPut: Map<String, String>,
        keysToRemove: Set<String>
    ) {
        val fault = durableFaults.removeFirstOrNull() ?: DurableFault.NONE
        if (fault == DurableFault.BEFORE) throw InjectedDurabilityFailure()

        keysToRemove.forEach(values::remove)
        values.putAll(valuesToPut)
        events += Event(
            name = when {
                WalletSecretMutationJournalStore.JOURNAL_KEY in valuesToPut -> "stage"
                keysToRemove.size > 1 -> "completeDelete"
                else -> "clear"
            },
            sequence = nextEventSequence()
        )

        if (fault == DurableFault.AFTER) throw InjectedDurabilityFailure()
    }

    @Synchronized
    override fun quarantineEncryptedStringDurably(
        sourceKey: String,
        quarantineKey: String,
        expectedSnapshot: EncryptedPreferenceSnapshot
    ): Boolean {
        val current = values[sourceKey]
        if (current == null) {
            val quarantined = values[quarantineKey]
            checkNotNull(quarantined)
            return expectedSnapshot.matchesUnencryptedStorageValue(
                quarantined
            )
        }
        if (!expectedSnapshot.matchesUnencryptedStorageValue(current)) {
            return false
        }
        values.remove(sourceKey)
        values[quarantineKey] = current
        return true
    }

    override fun requireDurableStorageHealthy() = Unit
}

private class InjectedDurabilityFailure : IllegalStateException()

private enum class DatabaseOperation {
    INSERT,
    UPDATE,
    DELETE
}

private class FakeWalletMutationDatabase : WalletMutationDatabase {

    private val accountRows = linkedMapOf<Long, MetaAccountLocal>()
    private val custodyRows = linkedMapOf<Long, WalletCustodyLocal>()
    val chainAccountIds = linkedMapOf<Long, MutableList<ByteArray>>()
    val accountChainIds = linkedMapOf<Long, List<String>>()
    val tonConnectionOwners =
        mutableListOf<Pair<Long, TonConnectionSecretOwner>>()
    val assetMetaIds = mutableSetOf<Long>()
    val events = mutableListOf<Event>()

    var failBeforeTransactionNumber: Int? = null
    var failAfterTransactionNumber: Int? = null
    var failOperationAfter: DatabaseOperation? = null
    var dropIsBackedUpOnNextUpdate = false
    private var transactionNumber = 0

    override suspend fun <T> inTransaction(
        block: suspend WalletMutationDatabase.() -> T
    ): T {
        transactionNumber += 1
        val currentTransaction = transactionNumber
        if (failBeforeTransactionNumber == currentTransaction) {
            failBeforeTransactionNumber = null
            throw InjectedDatabaseFailure()
        }
        val accountsSnapshot = accountRows.mapValues { (_, value) -> value.copyForTest() }
        val custodySnapshot = custodyRows.toMap()
        val chainSnapshot = chainAccountIds.mapValues { (_, value) ->
            value.map(ByteArray::clone).toMutableList()
        }
        val assetSnapshot = assetMetaIds.toMutableSet()
        val tonConnectionSnapshot = tonConnectionOwners.toList()

        try {
            val result = block(this)
            if (failAfterTransactionNumber == currentTransaction) {
                failAfterTransactionNumber = null
                throw InjectedDatabaseFailure()
            }
            return result
        } catch (failure: Throwable) {
            accountRows.clear()
            accountRows.putAll(accountsSnapshot)
            custodyRows.clear()
            custodyRows.putAll(custodySnapshot)
            chainAccountIds.clear()
            chainAccountIds.putAll(chainSnapshot)
            assetMetaIds.clear()
            assetMetaIds.addAll(assetSnapshot)
            tonConnectionOwners.clear()
            tonConnectionOwners.addAll(tonConnectionSnapshot)
            throw failure
        }
    }

    override suspend fun getMetaAccounts(): List<MetaAccountLocal> {
        return accountRows.values.map(MetaAccountLocal::copyForTest)
    }

    override suspend fun getMetaAccount(metaId: Long): MetaAccountLocal? {
        return accountRows[metaId]?.copyForTest()
    }

    override suspend fun getCustody(metaId: Long): WalletCustodyLocal? = custodyRows[metaId]

    override suspend fun insertCustody(marker: WalletCustodyLocal) {
        check(custodyRows.putIfAbsent(marker.metaId, marker) == null)
    }

    override suspend fun deleteCustody(metaId: Long) {
        custodyRows.remove(metaId)
    }

    override suspend fun metaAccountExists(metaId: Long): Boolean {
        return metaId in accountRows
    }

    override suspend fun hasIdentityConflict(
        metaId: Long,
        substrateAccountId: ByteArray?,
        ethereumAddress: ByteArray?,
        tonPublicKey: ByteArray?
    ): Boolean {
        return accountRows.values.any { account ->
            account.id != metaId && (
                substrateAccountId.matchesNonNull(account.substrateAccountId) ||
                    ethereumAddress.matchesNonNull(account.ethereumAddress) ||
                    tonPublicKey.matchesNonNull(account.tonPublicKey)
                )
        }
    }

    override suspend fun getAccountChainIds(metaId: Long): List<String> = accountChainIds[metaId].orEmpty()

    override suspend fun getChainAccountIds(metaId: Long): List<ByteArray> {
        return chainAccountIds[metaId].orEmpty().map(ByteArray::clone)
    }

    override suspend fun getTonConnectionSecretOwners(
        metaId: Long
    ): List<TonConnectionSecretOwner> {
        return tonConnectionOwners
            .filter { (ownerMetaId) -> ownerMetaId == metaId }
            .map { (_, owner) -> owner }
    }

    override suspend fun hasOtherTonConnectionOwner(
        metaId: Long,
        clientId: String
    ): Boolean {
        return tonConnectionOwners.any { (ownerMetaId, owner) ->
            ownerMetaId != metaId && owner.clientId == clientId
        }
    }

    override suspend fun hasAssets(metaId: Long): Boolean {
        return metaId in assetMetaIds
    }

    override suspend fun getNextPosition(): Int {
        return (accountRows.values.maxOfOrNull(MetaAccountLocal::position) ?: 0) + 1
    }

    override suspend fun insertMetaAccount(metaAccount: MetaAccountLocal): Long {
        check(metaAccount.id !in accountRows)
        accountRows[metaAccount.id] = metaAccount.copyForTest()
        events += Event("insert:${metaAccount.id}", nextEventSequence())
        failAfter(DatabaseOperation.INSERT)
        return metaAccount.id
    }

    override suspend fun updateMetaAccount(metaAccount: MetaAccountLocal) {
        check(metaAccount.id in accountRows)
        val current = accountRows.getValue(metaAccount.id)
        val stored = if (dropIsBackedUpOnNextUpdate) {
            dropIsBackedUpOnNextUpdate = false
            metaAccount.copyForTest(isBackedUp = current.isBackedUp)
        } else {
            metaAccount.copyForTest()
        }
        accountRows[metaAccount.id] = stored
        events += Event("update:${metaAccount.id}", nextEventSequence())
        failAfter(DatabaseOperation.UPDATE)
    }

    override suspend fun selectMetaAccount(metaId: Long) {
        check(metaId in accountRows)
        accountRows.replaceAll { id, account ->
            account.copyForTest(isSelected = id == metaId)
        }
        events += Event("select:$metaId", nextEventSequence())
    }

    override suspend fun deleteMetaAccountAndSelectSuccessor(metaId: Long): Boolean {
        val target = accountRows[metaId] ?: return false
        val successorId = if (target.isSelected) {
            accountRows.values.asSequence()
                .filter { it.id != metaId }
                .sortedWith(compareBy<MetaAccountLocal>({ it.position }, { it.id }))
                .firstOrNull()
                ?.id
        } else {
            null
        }
        accountRows.remove(metaId)
        custodyRows.remove(metaId)
        chainAccountIds.remove(metaId)
        tonConnectionOwners.removeAll { (ownerMetaId) ->
            ownerMetaId == metaId
        }
        assetMetaIds.remove(metaId)
        successorId?.let(::forceSelect)
        events += Event("delete:$metaId", nextEventSequence())
        failAfter(DatabaseOperation.DELETE)
        return true
    }

    fun put(account: MetaAccountLocal) {
        accountRows[account.id] = account.copyForTest()
    }

    fun account(metaId: Long): MetaAccountLocal? = accountRows[metaId]?.copyForTest()

    fun custody(metaId: Long): WalletCustodyLocal? = custodyRows[metaId]

    fun accounts(): List<MetaAccountLocal> = accountRows.values.map(MetaAccountLocal::copyForTest)

    fun selectedIds(): Set<Long> {
        return accountRows.values.filter(MetaAccountLocal::isSelected)
            .map(MetaAccountLocal::id)
            .toSet()
    }

    fun forceSelect(metaId: Long) {
        check(metaId in accountRows)
        accountRows.replaceAll { id, account ->
            account.copyForTest(isSelected = id == metaId)
        }
    }

    fun forceNoSelection() {
        accountRows.replaceAll { _, account ->
            account.copyForTest(isSelected = false)
        }
    }

    private fun failAfter(operation: DatabaseOperation) {
        if (failOperationAfter == operation) {
            failOperationAfter = null
            throw InjectedDatabaseFailure()
        }
    }
}

private class InjectedDatabaseFailure : IllegalStateException()

private data class Event(
    val name: String,
    val sequence: Long
)

private var eventSequence = 0L

@Synchronized
private fun nextEventSequence(): Long {
    eventSequence += 1
    return eventSequence
}

private fun MetaAccountLocal.copyForTest(
    id: Long = this.id,
    substratePublicKey: ByteArray? = this.substratePublicKey,
    substrateCryptoType: CryptoType? = this.substrateCryptoType,
    substrateAccountId: ByteArray? = this.substrateAccountId,
    ethereumPublicKey: ByteArray? = this.ethereumPublicKey,
    ethereumAddress: ByteArray? = this.ethereumAddress,
    tonPublicKey: ByteArray? = this.tonPublicKey,
    name: String = this.name,
    isSelected: Boolean = this.isSelected,
    position: Int = this.position,
    isBackedUp: Boolean = this.isBackedUp,
    googleBackupAddress: String? = this.googleBackupAddress,
    initialized: Boolean = this.initialized
): MetaAccountLocal {
    return MetaAccountLocal(
        substratePublicKey = substratePublicKey?.clone(),
        substrateCryptoType = substrateCryptoType,
        substrateAccountId = substrateAccountId?.clone(),
        ethereumPublicKey = ethereumPublicKey?.clone(),
        ethereumAddress = ethereumAddress?.clone(),
        tonPublicKey = tonPublicKey?.clone(),
        name = name,
        isSelected = isSelected,
        position = position,
        isBackedUp = isBackedUp,
        googleBackupAddress = googleBackupAddress,
        initialized = initialized
    ).apply {
        this.id = id
    }
}

private fun ByteArray?.matchesNonNull(other: ByteArray?): Boolean {
    return this != null && other != null && contentEquals(other)
}

private fun ByteArray.repeatToSize(size: Int): ByteArray {
    require(isNotEmpty())
    return ByteArray(size) { this[it % this.size] }
}

private fun privateKey(lastByte: Int): ByteArray {
    return ByteArray(32).apply {
        this[lastIndex] = lastByte.toByte()
    }
}

private fun canonicalOperationIds(): List<String> {
    return (1..128).map { index ->
        "00000000-0000-4000-8000-${index.toString().padStart(12, '0')}"
    }
}

private fun hex(bytes: ByteArray): String {
    return bytes.joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
