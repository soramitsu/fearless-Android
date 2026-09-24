package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.coredb.model.PortableWalletReservationLocal
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PortableWalletCohortFreshInstallStagerTest {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId

    @Test
    fun `fresh cohort reserves two IDs with the exact encrypted source before any wallet row`() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val database = Inventory()
        val ids = Ids(listOf(0L, 41L, 41L, 42L))
        val journal = PortableWalletCohortJournalStore(preferences)
        val semantic = semantic()
        try {
            val receiver = stager(database, journal, preferences, ids)
            val token = receiver.stage(semantic)
            assertTrue(database.walletIds.isEmpty())
            assertEquals(listOf(41L, 42L), database.reserved.map { it.metaId })
            assertTrue(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            assertTrue(preferences.hasKey(PortableWalletCohortJournalStore.reservationKey(41L)))
            assertTrue(preferences.hasKey(PortableWalletCohortJournalStore.reservationKey(42L)))
            assertFalse(preferences.hasKey("41:SUBSTRATE_SECRETS"))
            val replayToken = requireNotNull(receiver.reconcile())
            assertEquals(token.operationId, replayToken.operationId)
            assertEquals(token.afterImageSha256, replayToken.afterImageSha256)
            assertEquals(listOf(41L, 42L), database.reserved.map { it.metaId })

            val replay = requireNotNull(journal.load())
            try {
                assertArrayEquals(longArrayOf(41L, 42L), replay.afterImage.localMetaIdsCopy())
                val exact = replay.afterImage.semanticCopy()
                try {
                    assertArrayEquals(semantic, exact)
                } finally {
                    exact.fill(0)
                }
                val projection = PortableWalletCohortStorageProjection.project(replay.afterImage)
                try {
                    assertArrayEquals(byteArrayOf(0x22, 0x33), projection.originals.single().sourceBytesCopy())
                    assertEquals(listOf(0, 1), projection.wallets.map { it.freshInstallRoomPosition })
                    assertTrue(projection.blockers.isNotEmpty())
                } finally {
                    projection.clearSecrets()
                }
            } finally {
                replay.clearSecrets()
            }
            receiver.abandon(token)
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            assertEquals(2, database.reserved.size)
            assertTrue(database.reserved.all { it.state == PortableWalletReservationLocal.ABANDONED })
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.reservationKey(41L)))
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.reservationKey(42L)))
            assertEquals(null, receiver.reconcile())
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `existing wallet refuses fresh staging without any reservation write`() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val database = Inventory(mutableSetOf(9L))
        val semantic = semantic()
        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    stager(
                        database,
                        PortableWalletCohortJournalStore(preferences),
                        preferences,
                        Ids(listOf(41L, 42L)),
                    ).stage(semantic)
                }
            }
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.reservationKey(41L)))
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `occupied secret ID is skipped before durable staging`() = runBlocking {
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString("42:orphaned:ACCESS_SECRETS", "old secret")
        }
        val database = Inventory()
        val journal = PortableWalletCohortJournalStore(preferences)
        val semantic = semantic()
        try {
            val token = stager(database, journal, preferences, Ids(listOf(42L, 43L, 44L)))
                .stage(semantic)
            val replay = requireNotNull(journal.load())
            try {
                assertArrayEquals(longArrayOf(43L, 44L), replay.afterImage.localMetaIdsCopy())
            } finally {
                replay.clearSecrets()
            }
            assertEquals("old secret", preferences.getDecryptedString("42:orphaned:ACCESS_SECRETS"))
            stager(database, journal, preferences, Ids(emptyList())).abandon(token)
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `orphaned reservation quarantines a new cohort and remains occupied`() = runBlocking {
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(PortableWalletCohortJournalStore.reservationKey(41L), "unreconciled")
        }
        val journal = PortableWalletCohortJournalStore(preferences)
        val semantic = semantic()
        try {
            val replayFailure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                journal.load()
            }
            assertEquals(PortableWalletCohortJournalStore.FailureReason.CONFLICT, replayFailure.reason)
            val stageFailure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                runBlocking {
                    stager(Inventory(), journal, preferences, Ids(listOf(41L, 42L, 43L)))
                        .stage(semantic)
                }
            }
            assertEquals(PortableWalletCohortJournalStore.FailureReason.CONFLICT, stageFailure.reason)
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            assertEquals(
                "unreconciled",
                preferences.getDecryptedString(PortableWalletCohortJournalStore.reservationKey(41L)),
            )
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `a transaction failure after preference commit leaves a replayable blocked journal`() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val database = Inventory().apply { failAfterBlock = true }
        val journal = PortableWalletCohortJournalStore(preferences)
        val semantic = semantic()
        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking { stager(database, journal, preferences, Ids(listOf(41L, 42L))).stage(semantic) }
            }
            assertTrue(database.walletIds.isEmpty())
            assertTrue(database.reserved.isEmpty())
            assertTrue(preferences.hasKey(PortableWalletCohortJournalStore.reservationKey(41L)))
            assertTrue(preferences.hasKey(PortableWalletCohortJournalStore.reservationKey(42L)))
            val restartedJournal = PortableWalletCohortJournalStore(preferences)
            val replay = requireNotNull(restartedJournal.load())
            try {
                assertArrayEquals(longArrayOf(41L, 42L), replay.afterImage.localMetaIdsCopy())
                assertTrue(replay.afterImage.blockers.isNotEmpty())
                assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                    runBlocking {
                        stager(Inventory(), restartedJournal, preferences, Ids(listOf(43L, 44L)))
                            .stage(semantic)
                    }
                }
                database.failAfterBlock = false
                val receiver = stager(database, restartedJournal, preferences, Ids(emptyList()))
                receiver.reconcile()
                assertEquals(listOf(41L, 42L), database.reserved.map { it.metaId })
                receiver.abandon(replay.token)
            } finally {
                replay.clearSecrets()
            }
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `transaction failure before staging writes no journal or reservation`() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val database = Inventory().apply { failBeforeBlock = true }
        val semantic = semantic()
        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    stager(
                        database,
                        PortableWalletCohortJournalStore(preferences),
                        preferences,
                        Ids(listOf(41L, 42L)),
                    ).stage(semantic)
                }
            }
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.reservationKey(41L)))
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.reservationKey(42L)))
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `replay rejects a changed Room reservation without releasing the encrypted journal`() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val database = Inventory()
        val journal = PortableWalletCohortJournalStore(preferences)
        val receiver = stager(database, journal, preferences, Ids(listOf(41L, 42L)))
        val semantic = semantic()
        try {
            receiver.stage(semantic)
            database.reserved[0] = database.reserved[0].copy(afterImageSha256 = "0".repeat(64))
            assertThrows(IllegalStateException::class.java) {
                runBlocking { receiver.reconcile() }
            }
            assertTrue(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            assertEquals("0".repeat(64), database.reserved[0].afterImageSha256)
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `orphaned Room reservation blocks staging even without a preference journal`() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val database = Inventory().apply {
            reserved += PortableWalletReservationLocal(41L, "orphan", "0".repeat(64), "0".repeat(64))
        }
        val receiver = stager(
            database, PortableWalletCohortJournalStore(preferences), preferences, Ids(listOf(42L, 43L)),
        )
        val semantic = semantic()
        try {
            assertThrows(IllegalStateException::class.java) { runBlocking { receiver.stage(semantic) } }
            assertThrows(IllegalStateException::class.java) { runBlocking { receiver.reconcile() } }
            assertTrue(database.walletIds.isEmpty())
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `a wallet inserted after staging makes replay and abandon fail closed`() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val database = Inventory()
        val journal = PortableWalletCohortJournalStore(preferences)
        val receiver = stager(database, journal, preferences, Ids(listOf(41L, 42L)))
        val semantic = semantic()
        try {
            val token = receiver.stage(semantic)
            database.walletIds += 99L
            assertThrows(IllegalStateException::class.java) { runBlocking { receiver.reconcile() } }
            assertThrows(IllegalStateException::class.java) { runBlocking { receiver.abandon(token) } }
            assertEquals(2, database.reserved.size)
            assertTrue(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `failed Room tombstone commit keeps the journal retryable`() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val database = Inventory()
        val journal = PortableWalletCohortJournalStore(preferences)
        val receiver = stager(database, journal, preferences, Ids(listOf(41L, 42L)))
        val semantic = semantic()
        try {
            val token = receiver.stage(semantic)
            database.failAfterBlock = true
            assertThrows(IllegalStateException::class.java) { runBlocking { receiver.abandon(token) } }
            database.failAfterBlock = false
            assertEquals(listOf(41L, 42L), database.reserved.map { it.metaId })
            assertTrue(database.reserved.all { it.state == PortableWalletReservationLocal.PENDING })
            assertTrue(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            assertEquals(token.operationId, receiver.reconcile()?.operationId)
            receiver.abandon(token)
            assertTrue(database.reserved.all { it.state == PortableWalletReservationLocal.ABANDONED })
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            Unit
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `preference failure after Room tombstone commit replays without releasing IDs`() = runBlocking {
        val preferences = FaultPreferences()
        val database = Inventory()
        val journal = PortableWalletCohortJournalStore(preferences)
        val receiver = stager(database, journal, preferences, Ids(listOf(41L, 42L)))
        val semantic = semantic()
        try {
            val token = receiver.stage(semantic)
            preferences.abandonFailure = AbandonFailure.BEFORE_COMMIT
            assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                runBlocking { receiver.abandon(token) }
            }
            assertTrue(database.reserved.all { it.state == PortableWalletReservationLocal.ABANDONED })
            assertTrue(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            preferences.abandonFailure = AbandonFailure.NONE
            assertEquals(null, stager(database, journal, preferences, Ids(emptyList())).reconcile())
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            assertEquals(listOf(41L, 42L), database.reserved.map { it.metaId })

            val next = stager(database, journal, preferences, Ids(listOf(41L, 42L, 43L, 44L)))
                .stage(semantic)
            val replay = requireNotNull(journal.load())
            try {
                assertArrayEquals(longArrayOf(43L, 44L), replay.afterImage.localMetaIdsCopy())
            } finally {
                replay.clearSecrets()
            }
            assertTrue(next.afterImageSha256.isNotEmpty())
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `ambiguous preference commit leaves exact tombstones and no active journal`() = runBlocking {
        val preferences = FaultPreferences()
        val database = Inventory()
        val journal = PortableWalletCohortJournalStore(preferences)
        val receiver = stager(database, journal, preferences, Ids(listOf(41L, 42L)))
        val semantic = semantic()
        try {
            val token = receiver.stage(semantic)
            preferences.abandonFailure = AbandonFailure.AFTER_COMMIT
            assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                runBlocking { receiver.abandon(token) }
            }
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            assertTrue(database.reserved.all { it.state == PortableWalletReservationLocal.ABANDONED })
            preferences.abandonFailure = AbandonFailure.NONE
            assertEquals(null, stager(database, journal, preferences, Ids(emptyList())).reconcile())
            assertTrue(database.reserved.all { it.state == PortableWalletReservationLocal.ABANDONED })
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `malformed abandoned tombstones remain quarantined without an encrypted journal`() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val database = Inventory()
        val receiver = stager(
            database,
            PortableWalletCohortJournalStore(preferences),
            preferences,
            Ids(listOf(41L, 42L)),
        )
        val semantic = semantic()
        try {
            receiver.abandon(receiver.stage(semantic))
            val exact = database.reserved.toList()
            database.reserved.replaceAll { it.copy(idSetSha256 = "0".repeat(64)) }
            assertThrows(IllegalStateException::class.java) { runBlocking { receiver.reconcile() } }
            database.reserved.clear()
            database.reserved.addAll(exact.map { it.copy(operationId = "malformed") })
            assertThrows(IllegalStateException::class.java) { runBlocking { receiver.reconcile() } }
            assertTrue(database.reserved.all { it.state == PortableWalletReservationLocal.ABANDONED })
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `tombstone quota blocks another cohort before any preference write`() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val database = Inventory().apply { countOverride = 8_191 }
        val semantic = semantic()
        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    stager(
                        database,
                        PortableWalletCohortJournalStore(preferences),
                        preferences,
                        Ids(listOf(41L, 42L)),
                    ).stage(semantic)
                }
            }
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
        } finally {
            semantic.fill(0)
        }
    }

    @Test
    fun `ID exhaustion leaves no journal or reservation`() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val database = Inventory()
        val semantic = semantic()
        try {
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    stager(
                        database,
                        PortableWalletCohortJournalStore(preferences),
                        preferences,
                        Ids(List(64) { 0L }),
                    ).stage(semantic)
                }
            }
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            assertFalse(preferences.hasKey(PortableWalletCohortJournalStore.reservationKey(41L)))
        } finally {
            semantic.fill(0)
        }
    }

    private fun stager(
        database: Inventory,
        journal: PortableWalletCohortJournalStore,
        preferences: EncryptedPreferences,
        ids: Ids,
    ) = PortableWalletCohortFreshInstallStager(
        database,
        journal,
        WalletSecretMutationJournalStore(preferences),
        preferences,
        ids,
        Unit,
    )

    private fun semantic(): ByteArray {
        val first = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 1 }, emptyList())
        val second = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 2 }, emptyList())
        val snapshot = PortableWalletSemanticMaterial.Snapshot(
            1,
            listOf(
                wallet(1, 0xffff_ffffL, first.publicKey, first.privateKey, true),
                wallet(17, 0L, second.publicKey, second.privateKey, false),
            ),
        )
        return try {
            codec.encode(snapshot)
        } finally {
            snapshot.clearSecrets()
        }
    }

    private fun wallet(
        start: Int,
        position: Long,
        publicKey: ByteArray,
        privateKey: ByteArray,
        withSidecar: Boolean,
    ): PortableWalletSemanticMaterial.Wallet {
        val root = slot(
            role.SUBSTRATE_ROOT,
            "",
            bytes(field.PUBLIC_KEY, publicKey),
            bytes(field.PRIVATE_KEY, privateKey),
            bytes(field.ACCOUNT_ID_OR_ADDRESS, publicKey),
            one(field.CRYPTO_TYPE, 2),
            one(field.SOURCE_RECIPE, 0),
        )
        val slots = if (withSidecar) {
            listOf(
                root,
                slot(
                    role.AUXILIARY_SOURCE,
                    "0000",
                    one(field.SOURCE_RECIPE, 0),
                    one(field.SOURCE_PLATFORM, 1),
                    one(field.SOURCE_SLOT_ROLE, 11),
                    one(field.BINDING_KIND, 2),
                    one(field.SOURCE_FORMAT, 4),
                    bytes(field.SOURCE_BYTES, byteArrayOf(0x22, 0x33)),
                ),
            )
        } else {
            listOf(root)
        }
        return PortableWalletSemanticMaterial.Wallet(
            ByteArray(16) { (start + it).toByte() }, position, true, "Wallet $start", emptyList(), slots,
        )
    }

    private fun slot(
        roleCode: Int,
        key: String,
        vararg fields: PortableWalletSemanticMaterial.Field,
    ): PortableWalletSemanticMaterial.Slot = PortableWalletSemanticMaterial.Slot(
        roleCode,
        key,
        fields.sortedBy { it.id },
    )

    private fun one(id: Int, value: Int) = bytes(id, byteArrayOf(value.toByte()))
    private fun bytes(id: Int, value: ByteArray) = PortableWalletSemanticMaterial.Field(id, value.copyOf())

    private class Inventory(val walletIds: MutableSet<Long> = linkedSetOf()) : FreshInstallWalletInventory {
        var failBeforeBlock = false
        var failAfterBlock = false
        var countOverride: Int? = null
        val reserved = mutableListOf<PortableWalletReservationLocal>()

        override suspend fun <T> inTransaction(block: suspend FreshInstallWalletInventory.() -> T): T {
            if (failBeforeBlock) error("Injected Room transaction start failure")
            val before = reserved.toList()
            return try {
                val result = block(this)
                if (failAfterBlock) error("Injected Room transaction failure")
                result
            } catch (failure: Throwable) {
                reserved.clear()
                reserved.addAll(before)
                throw failure
            }
        }

        override suspend fun hasAnyWallet(): Boolean = walletIds.isNotEmpty()
        override suspend fun metaAccountExists(metaId: Long): Boolean = metaId in walletIds
        override suspend fun reservationExists(metaId: Long): Boolean = reserved.any { it.metaId == metaId }
        override suspend fun reservationCount(): Int = countOverride ?: reserved.size
        override suspend fun reservations(): List<PortableWalletReservationLocal> = reserved.sortedBy { it.metaId }
        override suspend fun reservationsFor(
            token: PortableWalletCohortJournalStore.Token,
        ): List<PortableWalletReservationLocal> = reserved.filter {
                it.operationId == token.operationId && it.afterImageSha256 == token.afterImageSha256
            }
                .sortedBy { it.metaId }
        override suspend fun reserve(rows: List<PortableWalletReservationLocal>) {
            check(rows.none { row -> reserved.any { it.metaId == row.metaId } })
            reserved.addAll(rows)
        }
        override suspend fun markAbandoned(token: PortableWalletCohortJournalStore.Token): Int {
            val count = reserved.count {
                it.operationId == token.operationId &&
                    it.afterImageSha256 == token.afterImageSha256 &&
                    it.state == PortableWalletReservationLocal.PENDING
            }
            reserved.replaceAll { row ->
                if (
                    row.operationId == token.operationId &&
                    row.afterImageSha256 == token.afterImageSha256 &&
                    row.state == PortableWalletReservationLocal.PENDING
                ) {
                    row.copy(state = PortableWalletReservationLocal.ABANDONED)
                } else {
                    row
                }
            }
            return count
        }
    }

    private class FaultPreferences(
        private val delegate: HashMapEncryptedPreferences = HashMapEncryptedPreferences(),
    ) : EncryptedPreferences by delegate {
        var abandonFailure = AbandonFailure.NONE

        override fun replaceEncryptedStringsDurablyIfStatesMatch(
            expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>,
            snapshotMoves: List<EncryptedPreferenceSnapshotMove>,
        ): Boolean {
            val abandoning = PortableWalletCohortJournalStore.JOURNAL_KEY in keysToRemove
            if (abandoning && abandonFailure == AbandonFailure.BEFORE_COMMIT) {
                throw IOException("Injected preference commit failure")
            }
            val result = delegate.replaceEncryptedStringsDurablyIfStatesMatch(
                expectedStates, valuesToPut, keysToRemove, snapshotMoves,
            )
            if (abandoning && abandonFailure == AbandonFailure.AFTER_COMMIT) {
                throw IOException("Injected ambiguous preference commit")
            }
            return result
        }
    }

    private enum class AbandonFailure { NONE, BEFORE_COMMIT, AFTER_COMMIT }

    private class Ids(metaIds: List<Long>) : WalletMutationIdentifierSource {
        private val candidates = ArrayDeque(metaIds)
        override fun nextMetaIdCandidate(): Long = candidates.removeFirst()
        override fun nextOperationId(): String = "123e4567-e89b-42d3-a456-426614174000"
    }
}
