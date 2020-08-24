package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType
import org.spongycastle.util.encoders.Hex

class AccountDatasourceImpl(
    private val preferences: Preferences,
    private val encryptedPreferences: EncryptedPreferences
) : AccountDatasource {

    companion object {
        private const val PREFS_AUTH_TYPE = "auth_type"
        private const val PREFS_SELECTED_LANGUAGE = "selected_language"
        private const val PREFS_PIN_CODE = "pin_code"
        private const val PREFS_SELECTED_ADDRESS = "selected_address"
        private const val PREFS_SELECTED_ACCOUNT_NAME = "account_name"
        private const val PREFS_SELECTED_PUBLIC_KEY = "account_pub_key"
        private const val PREFS_SELECTED_ACCOUNT_NETWORK_TYPE = "account_network_type"
        private const val PREFS_SELECTED_ACCOUNT_CRYPTO_TYPE = "account_crypto_type"
        private const val PREFS_CRYPTO_TYPE_MASK = "crypto_type_%s"
        private const val PREFS_CONNECTION_URL = "connection_url"
        private const val PREFS_NETWORK_TYPE = "network_type"
        private const val PREFS_NETWORK_LINK = "network_link"
        private const val PREFS_NETWORK_NAME = "network_name"
        private const val PREFS_NETWORK_DEFAULT = "network_default"
        private const val PREFS_MNEMONIC_IS_BACKED_UP = "mnemonic_backed_up"
        private const val PREFS_SEED_MASK = "seed_%s"
        private const val PREFS_ENTROPY_MASK = "entropy_%s"
        private const val PREFS_DERIVATION_MASK = "derivation_%s"
    }

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

    override fun saveSelectedAddress(address: String) {
        preferences.putString(PREFS_SELECTED_ADDRESS, address)
    }

    override fun getSelectedAddress(): String? {
        return preferences.getString(PREFS_SELECTED_ADDRESS)
    }

    override fun saveCryptoType(cryptoType: CryptoType, address: String) {
        val cryptoTypeKey = PREFS_CRYPTO_TYPE_MASK.format(address)
        preferences.putString(cryptoTypeKey, cryptoType.toString())
    }

    override fun getCryptoType(address: String): CryptoType? {
        val cryptoTypeKey = PREFS_CRYPTO_TYPE_MASK.format(address)
        return preferences.getString(cryptoTypeKey)?.let { CryptoType.valueOf(it) }
    }

    override fun saveConnectionUrl(connectionUrl: String) {
        preferences.putString(PREFS_CONNECTION_URL, connectionUrl)
    }

    override fun getConnectionUrl(): String? {
        return preferences.getString(PREFS_CONNECTION_URL)
    }

    override fun saveSelectedNetwork(network: Node) {
        preferences.putString(PREFS_NETWORK_TYPE, network.networkType.toString())
        preferences.putString(PREFS_NETWORK_NAME, network.name)
        preferences.putString(PREFS_NETWORK_LINK, network.link)
        preferences.putBoolean(PREFS_NETWORK_DEFAULT, network.default)
    }

    override fun getSelectedNetwork(): Node? {
        val type = preferences.getString(PREFS_NETWORK_TYPE)?.let { NetworkType.valueOf(it) }
        val name = preferences.getString(PREFS_NETWORK_NAME)
        val link = preferences.getString(PREFS_NETWORK_LINK)
        val default = preferences.getBoolean(PREFS_NETWORK_DEFAULT, false)

        return Node(name!!, type!!, link!!, default)
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

    override fun saveSelectedAccount(account: Account) {
        preferences.putString(PREFS_SELECTED_ACCOUNT_NAME, account.username)
        preferences.putString(PREFS_SELECTED_ADDRESS, account.address)
        preferences.putString(PREFS_SELECTED_ACCOUNT_CRYPTO_TYPE, account.cryptoType.toString())
        preferences.putString(PREFS_SELECTED_PUBLIC_KEY, account.publicKey)
        preferences.putString(PREFS_SELECTED_ACCOUNT_NETWORK_TYPE, account.networkType.toString())
    }

    override fun getSelectedAccount(): Account {
        val accountName = preferences.getString(PREFS_SELECTED_ACCOUNT_NAME)
        val address = preferences.getString(PREFS_SELECTED_ADDRESS)
        val cryptoType = CryptoType.valueOf(preferences.getString(PREFS_SELECTED_ACCOUNT_CRYPTO_TYPE)!!)
        val networkType = NetworkType.valueOf(preferences.getString(PREFS_SELECTED_ACCOUNT_NETWORK_TYPE)!!)
        val publicKey = preferences.getString(PREFS_SELECTED_PUBLIC_KEY)

        return Account(address!!, accountName!!, publicKey!!, cryptoType, networkType)
    }
}