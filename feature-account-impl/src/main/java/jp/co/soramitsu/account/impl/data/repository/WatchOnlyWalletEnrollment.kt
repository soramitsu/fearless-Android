package jp.co.soramitsu.account.impl.data.repository

import androidx.room.withTransaction
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.WalletCustodyDao
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.WalletCustodyLocal
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Explicit, forward-only public-wallet enrollment. No existing wallet can be promoted to WATCH.
 * This API is not wired to account UI, backup, signing or restore; an eventual caller must present
 * newly imported public identity, not a row whose signing key has gone missing.
 */
internal class WatchOnlyWalletEnrollment private constructor(
    private val database: WatchEnrollmentDatabase,
    private val secrets: WatchEnrollmentSecretInventory
) {
    @Inject constructor(
        appDatabase: AppDatabase,
        metaAccountDao: MetaAccountDao,
        custodyDao: WalletCustodyDao,
        encryptedPreferences: EncryptedPreferences,
        journalStore: WalletSecretMutationJournalStore
    ) : this(
        RoomWatchEnrollmentDatabase(appDatabase, metaAccountDao, custodyDao),
        object : WatchEnrollmentSecretInventory {
            override fun requireReady() {
                encryptedPreferences.requireDurableStorageHealthy()
                check(journalStore.load() == null) { "A wallet mutation is pending" }
            }

            override fun hasNamespace(metaId: Long): Boolean = journalStore.hasSecretNamespace(metaId)

            override fun hasLegacySubstrateSource(accountId: ByteArray?): Boolean {
                if (accountId == null) return false
                return encryptedPreferences.keysWithPrefixes(
                    prefixes = setOf(LEGACY_V1_PREFIX),
                    maxResultCount = 4_096,
                    maxKeyBytes = 256,
                    maxTotalKeyBytes = 524_288,
                    failOnOversizedMatch = true
                ).any { key ->
                    check(key.startsWith(LEGACY_V1_PREFIX)) { "Invalid V1 source inventory" }
                    val owner = try {
                        key.removePrefix(LEGACY_V1_PREFIX).toAccountId()
                    } catch (_: Exception) {
                        error("Ambiguous V1 source inventory during watch enrollment")
                    }
                    owner.contentEquals(accountId)
                }
            }
        }
    )

    internal constructor(
        database: WatchEnrollmentDatabase,
        secrets: WatchEnrollmentSecretInventory,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit = Unit
    ) : this(database, secrets)

    suspend fun enroll(prototype: MetaAccountLocal, chainPrototypes: List<ChainAccountLocal> = emptyList()): Long =
        withContext(Dispatchers.IO) {
            WalletCrossStoreMutationMutex.instance.withLock {
                require(prototype.id == 0L && !prototype.isBackedUp && prototype.googleBackupAddress == null) {
                    "Watch enrollment requires a new, unbacked-up wallet prototype"
                }
                require(chainPrototypes.all { it.metaId == 0L }) {
                    "Watch chain enrollment requires unbound new chain accounts"
                }
                secrets.requireReady()
                database.inTransaction {
                    val existing = getMetaAccounts()
                    require(
                        existing.size < MAX_WALLETS &&
                        (existing.isEmpty() || existing.count(MetaAccountLocal::isSelected) == 1)
                    ) {
                        "Wallet selection is inconsistent"
                    }
                    val nextPosition = getNextPosition()
                    require(nextPosition >= 0 && existing.none { it.position == nextPosition }) {
                        "Wallet order is inconsistent"
                    }
                    require(
                        !hasIdentityConflict(
                            prototype.substrateAccountId,
                            prototype.ethereumAddress,
                            prototype.tonPublicKey
                        )
                    ) { "Watch identity already belongs to another wallet" }
                    chainPrototypes.forEach { chain ->
                        require(!hasChainAccountIdentity(chain.chainId, chain.accountId)) {
                            "Watch chain identity already belongs to another wallet"
                        }
                    }

                    val row = MetaAccountLocal(
                        substratePublicKey = prototype.substratePublicKey?.copyOf(),
                        substrateCryptoType = prototype.substrateCryptoType,
                        substrateAccountId = prototype.substrateAccountId?.copyOf(),
                        ethereumPublicKey = prototype.ethereumPublicKey?.copyOf(),
                        ethereumAddress = prototype.ethereumAddress?.copyOf(),
                        tonPublicKey = prototype.tonPublicKey?.copyOf(),
                        name = prototype.name,
                        isSelected = existing.isEmpty() || prototype.isSelected,
                        position = nextPosition,
                        isBackedUp = false,
                        googleBackupAddress = null,
                        initialized = prototype.initialized
                    )
                    val id = insertMetaAccount(row)
                    require(id > 0) { "Watch wallet insertion returned an invalid ID" }
                    row.id = id
                    val chains = chainPrototypes.map { chain ->
                        ChainAccountLocal(
                            metaId = id,
                            chainId = chain.chainId,
                            publicKey = chain.publicKey.copyOf(),
                            accountId = chain.accountId.copyOf(),
                            cryptoType = chain.cryptoType,
                            name = chain.name,
                            initialized = chain.initialized
                        )
                    }
                    val marker = WalletCustodyProvenance.watchMarker(row, chains)
                    require(
                        !secrets.hasNamespace(id) &&
                        !secrets.hasLegacySubstrateSource(row.substrateAccountId)
                    ) {
                        "Watch wallet ID already owns encrypted signing material"
                    }
                    if (chains.isNotEmpty()) insertChainAccounts(chains)
                    insertCustody(marker)
                    if (row.isSelected && existing.isNotEmpty()) selectMetaAccount(id)
                    id
                }
            }
        }

    private companion object {
        const val MAX_WALLETS = 128
        const val LEGACY_V1_PREFIX = "security_source_"
    }
}

