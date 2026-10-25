package jp.co.soramitsu.coredb.migrations

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryStateIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class WalletOrphanSecretInventoryTest {

    @Test
    fun `strict parser accepts every active owner namespace`() {
        val chain20 = "ab".repeat(20)
        val chain32 = "cd".repeat(32)
        val chain64 = "ef".repeat(64)
        val tonKey = TonConnectStorageKeys.scoped(
            metaId = 7L,
            url = "https://wallet.example/manifest.json",
            source = "WEB"
        )

        listOf(
            "1:ACCESS_SECRETS",
            "2:SUBSTRATE_SECRETS",
            "3:ETHEREUM_SECRETS",
            "4:TON_SECRETS"
        ).forEachIndexed { index, key ->
            assertEquals(
                WalletActiveSecretOwner.Root((index + 1).toLong()),
                WalletActiveSecretKeyParser.parse(key)
            )
        }
        assertEquals(
            WalletActiveSecretOwner.ChainAccount(5L),
            WalletActiveSecretKeyParser.parse(
                "5:$chain20:ACCESS_SECRETS"
            )
        )
        assertEquals(
            WalletActiveSecretOwner.ChainAccount(6L),
            WalletActiveSecretKeyParser.parse(
                "6:$chain32:ACCESS_SECRETS"
            )
        )
        assertEquals(
            WalletActiveSecretOwner.ChainAccount(Long.MAX_VALUE),
            WalletActiveSecretKeyParser.parse(
                "${Long.MAX_VALUE}:$chain64:ACCESS_SECRETS"
            )
        )
        assertEquals(
            WalletActiveSecretOwner.TonConnectScoped(7L),
            WalletActiveSecretKeyParser.parse(tonKey)
        )
    }

    @Test
    fun `strict parser rejects malformed and lookalike namespaces`() {
        val validChain = "ab".repeat(20)
        val validTon = TonConnectStorageKeys.scoped(
            metaId = 8L,
            url = "https://wallet.example",
            source = "QR"
        )
        val malformedKeys = listOf(
            "",
            "ACCESS_SECRETS",
            "0:SUBSTRATE_SECRETS",
            "-1:SUBSTRATE_SECRETS",
            "+1:SUBSTRATE_SECRETS",
            "01:SUBSTRATE_SECRETS",
            "9223372036854775808:SUBSTRATE_SECRETS",
            "1:SUBSTRATE_SECRET",
            "1:SUBSTRATE_SECRETS:",
            "1:substrate_secrets",
            "1:${"ab".repeat(19)}:ACCESS_SECRETS",
            "1:${"ab".repeat(65)}:ACCESS_SECRETS",
            "1:${"ab".repeat(20)}f:ACCESS_SECRETS",
            "1:${validChain.uppercase()}:ACCESS_SECRETS",
            "1:${"gg".repeat(20)}:ACCESS_SECRETS",
            "1:$validChain:ACCESS_SECRETS:extra",
            "1:$validChain:SUBSTRATE_SECRETS",
            "1::$validChain:ACCESS_SECRETS",
            validTon.replace(
                "TON_CONNECT_SCOPED_V1_8_",
                "TON_CONNECT_SCOPED_V1_08_"
            ),
            validTon.dropLast(1),
            validTon.dropLast(1) + "A",
            "TON_CONNECT_SCOPED_V1_8_" + "a".repeat(63),
            "TON_CONNECT_SCOPED_V2_8_" + "a".repeat(64)
        )

        malformedKeys.forEach { key ->
            assertNull("Unexpected active namespace: $key", parser(key))
        }
    }

    /**
     * AND-S1: 4.0.1-4.2.0 deleted only a wallet's V2 keys, so every user who
     * deleted a wallet (including the TON wallet created silently on first
     * start) carries its V3 secrets. They are archived exactly, with no
     * quarantine and no recovery marker, so nothing blocks startup.
     */
    @Test
    fun `secrets left by deleted wallets are archived exactly without recovery state`() {
        val preferences = HashMapEncryptedPreferences()
        val orphanSecrets = linkedMapOf(
            "11:ACCESS_SECRETS" to "legacy-root",
            "12:SUBSTRATE_SECRETS" to "substrate-root",
            "13:ETHEREUM_SECRETS" to "ethereum-root",
            "14:TON_SECRETS" to "ton-root",
            "15:${"01".repeat(32)}:ACCESS_SECRETS" to "chain-secret",
            TonConnectStorageKeys.scoped(
                metaId = 16L,
                url = "https://dapp.example/tonconnect-manifest.json",
                source = "WEB"
            ) to "ton-connect-private-key"
        )
        orphanSecrets.forEach(preferences::putEncryptedString)
        preferences.putEncryptedString(OWNED_KEY, "owned-substrate-root")

        val report = WalletOrphanSecretInventory(preferences)
            .reconcile(databaseOwning(OWNED_META_ID))

        assertEquals(orphanSecrets.size, report.archivedCount)
        assertEquals(0, report.deferredCount)
        orphanSecrets.forEach { (activeKey, value) ->
            assertFalse(preferences.hasKey(activeKey))
            assertEquals(
                value,
                preferences.getDecryptedString(WalletSecretArchive.keyFor(activeKey))
            )
        }
        assertEquals("owned-substrate-root", preferences.getDecryptedString(OWNED_KEY))
        assertNoRecoveryState(preferences)
    }

    @Test
    fun `secrets of an existing wallet and its chain accounts are never archived`() {
        val chainKey = "$OWNED_META_ID:${"02".repeat(32)}:ACCESS_SECRETS"
        val tonKey = TonConnectStorageKeys.scoped(
            metaId = OWNED_META_ID,
            url = "https://owned.example",
            source = "QR"
        )
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(OWNED_KEY, "owned-substrate-root")
            putEncryptedString(chainKey, "owned-chain")
            putEncryptedString(tonKey, "owned-ton-connect")
        }
        val database = FakeWalletDatabase(
            mapOf(
                "meta_accounts" to listOf(mapOf("id" to OWNED_META_ID)),
                "chain_accounts" to listOf(
                    mapOf("metaId" to OWNED_META_ID, "accountId" to ByteArray(32) { 2 })
                ),
                "ton_connection" to listOf(
                    mapOf(
                        "metaId" to OWNED_META_ID,
                        "url" to "https://owned.example",
                        "source" to "QR"
                    )
                )
            )
        ).database

        val report = WalletOrphanSecretInventory(preferences).reconcile(database)

        assertEquals(0, report.archivedCount)
        assertEquals("owned-chain", preferences.getDecryptedString(chainKey))
        assertEquals("owned-ton-connect", preferences.getDecryptedString(tonKey))
    }

    @Test
    fun `a TON connection left by a deleted wallet never blocks and its key is archived`() {
        val scopedKey = TonConnectStorageKeys.scoped(
            metaId = 30L,
            url = "https://deleted.example",
            source = "WEB"
        )
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(OWNED_KEY, "owned-substrate-root")
            putEncryptedString(scopedKey, "deleted-wallet-session")
        }
        val database = FakeWalletDatabase(
            mapOf(
                "meta_accounts" to listOf(mapOf("id" to OWNED_META_ID)),
                "ton_connection" to listOf(
                    mapOf("metaId" to 30L, "url" to "https://deleted.example", "source" to "WEB")
                )
            )
        ).database

        val report = WalletOrphanSecretInventory(preferences).reconcile(database)

        assertEquals(1, report.archivedCount)
        assertEquals(
            "deleted-wallet-session",
            preferences.getDecryptedString(WalletSecretArchive.keyFor(scopedKey))
        )
    }

    /**
     * Critique D-2: if no wallet row owns any root secret, the rows are more
     * likely unreadable than deleted. Nothing moves and nothing blocks.
     */
    @Test
    fun `nothing moves when no wallet row owns any root secret`() {
        val activeSecrets = linkedMapOf(
            "11:ACCESS_SECRETS" to "legacy-root",
            "14:TON_SECRETS" to "ton-root",
            "15:${"01".repeat(32)}:ACCESS_SECRETS" to "chain-secret"
        )
        val preferences = HashMapEncryptedPreferences().apply {
            activeSecrets.forEach(::putEncryptedString)
        }

        val report = WalletOrphanSecretInventory(preferences)
            .reconcile(emptyDatabase())

        assertEquals(0, report.archivedCount)
        assertEquals(activeSecrets.size, report.deferredCount)
        activeSecrets.forEach { (key, value) ->
            assertEquals(value, preferences.getDecryptedString(key))
        }
        assertNoRecoveryState(preferences)
        assertFalse(preferences.hasKeyWithPrefix(WalletSecretArchive.KEY_PREFIX))
    }

    @Test
    fun `malformed lookalikes stay byte for byte untouched and never block`() {
        val lookalikes = linkedMapOf(
            "0:TON_SECRETS" to "zero-owner",
            "01:SUBSTRATE_SECRETS" to "leading-zero",
            "1:${"AB".repeat(20)}:ACCESS_SECRETS" to "uppercase-chain",
            "1:${"ab".repeat(19)}:ACCESS_SECRETS" to "short-chain",
            "1:SUBSTRATE_SECRET" to "singular-suffix",
            "TON_CONNECT_SCOPED_V1_1_${"a".repeat(63)}" to "short-ton"
        )
        lookalikes.forEach { (key, value) ->
            val preferences = HashMapEncryptedPreferences().apply {
                putEncryptedString(key, value)
                putEncryptedString(OWNED_KEY, "owned-substrate-root")
            }

            val report = WalletOrphanSecretInventory(preferences)
                .reconcile(databaseOwning(OWNED_META_ID))

            assertEquals(key, 1, report.unrecognizedSecretKeyCount)
            assertEquals(key, 0, report.archivedCount)
            assertEquals(value, preferences.getDecryptedString(key))
            assertNoRecoveryState(preferences)
        }
    }

    @Test
    fun `reconciliation is idempotent after exact archive commit`() {
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString("17:TON_SECRETS", "orphan-ton")
            putEncryptedString(OWNED_KEY, "owned-substrate-root")
        }
        val inventory = WalletOrphanSecretInventory(preferences)
        val database = databaseOwning(OWNED_META_ID)

        assertEquals(1, inventory.reconcile(database).archivedCount)
        assertEquals(0, inventory.reconcile(database).archivedCount)

        assertFalse(preferences.hasKey("17:TON_SECRETS"))
        assertEquals(
            "orphan-ton",
            preferences.getDecryptedString(WalletSecretArchive.keyFor("17:TON_SECRETS"))
        )
    }

    @Test
    fun `a value archived earlier under the same name is never overwritten`() {
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString("17:TON_SECRETS", "second-orphan")
            putEncryptedString(WalletSecretArchive.keyFor("17:TON_SECRETS"), "first-orphan")
            putEncryptedString(OWNED_KEY, "owned-substrate-root")
        }

        WalletOrphanSecretInventory(preferences).reconcile(databaseOwning(OWNED_META_ID))

        assertEquals(
            "first-orphan",
            preferences.getDecryptedString(WalletSecretArchive.keyFor("17:TON_SECRETS"))
        )
        assertEquals(
            "second-orphan",
            preferences.getDecryptedString(WalletSecretArchive.keyFor("17:TON_SECRETS", slot = 1))
        )
    }

    @Test
    fun `snapshot conflict rejects complete plan without a stale archive`() {
        val activeKey = "18:ETHEREUM_SECRETS"
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(activeKey, "first-value")
            putEncryptedString(OWNED_KEY, "owned-substrate-root")
        }
        val plan = WalletOrphanSecretInventory(preferences).prepare(
            database = databaseOwning(OWNED_META_ID),
            excludedActiveKeys = emptySet(),
            includeTonConnectScopedKeys = true
        )
        preferences.putEncryptedString(activeKey, "concurrent-value")

        assertThrows(WalletSecretConcurrentMutationException::class.java) {
            plan.requireReady()
        }

        assertEquals(
            "concurrent-value",
            preferences.getDecryptedString(activeKey)
        )
        assertFalse(
            preferences.hasKey(WalletSecretArchive.keyFor(activeKey))
        )
    }

    @Test
    fun `journal exclusions preserve every exact staged active namespace`() {
        val scopedKey = TonConnectStorageKeys.scoped(
            metaId = 22L,
            url = "https://journal.example",
            source = "WEB"
        )
        val activeSecrets = linkedMapOf(
            "20:TON_SECRETS" to "staged-root",
            "21:${"12".repeat(20)}:ACCESS_SECRETS" to "staged-chain",
            scopedKey to "staged-ton-connect"
        )
        val preferences = HashMapEncryptedPreferences().apply {
            activeSecrets.forEach(::putEncryptedString)
            putEncryptedString(OWNED_KEY, "owned-substrate-root")
        }
        val plan = WalletOrphanSecretInventory(preferences).prepare(
            database = databaseOwning(OWNED_META_ID),
            excludedActiveKeys = activeSecrets.keys,
            includeTonConnectScopedKeys = true
        )

        plan.requireReady()
        plan.commit()

        assertFalse(plan.hasOrphans)
        activeSecrets.forEach { (activeKey, expectedValue) ->
            assertEquals(
                expectedValue,
                preferences.getDecryptedString(activeKey)
            )
            assertFalse(
                preferences.hasKey(WalletSecretArchive.keyFor(activeKey))
            )
        }
    }

    @Test
    fun `pending TON journal defers only scoped keys`() {
        val rootKey = "23:TON_SECRETS"
        val scopedKey = TonConnectStorageKeys.scoped(
            metaId = 24L,
            url = "https://pending.example",
            source = "QR"
        )
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(rootKey, "ownerless-root")
            putEncryptedString(scopedKey, "journal-owned-scoped")
            putEncryptedString(OWNED_KEY, "owned-substrate-root")
        }
        val plan = WalletOrphanSecretInventory(preferences).prepare(
            database = databaseOwning(OWNED_META_ID),
            excludedActiveKeys = emptySet(),
            includeTonConnectScopedKeys = false
        )

        plan.requireReady()
        plan.commit()

        assertTrue(plan.hasOrphans)
        assertFalse(preferences.hasKey(rootKey))
        assertEquals(
            "ownerless-root",
            preferences.getDecryptedString(
                WalletSecretArchive.keyFor(rootKey)
            )
        )
        assertEquals(
            "journal-owned-scoped",
            preferences.getDecryptedString(scopedKey)
        )
        assertFalse(
            preferences.hasKey(WalletSecretArchive.keyFor(scopedKey))
        )
    }

    @Test
    fun `candidate key count accepts exact bound and rejects bound plus one`() {
        val exactPreferences = HashMapEncryptedPreferences().apply {
            repeat(8_191) { index ->
                val metaId = index + 2L
                putEncryptedString(
                    "$metaId:TON_SECRETS",
                    "secret-$metaId"
                )
            }
            putEncryptedString(OWNED_KEY, "owned-substrate-root")
        }
        assertEquals(
            8_191,
            WalletOrphanSecretInventory(exactPreferences)
                .reconcile(databaseOwning(OWNED_META_ID))
                .archivedCount
        )
        assertFalse(exactPreferences.hasKey("8192:TON_SECRETS"))

        val oversizedPreferences = HashMapEncryptedPreferences().apply {
            repeat(8_193) { index ->
                val metaId = index + 1L
                putEncryptedString(
                    "$metaId:TON_SECRETS",
                    "secret-$metaId"
                )
            }
        }
        assertThrows(WalletRecoveryStateIntegrityException::class.java) {
            WalletOrphanSecretInventory(oversizedPreferences)
                .reconcile(emptyDatabase())
        }
        assertTrue(oversizedPreferences.hasKey("1:TON_SECRETS"))
        assertFalse(
            oversizedPreferences.hasKey(
                WalletSecretArchive.keyFor("1:TON_SECRETS")
            )
        )
    }

    @Test
    fun `key byte limit accepts exact boundary and rejects one byte over`() {
        val limits = testLimits(
            maximumCandidateKeys = 2,
            maximumKeyBytes = 64,
            maximumTotalKeyBytes = 128
        )
        val exactKey = "1:" + "x".repeat(62)
        val exactPreferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(exactKey, "unrelated")
        }

        assertEquals(
            0,
            WalletOrphanSecretInventory(exactPreferences, limits)
                .reconcile(emptyDatabase())
                .archivedCount
        )
        assertEquals(
            "unrelated",
            exactPreferences.getDecryptedString(exactKey)
        )

        val oversizedKey = "1:" + "x".repeat(63)
        val oversizedPreferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(oversizedKey, "unrelated")
        }
        assertThrows(WalletRecoveryStateIntegrityException::class.java) {
            WalletOrphanSecretInventory(oversizedPreferences, limits)
                .reconcile(emptyDatabase())
        }
        assertEquals(
            "unrelated",
            oversizedPreferences.getDecryptedString(oversizedKey)
        )
    }

    @Test
    fun `total key bytes accept exact boundary and reject one additional key`() {
        val limits = testLimits(
            maximumCandidateKeys = 3,
            maximumKeyBytes = 64,
            maximumTotalKeyBytes = 128
        )
        val firstKey = "1:" + "x".repeat(62)
        val secondKey = "2:" + "y".repeat(62)
        val exactPreferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(firstKey, "first")
            putEncryptedString(secondKey, "second")
        }

        assertEquals(
            0,
            WalletOrphanSecretInventory(exactPreferences, limits)
                .reconcile(emptyDatabase())
                .archivedCount
        )

        val excessivePreferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(firstKey, "first")
            putEncryptedString(secondKey, "second")
            putEncryptedString("3:z", "third")
        }
        assertThrows(WalletRecoveryStateIntegrityException::class.java) {
            WalletOrphanSecretInventory(excessivePreferences, limits)
                .reconcile(emptyDatabase())
        }
        assertEquals(
            "first",
            excessivePreferences.getDecryptedString(firstKey)
        )
    }

    private fun databaseOwning(vararg metaIds: Long): SupportSQLiteDatabase =
        FakeWalletDatabase(
            mapOf("meta_accounts" to metaIds.map { mapOf("id" to it) })
        ).database

    private fun assertNoRecoveryState(preferences: HashMapEncryptedPreferences) {
        assertFalse(preferences.hasKeyWithPrefix(WalletSecretQuarantine.KEY_PREFIX))
        assertFalse(preferences.hasKeyWithPrefix(WalletPublicIdentityRecovery.KEY_PREFIX))
    }

    private fun emptyDatabase(): SupportSQLiteDatabase {
        val database = mock<SupportSQLiteDatabase>()
        val emptyCursor = mock<Cursor>()
        whenever(emptyCursor.moveToNext()).thenReturn(false)
        whenever(database.query(any<String>())).thenReturn(emptyCursor)
        return database
    }

    private fun parser(key: String): WalletActiveSecretOwner? {
        return WalletActiveSecretKeyParser.parse(key)
    }

    private companion object {
        const val OWNED_META_ID = 1L
        const val OWNED_KEY = "1:SUBSTRATE_SECRETS"
    }

    private fun testLimits(
        maximumCandidateKeys: Int,
        maximumKeyBytes: Int,
        maximumTotalKeyBytes: Int
    ) = WalletOrphanSecretInventoryLimits(
        maximumWalletRows = 4,
        maximumChainAccountRows = 4,
        maximumTonConnectionRows = 4,
        maximumCandidateKeys = maximumCandidateKeys,
        maximumKeyBytes = maximumKeyBytes,
        maximumTotalKeyBytes = maximumTotalKeyBytes
    )
}
