package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import org.spongycastle.util.encoders.Hex

private const val PREFS_AUTH_TYPE = "auth_type"
private const val PREFS_SELECTED_LANGUAGE = "selected_language"
private const val PREFS_PIN_CODE = "pin_code"

private const val PREFS_SELECTED_ACCOUNT = "selected_address"

private const val PREFS_SELECTED_NETWORK = "network"

private const val PREFS_MNEMONIC_IS_BACKED_UP = "mnemonic_backed_up"
private const val PREFS_SEED_MASK = "seed_%s"
private const val PREFS_ENTROPY_MASK = "entropy_%s"
private const val PREFS_DERIVATION_MASK = "derivation_%s"

private val DEFAULT_CRYPTO_TYPE = CryptoType.SR25519

class AccountDataSourceImpl(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences,
    private val jsonMapper: Gson
) : AccountDataSource {
    private val selectedAccountSubject = createAccountBehaviorSubject()

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

    override fun saveSelectedLanguage(language: String) {
        preferences.putString(PREFS_SELECTED_LANGUAGE, language)
    }

    override fun getSelectedLanguage(): String? {
        return preferences.getString(PREFS_SELECTED_LANGUAGE)
    }

    override fun savePinCode(pinCode: String) {
        encryptedPreferences.putEncryptedString(PREFS_PIN_CODE, pinCode)
    }

    override fun getPinCode(): String? {
        return encryptedPreferences.getDecryptedString(PREFS_PIN_CODE)
    }

    override fun saveSelectedNode(node: Node) {
        val raw = jsonMapper.toJson(node)

        preferences.putString(PREFS_SELECTED_NETWORK, raw)
    }

    override fun getSelectedNode(): Node? {
        val raw = preferences.getString(PREFS_SELECTED_NETWORK) ?: return null

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
            observeSelectedAccount()
                .singleOrError()
                .map(Account::cryptoType)
        } else {
            Single.just(DEFAULT_CRYPTO_TYPE)
        }
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
}