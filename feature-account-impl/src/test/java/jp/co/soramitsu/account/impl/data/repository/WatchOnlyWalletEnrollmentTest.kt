package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.WalletCustodyLocal
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchOnlyWalletEnrollmentTest {
    @Test
    fun `new watch wallet and bound chain identity commit with marker`() = runBlocking {
        val database = FakeDatabase()
        val enrollment = WatchOnlyWalletEnrollment(database, FakeSecrets(), Unit)
        val chain = ChainAccountLocal(
            metaId = 0, chainId = "chain-a", publicKey = ByteArray(32) { 2 },
            accountId = ByteArray(32) { 2 }, cryptoType = CryptoType.ED25519,
            name = "Public child", initialized = false
        )

        val id = enrollment.enroll(prototype(), listOf(chain))

        assertEquals(1L, id)
        assertEquals(1, database.wallets.size)
        assertTrue(database.wallets.single().isSelected)
        assertEquals(id, database.chains.single().metaId)
        assertEquals(
            WalletCustodyProvenance.Kind.WATCH,
            WalletCustodyProvenance.classify(
                database.wallets.single(), database.chains, database.markers.single()
            )
        )
    }

    @Test
    fun `legacy row cannot be promoted by new enrollment`() = runBlocking {
        val database = FakeDatabase()
        val old = prototype().apply { id = 41 }
        val enrollment = WatchOnlyWalletEnrollment(database, FakeSecrets(), Unit)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { enrollment.enroll(old) }
        }
        assertTrue(database.wallets.isEmpty())
        assertTrue(database.markers.isEmpty())
    }

    @Test
    fun `unexpected signing namespace rolls back public wallet insertion`() = runBlocking {
        val database = FakeDatabase()
        val enrollment = WatchOnlyWalletEnrollment(database, FakeSecrets(occupied = true), Unit)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { enrollment.enroll(prototype()) }
        }
        assertTrue(database.wallets.isEmpty())
        assertTrue(database.markers.isEmpty())
    }

    @Test
    fun `portable cohort reservation rolls back a colliding watch wallet insertion`() = runBlocking {
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(PortableWalletCohortJournalStore.reservationKey(1L), "reserved")
        }
        val inventory = DefaultWatchEnrollmentSecretInventory(
            preferences, WalletSecretMutationJournalStore(preferences)
        )
        val database = FakeDatabase()
        val enrollment = WatchOnlyWalletEnrollment(database, inventory, Unit)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { enrollment.enroll(prototype()) }
        }
        assertTrue(database.wallets.isEmpty())
        assertTrue(database.markers.isEmpty())
        assertEquals("reserved", preferences.getDecryptedString(PortableWalletCohortJournalStore.reservationKey(1L)))
    }

    @Test
    fun `portable cohort journal blocks watch enrollment even with a missing marker`() = runBlocking {
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(PortableWalletCohortJournalStore.JOURNAL_KEY, "unreconciled")
        }
        val inventory = DefaultWatchEnrollmentSecretInventory(
            preferences, WalletSecretMutationJournalStore(preferences)
        )
        val database = FakeDatabase()
        val enrollment = WatchOnlyWalletEnrollment(database, inventory, Unit)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { enrollment.enroll(prototype()) }
        }
        assertTrue(database.wallets.isEmpty())
        assertTrue(database.markers.isEmpty())
        assertEquals("unreconciled", preferences.getDecryptedString(PortableWalletCohortJournalStore.JOURNAL_KEY))
    }

    @Test
    fun `global legacy V1 source prevents watch classification`() = runBlocking {
        val database = FakeDatabase()
        val enrollment = WatchOnlyWalletEnrollment(database, FakeSecrets(legacyOccupied = true), Unit)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { enrollment.enroll(prototype()) }
        }
        assertTrue(database.wallets.isEmpty())
        assertTrue(database.markers.isEmpty())
    }

    @Test
    fun `invalid public binding and chain insertion failure both roll back`() = runBlocking {
        val database = FakeDatabase()
        val enrollment = WatchOnlyWalletEnrollment(database, FakeSecrets(), Unit)
        val bad = prototype()
        bad.substrateAccountId!![0] = 9
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { enrollment.enroll(bad) }
        }
        assertTrue(database.wallets.isEmpty())

        database.failChainInsertion = true
        val chain = ChainAccountLocal(
            0, "chain-a", ByteArray(32) { 2 }, ByteArray(32) { 2 },
            CryptoType.ED25519, "Child", false
        )
        assertThrows(IllegalStateException::class.java) {
            runBlocking { enrollment.enroll(prototype(), listOf(chain)) }
        }
        assertTrue(database.wallets.isEmpty())
        assertTrue(database.markers.isEmpty())
    }

    @Test
    fun `marker write failure rolls back the new public wallet`() = runBlocking {
        val database = FakeDatabase().apply { failMarkerInsertion = true }
        val enrollment = WatchOnlyWalletEnrollment(database, FakeSecrets(), Unit)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { enrollment.enroll(prototype()) }
        }
        assertTrue(database.wallets.isEmpty())
        assertTrue(database.markers.isEmpty())
    }

    private fun prototype() = MetaAccountLocal(
        substratePublicKey = ByteArray(32) { 1 },
        substrateCryptoType = CryptoType.SR25519,
        substrateAccountId = ByteArray(32) { 1 },
        ethereumPublicKey = null,
        ethereumAddress = null,
        tonPublicKey = null,
        name = "Watch",
        isSelected = false,
        position = 99,
        isBackedUp = false,
        googleBackupAddress = null,
        initialized = false
    )

    private class FakeSecrets(
        var occupied: Boolean = false,
        var legacyOccupied: Boolean = false
    ) : WatchEnrollmentSecretInventory {
        override fun requireReady() = Unit
        override fun hasNamespace(metaId: Long): Boolean = occupied
        override fun hasLegacySubstrateSource(accountId: ByteArray?): Boolean = legacyOccupied
    }

    private class FakeDatabase : WatchEnrollmentDatabase {
        val wallets = ArrayList<MetaAccountLocal>()
        val chains = ArrayList<ChainAccountLocal>()
        val markers = ArrayList<WalletCustodyLocal>()
        var failChainInsertion = false
        var failMarkerInsertion = false

        override suspend fun <T> inTransaction(block: suspend WatchEnrollmentDatabase.() -> T): T {
            val walletCount = wallets.size
            val chainCount = chains.size
            val markerCount = markers.size
            return try {
                block(this)
            } catch (failure: Exception) {
                while (wallets.size > walletCount) wallets.removeAt(wallets.lastIndex)
                while (chains.size > chainCount) chains.removeAt(chains.lastIndex)
                while (markers.size > markerCount) markers.removeAt(markers.lastIndex)
                throw failure
            }
        }

        override suspend fun getMetaAccounts(): List<MetaAccountLocal> = wallets.toList()
        override suspend fun getNextPosition(): Int = (wallets.maxOfOrNull { it.position } ?: -1) + 1
        override suspend fun hasIdentityConflict(
            substrate: ByteArray?,
            ethereum: ByteArray?,
            ton: ByteArray?
        ): Boolean = wallets.any { existing ->
            substrate != null && existing.substrateAccountId?.contentEquals(substrate) == true ||
                ethereum != null && existing.ethereumAddress?.contentEquals(ethereum) == true ||
                ton != null && existing.tonPublicKey?.contentEquals(ton) == true
        }

        override suspend fun hasChainAccountIdentity(chainId: String, accountId: ByteArray): Boolean =
            chains.any { it.chainId == chainId && it.accountId.contentEquals(accountId) }

        override suspend fun insertMetaAccount(meta: MetaAccountLocal): Long {
            meta.id = (wallets.maxOfOrNull { it.id } ?: 0) + 1
            wallets += meta
            return meta.id
        }

        override suspend fun insertChainAccounts(chains: List<ChainAccountLocal>) {
            if (failChainInsertion) error("Injected chain insertion failure")
            this.chains += chains
        }

        override suspend fun insertCustody(marker: WalletCustodyLocal) {
            if (failMarkerInsertion) error("Injected marker insertion failure")
            markers += marker
        }

        override suspend fun selectMetaAccount(metaId: Long) = Unit
    }
}
