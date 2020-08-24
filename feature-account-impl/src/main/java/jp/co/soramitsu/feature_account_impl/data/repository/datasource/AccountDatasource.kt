package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.Node

interface AccountDatasource {

    fun saveAuthType(authType: AuthType)

    fun getAuthType(): AuthType

    fun saveSelectedLanguage(language: String)

    fun getSelectedLanguage(): String?

    fun savePinCode(pinCode: String)

    fun getPinCode(): String?

    fun saveSelectedAddress(address: String)

    fun getSelectedAddress(): String?

    fun saveCryptoType(cryptoType: CryptoType, address: String)

    fun getCryptoType(address: String): CryptoType?

    fun saveConnectionUrl(connectionUrl: String)

    fun getConnectionUrl(): String?

    fun saveSelectedNetwork(network: Node)

    fun getSelectedNetwork(): Node?

    fun setMnemonicIsBackedUp(backedUp: Boolean)

    fun getMnemonicIsBackedUp(): Boolean

    fun saveSeed(seed: ByteArray, address: String)

    fun getSeed(address: String): ByteArray?

    fun saveEntropy(entropy: ByteArray, address: String)

    fun getEntropy(address: String): ByteArray?

    fun saveDerivationPath(derivationPath: String, address: String)

    fun getDerivationPath(address: String): String?

    fun saveSelectedAccount(account: Account)

    fun getSelectedAccount(): Account
}