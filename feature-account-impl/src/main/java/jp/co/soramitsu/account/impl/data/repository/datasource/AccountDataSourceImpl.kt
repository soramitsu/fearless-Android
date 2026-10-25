package jp.co.soramitsu.account.impl.data.repository.datasource

import jp.co.soramitsu.common.data.storage.encrypt.PinHealth

import com.google.gson.Gson
import jp.co.soramitsu.account.api.domain.model.Account
import jp.co.soramitsu.account.api.domain.model.AuthType
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccountOrdering
import jp.co.soramitsu.account.impl.data.mappers.mapChainAccountToAccount
import jp.co.soramitsu.account.impl.data.mappers.mapJoinedMetaAccountInfoToLightMetaAccount
import jp.co.soramitsu.account.impl.data.mappers.mapMetaAccountLocalToMetaAccount
import jp.co.soramitsu.account.impl.data.mappers.mapMetaAccountToAccount
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.MetaAccountPositionUpdate
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

private const val PREFS_AUTH_TYPE = "auth_type"
private const val PREFS_PIN_CODE = "pin_code"

private val DEFAULT_CRYPTO_TYPE = CryptoType.SR25519

class AccountDataSourceImpl(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences,
    private val jsonMapper: Gson,
    private val metaAccountDao: MetaAccountDao,
    secretStoreV1: SecretStoreV1,
    private val chainsRepository: ChainsRepository
) : AccountDataSource, SecretStoreV1 by secretStoreV1 {

    private val selectedMetaAccountLocal = metaAccountDao.selectedMetaAccountInfoFlow()

    private val selectedMetaAccountFlow = combine(
        chainsRepository.chainsByIdFlow(),
        selectedMetaAccountLocal.filterNotNull(),
        ::mapMetaAccountLocalToMetaAccount
    )
        .inBackground()

    /**
     * Fast lookup table for accessing account based on accountId
     */
    @OptIn(DelicateCoroutinesApi::class)
    override val selectedAccountMapping = selectedMetaAccountFlow.map { metaAccount ->
        val mapping =
            metaAccount.chainAccounts.mapValuesTo(mutableMapOf<String, Account?>()) { (_, chainAccount) ->
                mapChainAccountToAccount(metaAccount, chainAccount)
            }

        val chains = chainsRepository.getChainsById()

        chains.forEach { (chainId, chain) ->
            if (chainId !in mapping) {
                mapping[chainId] = mapMetaAccountToAccount(chain, metaAccount)
            }
        }

        mapping
    }
        .inBackground()
        .shareIn(GlobalScope, started = SharingStarted.Eagerly, replay = 1)

    override suspend fun saveAuthType(authType: AuthType) = withContext(Dispatchers.IO) {
        preferences.putString(PREFS_AUTH_TYPE, authType.toString())
    }

    override suspend fun getAuthType(): AuthType = withContext(Dispatchers.IO) {
        val savedValue = preferences.getString(PREFS_AUTH_TYPE)

        if (savedValue == null) {
            AuthType.PINCODE
        } else {
            AuthType.valueOf(savedValue)
        }
    }

    override suspend fun getPinHealth(): PinHealth = withContext(Dispatchers.IO) {
        encryptedPreferences.getPinHealth()
    }

    override suspend fun savePinCode(pinCode: String) = withContext(Dispatchers.IO) {
        encryptedPreferences.savePinCodeDurably(pinCode)
    }

    override suspend fun getPinCode(): String? {
        return withContext(Dispatchers.IO) {
            try {
                encryptedPreferences.getDecryptedString(PREFS_PIN_CODE)
            } catch (_: ClassCastException) {
                ""
            }
        }
    }

    override suspend fun anyAccountSelected(): Boolean = selectedMetaAccountLocal.firstOrNull() != null

    override suspend fun getPreferredCryptoTypeOrSelected(metaId: Long?): CryptoType {
        return metaId?.let { getMetaAccount(it).substrateCryptoType } ?: DEFAULT_CRYPTO_TYPE
    }

    override suspend fun getSelectedMetaAccount(): MetaAccount {
        val chainsById = chainsRepository.getChainsById()
        val selectedMetaAccount = metaAccountDao.selectedMetaAccountInfo()
        return mapMetaAccountLocalToMetaAccount(chainsById, selectedMetaAccount)
    }

    override fun selectedMetaAccountFlow(): Flow<MetaAccount> = selectedMetaAccountFlow

    /**
     * Revoke before waiting for prior cancellation cleanup or the replacement catalog.
     * Null is handed off without a buffer; no replacement can become ready until the old
     * job has ended. Downstream consumers still need their own generation checks.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun selectedMetaAccountStateFlow(): Flow<MetaAccount?> = channelFlow {
        val generation = AtomicLong()
        var currentSelection: Job? = null
        send(null)
        selectedMetaAccountLocal.collect { local ->
            val revision = generation.incrementAndGet()
            val previous = currentSelection
            previous?.cancel()
            send(null)
            previous?.join()
            currentSelection = if (local == null) null else launch {
                val publisherJob = currentCoroutineContext().job
                chainsRepository.chainsByIdFlow().collect { chains ->
                    val mapped = mapMetaAccountLocalToMetaAccount(chains, local)
                    if (generation.get() == revision && publisherJob.isActive) {
                        send(mapped)
                    }
                }
            }
        }
    }.buffer(0)

    /** Per-wallet cold observer; keeps TON-only replacements and collector cancellation visible. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun metaAccountStateFlow(metaId: Long): Flow<MetaAccount?> = channelFlow {
        val generation = AtomicLong()
        var currentSelection: Job? = null
        send(null)
        metaAccountDao.observeJoinedMetaAccountInfo(metaId).collect { local ->
            val revision = generation.incrementAndGet()
            val previous = currentSelection
            previous?.cancel()
            send(null)
            previous?.join()
            currentSelection = if (local == null) null else launch {
                val publisherJob = currentCoroutineContext().job
                chainsRepository.chainsByIdFlow().collect { chains ->
                    val mapped = mapMetaAccountLocalToMetaAccount(chains, local)
                    if (generation.get() == revision && publisherJob.isActive) {
                        send(mapped)
                    }
                }
            }
        }
    }.buffer(0)

    override fun selectedLightMetaAccount(): Flow<LightMetaAccount> {
        return metaAccountDao.selectedMetaAccountInfoFlow().map { accountLocal ->
            accountLocal?.let { mapJoinedMetaAccountInfoToLightMetaAccount(it) }
        }.filterNotNull().flowOn(Dispatchers.IO)
    }

    override fun lightMetaAccountFlow(metaId: Long): Flow<LightMetaAccount> {
        return metaAccountDao.observeJoinedMetaAccountInfo(metaId).map { accountLocal ->
            accountLocal?.let { mapJoinedMetaAccountInfoToLightMetaAccount(it) }
        }.filterNotNull().flowOn(Dispatchers.IO)
    }

    override suspend fun getSelectedLightMetaAccount(): LightMetaAccount {
        val local = withContext(Dispatchers.IO) { metaAccountDao.selectedMetaAccountInfo() }
        return mapJoinedMetaAccountInfoToLightMetaAccount(local)
    }

    override suspend fun findMetaAccount(accountId: ByteArray): MetaAccount? {
        return metaAccountDao.getMetaAccountInfo(accountId)?.let {
            mapMetaAccountLocalToMetaAccount(chainsRepository.getChainsById(), it)
        }
    }

    override suspend fun allMetaAccounts(): List<MetaAccount> {
        val chainsById = chainsRepository.getChainsById()

        return metaAccountDao.getJoinedMetaAccountsInfo().map {
            mapMetaAccountLocalToMetaAccount(chainsById, it)
        }
    }

    override fun observeAllMetaAccounts(): Flow<List<MetaAccount>> {
        return combine(
            chainsRepository.chainsByIdFlow(),
            metaAccountDao.observeJoinedMetaAccountsInfo()
        ) { chainsById, metaAccounts ->
            metaAccounts.map {
                mapMetaAccountLocalToMetaAccount(chainsById, it)
            }
        }
    }

    override fun lightMetaAccountsFlow(): Flow<List<LightMetaAccount>> {
        return metaAccountDao.observeOrderedJoinedMetaAccountsInfo().mapList {
            mapJoinedMetaAccountInfoToLightMetaAccount(it)
        }
    }

    override suspend fun selectMetaAccount(metaId: Long) {
        metaAccountDao.selectMetaAccount(metaId)
    }

    override suspend fun updateAccountPositions(accountOrdering: List<MetaAccountOrdering>) =
        withContext(Dispatchers.Default) {
            val positionUpdates = accountOrdering.map {
                MetaAccountPositionUpdate(id = it.id, position = it.position)
            }

            metaAccountDao.updatePositions(positionUpdates)
        }

    override suspend fun getSelectedLanguage(): Language = withContext(Dispatchers.IO) {
        preferences.getCurrentLanguage() ?: throw IllegalArgumentException("No language selected")
    }

    override suspend fun changeSelectedLanguage(language: Language) = withContext(Dispatchers.IO) {
        preferences.saveCurrentLanguage(language.iso)
    }

    override suspend fun accountExists(accountId: AccountId): Boolean {
        return metaAccountDao.isMetaAccountExists(accountId)
    }

    override suspend fun getMetaAccount(metaId: Long): MetaAccount {
        val joinedMetaAccountInfo = metaAccountDao.getJoinedMetaAccountInfo(metaId)

        return mapMetaAccountLocalToMetaAccount(
            chainsRepository.getChainsById(),
            joinedMetaAccountInfo
        )
    }

    override suspend fun getLightMetaAccount(metaId: Long): LightMetaAccount {
        val local = withContext(Dispatchers.IO) { metaAccountDao.getJoinedMetaAccountInfo(metaId) }
        return mapJoinedMetaAccountInfoToLightMetaAccount(local)
    }

    override suspend fun updateMetaAccountName(metaId: Long, newName: String) {
        metaAccountDao.updateName(metaId, newName)
    }

    override suspend fun updateMetaAccountBackedUp(metaId: Long) {
        metaAccountDao.updateBackedUp(metaId)
    }

    override suspend fun updateWalletOnGoogleBackupDelete(metaId: Long) {
        metaAccountDao.clearGoogleBackupInfo(metaId)
    }

    override fun observeFavoriteChains(metaId: Long) = metaAccountDao.observeFavoriteChains(metaId)
}
