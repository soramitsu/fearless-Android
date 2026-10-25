package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSecretAccessGuardTest {

    @Test
    fun `startup inventory finds quarantine and public identity markers`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        assertFalse(guard.hasAnyRecoveryState())

        val quarantineKey = WalletSecretQuarantine.keyFor(
            "1:ACCESS_SECRETS"
        )
        preferences.putEncryptedString(quarantineKey, "ciphertext")
        assertTrue(guard.hasAnyRecoveryState())

        preferences.removeKey(quarantineKey)
        preferences.putEncryptedString(
            WalletPublicIdentityRecovery.keyFor(
                metaId = 1L,
                activeSecretKey = "1:ETHEREUM_SECRETS"
            ),
            WalletPublicIdentityRecovery.MARKER_VALUE
        )
        assertTrue(guard.hasAnyRecoveryState())
    }

    @Test
    fun `startup inventory ignores oversized keys outside recovery namespaces`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        preferences.putEncryptedString(
            "unrelated:" + "x".repeat(2_048),
            "value"
        )

        assertFalse(guard.hasAnyRecoveryState())
    }

    @Test
    fun `oversized recovery key fails closed`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        preferences.putEncryptedString(
            WalletSecretQuarantine.KEY_PREFIX + "x".repeat(1_024),
            "ciphertext"
        )

        assertThrows(WalletRecoveryStateIntegrityException::class.java) {
            guard.hasAnyRecoveryState()
        }
    }

    @Test
    fun `attacker expanded recovery inventory fails closed`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        repeat(8_193) { index ->
            preferences.putEncryptedString(
                "${WalletPublicIdentityRecovery.KEY_PREFIX}$index",
                WalletPublicIdentityRecovery.MARKER_VALUE
            )
        }

        assertThrows(WalletRecoveryStateIntegrityException::class.java) {
            guard.hasAnyRecoveryState()
        }
    }

    @Test
    fun `each root quarantine or marker blocks only its own scope`() {
        // android-keys §3.2: rows are the flagged key, columns the active keys
        // present, and the expected blocked scopes.
        data class Case(
            val flaggedKey: String,
            val activeKeys: Set<String>,
            val blocked: Set<WalletSecretScope>
        )
        val cases = listOf(
            Case(SUBSTRATE, emptySet(), setOf(WalletSecretScope.Substrate)),
            Case(SUBSTRATE, setOf(SUBSTRATE, ETHEREUM, TON), setOf(WalletSecretScope.Substrate)),
            Case(ETHEREUM, setOf(SUBSTRATE, TON), setOf(WalletSecretScope.Ethereum)),
            Case(TON, setOf(SUBSTRATE, ETHEREUM), setOf(WalletSecretScope.Ton)),
            // A legacy payload blocks an ecosystem only while that ecosystem
            // has no converted key of its own.
            Case(
                ACCESS,
                emptySet(),
                setOf(WalletSecretScope.Substrate, WalletSecretScope.Ethereum)
            ),
            Case(ACCESS, setOf(SUBSTRATE), setOf(WalletSecretScope.Ethereum)),
            Case(ACCESS, setOf(ETHEREUM), setOf(WalletSecretScope.Substrate)),
            Case(ACCESS, setOf(SUBSTRATE, ETHEREUM), emptySet())
        )
        val scopes = listOf(
            WalletSecretScope.Substrate,
            WalletSecretScope.Ethereum,
            WalletSecretScope.Ton,
            WalletSecretScope.chainAccount(CHAIN_ACCOUNT_ID)
        )

        cases.forEach { case ->
            listOf(
                WalletSecretQuarantine.keyFor(case.flaggedKey),
                WalletPublicIdentityRecovery.keyFor(META_ID, case.flaggedKey)
            ).forEach { recoveryKey ->
                val preferences = HashMapEncryptedPreferences()
                val guard = WalletSecretAccessGuard(preferences)
                case.activeKeys.forEach { preferences.putEncryptedString(it, "active") }
                preferences.putEncryptedString(recoveryKey, "flagged")

                scopes.forEach { scope ->
                    assertEquals(
                        "$recoveryKey with ${case.activeKeys} for $scope",
                        scope in case.blocked,
                        guard.isRecoveryRequired(META_ID, scope, PUBLIC_KEY)
                    )
                }
                // Another wallet is never affected.
                scopes.forEach { scope ->
                    assertFalse(guard.isRecoveryRequired(OTHER_META_ID, scope, PUBLIC_KEY))
                }
            }
        }
    }

    @Test
    fun `chain account quarantine blocks only that account`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        val other = ByteArray(20) { 9 }
        preferences.putEncryptedString(
            WalletSecretQuarantine.chainAccountKey(META_ID, CHAIN_ACCOUNT_ID),
            "flagged"
        )

        assertTrue(
            guard.isRecoveryRequired(META_ID, WalletSecretScope.chainAccount(CHAIN_ACCOUNT_ID))
        )
        assertFalse(guard.isRecoveryRequired(META_ID, WalletSecretScope.chainAccount(other)))
        assertFalse(guard.isRecoveryRequired(META_ID, WalletSecretScope.Substrate, PUBLIC_KEY))
        assertFalse(guard.isRecoveryRequired(META_ID, WalletSecretScope.Ethereum))
    }

    @Test
    fun `bitcoin rows never make a wallet recovery required`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        preferences.putEncryptedString(SUBSTRATE, "active")
        val bitcoinAccountId = ByteArray(33) { 2 }

        assertTrue(
            guard.recoveryInventory(
                identity(chainAccountIds = listOf(bitcoinAccountId, ByteArray(64) { 1 }))
            ).isEmpty()
        )
        assertFalse(
            guard.isRecoveryRequired(META_ID, WalletSecretScope.chainAccount(bitcoinAccountId))
        )
    }

    @Test
    fun `inventory lists each blocked scope with its kind and key`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        preferences.putEncryptedString(SUBSTRATE, "active")
        preferences.putEncryptedString(WalletSecretQuarantine.keyFor(ETHEREUM), "flagged")
        preferences.putEncryptedString(TON, "active")
        preferences.putEncryptedString(
            WalletPublicIdentityRecovery.keyFor(META_ID, TON),
            WalletPublicIdentityRecovery.MARKER_VALUE
        )
        val chainKey = "$META_ID:${"07".repeat(32)}:ACCESS_SECRETS"
        preferences.putEncryptedString(WalletSecretQuarantine.keyFor(chainKey), "flagged")

        assertEquals(
            listOf(
                WalletRecoveryItem(
                    META_ID,
                    WalletSecretScope.Ethereum,
                    WalletRecoveryKind.QUARANTINED,
                    ETHEREUM
                ),
                WalletRecoveryItem(
                    META_ID,
                    WalletSecretScope.Ton,
                    WalletRecoveryKind.IDENTITY_MISMATCH,
                    TON
                ),
                WalletRecoveryItem(
                    META_ID,
                    WalletSecretScope.chainAccount(CHAIN_ACCOUNT_ID),
                    WalletRecoveryKind.QUARANTINED,
                    chainKey
                )
            ),
            guard.recoveryInventory(
                identity(hasEthereum = true, hasTon = true, chainAccountIds = listOf(CHAIN_ACCOUNT_ID))
            )
        )
    }

    @Test
    fun `missing ethereum or ton key is reported but never blocking`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        preferences.putEncryptedString(SUBSTRATE, "active")

        val items = guard.recoveryInventory(identity(hasEthereum = true, hasTon = true))

        assertEquals(
            listOf(
                WalletRecoveryItem(META_ID, WalletSecretScope.Ethereum, WalletRecoveryKind.MISSING_KEY, ETHEREUM),
                WalletRecoveryItem(META_ID, WalletSecretScope.Ton, WalletRecoveryKind.MISSING_KEY, TON)
            ),
            items
        )
        assertTrue(items.none(WalletRecoveryItem::isBlocking))
        assertFalse(guard.isRecoveryRequired(META_ID, WalletSecretScope.Ethereum))

        // A legacy payload still holds the Ethereum key: nothing is missing.
        preferences.putEncryptedString(ACCESS, "legacy")
        assertTrue(guard.recoveryInventory(identity(hasEthereum = true)).isEmpty())
    }

    @Test
    fun `watch only wallet is never recovery required`() {
        val guard = WalletSecretAccessGuard(HashMapEncryptedPreferences())

        assertTrue(
            guard.recoveryInventory(
                identity(hasEthereum = true, hasTon = true, chainAccountIds = listOf(CHAIN_ACCOUNT_ID))
            ).isEmpty()
        )
    }

    @Test
    fun `recovery state counts never fail startup`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        assertEquals(WalletRecoveryStateCounts(0, 0), guard.recoveryStateCounts())

        preferences.putEncryptedString(WalletSecretQuarantine.keyFor(SUBSTRATE), "flagged")
        preferences.putEncryptedString(WalletSecretArchive.keyFor(ACCESS), "legacy")
        assertEquals(WalletRecoveryStateCounts(1, 1), guard.recoveryStateCounts())

        preferences.putEncryptedString(
            WalletSecretQuarantine.KEY_PREFIX + "x".repeat(1_024),
            "ciphertext"
        )
        assertNull(guard.recoveryStateCounts())
    }

    private fun identity(
        hasEthereum: Boolean = false,
        hasTon: Boolean = false,
        chainAccountIds: List<ByteArray> = emptyList()
    ) = WalletRecoveryIdentity(
        metaId = META_ID,
        substratePublicKey = PUBLIC_KEY,
        hasEthereumIdentity = hasEthereum,
        hasTonIdentity = hasTon,
        chainAccountIds = chainAccountIds
    )

    private companion object {
        const val META_ID = 7L
        const val OTHER_META_ID = 8L
        const val ACCESS = "7:ACCESS_SECRETS"
        const val SUBSTRATE = "7:SUBSTRATE_SECRETS"
        const val ETHEREUM = "7:ETHEREUM_SECRETS"
        const val TON = "7:TON_SECRETS"
        val PUBLIC_KEY = ByteArray(32) { 1 }
        val CHAIN_ACCOUNT_ID = ByteArray(32) { 7 }
    }
}
