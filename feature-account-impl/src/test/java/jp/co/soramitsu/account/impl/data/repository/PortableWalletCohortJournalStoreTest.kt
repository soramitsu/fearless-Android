package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.impl.data.repository.PortableWalletCohortAfterImage.Kind
import jp.co.soramitsu.account.impl.data.repository.PortableWalletCohortJournalStore.FailureReason
import jp.co.soramitsu.account.impl.data.repository.PortableWalletReceiveInstallPlan.BlockerReason
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64

class PortableWalletCohortJournalStoreTest {
    private val codec = PortableWalletSemanticMaterial
    private val role = PortableWalletSemanticMaterial.Role
    private val field = PortableWalletSemanticMaterial.FieldId

    @Test
    fun `durable replay retains the entire blocked two-wallet cohort without publishing secrets`() {
        val preferences = RecordingPreferences()
        val record = cohort()
        val expected = record.semanticCopy()
        try {
            val token = PortableWalletCohortJournalStore(preferences).stage(OPERATION_ID, record)
            assertEquals(
                setOf(
                    PortableWalletCohortJournalStore.JOURNAL_KEY,
                    PortableWalletCohortJournalStore.ORIGINAL_SOURCE_KEY,
                    PortableWalletCohortJournalStore.reservationKey(41L),
                    PortableWalletCohortJournalStore.reservationKey(42L),
                ),
                preferences.values.keys,
            )
            assertEquals(1, preferences.writes)
            assertEquals("PortableWalletCohortJournalStore.Token(redacted)", token.toString())

            val replay = requireNotNull(PortableWalletCohortJournalStore(preferences).load())
            try {
                assertEquals(OPERATION_ID, replay.token.operationId)
                assertEquals(token.afterImageSha256, replay.token.afterImageSha256)
                assertEquals("PortableWalletCohortJournalStore.Entry(redacted)", replay.toString())
                assertArrayEquals(longArrayOf(41L, 42L), replay.afterImage.localMetaIdsCopy())
                assertArrayEquals(expected, replay.afterImage.semanticCopy())
                assertEquals(1, replay.afterImage.selectedIndex)
                assertEquals(
                    listOf(
                        Kind.WALLET_ROW, Kind.METADATA, Kind.V3_SUBSTRATE,
                        Kind.V1_LEGACY_SOURCE, Kind.V2_CHAIN, Kind.FAVORITE_CHAIN,
                        Kind.ORIGINAL_SOURCE, Kind.WATCH_IDENTITY, Kind.WALLET_ROW,
                        Kind.V3_SUBSTRATE, Kind.SELECTION,
                    ),
                    replay.afterImage.destinations.map { it.kind },
                )
                val installerBlocked = replay.afterImage.blockers.any {
                    it.reason == BlockerReason.TRANSACTIONAL_INSTALLER_UNAVAILABLE
                }
                assertTrue(installerBlocked)
                assertTrue(replay.afterImage.blockers.any { it.reason == BlockerReason.ORIGINAL_SOURCE_UNPROVEN })
                assertTrue(replay.afterImage.blockers.any { it.reason == BlockerReason.V1_SOURCE_UNPROVEN })
                assertTrue(replay.afterImage.blockers.any { it.reason == BlockerReason.V2_CHAIN_UNPROVEN })
                assertTrue(replay.afterImage.blockers.any { it.reason == BlockerReason.METADATA_UNMAPPED })
            } finally {
                replay.clearSecrets()
            }
            PortableWalletCohortJournalStore(preferences).abandon(token)
            assertTrue(preferences.values.isEmpty())
            assertEquals(null, PortableWalletCohortJournalStore(preferences).load())
        } finally {
            expected.fill(0)
            record.clearSecrets()
        }
    }

