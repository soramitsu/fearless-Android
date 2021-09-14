package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import com.google.gson.Gson
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_impl.data.mappers.mapNodeLocalToNode
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.migration.AccountDataMigration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_AUTH_TYPE = "auth_type"
private const val PREFS_PIN_CODE = "pin_code"

private const val PREFS_SELECTED_ACCOUNT = "selected_address"

private const val PREFS_SELECTED_NODE = "node"

private const val MOVED_ACTIVE_NODE_TO_DB = "MOVED_ACTIVE_NODE_TO_DB"

private val DEFAULT_CRYPTO_TYPE = CryptoType.SR25519

class AccountDataSourceImpl(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences,
    private val nodeDao: NodeDao,
    private val jsonMapper: Gson,
    secretStoreV1: SecretStoreV1,
    accountDataMigration: AccountDataMigration
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

    private val selectedNodeFlow = nodeDao.activeNodeFlow()
        .map { it?.let(::mapNodeLocalToNode) }
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

    override suspend fun anyAccountSelected(): Boolean = withContext(Dispatchers.IO) {
        preferences.contains(PREFS_SELECTED_ACCOUNT)
    }

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

    override suspend fun getSelectedLanguage(): Language = withContext(Dispatchers.IO) {
        preferences.getCurrentLanguage() ?: throw IllegalArgumentException("No language selected")
    }

    override suspend fun changeSelectedLanguage(language: Language) = withContext(Dispatchers.IO) {
        preferences.saveCurrentLanguage(language.iso)
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
