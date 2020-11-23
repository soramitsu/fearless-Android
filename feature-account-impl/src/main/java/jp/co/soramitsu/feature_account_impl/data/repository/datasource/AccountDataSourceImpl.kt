package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.data.network.scale.Schema
import jp.co.soramitsu.common.data.network.scale.byteArray
import jp.co.soramitsu.common.data.network.scale.invoke
import jp.co.soramitsu.common.data.network.scale.string
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SecuritySource
import jp.co.soramitsu.feature_account_api.domain.model.SigningData
import jp.co.soramitsu.feature_account_api.domain.model.WithDerivationPath
import jp.co.soramitsu.feature_account_api.domain.model.WithMnemonic
import jp.co.soramitsu.feature_account_api.domain.model.WithSeed
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.migration.AccountDataMigration

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
        if (accountDataMigration.migrationNeeded()) {
            accountDataMigration.migrate(::saveSecuritySource)
        }
    }

    private val selectedAccountSubject = createAccountBehaviorSubject()

    private val selectedNodeSubject = createNodeBehaviorSubject()

    override fun saveAuthType(authType: AuthType) {
        preferences.putString(PREFS_AUTH_TYPE, authType.toString())
    }

    override fun getAuthType(): AuthType {
        val savedValue = preferences.getString(PREFS_AUTH_TYPE)
        return if (savedValue == null) {
            AuthType.PINCODE
        } else {
            AuthType.valueOf(savedValue)
        }
    }

    override fun savePinCode(pinCode: String) {
        encryptedPreferences.putEncryptedString(PREFS_PIN_CODE, pinCode)
    }

    override fun getPinCode(): String? {
        return encryptedPreferences.getDecryptedString(PREFS_PIN_CODE)
    }

    override fun saveSelectedNode(node: Node) {
        selectedNodeSubject.onNext(node)

        val raw = jsonMapper.toJson(node)
        preferences.putString(PREFS_SELECTED_NODE, raw)
    }

    override fun getSelectedNode(): Node? {
        val raw = preferences.getString(PREFS_SELECTED_NODE) ?: return null
        return jsonMapper.fromJson(raw, Node::class.java)
    }

    override fun saveSecuritySource(accountAddress: String, source: SecuritySource) {
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

    override fun getSecuritySource(accountAddress: String): SecuritySource? {
        val key = PREFS_SECURITY_SOURCE_MASK.format(accountAddress)

        val raw = encryptedPreferences.getDecryptedString(key) ?: return null
        val internalSource = SourceInternal.read(raw)

        val signingData = SigningData(
            publicKey = internalSource[SourceInternal.PublicKey],
            privateKey = internalSource[SourceInternal.PrivateKey],
            nonce = internalSource[SourceInternal.Nonce]
        )

        val seed = internalSource[SourceInternal.Seed]
        val mnemonic = internalSource[SourceInternal.Mnemonic]
        val derivationPath = internalSource[SourceInternal.DerivationPath]

        return when (SourceType.valueOf(internalSource[SourceInternal.Type])) {
            SourceType.CREATE -> SecuritySource.Specified.Create(seed, signingData, mnemonic!!, derivationPath)
            SourceType.SEED -> SecuritySource.Specified.Seed(seed, signingData, derivationPath)
            SourceType.JSON -> SecuritySource.Specified.Json(seed, signingData)
            SourceType.MNEMONIC -> SecuritySource.Specified.Mnemonic(seed, signingData, mnemonic!!, derivationPath)
            SourceType.UNSPECIFIED -> SecuritySource.Unspecified(signingData)
        }
    }

    override fun anyAccountSelected(): Boolean {
        return preferences.contains(PREFS_SELECTED_ACCOUNT)
    }

    override fun saveSelectedAccount(account: Account) {
        val raw = jsonMapper.toJson(account)
        preferences.putString(PREFS_SELECTED_ACCOUNT, raw)

        selectedAccountSubject.onNext(account)
    }

    override fun observeSelectedAccount(): Observable<Account> {
        return selectedAccountSubject
    }

    override fun getPreferredCryptoType(): Single<CryptoType> {
        return if (anyAccountSelected()) {
            selectedAccountSubject
                .firstOrError()
                .map(Account::cryptoType)
        } else {
            Single.just(DEFAULT_CRYPTO_TYPE)
        }
    }

    override fun observeSelectedNode(): Observable<Node> {
        return selectedNodeSubject
    }

    private fun getSelectedAccount(): Account {
        val raw = preferences.getString(PREFS_SELECTED_ACCOUNT)
            ?: throw IllegalArgumentException("No account selected")

        return jsonMapper.fromJson(raw, Account::class.java)
    }

    private fun createAccountBehaviorSubject(): BehaviorSubject<Account> {
        val subject = BehaviorSubject.create<Account>()

        if (preferences.contains(PREFS_SELECTED_ACCOUNT)) {
            subject.onNext(getSelectedAccount())
        }

        return subject
    }

    private fun createNodeBehaviorSubject(): BehaviorSubject<Node> {
        val subject = BehaviorSubject.create<Node>()

        if (preferences.contains(PREFS_SELECTED_NODE)) {
            val selectedNode = getSelectedNode()
                ?: throw IllegalArgumentException("No node selected")
            subject.onNext(selectedNode)
        }

        return subject
    }

    override fun getSelectedLanguage(): Language {
        return preferences.getCurrentLanguage() ?: throw IllegalArgumentException("No language selected")
    }

    override fun changeSelectedLanguage(language: Language) {
        preferences.saveCurrentLanguage(language.iso)
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
}