    @Test
    fun `active old journal and occupied root V1 and V2 namespaces reject staging`() {
        val candidate = cohort()
        try {
            val root = candidate.destinations.single { it.kind == Kind.V3_SUBSTRATE && it.walletIndex == 0 }
            val v1 = candidate.destinations.single { it.kind == Kind.V1_LEGACY_SOURCE }
            val v2 = candidate.destinations.single { it.kind == Kind.V2_CHAIN }
            val keys = listOfNotNull(
                WalletSecretMutationJournalStore.JOURNAL_KEY,
                TonConnectStorageKeys.MUTATION_JOURNAL_KEY,
                root.candidateSecretKey,
                v1.candidateSecretKey,
                v2.candidateSecretKey,
                "41:orphaned:ACCESS_SECRETS",
                PortableWalletCohortJournalStore.reservationKey(41L),
                PortableWalletCohortJournalStore.ORIGINAL_SOURCE_KEY,
                PortableWalletCohortJournalStore.ORIGINAL_SOURCE_KEY + "rogue",
            )
            keys.forEach { key -> assertOccupiedKeyRejected(key, candidate) }
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `concurrent destination creation fails the atomic staging compare and swap`() {
        val candidate = cohort()
        val secretKey = candidate.destinations.single { it.kind == Kind.V2_CHAIN }.candidateSecretKey!!
        val preferences = RecordingPreferences().apply {
            beforeCompare = { values[secretKey] = "racing owner" }
        }
        try {
            val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                PortableWalletCohortJournalStore(preferences).stage(OPERATION_ID, candidate)
            }
            assertEquals(FailureReason.CONFLICT, failure.reason)
            assertFalse(preferences.values.containsKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `concurrent ID reservation fails the same atomic staging compare and swap`() {
        val candidate = cohort()
        val reservation = PortableWalletCohortJournalStore.reservationKey(41L)
        val preferences = RecordingPreferences().apply {
            beforeCompare = { values[reservation] = "racing owner" }
        }
        try {
            val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                PortableWalletCohortJournalStore(preferences).stage(OPERATION_ID, candidate)
            }
            assertEquals(FailureReason.CONFLICT, failure.reason)
            assertEquals(mapOf(reservation to "racing owner"), preferences.values)
            assertEquals(0, preferences.writes)
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `missing or changed ID reservation fails replay and cannot be abandoned`() {
        val candidate = cohort()
        val reservation = PortableWalletCohortJournalStore.reservationKey(41L)
        val preferences = RecordingPreferences()
        try {
            val store = PortableWalletCohortJournalStore(preferences)
            val token = store.stage(OPERATION_ID, candidate)
            val original = requireNotNull(preferences.values.remove(reservation))
            assertBrokenReservationBlocksReplayAndAbandon(store, token)
            preferences.values[reservation] = "different operation"
            assertBrokenReservationBlocksReplayAndAbandon(store, token)
            preferences.values[reservation] = original
            store.abandon(token)
            assertTrue(preferences.values.isEmpty())
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `v2 missing changed or rogue original source blocks replay and abandonment`() {
        val candidate = cohort()
        val preferences = RecordingPreferences()
        val store = PortableWalletCohortJournalStore(preferences)
        try {
            val token = store.stage(OPERATION_ID, candidate)
            val sourceKey = PortableWalletCohortJournalStore.ORIGINAL_SOURCE_KEY
            val exact = requireNotNull(preferences.values.remove(sourceKey))
            assertSidecarConflict(store, token, preferences)
            preferences.values[sourceKey] = "different original source"
            assertSidecarConflict(store, token, preferences)
            preferences.values[sourceKey] = exact
            preferences.values[sourceKey + "rogue"] = "other namespace"
            assertSidecarConflict(store, token, preferences)
            preferences.values.remove(sourceKey + "rogue")
            store.abandon(token)
            assertTrue(preferences.values.isEmpty())
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `orphaned original source blocks an empty journal replay`() {
        val preferences = RecordingPreferences()
        preferences.values[PortableWalletCohortJournalStore.ORIGINAL_SOURCE_KEY] = "orphaned"
        assertEquals(
            FailureReason.CONFLICT,
            assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                PortableWalletCohortJournalStore(preferences).load()
            }.reason,
        )
    }

    @Test
    fun `legacy v1 journal upgrades exact opaque source sidecar before receiving replay`() {
        val candidate = cohort()
        val preferences = RecordingPreferences()
        val store = PortableWalletCohortJournalStore(preferences)
        val expectedSemantic = candidate.semanticCopy()
        try {
            val token = store.stage(OPERATION_ID, candidate)
            val sourceKey = PortableWalletCohortJournalStore.ORIGINAL_SOURCE_KEY
            preferences.values.remove(sourceKey)
            val v2 = requireNotNull(preferences.values[PortableWalletCohortJournalStore.JOURNAL_KEY])
            preferences.values[PortableWalletCohortJournalStore.JOURNAL_KEY] = legacyV1Wire(v2)
            val before = requireNotNull(store.load())
            try {
                assertEquals(1, before.journalVersion)
                val actual = before.afterImage.semanticCopy()
                try { assertArrayEquals(expectedSemantic, actual) } finally { actual.fill(0) }
            } finally {
                before.clearSecrets()
            }
            preferences.beforeCompare = { preferences.values[sourceKey] = "racing source" }
            assertEquals(
                FailureReason.CONFLICT,
                assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                    store.ensureOriginalSourceSidecar(token)
                }.reason,
            )
            val unchanged = Base64.getDecoder().decode(
                requireNotNull(preferences.values[PortableWalletCohortJournalStore.JOURNAL_KEY])
            )
            try { assertEquals(1, unchanged[8].toInt()) } finally { unchanged.fill(0) }
            preferences.values.remove(sourceKey)
            store.ensureOriginalSourceSidecar(token)
            val after = requireNotNull(store.load())
            try {
                assertEquals(2, after.journalVersion)
                val actual = after.afterImage.semanticCopy()
                try { assertArrayEquals(expectedSemantic, actual) } finally { actual.fill(0) }
                val sidecar = requireNotNull(preferences.values[sourceKey])
                assertTrue(sidecar.length < requireNotNull(preferences.values[PortableWalletCohortJournalStore.JOURNAL_KEY]).length)
                assertTrue(sidecar != preferences.values[PortableWalletCohortJournalStore.JOURNAL_KEY])
            } finally {
                after.clearSecrets()
            }
            store.abandon(token)
            assertTrue(preferences.values.isEmpty())
        } finally {
            expectedSemantic.fill(0)
            candidate.clearSecrets()
        }
    }

    private fun legacyV1Wire(v2: String): String {
        val wire = Base64.getDecoder().decode(v2)
        try {
            wire[8] = 1
            val digest = MessageDigest.getInstance("SHA-256").apply {
                update(wire, 0, wire.size - 32)
            }.digest()
            try {
                System.arraycopy(digest, 0, wire, wire.size - 32, 32)
                return Base64.getEncoder().encodeToString(wire)
            } finally {
                digest.fill(0)
            }
        } finally {
            wire.fill(0)
        }
    }

    private fun assertSidecarConflict(
        store: PortableWalletCohortJournalStore,
        token: PortableWalletCohortJournalStore.Token,
        preferences: RecordingPreferences,
    ) {
        assertEquals(
            FailureReason.CONFLICT,
            assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) { store.load() }.reason,
        )
        assertEquals(
            FailureReason.CONFLICT,
            assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                store.abandon(token)
            }.reason,
        )
        assertTrue(preferences.values.containsKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
    }

    private fun assertBrokenReservationBlocksReplayAndAbandon(
        store: PortableWalletCohortJournalStore,
        token: PortableWalletCohortJournalStore.Token,
    ) {
        val replayFailure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
            store.load()
        }
        assertEquals(FailureReason.CONFLICT, replayFailure.reason)
        val abandonFailure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
            store.abandon(token)
        }
        assertEquals(FailureReason.CONFLICT, abandonFailure.reason)
    }

    @Test
    fun `concurrent TON Connect mutation journal fails the staging compare and swap`() {
        val candidate = cohort()
        val preferences = RecordingPreferences().apply {
            beforeCompare = { values[TonConnectStorageKeys.MUTATION_JOURNAL_KEY] = "racing mutation" }
        }
        try {
            val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                PortableWalletCohortJournalStore(preferences).stage(OPERATION_ID, candidate)
            }
            assertEquals(FailureReason.CONFLICT, failure.reason)
            assertEquals(mapOf(TonConnectStorageKeys.MUTATION_JOURNAL_KEY to "racing mutation"), preferences.values)
            assertEquals(0, preferences.writes)
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `nonrandom and malformed operation identifiers fail before staging`() {
        val candidate = cohort()
        val preferences = RecordingPreferences()
        try {
            listOf(
                "00000000-0000-0000-0000-000000000000",
                "123e4567-e89b-12d3-a456-426614174000",
                "123e4567-e89b-42d3-7456-426614174000",
                OPERATION_ID.uppercase(),
            ).forEach { invalid ->
                val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                    PortableWalletCohortJournalStore(preferences).stage(invalid, candidate)
                }
                assertEquals(FailureReason.INVALID_ARGUMENT, failure.reason)
            }
            assertTrue(preferences.values.isEmpty())
            assertEquals(0, preferences.writes)
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `ambiguous durable write is read only reconciled after process replacement`() {
        val candidate = cohort()
        val preferences = RecordingPreferences().apply { behavior = WriteBehavior.APPLY_THEN_THROW }
        try {
            val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                PortableWalletCohortJournalStore(preferences).stage(OPERATION_ID, candidate)
            }
            assertEquals(FailureReason.AMBIGUOUS_DURABILITY, failure.reason)
            assertEquals(1, preferences.writes)
            preferences.behavior = WriteBehavior.NORMAL
            val replay = requireNotNull(PortableWalletCohortJournalStore(preferences).load())
            try {
                assertEquals(OPERATION_ID, replay.token.operationId)
                assertTrue(replay.afterImage.blockers.isNotEmpty())
            } finally {
                replay.clearSecrets()
            }
            assertEquals(1, preferences.writes)
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `silent lost write and altered readback never return a staged token`() {
        listOf(WriteBehavior.SILENT_NO_OP, WriteBehavior.CORRUPT_AFTER_WRITE).forEach { behavior ->
            val candidate = cohort()
            val preferences = RecordingPreferences().apply { this.behavior = behavior }
            try {
                val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                    PortableWalletCohortJournalStore(preferences).stage(OPERATION_ID, candidate)
                }
                assertEquals(FailureReason.AMBIGUOUS_DURABILITY, failure.reason)
            } finally {
                candidate.clearSecrets()
            }
        }
    }

    @Test
    fun `truncation digest mutation noncanonical base64 and unsupported version fail replay`() {
        val candidate = cohort()
        val preferences = RecordingPreferences()
        try {
            PortableWalletCohortJournalStore(preferences).stage(OPERATION_ID, candidate)
            val valid = requireNotNull(preferences.values[PortableWalletCohortJournalStore.JOURNAL_KEY])
            assertCorruptWireFails(preferences, valid)
        } finally {
            candidate.clearSecrets()
        }
    }

    private fun assertCorruptWireFails(preferences: RecordingPreferences, valid: String) {
        val wire = Base64.getDecoder().decode(valid)
        try {
            val variants = listOf(
                "?",
                valid + "=",
                Base64.getEncoder().encodeToString(wire.copyOf(wire.size - 1)),
                Base64.getEncoder().encodeToString(wire.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }),
            )
            variants.forEach { variant -> assertMalformedReplay(preferences, variant) }
            val unsupported = wire.copyOf().also { it[8] = 3 }
            preferences.values[PortableWalletCohortJournalStore.JOURNAL_KEY] =
                Base64.getEncoder().encodeToString(unsupported)
            val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                PortableWalletCohortJournalStore(preferences).load()
            }
            assertEquals(FailureReason.UNSUPPORTED_VERSION, failure.reason)
            unsupported.fill(0)
        } finally {
            wire.fill(0)
        }
    }

    private fun assertOccupiedKeyRejected(key: String, candidate: PortableWalletCohortAfterImage.Record) {
        val preferences = RecordingPreferences().apply { values[key] = "existing" }
        val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
            PortableWalletCohortJournalStore(preferences).stage(OPERATION_ID, candidate)
        }
        assertEquals(FailureReason.CONFLICT, failure.reason)
        assertEquals(mapOf(key to "existing"), preferences.values)
        assertEquals(0, preferences.writes)
    }

    private fun assertMalformedReplay(preferences: RecordingPreferences, variant: String) {
        preferences.values[PortableWalletCohortJournalStore.JOURNAL_KEY] = variant
        val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
            PortableWalletCohortJournalStore(preferences).load()
        }
        assertEquals(FailureReason.MALFORMED_STORED_JOURNAL, failure.reason)
    }

    @Test
    fun `forged digest cannot make a changed after image replayable`() {
        val candidate = cohort()
        val preferences = RecordingPreferences()
        try {
            PortableWalletCohortJournalStore(preferences).stage(OPERATION_ID, candidate)
            val stored = requireNotNull(preferences.values[PortableWalletCohortJournalStore.JOURNAL_KEY])
            val wire = Base64.getDecoder().decode(stored)
            try {
                // Change the first local ID to zero, then recompute the unkeyed wire checksum.
                wire.fill(0, 60, 68)
                val hash = MessageDigest.getInstance("SHA-256").digest(wire.copyOf(wire.size - 32))
                hash.copyInto(wire, wire.size - 32)
                preferences.values[PortableWalletCohortJournalStore.JOURNAL_KEY] =
                    Base64.getEncoder().encodeToString(wire)
                val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                    PortableWalletCohortJournalStore(preferences).load()
                }
                assertEquals(FailureReason.MALFORMED_STORED_JOURNAL, failure.reason)
                hash.fill(0)
            } finally {
                wire.fill(0)
            }
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `replay refuses a late target collision and abandon cannot erase unrelated secrets`() {
        val candidate = cohort()
        val preferences = RecordingPreferences()
        try {
            val token = PortableWalletCohortJournalStore(preferences).stage(OPERATION_ID, candidate)
            val key = candidate.destinations.single { it.kind == Kind.V3_SUBSTRATE && it.walletIndex == 0 }
                .candidateSecretKey!!
            preferences.values[key] = "new owner"
            val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                PortableWalletCohortJournalStore(preferences).load()
            }
            assertEquals(FailureReason.CONFLICT, failure.reason)
            assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                PortableWalletCohortJournalStore(preferences).abandon(token)
            }
            assertEquals("new owner", preferences.values[key])
            assertTrue(preferences.values.containsKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `late unknown wallet namespace collision fails replay without removing it`() {
        val candidate = cohort()
        val preferences = RecordingPreferences()
        try {
            PortableWalletCohortJournalStore(preferences).stage(OPERATION_ID, candidate)
            val orphanKey = "41:unexpected:ACCESS_SECRETS"
            preferences.values[orphanKey] = "new owner"
            val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                PortableWalletCohortJournalStore(preferences).load()
            }
            assertEquals(FailureReason.CONFLICT, failure.reason)
            assertEquals("new owner", preferences.values[orphanKey])
            assertTrue(preferences.values.containsKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `abandon compare and swap cannot remove a replaced journal`() {
        val candidate = cohort()
        val preferences = RecordingPreferences()
        try {
            val token = PortableWalletCohortJournalStore(preferences).stage(OPERATION_ID, candidate)
            preferences.beforeCompare = {
                preferences.values[PortableWalletCohortJournalStore.JOURNAL_KEY] = "replaced"
            }
            val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                PortableWalletCohortJournalStore(preferences).abandon(token)
            }
            assertEquals(FailureReason.CONFLICT, failure.reason)
            assertEquals("replaced", preferences.values[PortableWalletCohortJournalStore.JOURNAL_KEY])
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `abandon compare and swap cannot remove a replaced ID reservation`() {
        val candidate = cohort()
        val preferences = RecordingPreferences()
        val reservation = PortableWalletCohortJournalStore.reservationKey(41L)
        try {
            val store = PortableWalletCohortJournalStore(preferences)
            val token = store.stage(OPERATION_ID, candidate)
            preferences.beforeCompare = { preferences.values[reservation] = "replaced" }
            val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                store.abandon(token)
            }
            assertEquals(FailureReason.CONFLICT, failure.reason)
            assertEquals("replaced", preferences.values[reservation])
            assertTrue(preferences.values.containsKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `abandon cannot discard a journal after a target or TON mutation races in`() {
        val candidate = cohort()
        try {
            val targetKey = candidate.destinations.single {
                it.kind == Kind.V2_CHAIN
            }.candidateSecretKey!!
            listOf(targetKey, TonConnectStorageKeys.MUTATION_JOURNAL_KEY).forEach { key ->
                val preferences = RecordingPreferences()
                val store = PortableWalletCohortJournalStore(preferences)
                val token = store.stage(OPERATION_ID, candidate)
                preferences.beforeCompare = { preferences.values[key] = "racing owner" }
                val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                    store.abandon(token)
                }
                assertEquals(FailureReason.CONFLICT, failure.reason)
                assertEquals("racing owner", preferences.values[key])
                assertTrue(preferences.values.containsKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            }
        } finally {
            candidate.clearSecrets()
        }
    }

    @Test
    fun `abandon requires exact operation and image commitment`() {
        val candidate = cohort()
        val preferences = RecordingPreferences()
        try {
            val token = PortableWalletCohortJournalStore(preferences).stage(OPERATION_ID, candidate)
            val store = PortableWalletCohortJournalStore(preferences)
            listOf(
                PortableWalletCohortJournalStore.Token(OTHER_OPERATION_ID, token.afterImageSha256),
                PortableWalletCohortJournalStore.Token(OPERATION_ID, "0".repeat(64)),
            ).forEach { wrong ->
                val failure = assertThrows(PortableWalletCohortJournalStore.JournalException::class.java) {
                    store.abandon(wrong)
                }
                assertEquals(FailureReason.CONFLICT, failure.reason)
            }
            assertTrue(preferences.values.containsKey(PortableWalletCohortJournalStore.JOURNAL_KEY))
            store.abandon(token)
            assertTrue(preferences.values.isEmpty())
        } finally {
            candidate.clearSecrets()
        }
    }

    private fun cohort(): PortableWalletCohortAfterImage.Record {
        val first = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 1 }, emptyList())
        val second = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 2 }, emptyList())
        val source = PortableWalletSemanticMaterial.Snapshot(
            1,
            listOf(
                wallet(
                    1, "First", 0xffff_ffffL, true,
                    listOf(PortableWalletSemanticMaterial.Metadata(3, "JPY".toByteArray())),
                    listOf(
                        root(first.publicKey, first.privateKey),
                        slot(
                            role.LEGACY_SUBSTRATE, "",
                            bytes(field.PUBLIC_KEY, first.publicKey),
                            bytes(field.PRIVATE_KEY, ByteArray(32) { 5 }),
                            bytes(field.ACCOUNT_ID_OR_ADDRESS, first.publicKey.toAddress(42).toByteArray()),
                            one(field.CRYPTO_TYPE, 2), one(field.SOURCE_RECIPE, 5),
                        ),
                        slot(
                            role.CHAIN_ACCOUNT, "chain-x",
                            bytes(field.PUBLIC_KEY, ByteArray(32) { 6 }),
                            bytes(field.PRIVATE_KEY, ByteArray(32) { 7 }),
                            bytes(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(32) { 0x20 }),
                            one(field.CRYPTO_TYPE, 2),
                            bytes(field.CHAIN_NAME, "Chain X".toByteArray()),
                            one(field.INITIALIZED_OR_FAVORITE, 1), one(field.SOURCE_RECIPE, 0),
                        ),
                        slot(role.FAVORITE_CHAIN, "chain-x", one(field.INITIALIZED_OR_FAVORITE, 1)),
                        slot(
                            role.AUXILIARY_SOURCE, "0000",
                            one(field.SOURCE_RECIPE, 0), one(field.SOURCE_PLATFORM, 1),
                            one(field.SOURCE_SLOT_ROLE, 11), one(field.BINDING_KIND, 2),
                            one(field.SOURCE_FORMAT, 4), bytes(field.SOURCE_BYTES, byteArrayOf(0x22, 0x33)),
                        ),
                        slot(
                            role.WATCH_IDENTITY, "0000",
                            bytes(field.ACCOUNT_ID_OR_ADDRESS, ByteArray(20) { 3 }),
                            one(field.WATCH_ECOSYSTEM, 2),
                        ),
                    ),
                ),
                wallet(17, "Second", 4, false, emptyList(), listOf(root(second.publicKey, second.privateKey))),
            ),
        )
        val semantic = codec.encode(source)
        try {
            return PortableWalletCohortAfterImage.create(semantic, listOf(41L, 42L))
        } finally {
            semantic.fill(0)
            source.clearSecrets()
        }
    }

    private fun wallet(
        firstPortableIdByte: Int,
        name: String,
        position: Long,
        initialized: Boolean,
        metadata: List<PortableWalletSemanticMaterial.Metadata>,
        slots: List<PortableWalletSemanticMaterial.Slot>,
    ) = PortableWalletSemanticMaterial.Wallet(
        ByteArray(16) { (firstPortableIdByte + it).toByte() },
        position, initialized, name, metadata, slots,
    )

    private fun root(publicKey: ByteArray, privateKey: ByteArray) = slot(
        role.SUBSTRATE_ROOT, "",
        bytes(field.PUBLIC_KEY, publicKey), bytes(field.PRIVATE_KEY, privateKey),
        bytes(field.ACCOUNT_ID_OR_ADDRESS, publicKey),
        one(field.CRYPTO_TYPE, 2), one(field.SOURCE_RECIPE, 0),
    )

    private fun slot(
        roleCode: Int,
        key: String,
        vararg fields: PortableWalletSemanticMaterial.Field,
    ): PortableWalletSemanticMaterial.Slot {
        return PortableWalletSemanticMaterial.Slot(roleCode, key, fields.sortedBy { it.id })
    }

    private fun one(id: Int, value: Int) = bytes(id, byteArrayOf(value.toByte()))

    private fun bytes(id: Int, value: ByteArray) = PortableWalletSemanticMaterial.Field(id, value.copyOf())

    private class RecordingPreferences : EncryptedPreferences {
        val values = linkedMapOf<String, String>()
        var writes = 0
        var behavior = WriteBehavior.NORMAL
        var beforeCompare: (() -> Unit)? = null

        override fun putEncryptedString(field: String, value: String) { values[field] = value }

        override fun getDecryptedString(field: String): String? = values[field]

        override fun hasKey(field: String): Boolean = values.containsKey(field)

        override fun hasKeyWithPrefix(prefix: String): Boolean = values.keys.any { it.startsWith(prefix) }

        override fun keysWithPrefixes(
            prefixes: Set<String>,
            maxResultCount: Int,
            maxKeyBytes: Int,
            maxTotalKeyBytes: Int,
            failOnOversizedMatch: Boolean,
        ): Set<String> {
            val matches = values.keys.filter { key -> prefixes.any(key::startsWith) }.toSet()
            check(matches.size <= maxResultCount)
            check(matches.all { it.toByteArray(Charsets.UTF_8).size <= maxKeyBytes })
            check(matches.sumOf { it.toByteArray(Charsets.UTF_8).size } <= maxTotalKeyBytes)
            return matches
        }

        override fun removeKey(field: String) { values.remove(field) }

        override fun replaceEncryptedStringsDurably(valuesToPut: Map<String, String>, keysToRemove: Set<String>) {
            writes++
            if (behavior == WriteBehavior.SILENT_NO_OP) return
            values.putAll(valuesToPut)
            keysToRemove.forEach(values::remove)
            if (behavior == WriteBehavior.CORRUPT_AFTER_WRITE) {
                values[PortableWalletCohortJournalStore.JOURNAL_KEY] = "corrupt"
            }
            if (behavior == WriteBehavior.APPLY_THEN_THROW) throw IOException("uncertain durable write")
        }

        override fun replaceEncryptedStringsDurablyIfStatesMatch(
            expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>,
            snapshotMoves: List<jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove>,
        ): Boolean {
            beforeCompare?.also { callback ->
                beforeCompare = null
                callback()
            }
            return super<EncryptedPreferences>.replaceEncryptedStringsDurablyIfStatesMatch(
                expectedStates, valuesToPut, keysToRemove, snapshotMoves,
            )
        }

        override fun quarantineEncryptedStringDurably(
            sourceKey: String,
            quarantineKey: String,
            expectedSnapshot: EncryptedPreferenceSnapshot,
        ): Boolean = error("No quarantine is used by this journal")

        override fun requireDurableStorageHealthy() = Unit
    }

    private enum class WriteBehavior { NORMAL, APPLY_THEN_THROW, SILENT_NO_OP, CORRUPT_AFTER_WRITE }

    private companion object {
        const val OPERATION_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val OTHER_OPERATION_ID = "223e4567-e89b-42d3-a456-426614174000"
    }
}
