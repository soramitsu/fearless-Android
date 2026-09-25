package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.common.data.storage.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EncryptedPreferencesImplDurabilityTest {

    private val preferences = mock<Preferences>()
    private val encryptionUtil = mock<EncryptionUtil>()
    private val encryptedPreferences = EncryptedPreferencesImpl(preferences, encryptionUtil)

    @Before
    fun setUp() {
        WalletSecureStorageHealth.resetForTest()
    }

    @After
    fun tearDown() {
        WalletSecureStorageHealth.resetForTest()
    }

    @Test
    fun prefixInspectionDelegatesWithoutReadingOrDecryptingValues() {
        whenever(preferences.hasKeyWithPrefix("42:")).thenReturn(true)

        assertEquals(true, encryptedPreferences.hasKeyWithPrefix("42:"))

        verify(preferences).hasKeyWithPrefix("42:")
        verify(preferences, never()).getString(any())
        verify(encryptionUtil, never()).decrypt(any())
    }

    @Test
    fun prefixInspectionRejectsEmptyPrefixBeforeStorageAccess() {
        assertThrows(IllegalArgumentException::class.java) {
            encryptedPreferences.hasKeyWithPrefix("")
        }

        verify(preferences, never()).hasKeyWithPrefix(any())
    }

    @Test
    fun boundedKeyInspectionDelegatesWithoutReadingOrDecryptingValues() {
        val expected = setOf("private_legacy-address")
        whenever(
            preferences.keysWithPrefixes(
                prefixes = setOf("private_"),
                maxResultCount = 8,
                maxKeyBytes = 128,
                maxTotalKeyBytes = 1_024,
                failOnOversizedMatch = true
            )
        ).thenReturn(expected)

        assertEquals(
            expected,
            encryptedPreferences.keysWithPrefixes(
                prefixes = setOf("private_"),
                maxResultCount = 8,
                maxKeyBytes = 128,
                maxTotalKeyBytes = 1_024,
                failOnOversizedMatch = true
            )
        )
        verify(preferences).keysWithPrefixes(
            prefixes = setOf("private_"),
            maxResultCount = 8,
            maxKeyBytes = 128,
            maxTotalKeyBytes = 1_024,
            failOnOversizedMatch = true
        )
        verify(preferences, never()).getString(any())
        verify(encryptionUtil, never()).decrypt(any())
    }

    @Test
    fun readingLegacyCiphertextNeverMutatesTheOnlyRecoverableSecret() {
        whenever(preferences.getString(LEGACY_KEY)).thenReturn(LEGACY_CIPHERTEXT)
        whenever(encryptionUtil.decrypt(LEGACY_CIPHERTEXT)).thenReturn(PLAINTEXT)

        org.junit.Assert.assertEquals(
            PLAINTEXT,
            encryptedPreferences.getDecryptedString(LEGACY_KEY)
        )

        verify(preferences, never()).putString(any(), any())
    }

    @Test
    fun oversizedCiphertextIsRejectedBeforeBase64OrCryptoAllocation() {
        val oversizedCiphertext = "A".repeat(2_097_153)
        whenever(preferences.getString(LEGACY_KEY)).thenReturn(oversizedCiphertext)

        val snapshot = encryptedPreferences.getDecryptedString(LEGACY_KEY)

        assertFalse(snapshot.isNullOrEmpty())
        assertFalse(snapshot == oversizedCiphertext)

        verify(encryptionUtil, never()).decrypt(any())
        verify(preferences, never()).replaceStringsDurably(any(), any())
    }

    @Test
    fun staleOversizedSnapshotCannotQuarantineDifferentOversizedCiphertext() {
        val staleCiphertext = "A".repeat(2_097_153)
        val currentCiphertext = "B".repeat(2_097_153)
        whenever(preferences.getString(LEGACY_KEY))
            .thenReturn(staleCiphertext, currentCiphertext)
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(true)
        whenever(preferences.contains(QUARANTINE_KEY)).thenReturn(false)
        val staleSnapshot = checkNotNull(
            encryptedPreferences.getDecryptedStringSnapshot(LEGACY_KEY)
        )

        val quarantined = encryptedPreferences.quarantineEncryptedStringDurably(
            sourceKey = LEGACY_KEY,
            quarantineKey = QUARANTINE_KEY,
            expectedSnapshot = staleSnapshot
        )

        assertFalse(quarantined)
        verify(encryptionUtil, never()).decrypt(any())
        verify(preferences, never()).replaceStringsDurably(any(), any())
    }

    @Test
    fun oversizedSnapshotCannotMatchNormalCiphertextWithSameTokenPlaintext() {
        val oversizedCiphertext =
            "A".repeat(OVERSIZED_TEST_CIPHERTEXT_CHARS)
        whenever(preferences.getString(LEGACY_KEY))
            .thenReturn(oversizedCiphertext, FRESH_CIPHERTEXT)
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(true)
        whenever(preferences.contains(QUARANTINE_KEY)).thenReturn(false)
        val oversizedSnapshot = checkNotNull(
            encryptedPreferences.getDecryptedStringSnapshot(LEGACY_KEY)
        )
        whenever(encryptionUtil.decrypt(FRESH_CIPHERTEXT))
            .thenReturn(oversizedSnapshot.plaintext)

        val quarantined = encryptedPreferences.quarantineEncryptedStringDurably(
            sourceKey = LEGACY_KEY,
            quarantineKey = QUARANTINE_KEY,
            expectedSnapshot = oversizedSnapshot
        )

        assertFalse(quarantined)
        verify(encryptionUtil, never()).decrypt(any())
        verify(preferences, never()).replaceStringsDurably(any(), any())
    }

    @Test
    fun normalTokenSnapshotCannotMatchOversizedCiphertextRepresentation() {
        val oversizedCiphertext =
            "B".repeat(OVERSIZED_TEST_CIPHERTEXT_CHARS)
        val oversizedToken = OVERSIZED_TOKEN_PREFIX +
            walletCiphertextFingerprint(oversizedCiphertext)
        whenever(preferences.getString(LEGACY_KEY))
            .thenReturn(FRESH_CIPHERTEXT, oversizedCiphertext)
        whenever(encryptionUtil.decrypt(FRESH_CIPHERTEXT))
            .thenReturn(oversizedToken)
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(true)
        whenever(preferences.contains(QUARANTINE_KEY)).thenReturn(false)
        val normalSnapshot = checkNotNull(
            encryptedPreferences.getDecryptedStringSnapshot(LEGACY_KEY)
        )

        val quarantined = encryptedPreferences.quarantineEncryptedStringDurably(
            sourceKey = LEGACY_KEY,
            quarantineKey = QUARANTINE_KEY,
            expectedSnapshot = normalSnapshot
        )

        assertFalse(quarantined)
        verify(encryptionUtil, times(1)).decrypt(FRESH_CIPHERTEXT)
        verify(preferences, never()).replaceStringsDurably(any(), any())
    }

    @Test
    fun silentEmptyEncryptionFailsBeforeMutatingTheOnlyLegacySecret() {
        whenever(encryptionUtil.encrypt(PLAINTEXT)).thenReturn("")

        assertThrows(IllegalStateException::class.java) {
            encryptedPreferences.replaceEncryptedStringsDurably(
                valuesToPut = mapOf(NEW_KEY to PLAINTEXT),
                keysToRemove = setOf(LEGACY_KEY)
            )
        }

        verify(preferences, never()).replaceStringsDurably(any(), any())
    }

    @Test
    fun failedSynchronousCommitLatchesRoomRetriesForTheCurrentProcess() {
        stubVerifiedEncryption()
        whenever(
            preferences.replaceStringsDurably(
                eq(mapOf(NEW_KEY to CIPHERTEXT)),
                eq(setOf(LEGACY_KEY))
            )
        ).thenReturn(false)

        assertThrows(IllegalStateException::class.java) {
            encryptedPreferences.replaceEncryptedStringsDurably(
                valuesToPut = mapOf(NEW_KEY to PLAINTEXT),
                keysToRemove = setOf(LEGACY_KEY)
            )
        }

        assertThrows(IllegalStateException::class.java) {
            encryptedPreferences.requireDurableStorageHealthy()
        }
    }

    @Test
    fun verifiedCiphertextUsesOneAtomicSynchronousReplacement() {
        stubVerifiedEncryption()
        whenever(
            preferences.replaceStringsDurably(
                eq(mapOf(NEW_KEY to CIPHERTEXT)),
                eq(setOf(LEGACY_KEY))
            )
        ).thenReturn(true)
        whenever(preferences.getString(NEW_KEY)).thenReturn(CIPHERTEXT)
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(false)

        encryptedPreferences.replaceEncryptedStringsDurably(
            valuesToPut = mapOf(NEW_KEY to PLAINTEXT),
            keysToRemove = setOf(LEGACY_KEY)
        )

        verify(preferences).replaceStringsDurably(
            mapOf(NEW_KEY to CIPHERTEXT),
            setOf(LEGACY_KEY)
        )
        encryptedPreferences.requireDurableStorageHealthy()
    }

    @Test
    fun snapshotBoundPublishAndQuarantineUseOneAtomicRawCommit() {
        stubVerifiedEncryption()
        whenever(encryptionUtil.decrypt(MALFORMED_CIPHERTEXT))
            .thenReturn(MALFORMED_PLAINTEXT)
        whenever(preferences.getString(NEW_KEY))
            .thenReturn(null, CIPHERTEXT)
        whenever(preferences.getString(LEGACY_KEY))
            .thenReturn(MALFORMED_CIPHERTEXT)
        whenever(preferences.getString(QUARANTINE_KEY))
            .thenReturn(
                null,
                MALFORMED_CIPHERTEXT,
                MALFORMED_CIPHERTEXT
            )
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(false)
        whenever(
            preferences.replaceStringsDurably(
                eq(
                    mapOf(
                        NEW_KEY to CIPHERTEXT,
                        QUARANTINE_KEY to MALFORMED_CIPHERTEXT
                    )
                ),
                eq(setOf(LEGACY_KEY))
            )
        ).thenReturn(true)

        val replaced =
            encryptedPreferences
                .replaceEncryptedStringsDurablyIfStatesMatch(
                    expectedStates = mapOf(NEW_KEY to null),
                    valuesToPut = mapOf(NEW_KEY to PLAINTEXT),
                    keysToRemove = emptySet(),
                    snapshotMoves = listOf(
                        EncryptedPreferenceSnapshotMove(
                            sourceKey = LEGACY_KEY,
                            destinationKey = QUARANTINE_KEY,
                            expectedSnapshot = malformedSnapshot()
                        )
                    )
                )

        assertTrue(replaced)
        assertEquals(
            1L,
            encryptedPreferences.walletSecretQuarantineVersion.value
        )
        verify(preferences, times(1)).replaceStringsDurably(
            mapOf(
                NEW_KEY to CIPHERTEXT,
                QUARANTINE_KEY to MALFORMED_CIPHERTEXT
            ),
            setOf(LEGACY_KEY)
        )
        verify(encryptionUtil, never())
            .encrypt(MALFORMED_PLAINTEXT)
    }

    @Test
    fun changedSnapshotMoveSourceRejectsWholePublishWithoutEncryption() {
        whenever(preferences.getString(NEW_KEY)).thenReturn(null)
        whenever(preferences.getString(LEGACY_KEY))
            .thenReturn(FRESH_CIPHERTEXT)
        whenever(preferences.getString(QUARANTINE_KEY))
            .thenReturn(null)

        val replaced =
            encryptedPreferences
                .replaceEncryptedStringsDurablyIfStatesMatch(
                    expectedStates = mapOf(NEW_KEY to null),
                    valuesToPut = mapOf(NEW_KEY to PLAINTEXT),
                    keysToRemove = emptySet(),
                    snapshotMoves = listOf(
                        EncryptedPreferenceSnapshotMove(
                            sourceKey = LEGACY_KEY,
                            destinationKey = QUARANTINE_KEY,
                            expectedSnapshot = malformedSnapshot()
                        )
                    )
                )

        assertFalse(replaced)
        assertEquals(
            0L,
            encryptedPreferences.walletSecretQuarantineVersion.value
        )
        verify(encryptionUtil, never()).encrypt(any())
        verify(preferences, never())
            .replaceStringsDurably(any(), any())
    }

    @Test
    fun conflictingSnapshotMoveDestinationRejectsWholePublish() {
        whenever(preferences.getString(NEW_KEY)).thenReturn(null)
        whenever(preferences.getString(LEGACY_KEY))
            .thenReturn(MALFORMED_CIPHERTEXT)
        whenever(preferences.getString(QUARANTINE_KEY))
            .thenReturn(FRESH_CIPHERTEXT)
        whenever(encryptionUtil.decrypt(MALFORMED_CIPHERTEXT))
            .thenReturn(MALFORMED_PLAINTEXT)

        val replaced =
            encryptedPreferences
                .replaceEncryptedStringsDurablyIfStatesMatch(
                    expectedStates = mapOf(NEW_KEY to null),
                    valuesToPut = mapOf(NEW_KEY to PLAINTEXT),
                    keysToRemove = emptySet(),
                    snapshotMoves = listOf(
                        EncryptedPreferenceSnapshotMove(
                            sourceKey = LEGACY_KEY,
                            destinationKey = QUARANTINE_KEY,
                            expectedSnapshot = malformedSnapshot()
                        )
                    )
                )

        assertFalse(replaced)
        verify(encryptionUtil, never()).encrypt(any())
        verify(preferences, never())
            .replaceStringsDurably(any(), any())
    }

    @Test
    fun completedSnapshotMoveAndPublishedTargetAreRetrySafe() {
        whenever(preferences.getString(NEW_KEY))
            .thenReturn(CIPHERTEXT)
        whenever(preferences.getString(LEGACY_KEY)).thenReturn(null)
        whenever(preferences.getString(QUARANTINE_KEY))
            .thenReturn(MALFORMED_CIPHERTEXT)
        whenever(encryptionUtil.decrypt(CIPHERTEXT))
            .thenReturn(PLAINTEXT)
        whenever(encryptionUtil.decrypt(MALFORMED_CIPHERTEXT))
            .thenReturn(MALFORMED_PLAINTEXT)

        val replaced =
            encryptedPreferences
                .replaceEncryptedStringsDurablyIfStatesMatch(
                    expectedStates = mapOf(
                        NEW_KEY to encryptedSnapshot(
                            plaintext = PLAINTEXT,
                            rawCiphertext = CIPHERTEXT
                        )
                    ),
                    valuesToPut = emptyMap(),
                    keysToRemove = emptySet(),
                    snapshotMoves = listOf(
                        EncryptedPreferenceSnapshotMove(
                            sourceKey = LEGACY_KEY,
                            destinationKey = QUARANTINE_KEY,
                            expectedSnapshot = malformedSnapshot()
                        )
                    )
                )

        assertTrue(replaced)
        assertEquals(
            0L,
            encryptedPreferences.walletSecretQuarantineVersion.value
        )
        verify(encryptionUtil, never()).encrypt(any())
        verify(preferences, never())
            .replaceStringsDurably(any(), any())
    }

    @Test
    fun failedAtomicPublishAndQuarantineCommitLatchesRetries() {
        stubVerifiedEncryption()
        whenever(encryptionUtil.decrypt(MALFORMED_CIPHERTEXT))
            .thenReturn(MALFORMED_PLAINTEXT)
        whenever(preferences.getString(NEW_KEY)).thenReturn(null)
        whenever(preferences.getString(LEGACY_KEY))
            .thenReturn(MALFORMED_CIPHERTEXT)
        whenever(preferences.getString(QUARANTINE_KEY))
            .thenReturn(null)
        whenever(
            preferences.replaceStringsDurably(
                eq(
                    mapOf(
                        NEW_KEY to CIPHERTEXT,
                        QUARANTINE_KEY to MALFORMED_CIPHERTEXT
                    )
                ),
                eq(setOf(LEGACY_KEY))
            )
        ).thenReturn(false)

        assertThrows(IllegalStateException::class.java) {
            encryptedPreferences
                .replaceEncryptedStringsDurablyIfStatesMatch(
                    expectedStates = mapOf(NEW_KEY to null),
                    valuesToPut = mapOf(NEW_KEY to PLAINTEXT),
                    keysToRemove = emptySet(),
                    snapshotMoves = listOf(
                        EncryptedPreferenceSnapshotMove(
                            sourceKey = LEGACY_KEY,
                            destinationKey = QUARANTINE_KEY,
                            expectedSnapshot = malformedSnapshot()
                        )
                    )
                )
        }

        assertThrows(IllegalStateException::class.java) {
            encryptedPreferences.requireDurableStorageHealthy()
        }
        assertEquals(
            0L,
            encryptedPreferences.walletSecretQuarantineVersion.value
        )
        verify(preferences, times(1)).replaceStringsDurably(
            mapOf(
                NEW_KEY to CIPHERTEXT,
                QUARANTINE_KEY to MALFORMED_CIPHERTEXT
            ),
            setOf(LEGACY_KEY)
        )
    }

    @Test
    fun exceptionDuringPostCommitReadbackLatchesAndPreventsASecondWrite() {
        stubVerifiedEncryption()
        whenever(
            preferences.replaceStringsDurably(
                eq(mapOf(NEW_KEY to CIPHERTEXT)),
                eq(setOf(LEGACY_KEY))
            )
        ).thenReturn(true)
        whenever(preferences.getString(NEW_KEY))
            .thenThrow(IllegalStateException("readback unavailable"))

        assertThrows(IllegalStateException::class.java) {
            encryptedPreferences.replaceEncryptedStringsDurably(
                valuesToPut = mapOf(NEW_KEY to PLAINTEXT),
                keysToRemove = setOf(LEGACY_KEY)
            )
        }
        assertThrows(IllegalStateException::class.java) {
            encryptedPreferences.replaceEncryptedStringsDurably(
                valuesToPut = mapOf(NEW_KEY to PLAINTEXT),
                keysToRemove = setOf(LEGACY_KEY)
            )
        }

        verify(preferences, times(1)).replaceStringsDurably(
            mapOf(NEW_KEY to CIPHERTEXT),
            setOf(LEGACY_KEY)
        )
    }

    @Test
    fun unreadableCiphertextIsMovedToQuarantineWithoutTransformation() {
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(true, false)
        whenever(preferences.contains(QUARANTINE_KEY)).thenReturn(false)
        whenever(preferences.getString(LEGACY_KEY)).thenReturn(MALFORMED_CIPHERTEXT)
        whenever(encryptionUtil.decrypt(MALFORMED_CIPHERTEXT))
            .thenReturn(MALFORMED_PLAINTEXT)
        whenever(
            preferences.replaceStringsDurably(
                eq(mapOf(QUARANTINE_KEY to MALFORMED_CIPHERTEXT)),
                eq(setOf(LEGACY_KEY))
            )
        ).thenReturn(true)
        whenever(preferences.getString(QUARANTINE_KEY)).thenReturn(MALFORMED_CIPHERTEXT)

        encryptedPreferences.quarantineEncryptedStringDurably(
            sourceKey = LEGACY_KEY,
            quarantineKey = QUARANTINE_KEY,
            expectedSnapshot = malformedSnapshot()
        )

        assertEquals(1L, encryptedPreferences.walletSecretQuarantineVersion.value)
        verify(preferences).replaceStringsDurably(
            mapOf(QUARANTINE_KEY to MALFORMED_CIPHERTEXT),
            setOf(LEGACY_KEY)
        )
        verify(encryptionUtil).decrypt(MALFORMED_CIPHERTEXT)
        verify(encryptionUtil, never()).encrypt(any())
    }

    @Test
    fun emptyCiphertextIsStillPreservedExactlyInQuarantine() {
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(true, false)
        whenever(preferences.contains(QUARANTINE_KEY)).thenReturn(false)
        whenever(preferences.getString(LEGACY_KEY)).thenReturn("")
        whenever(encryptionUtil.decrypt("")).thenReturn("")
        whenever(
            preferences.replaceStringsDurably(
                eq(mapOf(QUARANTINE_KEY to "")),
                eq(setOf(LEGACY_KEY))
            )
        ).thenReturn(true)
        whenever(preferences.getString(QUARANTINE_KEY)).thenReturn("")

        encryptedPreferences.quarantineEncryptedStringDurably(
            sourceKey = LEGACY_KEY,
            quarantineKey = QUARANTINE_KEY,
            expectedSnapshot = encryptedSnapshot("", "")
        )

        verify(preferences).replaceStringsDurably(
            mapOf(QUARANTINE_KEY to ""),
            setOf(LEGACY_KEY)
        )
    }

    @Test
    fun failedQuarantineCommitLatchesMigrationRetries() {
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(true)
        whenever(preferences.contains(QUARANTINE_KEY)).thenReturn(false)
        whenever(preferences.getString(LEGACY_KEY)).thenReturn(MALFORMED_CIPHERTEXT)
        whenever(encryptionUtil.decrypt(MALFORMED_CIPHERTEXT))
            .thenReturn(MALFORMED_PLAINTEXT)
        whenever(
            preferences.replaceStringsDurably(
                eq(mapOf(QUARANTINE_KEY to MALFORMED_CIPHERTEXT)),
                eq(setOf(LEGACY_KEY))
            )
        ).thenReturn(false)

        assertThrows(IllegalStateException::class.java) {
            encryptedPreferences.quarantineEncryptedStringDurably(
                sourceKey = LEGACY_KEY,
                quarantineKey = QUARANTINE_KEY,
                expectedSnapshot = malformedSnapshot()
            )
        }

        assertThrows(IllegalStateException::class.java) {
            encryptedPreferences.requireDurableStorageHealthy()
        }
        assertEquals(0L, encryptedPreferences.walletSecretQuarantineVersion.value)
    }

    @Test
    fun exceptionDuringQuarantineReadbackLatchesAndPreventsASecondWrite() {
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(true, false)
        whenever(preferences.contains(QUARANTINE_KEY)).thenReturn(false)
        whenever(preferences.getString(LEGACY_KEY)).thenReturn(MALFORMED_CIPHERTEXT)
        whenever(encryptionUtil.decrypt(MALFORMED_CIPHERTEXT))
            .thenReturn(MALFORMED_PLAINTEXT)
        whenever(
            preferences.replaceStringsDurably(
                eq(mapOf(QUARANTINE_KEY to MALFORMED_CIPHERTEXT)),
                eq(setOf(LEGACY_KEY))
            )
        ).thenReturn(true)
        whenever(preferences.getString(QUARANTINE_KEY))
            .thenThrow(IllegalStateException("readback unavailable"))

        assertThrows(IllegalStateException::class.java) {
            encryptedPreferences.quarantineEncryptedStringDurably(
                sourceKey = LEGACY_KEY,
                quarantineKey = QUARANTINE_KEY,
                expectedSnapshot = malformedSnapshot()
            )
        }
        assertThrows(IllegalStateException::class.java) {
            encryptedPreferences.quarantineEncryptedStringDurably(
                sourceKey = LEGACY_KEY,
                quarantineKey = QUARANTINE_KEY,
                expectedSnapshot = malformedSnapshot()
            )
        }

        verify(preferences, times(1)).replaceStringsDurably(
            mapOf(QUARANTINE_KEY to MALFORMED_CIPHERTEXT),
            setOf(LEGACY_KEY)
        )
        assertEquals(0L, encryptedPreferences.walletSecretQuarantineVersion.value)
    }

    @Test
    fun committedQuarantineIsIdempotentAfterDatabaseRollback() {
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(false)
        whenever(preferences.contains(QUARANTINE_KEY)).thenReturn(true)
        whenever(preferences.getString(QUARANTINE_KEY))
            .thenReturn(MALFORMED_CIPHERTEXT)
        whenever(encryptionUtil.decrypt(MALFORMED_CIPHERTEXT))
            .thenReturn(MALFORMED_PLAINTEXT)

        val quarantined =
            encryptedPreferences.quarantineEncryptedStringDurably(
            sourceKey = LEGACY_KEY,
            quarantineKey = QUARANTINE_KEY,
            expectedSnapshot = malformedSnapshot()
        )

        assertEquals(true, quarantined)
        assertEquals(0L, encryptedPreferences.walletSecretQuarantineVersion.value)
        verify(preferences, never()).replaceStringsDurably(any(), any())
    }

    @Test
    fun conflictingCommittedQuarantineReturnsFalseWithoutMutation() {
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(false)
        whenever(preferences.contains(QUARANTINE_KEY)).thenReturn(true)
        whenever(preferences.getString(QUARANTINE_KEY))
            .thenReturn(FRESH_CIPHERTEXT)

        val quarantined =
            encryptedPreferences.quarantineEncryptedStringDurably(
                sourceKey = LEGACY_KEY,
                quarantineKey = QUARANTINE_KEY,
                expectedSnapshot = malformedSnapshot()
            )

        assertFalse(quarantined)
        verify(encryptionUtil, never()).decrypt(any())
        verify(preferences, never()).replaceStringsDurably(any(), any())
    }

    @Test
    fun conflictingCommittedQuarantineRaisesTypedConcurrentMutation() {
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(false)
        whenever(preferences.contains(QUARANTINE_KEY)).thenReturn(true)
        whenever(preferences.getString(QUARANTINE_KEY))
            .thenReturn(FRESH_CIPHERTEXT)

        assertThrows(WalletSecretConcurrentMutationException::class.java) {
            encryptedPreferences.quarantineEncryptedStringSnapshotDurably(
                sourceKey = LEGACY_KEY,
                quarantineKey = QUARANTINE_KEY,
                expectedSnapshot = malformedSnapshot()
            )
        }

        verify(encryptionUtil, never()).decrypt(any())
        verify(preferences, never()).replaceStringsDurably(any(), any())
    }

    @Test
    fun staleCorruptSnapshotCannotQuarantineConcurrentValidReplacement() {
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(true)
        whenever(preferences.contains(QUARANTINE_KEY)).thenReturn(false)
        whenever(preferences.getString(LEGACY_KEY))
            .thenReturn(FRESH_CIPHERTEXT)
        whenever(encryptionUtil.decrypt(FRESH_CIPHERTEXT))
            .thenReturn(FRESH_PLAINTEXT)

        val quarantined = encryptedPreferences.quarantineEncryptedStringDurably(
            sourceKey = LEGACY_KEY,
            quarantineKey = QUARANTINE_KEY,
            expectedSnapshot = malformedSnapshot()
        )

        assertFalse(quarantined)
        assertEquals(0L, encryptedPreferences.walletSecretQuarantineVersion.value)
        verify(preferences, never()).replaceStringsDurably(any(), any())
        verify(encryptionUtil, never()).encrypt(any())
    }

    @Test
    fun providerFailureDuringSnapshotComparisonDoesNotMutateEitherKey() {
        whenever(preferences.contains(LEGACY_KEY)).thenReturn(true)
        whenever(preferences.contains(QUARANTINE_KEY)).thenReturn(false)
        whenever(preferences.getString(LEGACY_KEY))
            .thenReturn(FRESH_CIPHERTEXT)
        whenever(encryptionUtil.decrypt(FRESH_CIPHERTEXT))
            .thenThrow(IllegalStateException("provider unavailable"))

        assertThrows(IllegalStateException::class.java) {
            encryptedPreferences.quarantineEncryptedStringDurably(
                sourceKey = LEGACY_KEY,
                quarantineKey = QUARANTINE_KEY,
                expectedSnapshot = encryptedSnapshot(
                    plaintext = MALFORMED_PLAINTEXT,
                    rawCiphertext = FRESH_CIPHERTEXT
                )
            )
        }

        assertEquals(0L, encryptedPreferences.walletSecretQuarantineVersion.value)
        verify(preferences, never()).replaceStringsDurably(any(), any())
    }

    private fun stubVerifiedEncryption() {
        whenever(encryptionUtil.encrypt(PLAINTEXT)).thenReturn(CIPHERTEXT)
        whenever(encryptionUtil.isModernCiphertext(CIPHERTEXT)).thenReturn(true)
        whenever(encryptionUtil.decrypt(CIPHERTEXT)).thenReturn(PLAINTEXT)
    }

    private fun malformedSnapshot() = encryptedSnapshot(
        plaintext = MALFORMED_PLAINTEXT,
        rawCiphertext = MALFORMED_CIPHERTEXT
    )

    private fun encryptedSnapshot(
        plaintext: String,
        rawCiphertext: String
    ) = EncryptedPreferenceSnapshot.encrypted(
        plaintext = plaintext,
        rawCiphertext = rawCiphertext,
        oversizedCiphertext = false
    )

    private companion object {
        const val LEGACY_KEY = "71:ACCESS_SECRETS"
        const val NEW_KEY = "71:SUBSTRATE_SECRETS"
        const val PLAINTEXT = "0x01020304"
        const val CIPHERTEXT = "v2:verified-ciphertext"
        const val LEGACY_CIPHERTEXT = "legacy-ciphertext"
        const val MALFORMED_PLAINTEXT = "malformed-wallet-plaintext"
        const val MALFORMED_CIPHERTEXT = "not-a-valid-wallet-ciphertext\u0000"
        const val FRESH_PLAINTEXT = "valid-concurrent-wallet-plaintext"
        const val FRESH_CIPHERTEXT = "v2:fresh-concurrent-ciphertext"
        const val QUARANTINE_KEY = "wallet_secret_quarantine:$LEGACY_KEY"
        const val OVERSIZED_TEST_CIPHERTEXT_CHARS = 2_097_153
        const val OVERSIZED_TOKEN_PREFIX =
            "fearless-oversized-ciphertext-sha256:"
    }
}
