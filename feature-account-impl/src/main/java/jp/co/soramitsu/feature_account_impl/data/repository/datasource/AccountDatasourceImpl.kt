package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
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
        private const val PREFS_ACCOUNT_NAME_MASK = "account_name_%s"
        private const val PREFS_CRYPTO_TYPE_MASK = "crypto_type_%s"
        private const val PREFS_CONNECTION_URL = "connection_url"
        private const val PREFS_NETWORK_TYPE = "network_type"
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

    override fun saveAccountName(accountName: String, address: String) {
        val accountNameKey = PREFS_ACCOUNT_NAME_MASK.format(address)
        preferences.putString(accountNameKey, accountName)
    }

    override fun getAccountName(address: String): String? {
        val accountNameKey = PREFS_ACCOUNT_NAME_MASK.format(address)
        return preferences.getString(accountNameKey)
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

    override fun saveNetworkType(networkType: NetworkType) {
        preferences.putString(PREFS_NETWORK_TYPE, networkType.toString())
    }

    override fun getNetworkType(): NetworkType? {
        return preferences.getString(PREFS_NETWORK_TYPE)?.let { NetworkType.valueOf(it) }
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
}