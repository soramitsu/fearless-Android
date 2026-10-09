package jp.co.soramitsu.tonconnect.impl.data

import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.coredb.dao.TonConnectDao
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.coredb.model.TonConnectionReadProjection
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.xsalsa20poly1305.Keys
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.tonconnect.api.model.TonConnectionIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TonConnectRepositoryImplTest {

    @Test
    fun saveRoundTripsAndScopesListsToWallet() = runBlocking {
        val fixture = Fixture()
        val walletOne = connection(metaId = 1L, clientId = CLIENT_ID_QR)
        val walletTwo = connection(
            metaId = 2L,
            clientId = CLIENT_ID_TWO,
            url = SECOND_URL
        )

        fixture.repository().saveConnection(walletOne, keypair(PRIVATE_KEY))
        fixture.repository().saveConnection(walletTwo, keypair(PRIVATE_KEY_TWO))

        val restored = fixture.repository().getConnectionKeypair(walletOne.identity())
        assertArrayEquals(PRIVATE_KEY, restored?.privateKey)
        assertArrayEquals(PUBLIC_KEY, restored?.publicKey)
        assertEquals(
            listOf(walletOne.clientId),
            fixture.repository()
                .getConnections(walletOne.metaId, walletOne.source)
                .map { it.clientId }
        )
        assertFalse(fixture.preferences.hasKey(JOURNAL_KEY))
        assertTrue(fixture.preferences.keys().none { "example.com" in it })
    }

    @Test
    fun observerReplaysPendingJournalBeforeSubscribingToRoom() {
        val fixture = Fixture()
        val row = connection()
        val crashingRepository = fixture.repository { boundary ->
            if (boundary == TonConnectMutationBoundary.JOURNAL_STAGED) {
                throw SimulatedProcessDeath()
            }
        }
        assertThrows(SimulatedProcessDeath::class.java) {
            runBlocking {
                crashingRepository.saveConnection(row, keypair(PRIVATE_KEY))
            }
        }
        fixture.dao.beforeObserve = {
            assertFalse(fixture.preferences.hasKey(JOURNAL_KEY))
            assertEquals(row, fixture.dao.exact(row.identity()))
        }

        val observed = runBlocking {
            fixture.repository()
                .observeConnections(row.metaId, row.source)
                .first()
        }

        assertEquals(listOf(row.clientId), observed.map { it.clientId })
        assertSaveFinalState(fixture, row, PRIVATE_KEY)
    }

    @Test
    fun uppercaseThirtyTwoCharacterWebClientIdRoundTripsExactly() = runBlocking {
        val fixture = Fixture()
        val webClientId = CLIENT_ID_WEB.uppercase()
        val row = connection(
            clientId = webClientId,
            source = ConnectionSource.WEB
        )

        fixture.repository().saveConnection(row, keypair(PRIVATE_KEY))

        assertEquals(webClientId, fixture.dao.rows.single().clientId)
        assertArrayEquals(
            PRIVATE_KEY,
            fixture.repository().getConnectionKeypair(row.identity())?.privateKey
        )
        assertFalse(fixture.preferences.hasKey(legacyKey(webClientId.lowercase())))
    }

    @Test
    fun duplicateClientIdsAcrossWalletsRemainCryptographicallyIsolated() = runBlocking {
        val fixture = Fixture()
        val walletOne = connection(metaId = 1L)
        val walletTwo = connection(metaId = 2L, url = SECOND_URL)

        fixture.repository().saveConnection(walletOne, keypair(PRIVATE_KEY))
        fixture.repository().saveConnection(walletTwo, keypair(PRIVATE_KEY_TWO))

        val firstActive = scopedKey(walletOne)
        val secondActive = scopedKey(walletTwo)
        assertNotEquals(firstActive, secondActive)
        assertArrayEquals(
            PRIVATE_KEY,
            fixture.repository().getConnectionKeypair(walletOne.identity())?.privateKey
        )
        assertArrayEquals(
            PRIVATE_KEY_TWO,
            fixture.repository().getConnectionKeypair(walletTwo.identity())?.privateKey
        )

        fixture.repository().deleteConnection(walletOne.identity())

        assertNull(fixture.dao.exact(walletOne.identity()))
        assertEquals(walletTwo, fixture.dao.exact(walletTwo.identity()))
        assertFalse(fixture.preferences.hasKey(firstActive))
        assertTrue(fixture.preferences.hasKey(secondActive))
    }

    @Test
    fun samePrimaryKeyWithNewClientReplacesSecretWithoutLeakingOldLegacyKey() = runBlocking {
        val fixture = Fixture()
        val old = connection(clientId = CLIENT_ID_QR)
        val replacement = old.copy(clientId = CLIENT_ID_TWO, name = "Replacement")
        fixture.repository().saveConnection(old, keypair(PRIVATE_KEY))
        fixture.preferences.seed(
            legacyKey(old.clientId),
            encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        )

        fixture.repository().saveConnection(
            replacement,
            keypair(PRIVATE_KEY_TWO)
        )

        assertEquals(listOf(replacement), fixture.dao.rows)
        assertFalse(fixture.preferences.hasKey(legacyKey(old.clientId)))
        assertEquals(
            1,
            fixture.preferences.keys().count(TonConnectStorageKeys::isScopedKey)
        )
        assertArrayEquals(
            PRIVATE_KEY_TWO,
            fixture.repository()
                .getConnectionKeypair(replacement.identity())
                ?.privateKey
        )
    }

    @Test
    fun saveClearsScopedQuarantineOnlyWhenReplacementIsDurablyPromoted() = runBlocking {
        val fixture = Fixture()
        val row = connection()
        val active = scopedKey(row)
        fixture.preferences.seed(active, "old-active")
        fixture.preferences.seed(
            WalletSecretQuarantine.keyFor(active),
            "old-quarantine"
        )

        fixture.repository().saveConnection(row, keypair(PRIVATE_KEY))

        assertFalse(
            fixture.preferences.hasKey(
                WalletSecretQuarantine.keyFor(active)
            )
        )
        assertEquals(
            encodedKeypair(PRIVATE_KEY, PUBLIC_KEY),
            fixture.preferences.raw(active)
        )
    }

    @Test
    fun exactPrimaryKeyDeletionDoesNotDeleteAnotherRowWithSameClientId() = runBlocking {
        val fixture = Fixture()
        val first = connection(url = FIRST_URL)
        val second = connection(url = SECOND_URL)
        fixture.repository().saveConnection(first, keypair(PRIVATE_KEY))
        fixture.repository().saveConnection(second, keypair(PRIVATE_KEY_TWO))

        fixture.repository().deleteConnection(first.identity())

        assertNull(fixture.dao.exact(first.identity()))
        assertEquals(second, fixture.dao.exact(second.identity()))
        assertFalse(fixture.preferences.hasKey(scopedKey(first)))
        assertTrue(fixture.preferences.hasKey(scopedKey(second)))
    }

    @Test
    fun exactCaseLegacyReadFansOutAtomicallyToEveryOwner() = runBlocking {
        val fixture = Fixture()
        val uppercaseClientId = CLIENT_ID_QR.uppercase()
        val first = connection(metaId = 1L, clientId = uppercaseClientId)
        val second = connection(
            metaId = 2L,
            clientId = uppercaseClientId,
            url = SECOND_URL
        )
        fixture.dao.rows += listOf(first, second)
        val encoded = encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        fixture.preferences.seed(legacyKey(uppercaseClientId), encoded)
        fixture.preferences.seed(legacyKey(CLIENT_ID_QR), "unrelated-lowercase")

        val restored = fixture.repository().getConnectionKeypair(first.identity())

        assertArrayEquals(PRIVATE_KEY, restored?.privateKey)
        assertEquals(encoded, fixture.preferences.raw(scopedKey(first)))
        assertEquals(encoded, fixture.preferences.raw(scopedKey(second)))
        assertFalse(fixture.preferences.hasKey(legacyKey(uppercaseClientId)))
        assertEquals(
            "unrelated-lowercase",
            fixture.preferences.raw(legacyKey(CLIENT_ID_QR))
        )
        val fanOut = fixture.preferences.replaceCalls.last()
        assertEquals(setOf(scopedKey(first), scopedKey(second)), fanOut.first.keys)
        assertEquals(setOf(legacyKey(uppercaseClientId)), fanOut.second)
    }

    @Test
    fun legacySecretIsRetainedWhenAnyOwnerCannotBeMaterialized() = runBlocking {
        val fixture = Fixture()
        val first = connection(metaId = 1L)
        val second = connection(metaId = 2L, url = SECOND_URL)
        fixture.dao.rows += listOf(first, second)
        val encoded = encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        fixture.preferences.seed(legacyKey(CLIENT_ID_QR), encoded)
        fixture.preferences.seed(
            WalletSecretQuarantine.keyFor(scopedKey(second)),
            "existing-quarantine"
        )

        val restored = fixture.repository().getConnectionKeypair(first.identity())

        assertArrayEquals(PRIVATE_KEY, restored?.privateKey)
        assertEquals(encoded, fixture.preferences.raw(legacyKey(CLIENT_ID_QR)))
        assertFalse(fixture.preferences.hasKey(scopedKey(first)))
    }

    @Test
    fun staleLegacyFillsOnlyMissingOwnerAndKeepsDifferentValidScopedSecret() = runBlocking {
        val fixture = Fixture()
        val first = connection(metaId = 1L)
        val second = connection(metaId = 2L, url = SECOND_URL)
        fixture.dao.rows += listOf(first, second)
        val legacyEncoded = encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        val scopedEncoded = encodedKeypair(
            PRIVATE_KEY_TWO,
            Keys.generatePublicKey(PRIVATE_KEY_TWO)
        )
        fixture.preferences.seed(legacyKey(CLIENT_ID_QR), legacyEncoded)
        fixture.preferences.seed(scopedKey(second), scopedEncoded)

        val restored = fixture.repository()
            .getConnectionKeypair(first.identity())

        assertArrayEquals(PRIVATE_KEY, restored?.privateKey)
        assertFalse(
            fixture.preferences.hasKey(legacyKey(CLIENT_ID_QR))
        )
        assertEquals(legacyEncoded, fixture.preferences.raw(scopedKey(first)))
        assertEquals(scopedEncoded, fixture.preferences.raw(scopedKey(second)))
    }

    @Test
    fun deletingOneSharedLegacyOwnerRetainsSecretForRemainingOwner() = runBlocking {
        val fixture = Fixture()
        val first = connection(metaId = 1L)
        val second = connection(metaId = 2L, url = SECOND_URL)
        fixture.dao.rows += listOf(first, second)
        val encoded = encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        fixture.preferences.seed(legacyKey(CLIENT_ID_QR), encoded)

        fixture.repository().deleteConnection(first.identity())

        assertTrue(fixture.preferences.hasKey(legacyKey(CLIENT_ID_QR)))
        assertEquals(second, fixture.dao.exact(second.identity()))
        assertArrayEquals(
            PRIVATE_KEY,
            fixture.repository().getConnectionKeypair(second.identity())?.privateKey
        )
    }

    @Test
    fun newClientReplayRetiresOldLegacyOnlyWhenItsLastOwnerIsGone() = runBlocking {
        val fixture = Fixture()
        val old = connection(clientId = CLIENT_ID_QR)
        val replacement = old.copy(clientId = CLIENT_ID_TWO)
        val oldLegacy = legacyKey(old.clientId)
        fixture.dao.rows += old
        fixture.preferences.seed(
            oldLegacy,
            encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        )
        fixture.preferences.seed(
            WalletSecretQuarantine.keyFor(oldLegacy),
            "stale-quarantine"
        )
        val crashing = fixture.repository { boundary ->
            if (boundary == TonConnectMutationBoundary.DATABASE_MUTATED) {
                throw SimulatedProcessDeath()
            }
        }

        assertThrows(SimulatedProcessDeath::class.java) {
            runBlocking {
                crashing.saveConnection(
                    replacement,
                    keypair(PRIVATE_KEY_TWO)
                )
            }
        }
        assertTrue(fixture.preferences.hasKey(oldLegacy))

        fixture.repository()
            .getConnections(replacement.metaId, replacement.source)

        assertFalse(fixture.preferences.hasKey(oldLegacy))
        assertFalse(
            fixture.preferences.hasKey(
                WalletSecretQuarantine.keyFor(oldLegacy)
            )
        )
    }

    @Test
    fun replacingOneOfSeveralOldClientOwnersRetainsSharedLegacyState() = runBlocking {
        val fixture = Fixture()
        val first = connection(metaId = 1L, clientId = CLIENT_ID_QR)
        val second = connection(
            metaId = 2L,
            clientId = CLIENT_ID_QR,
            url = SECOND_URL
        )
        val replacement = first.copy(clientId = CLIENT_ID_TWO)
        val legacy = legacyKey(CLIENT_ID_QR)
        fixture.dao.rows += listOf(first, second)
        fixture.preferences.seed(
            legacy,
            encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        )

        fixture.repository().saveConnection(
            replacement,
            keypair(PRIVATE_KEY_TWO)
        )

        assertTrue(fixture.preferences.hasKey(legacy))
        assertEquals(second, fixture.dao.exact(second.identity()))
    }

    @Test
    fun oldClientExistenceCheckFinalizesSaveWithMoreThanFanOutBound() = runBlocking {
        val fixture = Fixture()
        val target = connection(metaId = 1L)
        repeat(257) { index ->
            fixture.dao.rows += connection(
                metaId = index.toLong() + 2L,
                url = "https://owner.example/$index"
            )
        }
        fixture.dao.rows += target
        val replacement = target.copy(clientId = CLIENT_ID_TWO)
        val legacy = legacyKey(CLIENT_ID_QR)
        fixture.preferences.seed(
            legacy,
            encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        )

        fixture.repository().saveConnection(
            replacement,
            keypair(PRIVATE_KEY_TWO)
        )

        assertFalse(fixture.preferences.hasKey(JOURNAL_KEY))
        assertTrue(fixture.preferences.hasKey(legacy))
        assertEquals(replacement, fixture.dao.exact(replacement.identity()))
    }

    @Test
    fun sharedLegacyExistenceCheckFinalizesDeleteWithMoreThanFanOutBound() = runBlocking {
        val fixture = Fixture()
        val target = connection(metaId = 1L)
        repeat(257) { index ->
            fixture.dao.rows += connection(
                metaId = index.toLong() + 2L,
                url = "https://owner.example/$index"
            )
        }
        fixture.dao.rows += target
        val legacy = legacyKey(CLIENT_ID_QR)
        fixture.preferences.seed(
            legacy,
            encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        )

        fixture.repository().deleteConnection(target.identity())

        assertFalse(fixture.preferences.hasKey(JOURNAL_KEY))
        assertTrue(fixture.preferences.hasKey(legacy))
        assertNull(fixture.dao.exact(target.identity()))
    }

    @Test
    fun legacyOwnerOverflowFailsClosedAndRetainsGlobalSecret() {
        val fixture = Fixture()
        val encoded = encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        repeat(257) { index ->
            fixture.dao.rows += connection(
                metaId = index.toLong() + 1L,
                url = "https://example.com/$index"
            )
        }
        val first = fixture.dao.rows.first()
        fixture.preferences.seed(legacyKey(CLIENT_ID_QR), encoded)

        assertThrows(TonConnectMutationJournalException::class.java) {
            runBlocking {
                fixture.repository().getConnectionKeypair(first.identity())
            }
        }

        assertEquals(encoded, fixture.preferences.raw(legacyKey(CLIENT_ID_QR)))
        assertTrue(
            fixture.preferences.keys().none(TonConnectStorageKeys::isScopedKey)
        )
    }

    @Test
    fun walletConnectionListOverflowFailsClosed() {
        val fixture = Fixture()
        repeat(257) { index ->
            fixture.dao.rows += connection(
                url = "https://example.com/$index"
            )
        }

        assertThrows(TonConnectMutationJournalException::class.java) {
            runBlocking {
                fixture.repository()
                    .getConnections(1L, ConnectionSource.QR)
            }
        }

        assertTrue(fixture.preferences.replaceCalls.isEmpty())
    }

    @Test
    fun oversizedSqlProjectionsNeverReachSecretsOrDomainModels() {
        val row = connection()
        val activeKey = scopedKey(row)
        val safeProjection = row.readProjection()
        val hostileProjections = listOf(
            safeProjection.copy(
                clientId = "",
                rowWithinBounds = false
            ),
            safeProjection.copy(
                name = "",
                rowWithinBounds = false
            ),
            safeProjection.copy(
                icon = "",
                rowWithinBounds = false
            ),
            safeProjection.copy(
                url = "",
                rowWithinBounds = false
            )
        )

        hostileProjections.forEach { hostile ->
            val fixture = Fixture()
            val encoded = encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
            fixture.dao.listProjectionOverride = listOf(hostile)
            fixture.preferences.seed(activeKey, encoded)

            assertThrows(TonConnectMutationJournalException::class.java) {
                runBlocking {
                    fixture.repository()
                        .getConnections(row.metaId, row.source)
                }
            }

            assertEquals(encoded, fixture.preferences.raw(activeKey))
            assertTrue(fixture.preferences.decryptedFields.isEmpty())
            assertEquals(0, fixture.preferences.replaceCount)
            assertEquals(0, fixture.dao.deleteCount)
        }
    }

    @Test
    fun oversizedExactProjectionCannotStartDeletion() {
        val fixture = Fixture()
        val row = connection()
        val activeKey = scopedKey(row)
        val encoded = encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        fixture.dao.rows += row
        fixture.dao.exactProjectionOverride = row.readProjection().copy(
            icon = "",
            rowWithinBounds = false
        )
        fixture.preferences.seed(activeKey, encoded)

        assertThrows(TonConnectMutationJournalException::class.java) {
            runBlocking {
                fixture.repository().deleteConnection(row.identity())
            }
        }

        assertEquals(row, fixture.dao.exact(row.identity()))
        assertEquals(encoded, fixture.preferences.raw(activeKey))
        assertFalse(fixture.preferences.hasKey(JOURNAL_KEY))
        assertEquals(0, fixture.preferences.replaceCount)
        assertEquals(0, fixture.dao.deleteCount)
    }

    @Test
    fun nonExactUtf8SqlProjectionFailsClosedBeforeSecretAccess() {
        val row = connection()
        val activeKey = scopedKey(row)
        val encoded = encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        val replacementUrl = "${row.url}/\uFFFD"
        val hostileProjections = listOf(
            row.readProjection().copy(
                url = replacementUrl,
                urlUtf8Bytes = replacementUrl.toByteArray().size.toLong()
            ),
            row.readProjection().copy(
                name = "é",
                nameUtf8Bytes = 1L
            )
        )

        hostileProjections.forEach { hostile ->
            val fixture = Fixture()
            fixture.dao.listProjectionOverride = listOf(hostile)
            fixture.preferences.seed(activeKey, encoded)

            assertThrows(TonConnectMutationJournalException::class.java) {
                runBlocking {
                    fixture.repository()
                        .getConnections(row.metaId, row.source)
                }
            }

            assertEquals(encoded, fixture.preferences.raw(activeKey))
            assertTrue(fixture.preferences.decryptedFields.isEmpty())
            assertEquals(0, fixture.preferences.replaceCount)
        }
    }

    @Test
    fun hostileScaleLengthIsQuarantinedWithoutCallingDeriver() = runBlocking {
        var derivationCalled = false
        val fixture = Fixture(
            deriver = {
                derivationCalled = true
                Keys.generatePublicKey(it)
            }
        )
        val row = connection()
        fixture.dao.rows += row
        val hostile = "0x0300000040"
        fixture.preferences.seed(scopedKey(row), hostile)

        assertNull(fixture.repository().getConnectionKeypair(row.identity()))

        assertFalse(derivationCalled)
        assertFalse(fixture.preferences.hasKey(scopedKey(row)))
        assertEquals(
            hostile,
            fixture.preferences.raw(WalletSecretQuarantine.keyFor(scopedKey(row)))
        )
    }

    @Test
    fun oversizedKeypairPayloadIsQuarantinedBeforeScaleOrDerivation() = runBlocking {
        var derivationCalled = false
        val fixture = Fixture(
            deriver = {
                derivationCalled = true
                Keys.generatePublicKey(it)
            }
        )
        val row = connection()
        val oversized = "0x" + "00".repeat(1_000)
        fixture.dao.rows += row
        fixture.preferences.seed(scopedKey(row), oversized)

        assertNull(
            fixture.repository().getConnectionKeypair(row.identity())
        )

        assertFalse(derivationCalled)
        assertEquals(
            oversized,
            fixture.preferences.raw(
                WalletSecretQuarantine.keyFor(scopedKey(row))
            )
        )
    }

    @Test
    fun staleCorruptionReaderCannotQuarantineConcurrentValidSave() {
        val fixture = Fixture()
        val row = connection()
        fixture.dao.rows += row
        val corrupt = "0x0300000040"
        val valid = encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        fixture.preferences.seed(scopedKey(row), corrupt)
        fixture.preferences.beforeQuarantineCompare = { sourceKey ->
            fixture.preferences.seed(sourceKey, valid)
        }

        assertThrows(WalletSecretConcurrentMutationException::class.java) {
            runBlocking {
                fixture.repository().getConnectionKeypair(row.identity())
            }
        }

        assertEquals(valid, fixture.preferences.raw(scopedKey(row)))
        assertFalse(
            fixture.preferences.hasKey(
                WalletSecretQuarantine.keyFor(scopedKey(row))
            )
        )
    }

    @Test
    fun corruptSharedLegacyPayloadIsQuarantinedExactlyOnceForAllOwners() = runBlocking {
        val fixture = Fixture()
        val first = connection(metaId = 1L)
        val second = connection(metaId = 2L, url = SECOND_URL)
        val corrupt = "0x0300000040"
        val legacy = legacyKey(CLIENT_ID_QR)
        fixture.dao.rows += listOf(first, second)
        fixture.preferences.seed(legacy, corrupt)

        assertNull(
            fixture.repository().getConnectionKeypair(first.identity())
        )
        assertNull(
            fixture.repository().getConnectionKeypair(second.identity())
        )

        assertFalse(fixture.preferences.hasKey(legacy))
        assertEquals(
            corrupt,
            fixture.preferences.raw(WalletSecretQuarantine.keyFor(legacy))
        )
    }

    @Test
    fun quarantineCommitFailureRetainsExactActivePayload() {
        val fixture = Fixture()
        val row = connection()
        fixture.dao.rows += row
        val corrupt = "0x0300000040"
        fixture.preferences.seed(scopedKey(row), corrupt)
        fixture.preferences.quarantineFailure =
            IllegalStateException("durable commit unavailable")

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fixture.repository().getConnectionKeypair(row.identity())
            }
        }

        assertEquals(corrupt, fixture.preferences.raw(scopedKey(row)))
        assertFalse(
            fixture.preferences.hasKey(
                WalletSecretQuarantine.keyFor(scopedKey(row))
            )
        )
    }

    @Test
    fun providerFailureAndErrorNeverMutateActivePayload() {
        val failures = listOf<Throwable>(
            UnsupportedOperationException("provider unavailable"),
            AssertionError("native linkage unavailable")
        )
        failures.forEach { expectedFailure ->
            val row = connection()
            val encoded = encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
            val fixture = Fixture(deriver = { throw expectedFailure })
            fixture.dao.rows += row
            fixture.preferences.seed(scopedKey(row), encoded)

            assertThrows(expectedFailure::class.java) {
                runBlocking {
                    fixture.repository().getConnectionKeypair(row.identity())
                }
            }

            assertEquals(encoded, fixture.preferences.raw(scopedKey(row)))
            assertFalse(
                fixture.preferences.hasKey(
                    WalletSecretQuarantine.keyFor(scopedKey(row))
                )
            )
        }
    }

    @Test
    fun saveProviderFailureCreatesNeitherJournalNorDatabaseRow() {
        val fixture = Fixture(
            deriver = {
                throw UnsupportedOperationException("provider unavailable")
            }
        )
        val row = connection()

        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking {
                fixture.repository().saveConnection(
                    row,
                    keypair(PRIVATE_KEY)
                )
            }
        }

        assertTrue(fixture.dao.rows.isEmpty())
        assertTrue(fixture.preferences.keys().isEmpty())
    }

    @Test
    fun replayProviderFailureRetainsJournalStageAndDatabaseBeforeImage() {
        val fixture = Fixture()
        val row = connection()
        val crashing = fixture.repository { boundary ->
            if (boundary == TonConnectMutationBoundary.JOURNAL_STAGED) {
                throw SimulatedProcessDeath()
            }
        }
        assertThrows(SimulatedProcessDeath::class.java) {
            runBlocking {
                crashing.saveConnection(row, keypair(PRIVATE_KEY))
            }
        }
        val journalSnapshot = fixture.preferences.raw(JOURNAL_KEY)
        val stageSnapshot = fixture.preferences.keys()
            .single { it.startsWith("TON_CONNECT_MUTATION_STAGE_V1_") }
            .let(fixture.preferences::raw)
        val failingRestart = Fixture(
            preferences = fixture.preferences,
            dao = fixture.dao,
            deriver = {
                throw UnsupportedOperationException("provider unavailable")
            }
        )

        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking {
                failingRestart.repository()
                    .getConnections(row.metaId, row.source)
            }
        }

        assertTrue(fixture.dao.rows.isEmpty())
        assertEquals(journalSnapshot, fixture.preferences.raw(JOURNAL_KEY))
        assertEquals(
            stageSnapshot,
            fixture.preferences.keys()
                .single { it.startsWith("TON_CONNECT_MUTATION_STAGE_V1_") }
                .let(fixture.preferences::raw)
        )
    }

    @Test
    fun legacyProviderFailureNeverMutatesSharedPayload() {
        val failures = listOf<Throwable>(
            UnsupportedOperationException("provider unavailable"),
            AssertionError("native linkage unavailable")
        )
        failures.forEach { expectedFailure ->
            val fixture = Fixture(deriver = { throw expectedFailure })
            val row = connection()
            val encoded = encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
            val legacy = legacyKey(row.clientId)
            fixture.dao.rows += row
            fixture.preferences.seed(legacy, encoded)

            assertThrows(expectedFailure::class.java) {
                runBlocking {
                    fixture.repository().getConnectionKeypair(row.identity())
                }
            }

            assertEquals(encoded, fixture.preferences.raw(legacy))
            assertFalse(
                fixture.preferences.hasKey(
                    WalletSecretQuarantine.keyFor(legacy)
                )
            )
        }
    }

    @Test
    fun saveCrashAtEveryBoundaryReplaysForwardTwiceIdentically() {
        TonConnectMutationBoundary.entries.forEach { boundary ->
            val fixture = Fixture()
            val row = connection()
            val crashingRepository = fixture.repository { observed ->
                if (observed == boundary) throw SimulatedProcessDeath()
            }

            assertThrows(SimulatedProcessDeath::class.java) {
                runBlocking {
                    crashingRepository.saveConnection(row, keypair(PRIVATE_KEY))
                }
            }

            runBlocking {
                val restarted = fixture.repository()
                assertEquals(
                    listOf(row.clientId),
                    restarted.getConnections(row.metaId, row.source)
                        .map { it.clientId }
                )
                assertEquals(
                    listOf(row.clientId),
                    restarted.getConnections(row.metaId, row.source)
                        .map { it.clientId }
                )
            }
            assertSaveFinalState(fixture, row, PRIVATE_KEY)
        }
    }

    @Test
    fun deleteCrashAtEveryBoundaryReplaysForwardTwiceIdentically() {
        TonConnectMutationBoundary.entries.forEach { boundary ->
            val fixture = Fixture()
            val row = connection()
            runBlocking {
                fixture.repository().saveConnection(row, keypair(PRIVATE_KEY))
            }
            val crashingRepository = fixture.repository { observed ->
                if (observed == boundary) throw SimulatedProcessDeath()
            }

            assertThrows(SimulatedProcessDeath::class.java) {
                runBlocking {
                    crashingRepository.deleteConnection(row.identity())
                }
            }

            runBlocking {
                val restarted = fixture.repository()
                assertTrue(
                    restarted.getConnections(row.metaId, row.source).isEmpty()
                )
                assertTrue(
                    restarted.getConnections(row.metaId, row.source).isEmpty()
                )
            }
            assertDeleteFinalState(fixture, row)
        }
    }

    @Test
    fun daoInsertFailureLeavesRecoverableSaveJournal() {
        val fixture = Fixture()
        val row = connection()
        fixture.dao.failNextInsertBeforeCommit =
            IllegalStateException("database unavailable")

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fixture.repository().saveConnection(row, keypair(PRIVATE_KEY))
            }
        }

        assertTrue(fixture.preferences.hasKey(JOURNAL_KEY))
        runBlocking {
            val restarted = fixture.repository()
            restarted.reconcilePendingMutation()
            assertEquals(1, restarted.getConnections(row.metaId, row.source).size)
            assertEquals(1, restarted.getConnections(row.metaId, row.source).size)
        }
        assertSaveFinalState(fixture, row, PRIVATE_KEY)
    }

    @Test
    fun daoInsertAfterCommitFailureReplaysIdempotently() {
        val fixture = Fixture()
        val row = connection()
        fixture.dao.failNextInsertAfterCommit =
            IllegalStateException("process died after Room commit")

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fixture.repository().saveConnection(row, keypair(PRIVATE_KEY))
            }
        }

        assertEquals(row, fixture.dao.exact(row.identity()))
        assertTrue(fixture.preferences.hasKey(JOURNAL_KEY))
        runBlocking {
            fixture.repository().getConnections(row.metaId, row.source)
            fixture.repository().getConnections(row.metaId, row.source)
        }
        assertSaveFinalState(fixture, row, PRIVATE_KEY)
    }

    @Test
    fun daoDeleteFailureLeavesRecoverableDeleteJournal() = runBlocking {
        val fixture = Fixture()
        val row = connection()
        fixture.repository().saveConnection(row, keypair(PRIVATE_KEY))
        fixture.dao.failNextDeleteBeforeCommit =
            IllegalStateException("database unavailable")

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fixture.repository().deleteConnection(row.identity())
            }
        }

        assertTrue(fixture.preferences.hasKey(JOURNAL_KEY))
        val restarted = fixture.repository()
        assertTrue(restarted.getConnections(row.metaId, row.source).isEmpty())
        assertTrue(restarted.getConnections(row.metaId, row.source).isEmpty())
        assertDeleteFinalState(fixture, row)
    }

    @Test
    fun daoDeleteAfterCommitFailureReplaysIdempotently() = runBlocking {
        val fixture = Fixture()
        val row = connection()
        fixture.repository().saveConnection(row, keypair(PRIVATE_KEY))
        fixture.dao.failNextDeleteAfterCommit =
            IllegalStateException("process died after Room delete")

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fixture.repository().deleteConnection(row.identity())
            }
        }

        assertNull(fixture.dao.exact(row.identity()))
        assertTrue(fixture.preferences.hasKey(JOURNAL_KEY))
        fixture.repository().getConnections(row.metaId, row.source)
        fixture.repository().getConnections(row.metaId, row.source)
        assertDeleteFinalState(fixture, row)
    }

    @Test
    fun preferencePromotionFailureLeavesRecoverableSaveJournal() {
        val fixture = Fixture()
        val row = connection()
        fixture.preferences.failReplaceCall =
            fixture.preferences.replaceCount + FINALIZE_REPLACE_OFFSET

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fixture.repository().saveConnection(row, keypair(PRIVATE_KEY))
            }
        }

        assertEquals(row, fixture.dao.exact(row.identity()))
        assertTrue(fixture.preferences.hasKey(JOURNAL_KEY))
        runBlocking {
            fixture.repository().getConnections(row.metaId, row.source)
            fixture.repository().getConnections(row.metaId, row.source)
        }
        assertSaveFinalState(fixture, row, PRIVATE_KEY)
    }

    @Test
    fun preferenceDeleteFinalizationFailureLeavesRecoverableJournal() = runBlocking {
        val fixture = Fixture()
        val row = connection()
        fixture.repository().saveConnection(row, keypair(PRIVATE_KEY))
        fixture.preferences.failReplaceCall =
            fixture.preferences.replaceCount + FINALIZE_REPLACE_OFFSET

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fixture.repository().deleteConnection(row.identity())
            }
        }

        assertNull(fixture.dao.exact(row.identity()))
        assertTrue(fixture.preferences.hasKey(JOURNAL_KEY))
        fixture.repository().getConnections(row.metaId, row.source)
        fixture.repository().getConnections(row.metaId, row.source)
        assertDeleteFinalState(fixture, row)
    }

    @Test
    fun ambiguousPreferenceCommitThatAppliedIsAlreadySafeOnRestart() {
        val fixture = Fixture()
        val row = connection()
        fixture.preferences.applyBeforeReplaceFailure = true
        fixture.preferences.failReplaceCall =
            fixture.preferences.replaceCount + FINALIZE_REPLACE_OFFSET

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fixture.repository().saveConnection(row, keypair(PRIVATE_KEY))
            }
        }

        assertFalse(fixture.preferences.hasKey(JOURNAL_KEY))
        runBlocking {
            assertEquals(
                1,
                fixture.repository().getConnections(row.metaId, row.source).size
            )
        }
        assertSaveFinalState(fixture, row, PRIVATE_KEY)
    }

    @Test
    fun malformedTruncatedOversizedAndUnknownJournalsFailClosed() {
        val malformedJournals = listOf(
            "{",
            "x".repeat(32_769),
            """
            {"version":1,"operation":"UNKNOWN","operationId":"${"a".repeat(32)}"}
            """.trimIndent()
        )

        malformedJournals.forEach { malformed ->
            val fixture = Fixture()
            val row = connection()
            fixture.dao.rows += row
            fixture.preferences.seed(JOURNAL_KEY, malformed)
            val replaceCount = fixture.preferences.replaceCount

            val failure = assertThrows(TonConnectMutationJournalException::class.java) {
                runBlocking {
                    fixture.repository()
                        .getConnections(row.metaId, row.source)
                }
            }

            assertFalse(failure.message.orEmpty().contains(PRIVATE_KEY.toString()))
            assertEquals(row, fixture.dao.exact(row.identity()))
            assertEquals(malformed, fixture.preferences.raw(JOURNAL_KEY))
            assertEquals(replaceCount, fixture.preferences.replaceCount)
        }
    }

    @Test
    fun invalidClientIdsAreRejectedBeforePreferenceOrDatabaseMutation() {
        val invalidIds = listOf(
            "",
            "ab",
            "g".repeat(32),
            "0x" + "ab".repeat(15),
            "é".repeat(32),
            "a".repeat(65),
            "a".repeat(1_000_000)
        )

        invalidIds.forEach { invalidClientId ->
            val fixture = Fixture()
            val invalid = connection(clientId = invalidClientId)

            assertThrows(RuntimeException::class.java) {
                runBlocking {
                    fixture.repository().saveConnection(
                        invalid,
                        keypair(PRIVATE_KEY)
                    )
                }
            }

            assertTrue(fixture.dao.rows.isEmpty())
            assertEquals(0, fixture.preferences.replaceCount)
        }
    }

    @Test
    fun nonExactPersistedTextCannotLeaveAStartupReplayJournal() {
        val hostileRows = listOf(
            connection().copy(name = "Dapp\uFFFD"),
            connection().copy(icon = "https://example.com/\uFFFD.png"),
            connection().copy(url = "https://example.com/\uFFFD"),
            connection().copy(name = "Dapp\uD800"),
            connection().copy(icon = "https://example.com/\uD800.png"),
            connection().copy(url = "https://example.com/\uD800")
        )

        hostileRows.forEach { hostile ->
            val fixture = Fixture()

            assertThrows(TonConnectMutationJournalException::class.java) {
                runBlocking {
                    fixture.repository().saveConnection(
                        hostile,
                        keypair(PRIVATE_KEY)
                    )
                }
            }

            assertTrue(fixture.dao.rows.isEmpty())
            assertEquals(0, fixture.preferences.replaceCount)
            assertFalse(fixture.preferences.hasKey(JOURNAL_KEY))

            runBlocking {
                fixture.repository().reconcilePendingMutation()
            }
            assertTrue(fixture.dao.rows.isEmpty())
            assertEquals(0, fixture.preferences.replaceCount)
        }
    }

    @Test
    fun existingRowWithMissingSecretIsNeverExposed() = runBlocking {
        val fixture = Fixture()
        val row = connection()
        fixture.dao.rows += row

        assertTrue(
            fixture.repository().getConnections(row.metaId, row.source).isEmpty()
        )
        assertNull(fixture.repository().getConnection(row.identity()))
    }

    @Test
    fun orphanScopedSecretIsNeverExposedWithoutItsExactDatabaseRow() = runBlocking {
        val fixture = Fixture()
        val absentRow = connection()
        val encoded = encodedKeypair(PRIVATE_KEY, PUBLIC_KEY)
        fixture.preferences.seed(scopedKey(absentRow), encoded)

        assertNull(
            fixture.repository()
                .getConnectionKeypair(absentRow.identity())
        )

        assertEquals(encoded, fixture.preferences.raw(scopedKey(absentRow)))
        assertTrue(fixture.preferences.replaceCalls.isEmpty())
    }

    @Test
    fun invalidKeyShapeAndNonceAreLocallyQuarantined() {
        val invalidPayloads = listOf(
            encodedKeypair(ByteArray(31), PUBLIC_KEY),
            encodedKeypair(PRIVATE_KEY, ByteArray(31)),
            encodedKeypair(PRIVATE_KEY, PUBLIC_KEY, ByteArray(32)),
            encodedKeypair(
                PRIVATE_KEY,
                Keys.generatePublicKey(PRIVATE_KEY_TWO)
            )
        )
        invalidPayloads.forEachIndexed { index, encoded ->
            val fixture = Fixture()
            val row = connection(
                url = "https://example.com/$index"
            )
            fixture.dao.rows += row
            fixture.preferences.seed(scopedKey(row), encoded)

            assertNull(
                runBlocking {
                    fixture.repository().getConnectionKeypair(row.identity())
                }
            )
            assertEquals(
                encoded,
                fixture.preferences.raw(
                    WalletSecretQuarantine.keyFor(scopedKey(row))
                )
            )
        }
    }

    private class Fixture(
        val preferences: FakeEncryptedPreferences = FakeEncryptedPreferences(),
        val dao: FakeTonConnectDao = FakeTonConnectDao(),
        private val deriver: (ByteArray) -> ByteArray = Keys::generatePublicKey
    ) {
        fun repository(boundaryObserver: (TonConnectMutationBoundary) -> Unit = {}) = TonConnectRepositoryImpl(
                tonConnectDao = dao,
                encryptedPreferences = preferences,
                tonPublicKeyDeriver = deriver,
                operationIdFactory = { OPERATION_ID },
                mutationBoundaryObserver = boundaryObserver
            )
    }

    private class FakeEncryptedPreferences : EncryptedPreferences {

        private val values = linkedMapOf<String, String>()
        val decryptedFields = mutableListOf<String>()
        val replaceCalls =
            mutableListOf<Pair<Map<String, String>, Set<String>>>()
        var replaceCount = 0
            private set
        var failReplaceCall: Int? = null
        var applyBeforeReplaceFailure = false
        var quarantineFailure: Throwable? = null
        var beforeQuarantineCompare: ((String) -> Unit)? = null

        override fun putEncryptedString(field: String, value: String) {
            values[field] = value
        }

        override fun getDecryptedString(field: String): String? {
            decryptedFields += field
            return values[field]
        }

        override fun hasKey(field: String): Boolean = field in values

        override fun removeKey(field: String) {
            values.remove(field)
        }

        override fun replaceEncryptedStringsDurably(valuesToPut: Map<String, String>, keysToRemove: Set<String>) {
            replaceCount += 1
            val shouldFail = failReplaceCall == replaceCount
            if (shouldFail && !applyBeforeReplaceFailure) {
                failReplaceCall = null
                error("simulated durable preference failure")
            }
            values.putAll(valuesToPut)
            keysToRemove.forEach(values::remove)
            replaceCalls += valuesToPut.toMap() to keysToRemove.toSet()
            if (shouldFail) {
                failReplaceCall = null
                error("simulated ambiguous preference failure")
            }
        }

        override fun quarantineEncryptedStringDurably(
            sourceKey: String,
            quarantineKey: String,
            expectedSnapshot: EncryptedPreferenceSnapshot
        ): Boolean {
            quarantineFailure?.let(::throwFailure)
            beforeQuarantineCompare?.also { beforeQuarantineCompare = null }
                ?.invoke(sourceKey)
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
            check(values[quarantineKey] == null || values[quarantineKey] == current)
            values[quarantineKey] = checkNotNull(current)
            values.remove(sourceKey)
            return true
        }

        override fun requireDurableStorageHealthy() = Unit

        fun seed(key: String, value: String) {
            values[key] = value
        }

        fun raw(key: String): String? = values[key]

        fun keys(): Set<String> = values.keys.toSet()
    }

    private class FakeTonConnectDao : TonConnectDao() {

        val rows = mutableListOf<TonConnectionLocal>()
        var failNextInsertBeforeCommit: Throwable? = null
        var failNextInsertAfterCommit: Throwable? = null
        var failNextDeleteBeforeCommit: Throwable? = null
        var failNextDeleteAfterCommit: Throwable? = null
        var beforeObserve: (() -> Unit)? = null
        var listProjectionOverride: List<TonConnectionReadProjection>? = null
        var exactProjectionOverride: TonConnectionReadProjection? = null
        var ownerProjectionOverride: List<TonConnectionReadProjection>? = null
        var deleteCount = 0
            private set

        override suspend fun insertTonConnection(connection: TonConnectionLocal) {
            failNextInsertBeforeCommit?.also {
                failNextInsertBeforeCommit = null
                throwFailure(it)
            }
            rows.removeAll { it.identity() == connection.identity() }
            rows += connection
            failNextInsertAfterCommit?.also {
                failNextInsertAfterCommit = null
                throwFailure(it)
            }
        }

        override fun observeTonConnections(
            metaId: Long,
            source: ConnectionSource
        ): Flow<List<TonConnectionReadProjection>> {
            beforeObserve?.invoke()
            return flowOf(
                listProjectionOverride ?: rows
                    .filter { it.metaId == metaId && it.source == source }
                    .map(TonConnectionLocal::readProjection)
            )
        }

        override suspend fun getTonConnections(
            metaId: Long,
            source: ConnectionSource
        ): List<TonConnectionReadProjection> {
            return listProjectionOverride ?: rows
                .filter { it.metaId == metaId && it.source == source }
                .map(TonConnectionLocal::readProjection)
        }

        override suspend fun getTonConnection(
            metaId: Long,
            url: String,
            source: ConnectionSource
        ): TonConnectionReadProjection? {
            exactProjectionOverride?.let { return it }
            return rows.firstOrNull {
                it.metaId == metaId &&
                    it.url == url &&
                    it.source == source
            }?.readProjection()
        }

        override suspend fun getTonConnectionsByClientId(clientId: String): List<TonConnectionReadProjection> {
            return ownerProjectionOverride ?: rows
                .filter { it.clientId == clientId }
                .map(TonConnectionLocal::readProjection)
        }

        override suspend fun hasTonConnectionsByClientId(clientId: String): Boolean {
            return rows.any { it.clientId == clientId }
        }

        override suspend fun deleteTonConnection(
            metaId: Long,
            url: String,
            source: ConnectionSource
        ) {
            deleteCount += 1
            failNextDeleteBeforeCommit?.also {
                failNextDeleteBeforeCommit = null
                throwFailure(it)
            }
            rows.removeAll {
                it.metaId == metaId &&
                    it.url == url &&
                    it.source == source
            }
            failNextDeleteAfterCommit?.also {
                failNextDeleteAfterCommit = null
                throwFailure(it)
            }
        }

        fun exact(identity: TonConnectionIdentity): TonConnectionLocal? {
            return rows.firstOrNull { it.identity() == identity }
        }
    }

    private class SimulatedProcessDeath : Error()

    companion object {
        private const val CLIENT_ID_QR =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        private const val CLIENT_ID_TWO =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        private const val CLIENT_ID_WEB = "abcdef0123456789abcdef0123456789"
        private const val FIRST_URL = "https://example.com"
        private const val SECOND_URL = "https://second.example"
        private const val OPERATION_ID = "aabbccddeeff00112233445566778899"
        private const val FINALIZE_REPLACE_OFFSET = 2
        private const val JOURNAL_KEY =
            TonConnectMutationJournalCodec.JOURNAL_KEY
        private val PRIVATE_KEY = ByteArray(32) { (it + 1).toByte() }
        private val PRIVATE_KEY_TWO = ByteArray(32) { (it + 33).toByte() }
        private val PUBLIC_KEY = Keys.generatePublicKey(PRIVATE_KEY)

        private fun keypair(privateKey: ByteArray): Keypair {
            return BaseKeypair(
                privateKey = privateKey,
                publicKey = Keys.generatePublicKey(privateKey)
            )
        }

        private fun connection(
            metaId: Long = 1L,
            clientId: String = CLIENT_ID_QR,
            url: String = FIRST_URL,
            source: ConnectionSource = ConnectionSource.QR
        ) = TonConnectionLocal(
            metaId = metaId,
            clientId = clientId,
            name = "Dapp",
            icon = "https://example.com/icon.png",
            url = url,
            source = source
        )

        private fun scopedKey(connection: TonConnectionLocal): String {
            return TonConnectStorageKeys.scoped(
                metaId = connection.metaId,
                url = connection.url,
                source = connection.source.name
            )
        }

        private fun legacyKey(clientId: String): String {
            return TonConnectStorageKeys.legacy(clientId)
        }

        private fun encodedKeypair(
            privateKey: ByteArray,
            publicKey: ByteArray,
            nonce: ByteArray? = null
        ): String {
            return KeyPairSchema { keypair ->
                keypair[PrivateKey] = privateKey
                keypair[PublicKey] = publicKey
                keypair[Nonce] = nonce
            }.toHexString()
        }

        private fun assertSaveFinalState(
            fixture: Fixture,
            row: TonConnectionLocal,
            privateKey: ByteArray
        ) {
            assertEquals(row, fixture.dao.exact(row.identity()))
            assertFalse(fixture.preferences.hasKey(JOURNAL_KEY))
            assertTrue(
                fixture.preferences.keys().none {
                    it.startsWith("TON_CONNECT_MUTATION_STAGE_V1_")
                }
            )
            val encoded = fixture.preferences.raw(scopedKey(row))
            assertEquals(
                encodedKeypair(
                    privateKey,
                    Keys.generatePublicKey(privateKey)
                ),
                encoded
            )
        }

        private fun assertDeleteFinalState(fixture: Fixture, row: TonConnectionLocal) {
            assertNull(fixture.dao.exact(row.identity()))
            assertFalse(fixture.preferences.hasKey(JOURNAL_KEY))
            assertFalse(fixture.preferences.hasKey(scopedKey(row)))
            assertFalse(
                fixture.preferences.hasKey(
                    WalletSecretQuarantine.keyFor(scopedKey(row))
                )
            )
        }

        private fun throwFailure(failure: Throwable): Nothing {
            throw failure
        }
    }
}

private fun TonConnectionLocal.identity() = TonConnectionIdentity(this)

private fun TonConnectionLocal.readProjection() = TonConnectionReadProjection(
    metaId = metaId,
    clientId = clientId,
    name = name,
    icon = icon,
    url = url,
    source = source.name,
    clientIdUtf8Bytes = clientId.toByteArray().size.toLong(),
    nameUtf8Bytes = name.toByteArray().size.toLong(),
    iconUtf8Bytes = icon.toByteArray().size.toLong(),
    urlUtf8Bytes = url.toByteArray().size.toLong(),
    sourceUtf8Bytes = source.name.toByteArray().size.toLong(),
    rowWithinBounds = true
)