internal interface WatchEnrollmentSecretInventory {
    fun requireReady()
    fun hasNamespace(metaId: Long): Boolean
    fun hasLegacySubstrateSource(accountId: ByteArray?): Boolean
}

internal interface WatchEnrollmentDatabase {
    suspend fun <T> inTransaction(block: suspend WatchEnrollmentDatabase.() -> T): T
    suspend fun getMetaAccounts(): List<MetaAccountLocal>
    suspend fun getNextPosition(): Int
    suspend fun hasIdentityConflict(
        substrate: ByteArray?,
        ethereum: ByteArray?,
        ton: ByteArray?
    ): Boolean
    suspend fun hasChainAccountIdentity(chainId: String, accountId: ByteArray): Boolean
    suspend fun insertMetaAccount(meta: MetaAccountLocal): Long
    suspend fun insertChainAccounts(chains: List<ChainAccountLocal>)
    suspend fun insertCustody(marker: WalletCustodyLocal)
    suspend fun selectMetaAccount(metaId: Long)
}

private class RoomWatchEnrollmentDatabase(
    private val appDatabase: AppDatabase,
    private val metaAccountDao: MetaAccountDao,
    private val custodyDao: WalletCustodyDao
) : WatchEnrollmentDatabase {
    override suspend fun <T> inTransaction(block: suspend WatchEnrollmentDatabase.() -> T): T =
        appDatabase.withTransaction { block(this@RoomWatchEnrollmentDatabase) }

    override suspend fun getMetaAccounts(): List<MetaAccountLocal> = metaAccountDao.getMetaAccounts()
    override suspend fun getNextPosition(): Int = metaAccountDao.getNextPosition()
    override suspend fun hasIdentityConflict(
        substrate: ByteArray?,
        ethereum: ByteArray?,
        ton: ByteArray?
    ): Boolean = metaAccountDao.hasIdentityConflict(0, substrate, ethereum, ton)

    override suspend fun hasChainAccountIdentity(chainId: String, accountId: ByteArray): Boolean =
        metaAccountDao.hasChainAccountIdentity(chainId, accountId)

    override suspend fun insertMetaAccount(meta: MetaAccountLocal): Long = metaAccountDao.insertMetaAccount(meta)

    override suspend fun insertChainAccounts(chains: List<ChainAccountLocal>) {
        metaAccountDao.insertWatchChainAccounts(chains)
    }

    override suspend fun insertCustody(marker: WalletCustodyLocal) {
        custodyDao.insert(marker)
    }

    override suspend fun selectMetaAccount(metaId: Long) {
        metaAccountDao.selectMetaAccount(metaId)
    }
}
