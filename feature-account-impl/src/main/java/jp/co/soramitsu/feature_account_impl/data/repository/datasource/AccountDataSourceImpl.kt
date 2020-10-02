package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.data.network.scale.Schema
import jp.co.soramitsu.common.data.network.scale.byteArray
import jp.co.soramitsu.common.data.network.scale.invoke
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.SigningData
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.ScaleSigningData.Nonce
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.ScaleSigningData.PrivateKey
import jp.co.soramitsu.feature_account_impl.data.repository.datasource.ScaleSigningData.PublicKey
import org.spongycastle.util.encoders.Hex

private const val PREFS_AUTH_TYPE = "auth_type"
private const val PREFS_PIN_CODE = "pin_code"

private const val PREFS_SELECTED_ACCOUNT = "selected_address"

private const val PREFS_SELECTED_NODE = "node"

private const val PREFS_MNEMONIC_IS_BACKED_UP = "mnemonic_backed_up"
private const val PREFS_SEED_MASK = "seed_%s"
private const val PREFS_ENTROPY_MASK = "entropy_%s"
private const val PREFS_DERIVATION_MASK = "derivation_%s"
private const val PREFS_PRIVATE_KEY = "private_%s"

private val DEFAULT_CRYPTO_TYPE = CryptoType.SR25519

private object ScaleSigningData : Schema<ScaleSigningData>() {
    val PrivateKey by byteArray()
    val PublicKey by byteArray()

    val Nonce by byteArray().optional()
}

class AccountDataSourceImpl(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences,
    private val jsonMapper: Gson
) : AccountDataSource {

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

    override fun setMnemonicIsBackedUp(backedUp: Boolean) {
        preferences.putBoolean(PREFS_MNEMONIC_IS_BACKED_UP, backedUp)
    }

    override fun getMnemonicIsBackedUp(): Boolean {
        return preferences.getBoolean(PREFS_MNEMONIC_IS_BACKED_UP, false)
    }

    override fun saveSeed(seed: ByteArray, address: String) {
        val seedKey = PREFS_SEED_MASK.format(address)
        val seedStr = Hex.toHexString(seed)
        encryptedPreferences.putEncryptedString(seedKey, seedStr)
    }

    override fun saveSigningData(address: String, signingData: SigningData) {
        val key = PREFS_PRIVATE_KEY.format(address)

        val toSave = ScaleSigningData {
            it[PrivateKey] = signingData.privateKey
            it[PublicKey] = signingData.publicKey
            it[Nonce] = signingData.nonce
        }

        encryptedPreferences.putEncryptedString(key, ScaleSigningData.toHexString(toSave))
    }

    override fun getSigningData(address: String): SigningData? {
        val key = PREFS_PRIVATE_KEY.format(address)

        val raw = encryptedPreferences.getDecryptedString(key) ?: return null

        val data = ScaleSigningData.read(raw)

        return SigningData(
            publicKey = data[PublicKey],
            privateKey = data[PrivateKey],
            nonce = data[Nonce]
        )
    }

    override fun getSeed(address: String): ByteArray? {
        val seedKey = PREFS_SEED_MASK.format(address)
        val seedValue = encryptedPreferences.getDecryptedString(seedKey)
        return seedValue?.let { Hex.decode(it) }
    }

    override fun saveEntropy(entropy: ByteArray, address: String) {
        val entropyKey = PREFS_ENTROPY_MASK.format(address)
        val entropyStr = Hex.toHexString(entropy)
        encryptedPreferences.putEncryptedString(entropyKey, entropyStr)
    }

    override fun getEntropy(address: String): ByteArray? {
        val entropyKey = PREFS_ENTROPY_MASK.format(address)
        val entropyValue = encryptedPreferences.getDecryptedString(entropyKey)
        return entropyValue?.let { Hex.decode(it) }
    }

    override fun saveDerivationPath(derivationPath: String, address: String) {
        val derivationKey = PREFS_DERIVATION_MASK.format(address)
        encryptedPreferences.putEncryptedString(derivationKey, derivationPath)
    }

    override fun getDerivationPath(address: String): String? {
        val derivationKey = PREFS_DERIVATION_MASK.format(address)
        return encryptedPreferences.getDecryptedString(derivationKey)
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
}