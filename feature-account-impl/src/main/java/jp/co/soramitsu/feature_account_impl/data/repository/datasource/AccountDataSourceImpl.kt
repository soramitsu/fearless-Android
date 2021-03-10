package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import com.google.gson.Gson
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.core.model.CryptoType
import jp.co.soramitsu.core.model.Language
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.model.SigningData
import jp.co.soramitsu.core.model.WithDerivationPath
import jp.co.soramitsu.core.model.WithMnemonic
import jp.co.soramitsu.core.model.WithSeed
import jp.co.soramitsu.fearless_utils.scale.Schema
import jp.co.soramitsu.fearless_utils.scale.byteArray
import jp.co.soramitsu.fearless_utils.scale.invoke
import jp.co.soramitsu.fearless_utils.scale.string
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.migration.AccountDataMigration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_AUTH_TYPE = "auth_type"
private const val PREFS_PIN_CODE = "pin_code"

private const val PREFS_SELECTED_ACCOUNT = "selected_address"

private const val PREFS_SELECTED_NODE = "node"

private const val PREFS_SECURITY_SOURCE_MASK = "security_source_%s"

private val DEFAULT_CRYPTO_TYPE = CryptoType.SR25519

enum class SourceType {
    CREATE, SEED, MNEMONIC, JSON, UNSPECIFIED
}

object SourceInternal : Schema<SourceInternal>() {
    val Type by string()

    val PrivateKey by byteArray()
    val PublicKey by byteArray()

    val Nonce by byteArray().optional()

    val Seed by byteArray().optional()
    val Mnemonic by string().optional()

    val DerivationPath by string().optional()
}

class AccountDataSourceImpl(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences,
    private val jsonMapper: Gson,
    accountDataMigration: AccountDataMigration
) : AccountDataSource {

    init {
        migrateIfNeeded(accountDataMigration)
    }

    private fun migrateIfNeeded(migration: AccountDataMigration) = async {
        if (migration.migrationNeeded()) {
            migration.migrate(::saveSecuritySource)
        }
    }

    private var selectedAccountSubject = createAccountFlow()

    private var selectedNodeFlow = createNodeFlow()

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
        val raw = jsonMapper.toJson(node)
        preferences.putString(PREFS_SELECTED_NODE, raw)

        selectedNodeFlow.emit(node)
    }

    override suspend fun getSelectedNode(): Node? = withContext(Dispatchers.Default) {
        val raw = preferences.getString(PREFS_SELECTED_NODE) ?: return@withContext null

        jsonMapper.fromJson(raw, Node::class.java)
    }

    override suspend fun saveSecuritySource(accountAddress: String, source: SecuritySource) = withContext(Dispatchers.Default) {
        val key = PREFS_SECURITY_SOURCE_MASK.format(accountAddress)

        val signingData = source.signingData
        val seed = (source as? WithSeed)?.seed
        val mnemonic = (source as? WithMnemonic)?.mnemonic
        val derivationPath = (source as? WithDerivationPath)?.derivationPath

        val toSave = SourceInternal {
            it[SourceInternal.Type] = getSourceType(source).name

            it[SourceInternal.PrivateKey] = signingData.privateKey
            it[SourceInternal.PublicKey] = signingData.publicKey
            it[SourceInternal.Nonce] = signingData.nonce

            it[SourceInternal.Seed] = seed
            it[SourceInternal.Mnemonic] = mnemonic
            it[SourceInternal.DerivationPath] = derivationPath
        }

        val raw = SourceInternal.toHexString(toSave)

        encryptedPreferences.putEncryptedString(key, raw)
    }

    override suspend fun getSecuritySource(accountAddress: String): SecuritySource? = withContext(Dispatchers.Default) {
        val key = PREFS_SECURITY_SOURCE_MASK.format(accountAddress)

        val raw = encryptedPreferences.getDecryptedString(key) ?: return@withContext null
        val internalSource = SourceInternal.read(raw)

        val signingData = SigningData(
            publicKey = internalSource[SourceInternal.PublicKey],
            privateKey = internalSource[SourceInternal.PrivateKey],
            nonce = internalSource[SourceInternal.Nonce]
        )

        val seed = internalSource[SourceInternal.Seed]
        val mnemonic = internalSource[SourceInternal.Mnemonic]
        val derivationPath = internalSource[SourceInternal.DerivationPath]

        when (SourceType.valueOf(internalSource[SourceInternal.Type])) {
            SourceType.CREATE -> SecuritySource.Specified.Create(seed, signingData, mnemonic!!, derivationPath)
            SourceType.SEED -> SecuritySource.Specified.Seed(seed, signingData, derivationPath)
            SourceType.JSON -> SecuritySource.Specified.Json(seed, signingData)
            SourceType.MNEMONIC -> SecuritySource.Specified.Mnemonic(seed, signingData, mnemonic!!, derivationPath)
            SourceType.UNSPECIFIED -> SecuritySource.Unspecified(signingData)
        }
    }

    override suspend fun anyAccountSelected(): Boolean = withContext(Dispatchers.IO) {
        preferences.contains(PREFS_SELECTED_ACCOUNT)
    }

    override suspend fun saveSelectedAccount(account: Account) = withContext(Dispatchers.Default) {
        val raw = jsonMapper.toJson(account)
        preferences.putString(PREFS_SELECTED_ACCOUNT, raw)

        selectedAccountSubject.emit(account)
    }

    override fun selectedAccountFlow(): Flow<Account> {
        return selectedAccountSubject
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
    }

    override suspend fun getSelectedAccount(): Account {
        return selectedAccountSubject.replayCache.firstOrNull() ?: retrieveAccountFromStorage()
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

    private fun createNodeFlow(): MutableSharedFlow<Node> {
        val flow = MutableSharedFlow<Node>(replay = 1)

        async {
            if (preferences.contains(PREFS_SELECTED_NODE)) {
                val selectedNode = getSelectedNode() ?: throw IllegalArgumentException("No node selected")
                flow.emit(selectedNode)
            }
        }

        return flow
    }

    private fun getSourceType(securitySource: SecuritySource): SourceType {
        return when (securitySource) {
            is SecuritySource.Specified.Create -> SourceType.CREATE
            is SecuritySource.Specified.Mnemonic -> SourceType.MNEMONIC
            is SecuritySource.Specified.Json -> SourceType.JSON
            is SecuritySource.Specified.Seed -> SourceType.SEED
            else -> SourceType.UNSPECIFIED
        }
    }

    private inline fun async(crossinline action: suspend () -> Unit) {
        GlobalScope.launch(Dispatchers.Default) {
            action()
        }
    }
}