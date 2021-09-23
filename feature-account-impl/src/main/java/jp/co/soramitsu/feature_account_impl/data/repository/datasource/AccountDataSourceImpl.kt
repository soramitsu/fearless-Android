package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import com.google.gson.Gson
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.dao.MetaAccountDao
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_impl.data.mappers.mapChainAccountToAccount
import jp.co.soramitsu.feature_account_impl.data.mappers.mapMetaAccountLocalToMetaAccount
import jp.co.soramitsu.feature_account_impl.data.mappers.mapMetaAccountToAccount
import jp.co.soramitsu.feature_account_impl.data.mappers.mapNodeLocalToNode
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.migration.AccountDataMigration
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_AUTH_TYPE = "auth_type"
private const val PREFS_PIN_CODE = "pin_code"

private const val PREFS_SELECTED_ACCOUNT = "selected_address"

private val DEFAULT_CRYPTO_TYPE = CryptoType.SR25519

class AccountDataSourceImpl(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences,
    private val nodeDao: NodeDao,
    private val jsonMapper: Gson,
    private val metaAccountDao: MetaAccountDao,
    private val chainRegistry: ChainRegistry,
    secretStoreV1: SecretStoreV1,
    accountDataMigration: AccountDataMigration,
) : AccountDataSource, SecretStoreV1 by secretStoreV1 {

    init {
        migrateIfNeeded(accountDataMigration)
    }

    private fun migrateIfNeeded(migration: AccountDataMigration) = async {
        if (migration.migrationNeeded()) {
            migration.migrate(::saveSecuritySource)
        }
    }

    private val selectedAccountFlow = createAccountFlow()

    private val selectedMetaAccountLocal = metaAccountDao.selectedMetaAccountInfoFlow()
        .shareIn(GlobalScope, started = SharingStarted.Eagerly, replay = 1)

    private val selectedMetaAccountFlow = combine(
        chainRegistry.chainsById,
        selectedMetaAccountLocal.filterNotNull(),
        ::mapMetaAccountLocalToMetaAccount
    )
        .inBackground()
        .shareIn(GlobalScope, started = SharingStarted.Eagerly, replay = 1)

    private val selectedNodeFlow = nodeDao.activeNodeFlow()
        .map { it?.let(::mapNodeLocalToNode) }
        .shareIn(GlobalScope, started = SharingStarted.Eagerly, replay = 1)

    /**
     * Fast lookup table for accessing account based on accountId
     */
    override val selectedAccountMapping = selectedMetaAccountFlow.map { metaAccount ->
        val mapping = metaAccount.chainAccounts.mapValuesTo(mutableMapOf<String, Account?>()) { (_, chainAccount) ->
            mapChainAccountToAccount(metaAccount, chainAccount)
        }

        val chains = chainRegistry.chainsById.first()

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

    override suspend fun savePinCode(pinCode: String) = withContext(Dispatchers.IO) {
        encryptedPreferences.putEncryptedString(PREFS_PIN_CODE, pinCode)
    }

    override suspend fun getPinCode(): String? {
        return withContext(Dispatchers.IO) {
            encryptedPreferences.getDecryptedString(PREFS_PIN_CODE)
        }
    }

    override suspend fun saveSelectedNode(node: Node) = withContext(Dispatchers.Default) {
        nodeDao.switchActiveNode(node.id)
    }

    override suspend fun getSelectedNode(): Node? = selectedNodeFlow.first()

    override suspend fun anyAccountSelected(): Boolean = selectedMetaAccountLocal.first() != null

    override suspend fun saveSelectedAccount(account: Account) = withContext(Dispatchers.Default) {
        val raw = jsonMapper.toJson(account)
        preferences.putString(PREFS_SELECTED_ACCOUNT, raw)

        selectedAccountFlow.emit(account)
    }

    override fun selectedAccountFlow(): Flow<Account> {
        return selectedAccountFlow
    }

    override suspend fun getPreferredCryptoType(): CryptoType {
        return if (anyAccountSelected()) {
            getSelectedAccount().cryptoType
        } else {
            DEFAULT_CRYPTO_TYPE
        }
    }

    override fun selectedNodeFlow(): Flow<Node> {
        return selectedNodeFlow
            .filterNotNull()
    }

    override suspend fun getSelectedAccount(): Account {
        return selectedAccountFlow.replayCache.firstOrNull() ?: retrieveAccountFromStorage()
    }

    override suspend fun getSelectedMetaAccount(): MetaAccount {
        return selectedMetaAccountFlow.first()
    }

    override fun selectedMetaAccountFlow(): Flow<MetaAccount> = selectedMetaAccountFlow

    override suspend fun findMetaAccount(accountId: ByteArray): MetaAccount? {
        return metaAccountDao.getMetaAccountInfo(accountId)?.let {
            mapMetaAccountLocalToMetaAccount(chainRegistry.chainsById.first(), it)
        }
    }

    override suspend fun allMetaAccounts(): List<MetaAccount> {
        val chainsById = chainRegistry.chainsById.first()

        return metaAccountDao.getJoinedMetaAccountsInfo().map {
            mapMetaAccountLocalToMetaAccount(chainsById, it)
        }
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

    private fun createAccountFlow(): MutableSharedFlow<Account> {
        val flow = MutableSharedFlow<Account>(replay = 1)

        async {
            if (preferences.contains(PREFS_SELECTED_ACCOUNT)) {
                flow.emit(retrieveAccountFromStorage())
            }
        }

        return flow
    }

    private suspend fun retrieveAccountFromStorage(): Account = withContext(Dispatchers.Default) {
        val raw = preferences.getString(PREFS_SELECTED_ACCOUNT)
            ?: throw IllegalArgumentException("No account selected")

        jsonMapper.fromJson(raw, Account::class.java)
    }

    private inline fun async(crossinline action: suspend () -> Unit) {
        GlobalScope.launch(Dispatchers.Default) {
            action()
        }
    }
}
