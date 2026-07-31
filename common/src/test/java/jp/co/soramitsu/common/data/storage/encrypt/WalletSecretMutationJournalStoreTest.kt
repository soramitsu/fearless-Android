package jp.co.soramitsu.common.data.storage.encrypt

import java.io.IOException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.FailureReason
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.Journal
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.JournalException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.Operation
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.PublicAfterImage
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.StagedMutation
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.SubstrateCryptoType
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSecretMutationJournalStoreTest {

    @Test
    fun stageCreateAtomicallyPersistsCanonicalJournalAndExactFinalSecrets() {
        val preferences = RecordingEncryptedPreferences()
        val store = WalletSecretMutationJournalStore(preferences)
        val journal = createJournal()
        val exactSecrets = linkedMapOf(
            TON_SECRET_KEY to "  ton-secret\u0000 ",
            SUBSTRATE_SECRET_KEY to "substrate-secret",
            ETHEREUM_SECRET_KEY to "ethereum-secret"
        )

        store.stage(journal, exactSecrets)

        assertEquals(1, preferences.replacements.size)
        val replacement = preferences.replacements.single()
        assertEquals(
            setOf(
                WalletSecretMutationJournalStore.JOURNAL_KEY,
                SUBSTRATE_SECRET_KEY,
                ETHEREUM_SECRET_KEY,
                TON_SECRET_KEY
            ),
            replacement.valuesToPut.keys
        )
        assertEquals(emptySet<String>(), replacement.keysToRemove)
        exactSecrets.forEach { (key, plaintext) ->
            assertEquals(plaintext, replacement.valuesToPut[key])
            assertEquals(plaintext, preferences.value(key))
        }
        val encodedJournal = replacement.valuesToPut.getValue(
            WalletSecretMutationJournalStore.JOURNAL_KEY
        )
        exactSecrets.values.forEach { secret ->
            assertFalse(encodedJournal.contains(secret))
        }
        assertEquals(journal, store.load())
        assertEquals(
            WalletSecretMutationJournalStore.StagedMutation(
                journal = journal,
                secretPlaintexts = exactSecrets
            ),
            store.loadStagedMutation()
        )
    }

    @Test
    fun stageAddEvmRoundTripsEscapedPublicAfterImage() {
        val preferences = RecordingEncryptedPreferences()
        val store = WalletSecretMutationJournalStore(preferences)
        val afterImage = fullAfterImage().copy(
            name = "Fearless \"<wallet>\" \uD83E\uDD81",
            googleBackupAddress = "public+backup@example.test"
        )
        val journal = addEvmJournal(afterImage = afterImage)

        store.stage(journal, mapOf(ETHEREUM_SECRET_KEY to "exact-evm-secret"))

        assertEquals(journal, store.load())
        assertEquals(
            mapOf(ETHEREUM_SECRET_KEY to "exact-evm-secret"),
            store.loadStagedMutation()?.secretPlaintexts
        )
        val encoded = preferences.value(WalletSecretMutationJournalStore.JOURNAL_KEY)
            .orEmpty()
        assertTrue(encoded.contains("\\u003cwallet\\u003e"))
        assertTrue(encoded.contains("\\\""))
    }

    @Test
    fun stageDeletePersistsOnlyJournalAndCompleteDeleteRemovesExactSecrets() {
        val preferences = RecordingEncryptedPreferences()
        val store = WalletSecretMutationJournalStore(preferences)
        preferences.putRaw(SUBSTRATE_SECRET_KEY, "existing-secret")
        preferences.putRaw(UNRELATED_SECRET_KEY, "unrelated-secret")
        val journal = deleteJournal()

        store.stage(journal, emptyMap())

        val stageReplacement = preferences.replacements.single()
        assertEquals(
            setOf(WalletSecretMutationJournalStore.JOURNAL_KEY),
            stageReplacement.valuesToPut.keys
        )
        assertEquals(journal, store.load())
        assertEquals(
            emptyMap<String, String>(),
            store.loadStagedMutation()?.secretPlaintexts
        )

        assertFailure(FailureReason.CONFLICT) {
            store.clear(StagedMutation(journal, emptyMap()))
        }
        store.completeDelete(OPERATION_ID)

        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertFalse(preferences.hasKey(SUBSTRATE_SECRET_KEY))
        assertEquals("unrelated-secret", preferences.value(UNRELATED_SECRET_KEY))
        assertEquals(
            journal.secretKeysToRemove + WalletSecretMutationJournalStore.JOURNAL_KEY,
            preferences.replacements.last().keysToRemove
        )
    }

    @Test
    fun loadReturnsNullOnlyWhenJournalKeyIsAbsent() {
        val preferences = RecordingEncryptedPreferences()
        val store = WalletSecretMutationJournalStore(preferences)

        assertNull(store.load())

        preferences.putRaw(WalletSecretMutationJournalStore.JOURNAL_KEY, "")
        assertFailure(FailureReason.MALFORMED_STORED_JOURNAL) {
            store.load()
        }

        preferences.nullReadKeys += WalletSecretMutationJournalStore.JOURNAL_KEY
        assertFailure(FailureReason.MALFORMED_STORED_JOURNAL) {
            store.load()
        }
    }

    @Test
    fun validateAcceptsAllSupportedOperationShapesWithoutWriting() {
        val preferences = RecordingEncryptedPreferences()
        val store = WalletSecretMutationJournalStore(preferences)

        store.validate(createJournal())
        store.validate(addEvmJournal())
        store.validate(deleteJournal())

        assertTrue(preferences.replacements.isEmpty())
        assertTrue(preferences.values.isEmpty())
    }

    @Test
    fun deleteJournalBindsEveryChainSecretAndQuarantineToTheWallet() {
        val chainAccountIds = linkedSetOf(
            "aa".repeat(32),
            "bb".repeat(20)
        )
        val journal = deleteJournal(
            chainAccountIdsHex = chainAccountIds,
            selectedMetaIdAfterDelete = META_ID + 1
        )
        val preferences = RecordingEncryptedPreferences()
        val store = WalletSecretMutationJournalStore(preferences)

        assertEquals(
            journal.secretKeysToRemove,
            store.deletionSecretKeys(
                metaId = META_ID,
                beforeImage = journal.beforeImage!!,
                chainAccountIdsHex = chainAccountIds
            )
        )
        store.stage(journal, emptyMap())

        assertEquals(journal, store.load())
        chainAccountIds.forEach { accountIdHex ->
            val activeKey = "$META_ID:$accountIdHex:ACCESS_SECRETS"
            assertTrue(activeKey in journal.secretKeysToRemove)
            assertTrue(
                "wallet_secret_quarantine:$activeKey" in journal.secretKeysToRemove
            )
        }
        assertFailure(FailureReason.INVALID_ARGUMENT) {
            store.validate(
                journal.copy(
                    secretKeysToRemove =
                        journal.secretKeysToRemove + "unrelated_preference"
                )
            )
        }
        assertFailure(FailureReason.INVALID_ARGUMENT) {
            store.deletionSecretKeys(
                metaId = 0,
                beforeImage = journal.beforeImage!!,
                chainAccountIdsHex = chainAccountIds
            )
        }
        assertFailure(FailureReason.INVALID_ARGUMENT) {
            store.deletionSecretKeys(
                metaId = META_ID,
                beforeImage = journal.beforeImage!!,
                chainAccountIdsHex = setOf("not-hex")
            )
        }
    }

    @Test
    fun deletionDiscoversOnlyCanonicalOrphanedV2ChainSecretsForExactWallet() {
        val orphan20 = "ab".repeat(20)
        val orphan64 = "cd".repeat(64)
        val owned = setOf(
            "$META_ID:$orphan20:ACCESS_SECRETS",
            "wallet_secret_quarantine:$META_ID:$orphan20:ACCESS_SECRETS",
            "$META_ID:$orphan64:ACCESS_SECRETS",
            "wallet_secret_quarantine:$META_ID:$orphan64:ACCESS_SECRETS"
        )
        val unrelated = setOf(
            "${META_ID}0:$orphan20:ACCESS_SECRETS",
            "${META_ID + 1}:$orphan20:ACCESS_SECRETS",
            "$META_ID:${orphan20.uppercase()}:ACCESS_SECRETS",
            "$META_ID:${"ab".repeat(19)}:ACCESS_SECRETS",
            "$META_ID:$orphan20:ACCESS_SECRETS_suffix",
            "$META_ID:not_a_wallet_secret",
            "wallet_secret_quarantine:${META_ID + 1}:$orphan64:ACCESS_SECRETS"
        )
        val preferences = RecordingEncryptedPreferences().apply {
            (owned + unrelated).forEach { putRaw(it, "must-not-be-read") }
        }
        val store = WalletSecretMutationJournalStore(preferences)

        val keys = store.deletionSecretKeys(
            metaId = META_ID,
            beforeImage = fullAfterImage(),
            chainAccountIdsHex = emptySet()
        )

        assertTrue(keys.containsAll(owned))
        assertTrue(keys.intersect(unrelated).isEmpty())
        assertTrue(preferences.decryptedReads.isEmpty())
        store.validate(deleteJournal().copy(secretKeysToRemove = keys))
    }

    @Test
    fun deletionFailsClosedWhenV2NamespaceEnumerationWouldTruncate() {
        val oversized = "$META_ID:${"a".repeat(1_100)}:ACCESS_SECRETS"
        val oversizedPreferences = RecordingEncryptedPreferences().apply {
            putRaw(oversized, "must-not-be-read")
        }
        assertFailure(FailureReason.INVALID_ARGUMENT) {
            WalletSecretMutationJournalStore(oversizedPreferences).deletionSecretKeys(
                metaId = META_ID,
                beforeImage = fullAfterImage(),
                chainAccountIdsHex = emptySet()
            )
        }

        val overflowingPreferences = RecordingEncryptedPreferences().apply {
            repeat(4_097) { index ->
                putRaw("$META_ID:unrelated_$index", "must-not-be-read")
            }
        }
        assertFailure(FailureReason.INVALID_ARGUMENT) {
            WalletSecretMutationJournalStore(overflowingPreferences).deletionSecretKeys(
                metaId = META_ID,
                beforeImage = fullAfterImage(),
                chainAccountIdsHex = emptySet()
            )
        }

        assertTrue(oversizedPreferences.decryptedReads.isEmpty())
        assertTrue(overflowingPreferences.decryptedReads.isEmpty())
    }

    @Test
    fun frozenVersionOneDeleteFixtureParsesAndRediscoversOrphanedV2Keys() {
        val orphanKey = "$META_ID:${"ef".repeat(32)}:ACCESS_SECRETS"
        val quarantinedOrphanKey = "wallet_secret_quarantine:$orphanKey"
        val ambiguousLegacyPublicMarker =
            "wallet_secret_quarantine:legacy_v1_public_${"11".repeat(32)}"
        val unrelated = "${META_ID + 1}:${"ef".repeat(32)}:ACCESS_SECRETS"
        val preferences = RecordingEncryptedPreferences().apply {
            putRaw(
                WalletSecretMutationJournalStore.JOURNAL_KEY,
                frozenVersionOneDeleteJournal()
            )
            putRaw(SUBSTRATE_SECRET_KEY, "old-v1-substrate")
            putRaw(orphanKey, "old-v1-orphan")
            putRaw(quarantinedOrphanKey, "old-v1-quarantine")
            putRaw(ambiguousLegacyPublicMarker, "belongs-to-duplicate-wallet")
            putRaw(unrelated, "keep")
        }
        val store = WalletSecretMutationJournalStore(preferences)

        val loaded = requireNotNull(store.load())

        assertEquals(WalletSecretMutationJournalStore.CURRENT_VERSION, loaded.version)
        assertEquals(OPERATION_ID, loaded.operationId)
        assertEquals(Operation.DELETE, loaded.operation)
        assertEquals(META_ID, loaded.metaId)
        assertFalse(orphanKey in loaded.secretKeysToRemove)
        assertFalse(quarantinedOrphanKey in loaded.secretKeysToRemove)

        store.completeDelete(OPERATION_ID)

        assertFalse(preferences.hasKey(SUBSTRATE_SECRET_KEY))
        assertFalse(preferences.hasKey(orphanKey))
        assertFalse(preferences.hasKey(quarantinedOrphanKey))
        assertEquals(
            "belongs-to-duplicate-wallet",
            preferences.value(ambiguousLegacyPublicMarker)
        )
        assertEquals("keep", preferences.value(unrelated))
        assertFalse(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertTrue(
            preferences.replacements.last().keysToRemove.containsAll(
                setOf(orphanKey, quarantinedOrphanKey)
            )
        )
    }

    @Test
    fun tamperedFrozenVersionOneDeleteFixtureFailsClosedWithoutRemovingSecrets() {
        val orphanKey = "$META_ID:${"ef".repeat(32)}:ACCESS_SECRETS"
        val tampered = frozenVersionOneDeleteJournal().replace(
            "\"metaId\":$META_ID",
            "\"metaId\":${META_ID + 1}"
        )
        val preferences = RecordingEncryptedPreferences().apply {
            putRaw(WalletSecretMutationJournalStore.JOURNAL_KEY, tampered)
            putRaw(SUBSTRATE_SECRET_KEY, "old-v1-substrate")
            putRaw(orphanKey, "old-v1-orphan")
        }
        val store = WalletSecretMutationJournalStore(preferences)

        assertFailure(FailureReason.MALFORMED_STORED_JOURNAL) {
            store.load()
        }
        assertFailure(FailureReason.MALFORMED_STORED_JOURNAL) {
            store.completeDelete(OPERATION_ID)
        }

        assertEquals(
            tampered,
            preferences.value(WalletSecretMutationJournalStore.JOURNAL_KEY)
        )
        assertEquals("old-v1-substrate", preferences.value(SUBSTRATE_SECRET_KEY))
        assertEquals("old-v1-orphan", preferences.value(orphanKey))
        assertTrue(preferences.replacements.isEmpty())
    }

    @Test
    fun deletionDiscoversOnlyExactActiveAndQuarantinedLegacyKeysOwnedByAccountId() {
        val accountId = ByteArray(32) { 0x22 }
        val address0 = accountId.toAddress(0)
        val address42 = accountId.toAddress(42)
        val unrelatedAddress = ByteArray(32) { 0x23 }.toAddress(0)
        val owned = setOf(
            "security_source_$address0",
            "private_$address42",
            "seed_$address0",
            "entropy_$address42",
            "derivation_$address0",
            "wallet_secret_quarantine:security_source_$address42",
            "wallet_secret_quarantine:private_$address0",
            "wallet_secret_quarantine:seed_$address42",
            "wallet_secret_quarantine:entropy_$address0",
            "wallet_secret_quarantine:derivation_$address42"
        )
        val unrelated = setOf(
            "security_source_$unrelatedAddress",
            "wallet_secret_quarantine:private_$unrelatedAddress",
            "private_private_$address0",
            "security_source_${"x".repeat(4_096)}",
            "not_security_source_$address0"
        )
        val preferences = RecordingEncryptedPreferences().apply {
            (owned + unrelated).forEach { putRaw(it, "must-not-be-read") }
        }
        val store = WalletSecretMutationJournalStore(preferences)

        val keys = store.deletionSecretKeys(
            metaId = META_ID,
            beforeImage = fullAfterImage(),
            chainAccountIdsHex = emptySet()
        )

        assertTrue(keys.containsAll(owned))
        assertTrue(keys.intersect(unrelated).isEmpty())
        assertTrue(preferences.decryptedReads.isEmpty())
    }

    @Test
    fun deletionFailsClosedForPlausibleSs58CandidateWithBrokenChecksum() {
        val accountId = ByteArray(32) { 0x22 }
        val address = accountId.toAddress(0)
        val changedLast = if (address.last() == '1') '2' else '1'
        val ambiguousKey = "private_${address.dropLast(1)}$changedLast"
        val preferences = RecordingEncryptedPreferences().apply {
            putRaw(ambiguousKey, "must-not-be-read")
        }

        assertFailure(FailureReason.INVALID_ARGUMENT) {
            WalletSecretMutationJournalStore(preferences).deletionSecretKeys(
                metaId = META_ID,
                beforeImage = fullAfterImage(),
                chainAccountIdsHex = emptySet()
            )
        }
        assertTrue(preferences.decryptedReads.isEmpty())
    }

    @Test
    fun stagedDeleteKeepsExactLegacyNamesValidAfterPartialRemoval() {
        val address = ByteArray(32) { 0x22 }.toAddress(0)
        val legacyKeys = setOf(
            "security_source_$address",
            "wallet_secret_quarantine:seed_$address"
        )
        val preferences = RecordingEncryptedPreferences().apply {
            legacyKeys.forEach { putRaw(it, "must-not-be-read") }
        }
        val store = WalletSecretMutationJournalStore(preferences)
        val base = deleteJournal()
        val removalKeys = store.deletionSecretKeys(
            metaId = META_ID,
            beforeImage = requireNotNull(base.beforeImage),
            chainAccountIdsHex = emptySet()
        )
        val journal = base.copy(secretKeysToRemove = removalKeys)
        store.stage(journal, emptyMap())

        legacyKeys.forEach(preferences::removeRaw)

        assertEquals(journal, store.load())
    }

    @Test
    fun deletionAcceptsOnlyBoundedTonConnectKeysForTargetWallet() {
        val store = WalletSecretMutationJournalStore(
            RecordingEncryptedPreferences()
        )
        val scoped = TonConnectStorageKeys.scoped(
            metaId = META_ID,
            url = "https://target.example/connect",
            source = "WEB"
        )
        val legacy = TonConnectStorageKeys.legacy("aB".repeat(16))
        val validTonKeys = setOf(
            scoped,
            WalletSecretQuarantine.keyFor(scoped),
            legacy,
            WalletSecretQuarantine.keyFor(legacy)
        )

        val keys = store.deletionSecretKeys(
            metaId = META_ID,
            beforeImage = fullAfterImage(),
            chainAccountIdsHex = emptySet(),
            tonConnectSecretKeys = validTonKeys
        )

        assertTrue(keys.containsAll(validTonKeys))
        assertFailure(FailureReason.INVALID_ARGUMENT) {
            store.deletionSecretKeys(
                metaId = META_ID,
                beforeImage = fullAfterImage(),
                chainAccountIdsHex = emptySet(),
                tonConnectSecretKeys = setOf(
                    TonConnectStorageKeys.scoped(
                        metaId = META_ID + 1,
                        url = "https://other.example/connect",
                        source = "WEB"
                    )
                )
            )
        }
        assertFailure(FailureReason.INVALID_ARGUMENT) {
            store.deletionSecretKeys(
                metaId = META_ID,
                beforeImage = fullAfterImage(),
                chainAccountIdsHex = emptySet(),
                tonConnectSecretKeys = setOf(
                    "TON_CONNECT_SCOPED_V1_${META_ID}_not-a-digest"
                )
            )
        }
    }

    @Test
    fun tonConnectScopedDeletionInventoryIsExactAndNeverDecryptsValues() {
        val targetActive = TonConnectStorageKeys.scoped(
            metaId = META_ID,
            url = "https://target.example/active",
            source = "WEB"
        )
        val targetQuarantine = WalletSecretQuarantine.keyFor(
            TonConnectStorageKeys.scoped(
                metaId = META_ID,
                url = "https://target.example/quarantine",
                source = "QR"
            )
        )
        val otherActive = TonConnectStorageKeys.scoped(
            metaId = META_ID + 1,
            url = "https://other.example/active",
            source = "WEB"
        )
        val otherQuarantine = WalletSecretQuarantine.keyFor(otherActive)
        val preferences = RecordingEncryptedPreferences().apply {
            setOf(
                targetActive,
                targetQuarantine,
                otherActive,
                otherQuarantine
            ).forEach {
                putRaw(it, "must-not-be-read")
            }
        }

        val actual = WalletSecretMutationJournalStore(preferences)
            .tonConnectScopedDeletionKeys(META_ID)

        assertEquals(setOf(targetActive, targetQuarantine), actual)
        assertTrue(preferences.decryptedReads.isEmpty())
    }

    @Test
    fun tonConnectScopedDeletionInventoryRejectsMalformedPrefixMatch() {
        val malformed = TonConnectStorageKeys.scopedPrefix(META_ID) +
            "A".repeat(64)
        val preferences = RecordingEncryptedPreferences().apply {
            putRaw(malformed, "must-not-be-read")
        }

        assertFailure(FailureReason.CONFLICT) {
            WalletSecretMutationJournalStore(preferences)
                .tonConnectScopedDeletionKeys(META_ID)
        }

        assertTrue(preferences.hasKey(malformed))
        assertTrue(preferences.decryptedReads.isEmpty())
    }

    @Test
    fun deletionCandidateOverflowFailsClosedWithoutReadingValues() {
        val preferences = RecordingEncryptedPreferences().apply {
            repeat(4_097) { index ->
                putRaw(
                    "private_${"1".repeat(40)}${index.toString().padStart(7, '0')}",
                    "must-not-be-read"
                )
            }
        }

        assertFailure(FailureReason.INVALID_ARGUMENT) {
            WalletSecretMutationJournalStore(preferences).deletionSecretKeys(
                metaId = META_ID,
                beforeImage = fullAfterImage(),
                chainAccountIdsHex = emptySet()
            )
        }
        assertTrue(preferences.decryptedReads.isEmpty())
    }

    @Test
    fun hasSecretNamespaceFindsRootChainAndQuarantineKeysWithoutReadingValues() {
        val tonConnectKey = TonConnectStorageKeys.scoped(
            metaId = META_ID,
            url = "https://example.com/orphan",
            source = "QR"
        )
        val scenarios = listOf(
            "$META_ID:SUBSTRATE_SECRETS",
            "$META_ID:${"aa".repeat(32)}:ACCESS_SECRETS",
            "wallet_secret_quarantine:$META_ID:TON_SECRETS",
            "wallet_secret_quarantine:$META_ID:${"bb".repeat(32)}:ACCESS_SECRETS",
            "wallet_secret_quarantine:legacy_v1_meta_${META_ID}_public_" +
                "11".repeat(32),
            "wallet_secret_quarantine:legacy_v04_meta_${META_ID}_public_" +
                "11".repeat(32),
            WalletPublicIdentityRecovery.keyFor(
                META_ID,
                "$META_ID:ETHEREUM_SECRETS"
            ),
            tonConnectKey,
            WalletSecretQuarantine.keyFor(tonConnectKey)
        )

        scenarios.forEach { key ->
            val preferences = RecordingEncryptedPreferences().apply {
                putRaw(key, "must-not-be-read")
            }
            val store = WalletSecretMutationJournalStore(preferences)

            assertTrue(store.hasSecretNamespace(META_ID))
            assertTrue(preferences.decryptedReads.isEmpty())
            assertFalse(store.hasSecretNamespace(META_ID + 1))
        }

        assertFailure(FailureReason.INVALID_ARGUMENT) {
            WalletSecretMutationJournalStore(RecordingEncryptedPreferences())
                .hasSecretNamespace(0)
        }
    }

    @Test
    fun validateRejectsUnknownVersionsAndInvalidIdentifiers() {
        val store = WalletSecretMutationJournalStore(RecordingEncryptedPreferences())

        assertFailure(FailureReason.UNSUPPORTED_VERSION) {
            store.validate(createJournal().copy(version = 2))
        }
        listOf(0L, -1L, Long.MIN_VALUE).forEach { invalidMetaId ->
            assertFailure(FailureReason.INVALID_ARGUMENT) {
                store.validate(createJournal(metaId = invalidMetaId))
            }
        }
        listOf(
            "",
            "not-a-uuid",
            OPERATION_ID.uppercase(),
            "00000000-0000-0000-0000-000000000000",
            "123e4567-e89b-72d3-a456-426614174000",
            "$OPERATION_ID "
        ).forEach { invalidOperationId ->
            assertFailure(FailureReason.INVALID_ARGUMENT) {
                store.validate(createJournal(operationId = invalidOperationId))
            }
        }
    }

    @Test
    fun validateRejectsInvalidOperationSpecificAfterImages() {
        val store = WalletSecretMutationJournalStore(RecordingEncryptedPreferences())
        val emptyEcosystems = fullAfterImage().copy(
            substratePublicKeyHex = null,
            substrateAccountIdHex = null,
            substrateCryptoType = null,
            ethereumPublicKeyHex = null,
            ethereumAddressHex = null,
            tonPublicKeyHex = null
        )

        listOf(
            createJournal().copy(afterImage = null),
            createJournal().copy(
                afterImage = emptyEcosystems,
                secretKeysToPut = emptySet()
            ),
            createJournal().copy(beforeImage = fullAfterImage()),
            addEvmJournal().copy(afterImage = null),
            addEvmJournal().copy(beforeImage = null),
            addEvmJournal(
                afterImage = fullAfterImage().copy(
                    ethereumPublicKeyHex = null,
                    ethereumAddressHex = null
                )
            ),
            addEvmJournal().copy(
                afterImage = fullAfterImage().copy(tonPublicKeyHex = null)
            ),
            addEvmJournal().copy(chainAccountIdsHex = setOf("aa".repeat(32))),
            deleteJournal().copy(afterImage = fullAfterImage()),
            deleteJournal().copy(beforeImage = null),
            deleteJournal().copy(secretKeysToRemove = setOf(SUBSTRATE_SECRET_KEY)),
            deleteJournal().copy(selectedMetaIdAfterDelete = META_ID),
            deleteJournal().copy(chainAccountIdsHex = setOf("AA".repeat(32))),
            deleteJournal().copy(chainAccountIdsHex = setOf("aa")),
            deleteJournal().copy(
                secretKeysToPut = setOf(SUBSTRATE_SECRET_KEY)
            )
        ).forEach { invalidJournal ->
            assertFailure(FailureReason.INVALID_ARGUMENT) {
                store.validate(invalidJournal)
            }
        }
    }

    @Test
    fun validateRejectsPartialOrUnsafePublicAfterImageFields() {
        val store = WalletSecretMutationJournalStore(RecordingEncryptedPreferences())
        val valid = fullAfterImage()
        val invalidAfterImages = listOf(
            valid.copy(name = " "),
            valid.copy(name = "unsafe\u0000name"),
            valid.copy(name = "x".repeat(257)),
            valid.copy(name = "\uD800"),
            valid.copy(position = -1),
            valid.copy(googleBackupAddress = "unsafe\naddress"),
            valid.copy(googleBackupAddress = "x".repeat(2_049)),
            valid.copy(substratePublicKeyHex = null),
            valid.copy(substrateAccountIdHex = null),
            valid.copy(substrateCryptoType = null),
            valid.copy(substratePublicKeyHex = "AA".repeat(32)),
            valid.copy(substratePublicKeyHex = "a".repeat(63)),
            valid.copy(substratePublicKeyHex = "gg".repeat(32)),
            valid.copy(substratePublicKeyHex = "aa".repeat(31)),
            valid.copy(substrateAccountIdHex = "aa".repeat(31)),
            valid.copy(ethereumPublicKeyHex = null),
            valid.copy(ethereumAddressHex = null),
            valid.copy(ethereumPublicKeyHex = "aa".repeat(31)),
            valid.copy(ethereumAddressHex = "aa".repeat(19)),
            valid.copy(tonPublicKeyHex = "aa".repeat(31))
        )

        invalidAfterImages.forEach { invalidAfterImage ->
            assertFailure(FailureReason.INVALID_ARGUMENT) {
                store.validate(createJournal(afterImage = invalidAfterImage))
            }
        }
    }

    @Test
    fun stageRejectsMissingUnexpectedOrWrongWalletSecretKeys() {
        val store = WalletSecretMutationJournalStore(RecordingEncryptedPreferences())
        val validJournal = createJournal()
        val validSecrets = createSecrets()

        assertFailure(FailureReason.INVALID_ARGUMENT) {
            store.stage(
                validJournal,
                validSecrets - TON_SECRET_KEY
            )
        }
        assertFailure(FailureReason.INVALID_ARGUMENT) {
            store.stage(
                validJournal,
                validSecrets + ("$META_ID:ACCESS_SECRETS" to "unexpected")
            )
        }
        assertFailure(FailureReason.INVALID_ARGUMENT) {
            store.validate(
                validJournal.copy(
                    secretKeysToPut = validJournal.secretKeysToPut +
                        "${META_ID + 1}:SUBSTRATE_SECRETS"
                )
            )
        }
        assertFailure(FailureReason.INVALID_ARGUMENT) {
            store.validate(
                addEvmJournal().copy(
                    secretKeysToPut = setOf(
                        ETHEREUM_SECRET_KEY,
                        SUBSTRATE_SECRET_KEY
                    )
                )
            )
        }
        assertFailure(FailureReason.INVALID_ARGUMENT) {
            store.stage(
                validJournal,
                validSecrets + (
                    WalletSecretMutationJournalStore.JOURNAL_KEY to
                        "must-never-be-a-secret"
                    )
            )
        }
    }

    @Test
    fun stageRejectsEmptyOrOversizedSecretPlaintextBeforeAnyWrite() {
        val preferences = RecordingEncryptedPreferences()
        val store = WalletSecretMutationJournalStore(preferences)
        val journal = addEvmJournal()

        assertFailure(FailureReason.INVALID_ARGUMENT) {
            store.stage(journal, mapOf(ETHEREUM_SECRET_KEY to ""))
        }
        assertFailure(FailureReason.INVALID_ARGUMENT) {
            store.stage(
                journal,
                mapOf(ETHEREUM_SECRET_KEY to "x".repeat(1_048_577))
            )
        }

        assertTrue(preferences.replacements.isEmpty())
    }

    @Test
    fun stageNeverOverwritesActiveJournalOrTargetSecret() {
        val preferences = RecordingEncryptedPreferences()
        val store = WalletSecretMutationJournalStore(preferences)
        preferences.putRaw(
            WalletSecretMutationJournalStore.JOURNAL_KEY,
            "existing-journal"
        )

        assertFailure(FailureReason.CONFLICT) {
            store.stage(addEvmJournal(), mapOf(ETHEREUM_SECRET_KEY to "new-secret"))
        }
        assertTrue(preferences.replacements.isEmpty())

        preferences.removeRaw(WalletSecretMutationJournalStore.JOURNAL_KEY)
        preferences.putRaw(ETHEREUM_SECRET_KEY, "existing-secret")
        assertFailure(FailureReason.CONFLICT) {
            store.stage(addEvmJournal(), mapOf(ETHEREUM_SECRET_KEY to "new-secret"))
        }
        assertEquals("existing-secret", preferences.value(ETHEREUM_SECRET_KEY))
        assertTrue(preferences.replacements.isEmpty())
    }

    @Test
    fun loadRejectsMalformedUnknownDuplicateMissingAndTypeCoercedJson() {
        val canonical = encodedDeleteJournal()
        val malformedVariants = listOf(
            "{",
            canonical + "{}",
            canonical.replaceFirst(
                "\"version\":1,",
                "\"version\":1,\"version\":1,"
            ),
            canonical.replaceFirst(
                "\"version\":1,",
                "\"unknown\":true,\"version\":1,"
            ),
            canonical.replace("\"version\":1", "\"version\":\"1\""),
            canonical.replace("\"version\":1", "\"version\":1.0"),
            canonical.replace(
                "\"operation\":\"DELETE\"",
                "\"operation\":\"UNKNOWN\""
            ),
            canonical.replace(
                "\"secretKeysToPut\":[]",
                "\"secretKeysToPut\":null"
            ),
            canonical.replace(
                ",\"secretKeysToPut\":[]",
                ""
            )
        )

        malformedVariants.forEach { malformed ->
            val preferences = RecordingEncryptedPreferences().apply {
                putRaw(WalletSecretMutationJournalStore.JOURNAL_KEY, malformed)
            }
            assertFailure(FailureReason.MALFORMED_STORED_JOURNAL) {
                WalletSecretMutationJournalStore(preferences).load()
            }
        }
    }

    @Test
    fun loadRejectsDuplicateOrUnexpectedSecretKeysFromStoredJson() {
        val canonical = encodedAddEvmJournal()
        val duplicateKeyJson = canonical.replace(
            "\"secretKeysToPut\":[\"$ETHEREUM_SECRET_KEY\"]",
            "\"secretKeysToPut\":[\"$ETHEREUM_SECRET_KEY\",\"$ETHEREUM_SECRET_KEY\"]"
        )
        val unexpectedKeyJson = canonical.replace(
            "\"secretKeysToPut\":[\"$ETHEREUM_SECRET_KEY\"]",
            "\"secretKeysToPut\":[\"$SUBSTRATE_SECRET_KEY\"]"
        )

        listOf(duplicateKeyJson, unexpectedKeyJson).forEach { malformed ->
            val preferences = RecordingEncryptedPreferences().apply {
                putRaw(WalletSecretMutationJournalStore.JOURNAL_KEY, malformed)
                putRaw(ETHEREUM_SECRET_KEY, "evm-secret")
                putRaw(SUBSTRATE_SECRET_KEY, "substrate-secret")
            }

            assertFailure(FailureReason.MALFORMED_STORED_JOURNAL) {
                WalletSecretMutationJournalStore(preferences).load()
            }
        }
    }

    @Test
    fun loadRejectsDuplicateUnknownOrMissingAfterImageFields() {
        val canonical = encodedAddEvmJournal()
        val duplicateFieldJson = canonical.replaceFirst(
            "\"name\":\"Fearless wallet\",",
            "\"name\":\"Fearless wallet\",\"name\":\"duplicate\","
        )
        val unknownFieldJson = canonical.replaceFirst(
            "\"name\":\"Fearless wallet\",",
            "\"name\":\"Fearless wallet\",\"unknown\":true,"
        )
        val missingFieldJson = canonical.replace(
            "\"initialized\":false",
            ""
        ).replace(",}", "}")

        listOf(duplicateFieldJson, unknownFieldJson, missingFieldJson).forEach { malformed ->
            val preferences = RecordingEncryptedPreferences().apply {
                putRaw(WalletSecretMutationJournalStore.JOURNAL_KEY, malformed)
                putRaw(ETHEREUM_SECRET_KEY, "evm-secret")
            }

            assertFailure(FailureReason.MALFORMED_STORED_JOURNAL) {
                WalletSecretMutationJournalStore(preferences).load()
            }
        }
    }

    @Test
    fun loadRejectsOversizedJournalBeforeJsonParsing() {
        val preferences = RecordingEncryptedPreferences().apply {
            putRaw(
                WalletSecretMutationJournalStore.JOURNAL_KEY,
                "x".repeat(262_145)
            )
        }

        assertFailure(FailureReason.MALFORMED_STORED_JOURNAL) {
            WalletSecretMutationJournalStore(preferences).load()
        }
        assertEquals(
            listOf(WalletSecretMutationJournalStore.JOURNAL_KEY),
            preferences.decryptedReads
        )
    }

    @Test
    fun loadRejectsUnsupportedStoredVersionAndInvalidStoredIds() {
        val canonical = encodedDeleteJournal()
        val variants = listOf(
            canonical.replace("\"version\":1", "\"version\":2") to
                FailureReason.UNSUPPORTED_VERSION,
            canonical.replace("\"metaId\":$META_ID", "\"metaId\":0") to
                FailureReason.MALFORMED_STORED_JOURNAL,
            canonical.replace(OPERATION_ID, OPERATION_ID.uppercase()) to
                FailureReason.MALFORMED_STORED_JOURNAL
        )

        variants.forEach { (encoded, expectedReason) ->
            val preferences = RecordingEncryptedPreferences().apply {
                putRaw(WalletSecretMutationJournalStore.JOURNAL_KEY, encoded)
            }
            assertFailure(expectedReason) {
                WalletSecretMutationJournalStore(preferences).load()
            }
        }
    }

    @Test
    fun loadRejectsMissingUnreadableEmptyAndOversizedStagedSecrets() {
        val scenarios = listOf<(RecordingEncryptedPreferences) -> Unit>(
            { it.removeRaw(ETHEREUM_SECRET_KEY) },
            { it.nullReadKeys += ETHEREUM_SECRET_KEY },
            { it.putRaw(ETHEREUM_SECRET_KEY, "") },
            { it.putRaw(ETHEREUM_SECRET_KEY, "x".repeat(1_048_577)) }
        )

        scenarios.forEach { corrupt ->
            val preferences = stagedAddEvmPreferences()
            corrupt(preferences)

            assertFailure(FailureReason.MALFORMED_STORED_JOURNAL) {
                WalletSecretMutationJournalStore(preferences).load()
            }
        }
    }

    @Test
    fun loadPropagatesGlobalSecureStorageFailureWithoutReclassifyingWallet() {
        val preferences = stagedDeletePreferences()
        val globalFailure = WalletSecureStorageUnavailableException("keystore unavailable")
        preferences.readFailures[WalletSecretMutationJournalStore.JOURNAL_KEY] =
            globalFailure

        val thrown = assertThrows(WalletSecureStorageUnavailableException::class.java) {
            WalletSecretMutationJournalStore(preferences).load()
        }

        assertSame(globalFailure, thrown)
    }

    @Test
    fun stageTreatsApplyThenThrowAsAmbiguousAndNeverRetries() {
        val preferences = RecordingEncryptedPreferences().apply {
            writeBehavior = WriteBehavior.APPLY_THEN_THROW
        }
        val store = WalletSecretMutationJournalStore(preferences)

        assertFailure(FailureReason.AMBIGUOUS_DURABILITY) {
            store.stage(addEvmJournal(), mapOf(ETHEREUM_SECRET_KEY to "evm-secret"))
        }

        assertEquals(1, preferences.replacements.size)
        assertTrue(preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertEquals("evm-secret", preferences.value(ETHEREUM_SECRET_KEY))
    }

    @Test
    fun stageTreatsSilentNoOpDroppedSecretAndCorruptReadbackAsAmbiguous() {
        val configurations = listOf<(RecordingEncryptedPreferences) -> Unit>(
            { it.writeBehavior = WriteBehavior.SILENT_NO_OP },
            { it.dropAfterWrite += ETHEREUM_SECRET_KEY },
            {
                it.replaceAfterWrite[
                    WalletSecretMutationJournalStore.JOURNAL_KEY
                ] = "{}"
            }
        )

        configurations.forEach { configure ->
            val preferences = RecordingEncryptedPreferences()
            configure(preferences)

            assertFailure(FailureReason.AMBIGUOUS_DURABILITY) {
                WalletSecretMutationJournalStore(preferences).stage(
                    addEvmJournal(),
                    mapOf(ETHEREUM_SECRET_KEY to "evm-secret")
                )
            }
            assertEquals(1, preferences.replacements.size)
        }
    }

    @Test
    fun stageTreatsPriorDurabilityLatchAsAmbiguousWithoutWriting() {
        val preferences = RecordingEncryptedPreferences().apply {
            durableStorageFailure = IllegalStateException("prior ambiguous commit")
        }

        assertFailure(FailureReason.AMBIGUOUS_DURABILITY) {
            WalletSecretMutationJournalStore(preferences).stage(
                addEvmJournal(),
                mapOf(ETHEREUM_SECRET_KEY to "evm-secret")
            )
        }

        assertTrue(preferences.replacements.isEmpty())
    }

    @Test
    fun stagePropagatesGlobalSecureStorageWriteFailure() {
        val globalFailure = WalletSecureStorageUnavailableException("keystore unavailable")
        val preferences = RecordingEncryptedPreferences().apply {
            writeFailure = globalFailure
        }

        val thrown = assertThrows(WalletSecureStorageUnavailableException::class.java) {
            WalletSecretMutationJournalStore(preferences).stage(
                addEvmJournal(),
                mapOf(ETHEREUM_SECRET_KEY to "evm-secret")
            )
        }

        assertSame(globalFailure, thrown)
        assertEquals(1, preferences.replacements.size)
    }

    @Test
    fun clearRejectsAbsentOrStaleOperationWithoutMutation() {
        val expectedMutation = stagedAddEvmMutation()
        val emptyPreferences = RecordingEncryptedPreferences()
        assertFailure(FailureReason.CONFLICT) {
            WalletSecretMutationJournalStore(emptyPreferences).clear(
                expectedMutation
            )
        }
        assertTrue(emptyPreferences.replacements.isEmpty())

        val stagedPreferences = stagedAddEvmPreferences()
        val staleMutation = expectedMutation.copy(
            journal = expectedMutation.journal.copy(
                operationId = OTHER_OPERATION_ID
            )
        )
        assertFailure(FailureReason.CONFLICT) {
            WalletSecretMutationJournalStore(stagedPreferences).clear(
                staleMutation
            )
        }
        assertTrue(
            stagedPreferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
        )
        assertEquals(1, stagedPreferences.replacements.size)
    }

    @Test
    fun clearRemovesOnlyExactJournalOwnedRecoveryMarker() {
        val preferences = stagedAddEvmPreferences()
        val ownedMarker = WalletPublicIdentityRecovery.keyFor(
            META_ID,
            ETHEREUM_SECRET_KEY
        )
        val unrelatedMarker = WalletPublicIdentityRecovery.keyFor(
            META_ID,
            SUBSTRATE_SECRET_KEY
        )
        preferences.putRaw(
            ownedMarker,
            WalletPublicIdentityRecovery.MARKER_VALUE
        )
        preferences.putRaw(
            unrelatedMarker,
            WalletPublicIdentityRecovery.MARKER_VALUE
        )

        WalletSecretMutationJournalStore(preferences).clear(
            stagedAddEvmMutation()
        )

        assertFalse(
            preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
        )
        assertFalse(preferences.hasKey(ownedMarker))
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            preferences.value(unrelatedMarker)
        )
        assertEquals("evm-secret", preferences.value(ETHEREUM_SECRET_KEY))
        assertEquals(
            setOf(
                WalletSecretMutationJournalStore.JOURNAL_KEY,
                ownedMarker
            ),
            preferences.replacements.last().keysToRemove
        )
    }

    @Test
    fun clearRejectsMalformedOwnedRecoveryMarkerWithoutMutation() {
        val preferences = stagedAddEvmPreferences()
        val ownedMarker = WalletPublicIdentityRecovery.keyFor(
            META_ID,
            ETHEREUM_SECRET_KEY
        )
        preferences.putRaw(ownedMarker, "attacker-controlled-marker")

        assertFailure(FailureReason.CONFLICT) {
            WalletSecretMutationJournalStore(preferences).clear(
                stagedAddEvmMutation()
            )
        }

        assertTrue(
            preferences.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
        )
        assertEquals(
            "attacker-controlled-marker",
            preferences.value(ownedMarker)
        )
        assertEquals("evm-secret", preferences.value(ETHEREUM_SECRET_KEY))
        assertEquals(1, preferences.replacements.size)
    }

    @Test
    fun clearRejectsStagedSecretReplacementBeforeOrDuringFinalCas() {
        val changedBeforeClear = stagedAddEvmPreferences()
        changedBeforeClear.putRaw(
            ETHEREUM_SECRET_KEY,
            "attacker-replaced-before-clear"
        )
        assertFailure(FailureReason.CONFLICT) {
            WalletSecretMutationJournalStore(changedBeforeClear).clear(
                stagedAddEvmMutation()
            )
        }
        assertTrue(
            changedBeforeClear.hasKey(
                WalletSecretMutationJournalStore.JOURNAL_KEY
            )
        )
        assertEquals(1, changedBeforeClear.replacements.size)

        val changedDuringCas = stagedAddEvmPreferences().apply {
            beforeStateBoundReplacement = {
                putRaw(
                    ETHEREUM_SECRET_KEY,
                    "attacker-replaced-during-clear"
                )
            }
        }
        assertFailure(FailureReason.CONFLICT) {
            WalletSecretMutationJournalStore(changedDuringCas).clear(
                stagedAddEvmMutation()
            )
        }
        assertTrue(
            changedDuringCas.hasKey(
                WalletSecretMutationJournalStore.JOURNAL_KEY
            )
        )
        assertEquals(
            "attacker-replaced-during-clear",
            changedDuringCas.value(ETHEREUM_SECRET_KEY)
        )
        assertEquals(1, changedDuringCas.replacements.size)
    }

    @Test
    fun clearTreatsApplyThenThrowAndSilentNoOpAsAmbiguous() {
        val applyThenThrow = stagedAddEvmPreferences().apply {
            writeBehavior = WriteBehavior.APPLY_THEN_THROW
        }
        assertFailure(FailureReason.AMBIGUOUS_DURABILITY) {
            WalletSecretMutationJournalStore(applyThenThrow).clear(
                stagedAddEvmMutation()
            )
        }
        assertFalse(
            applyThenThrow.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
        )
        assertEquals(2, applyThenThrow.replacements.size)

        val silentNoOp = stagedAddEvmPreferences().apply {
            writeBehavior = WriteBehavior.SILENT_NO_OP
        }
        assertFailure(FailureReason.AMBIGUOUS_DURABILITY) {
            WalletSecretMutationJournalStore(silentNoOp).clear(
                stagedAddEvmMutation()
            )
        }
        assertTrue(silentNoOp.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertEquals(2, silentNoOp.replacements.size)
    }

    @Test
    fun completeDeleteRejectsWrongOperationAndHandlesAmbiguousDurability() {
        val wrongOperation = stagedAddEvmPreferences()
        assertFailure(FailureReason.CONFLICT) {
            WalletSecretMutationJournalStore(wrongOperation).completeDelete(OPERATION_ID)
        }

        val applyThenThrow = stagedDeletePreferences().apply {
            putRaw(SUBSTRATE_SECRET_KEY, "secret")
            writeBehavior = WriteBehavior.APPLY_THEN_THROW
        }
        assertFailure(FailureReason.AMBIGUOUS_DURABILITY) {
            WalletSecretMutationJournalStore(applyThenThrow)
                .completeDelete(OPERATION_ID)
        }
        assertFalse(applyThenThrow.hasKey(SUBSTRATE_SECRET_KEY))
        assertFalse(
            applyThenThrow.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
        )

        val silentNoOp = stagedDeletePreferences().apply {
            putRaw(SUBSTRATE_SECRET_KEY, "secret")
            writeBehavior = WriteBehavior.SILENT_NO_OP
        }
        assertFailure(FailureReason.AMBIGUOUS_DURABILITY) {
            WalletSecretMutationJournalStore(silentNoOp)
                .completeDelete(OPERATION_ID)
        }
        assertTrue(silentNoOp.hasKey(SUBSTRATE_SECRET_KEY))
        assertTrue(
            silentNoOp.hasKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
        )
    }

    @Test
    fun canonicalEncodingIsDeterministicAcrossSecretSetAndMapOrder() {
        val firstPreferences = RecordingEncryptedPreferences()
        val secondPreferences = RecordingEncryptedPreferences()
        val firstJournal = createJournal(
            secretKeysToPut = linkedSetOf(
                TON_SECRET_KEY,
                SUBSTRATE_SECRET_KEY,
                ETHEREUM_SECRET_KEY
            )
        )
        val secondJournal = createJournal(
            secretKeysToPut = linkedSetOf(
                ETHEREUM_SECRET_KEY,
                TON_SECRET_KEY,
                SUBSTRATE_SECRET_KEY
            )
        )

        WalletSecretMutationJournalStore(firstPreferences).stage(
            firstJournal,
            linkedMapOf(
                TON_SECRET_KEY to "ton",
                SUBSTRATE_SECRET_KEY to "substrate",
                ETHEREUM_SECRET_KEY to "ethereum"
            )
        )
        WalletSecretMutationJournalStore(secondPreferences).stage(
            secondJournal,
            linkedMapOf(
                ETHEREUM_SECRET_KEY to "ethereum",
                SUBSTRATE_SECRET_KEY to "substrate",
                TON_SECRET_KEY to "ton"
            )
        )

        assertEquals(
            firstPreferences.value(WalletSecretMutationJournalStore.JOURNAL_KEY),
            secondPreferences.value(WalletSecretMutationJournalStore.JOURNAL_KEY)
        )
    }

    private fun encodedDeleteJournal(): String {
        return stagedDeletePreferences()
            .value(WalletSecretMutationJournalStore.JOURNAL_KEY)
            .orEmpty()
    }

    private fun encodedAddEvmJournal(): String {
        return stagedAddEvmPreferences()
            .value(WalletSecretMutationJournalStore.JOURNAL_KEY)
            .orEmpty()
    }

    // Captured from the original version-one encoder before replay-time orphan
    // V2 discovery existed. Never regenerate this fixture with the current codec.
    private fun frozenVersionOneDeleteJournal(): String {
        return requireNotNull(
            javaClass.getResourceAsStream(FROZEN_V1_DELETE_JOURNAL_RESOURCE)
        ) {
            "Missing frozen version-one wallet mutation journal fixture"
        }.bufferedReader().use { it.readText() }.trim()
    }

    private fun stagedDeletePreferences(): RecordingEncryptedPreferences {
        return RecordingEncryptedPreferences().also { preferences ->
            WalletSecretMutationJournalStore(preferences).stage(
                deleteJournal(),
                emptyMap()
            )
        }
    }

    private fun stagedAddEvmPreferences(): RecordingEncryptedPreferences {
        return RecordingEncryptedPreferences().also { preferences ->
            val mutation = stagedAddEvmMutation()
            WalletSecretMutationJournalStore(preferences).stage(
                mutation.journal,
                mutation.secretPlaintexts
            )
        }
    }

    private fun stagedAddEvmMutation() = StagedMutation(
        journal = addEvmJournal(),
        secretPlaintexts = mapOf(ETHEREUM_SECRET_KEY to "evm-secret")
    )

    private fun createJournal(
        version: Int = WalletSecretMutationJournalStore.CURRENT_VERSION,
        operationId: String = OPERATION_ID,
        metaId: Long = META_ID,
        afterImage: PublicAfterImage = fullAfterImage(),
        secretKeysToPut: Set<String> = setOf(
            SUBSTRATE_SECRET_KEY,
            ETHEREUM_SECRET_KEY,
            TON_SECRET_KEY
        )
    ): Journal {
        return Journal(
            version = version,
            operationId = operationId,
            operation = Operation.CREATE,
            metaId = metaId,
            beforeImage = null,
            afterImage = afterImage,
            selectedMetaIdAfterDelete = null,
            chainAccountIdsHex = emptySet(),
            secretKeysToPut = secretKeysToPut,
            secretKeysToRemove = emptySet()
        )
    }

    private fun addEvmJournal(
        afterImage: PublicAfterImage = fullAfterImage(),
        beforeImage: PublicAfterImage = afterImage.copy(
            ethereumPublicKeyHex = null,
            ethereumAddressHex = null
        )
    ): Journal {
        return Journal(
            operationId = OPERATION_ID,
            operation = Operation.ADD_EVM,
            metaId = META_ID,
            beforeImage = beforeImage,
            afterImage = afterImage,
            selectedMetaIdAfterDelete = null,
            chainAccountIdsHex = emptySet(),
            secretKeysToPut = setOf(ETHEREUM_SECRET_KEY),
            secretKeysToRemove = emptySet()
        )
    }

    private fun deleteJournal(
        chainAccountIdsHex: Set<String> = emptySet(),
        selectedMetaIdAfterDelete: Long? = null
    ): Journal {
        return Journal(
            operationId = OPERATION_ID,
            operation = Operation.DELETE,
            metaId = META_ID,
            beforeImage = fullAfterImage(),
            afterImage = null,
            selectedMetaIdAfterDelete = selectedMetaIdAfterDelete,
            chainAccountIdsHex = chainAccountIdsHex,
            secretKeysToPut = emptySet(),
            secretKeysToRemove = deletionSecretKeys(chainAccountIdsHex)
        )
    }

    private fun deletionSecretKeys(
        chainAccountIdsHex: Set<String> = emptySet()
    ): Set<String> {
        val activeKeys = buildSet {
            add("$META_ID:ACCESS_SECRETS")
            add(SUBSTRATE_SECRET_KEY)
            add(ETHEREUM_SECRET_KEY)
            add(TON_SECRET_KEY)
            chainAccountIdsHex.forEach {
                add("$META_ID:$it:ACCESS_SECRETS")
            }
        }
        return buildSet {
            addAll(activeKeys)
            activeKeys.forEach { add("wallet_secret_quarantine:$it") }
            activeKeys.forEach {
                add(WalletPublicIdentityRecovery.keyFor(META_ID, it))
            }
            add(
                "wallet_secret_quarantine:legacy_v1_meta_${META_ID}_public_" +
                    "11".repeat(32)
            )
            add(
                "wallet_secret_quarantine:legacy_v04_meta_${META_ID}_public_" +
                    "11".repeat(32)
            )
        }
    }

    private fun fullAfterImage(): PublicAfterImage {
        return PublicAfterImage(
            name = "Fearless wallet",
            substratePublicKeyHex = "11".repeat(32),
            substrateAccountIdHex = "22".repeat(32),
            substrateCryptoType = SubstrateCryptoType.SR25519,
            ethereumPublicKeyHex = "33".repeat(64),
            ethereumAddressHex = "44".repeat(20),
            tonPublicKeyHex = "55".repeat(32),
            isSelected = true,
            position = 7,
            isBackedUp = false,
            googleBackupAddress = null,
            initialized = false
        )
    }

    private fun createSecrets(): Map<String, String> {
        return mapOf(
            SUBSTRATE_SECRET_KEY to "substrate-secret",
            ETHEREUM_SECRET_KEY to "ethereum-secret",
            TON_SECRET_KEY to "ton-secret"
        )
    }

    private fun assertFailure(
        expectedReason: FailureReason,
        operation: () -> Unit
    ): JournalException {
        val failure = assertThrows(JournalException::class.java) {
            operation()
        }
        assertEquals(expectedReason, failure.reason)
        return failure
    }

    private data class DurableReplacement(
        val valuesToPut: Map<String, String>,
        val keysToRemove: Set<String>
    )

    private enum class WriteBehavior {
        NORMAL,
        SILENT_NO_OP,
        APPLY_THEN_THROW
    }

    private class RecordingEncryptedPreferences : EncryptedPreferences {

        val values = linkedMapOf<String, String>()
        val replacements = mutableListOf<DurableReplacement>()
        val decryptedReads = mutableListOf<String>()
        val nullReadKeys = mutableSetOf<String>()
        val readFailures = mutableMapOf<String, RuntimeException>()
        val dropAfterWrite = mutableSetOf<String>()
        val replaceAfterWrite = mutableMapOf<String, String>()

        var writeBehavior = WriteBehavior.NORMAL
        var writeFailure: RuntimeException? = null
        var durableStorageFailure: RuntimeException? = null
        var beforeStateBoundReplacement: (() -> Unit)? = null

        override fun putEncryptedString(field: String, value: String) {
            values[field] = value
        }

        override fun getDecryptedString(field: String): String? {
            decryptedReads += field
            readFailures[field]?.let { throw it }
            if (field in nullReadKeys) return null
            return values[field]
        }

        override fun hasKey(field: String): Boolean {
            return values.containsKey(field)
        }

        override fun hasKeyWithPrefix(prefix: String): Boolean {
            return values.keys.any { it.startsWith(prefix) }
        }

        override fun keysWithPrefixes(
            prefixes: Set<String>,
            maxResultCount: Int,
            maxKeyBytes: Int,
            maxTotalKeyBytes: Int,
            failOnOversizedMatch: Boolean
        ): Set<String> {
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

        override fun removeKey(field: String) {
            values.remove(field)
        }

        override fun replaceEncryptedStringsDurably(
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>
        ) {
            replacements += DurableReplacement(
                valuesToPut = LinkedHashMap(valuesToPut),
                keysToRemove = LinkedHashSet(keysToRemove)
            )
            writeFailure?.let { throw it }
            when (writeBehavior) {
                WriteBehavior.SILENT_NO_OP -> return
                WriteBehavior.NORMAL,
                WriteBehavior.APPLY_THEN_THROW -> {
                    values.putAll(valuesToPut)
                    keysToRemove.forEach(values::remove)
                    dropAfterWrite.forEach(values::remove)
                    values.putAll(replaceAfterWrite)
                }
            }
            if (writeBehavior == WriteBehavior.APPLY_THEN_THROW) {
                throw IOException("durability result is ambiguous")
            }
        }

        @Synchronized
        override fun replaceEncryptedStringsDurablyIfStatesMatch(
            expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>,
            snapshotMoves: List<EncryptedPreferenceSnapshotMove>
        ): Boolean {
            beforeStateBoundReplacement?.also {
                beforeStateBoundReplacement = null
                it()
            }
            return super<EncryptedPreferences>.replaceEncryptedStringsDurablyIfStatesMatch(
                expectedStates = expectedStates,
                valuesToPut = valuesToPut,
                keysToRemove = keysToRemove,
                snapshotMoves = snapshotMoves
            )
        }

        override fun quarantineEncryptedStringDurably(
            sourceKey: String,
            quarantineKey: String,
            expectedSnapshot: EncryptedPreferenceSnapshot
        ): Boolean {
            error("Not used by the mutation journal store")
        }

        override fun requireDurableStorageHealthy() {
            durableStorageFailure?.let { throw it }
        }

        fun putRaw(key: String, value: String) {
            values[key] = value
        }

        fun removeRaw(key: String) {
            values.remove(key)
        }

        fun value(key: String): String? = values[key]
    }

    private companion object {
        const val META_ID = 42L
        const val OPERATION_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val OTHER_OPERATION_ID = "223e4567-e89b-42d3-a456-426614174000"

        const val SUBSTRATE_SECRET_KEY = "$META_ID:SUBSTRATE_SECRETS"
        const val ETHEREUM_SECRET_KEY = "$META_ID:ETHEREUM_SECRETS"
        const val TON_SECRET_KEY = "$META_ID:TON_SECRETS"
        const val UNRELATED_SECRET_KEY = "999:SUBSTRATE_SECRETS"
        const val FROZEN_V1_DELETE_JOURNAL_RESOURCE =
            "/wallet_secret_mutation_journal_v1_delete.json"
    }
}
