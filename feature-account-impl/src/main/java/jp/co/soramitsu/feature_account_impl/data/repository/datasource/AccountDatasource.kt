package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.feature_account_api.domain.model.AuthType
import jp.co.soramitsu.feature_account_api.domain.model.CryptoType
import jp.co.soramitsu.feature_account_api.domain.model.NetworkType

interface AccountDatasource {

    fun saveAuthType(authType: AuthType)

    fun getAuthType(): AuthType

    fun saveSelectedLanguage(language: String)

    fun getSelectedLanguage(): String?

    fun savePinCode(pinCode: String)

    fun getPinCode(): String?

    fun saveSelectedAddress(address: String)

    fun getSelectedAddress(): String?

    fun saveAccountName(accountName: String, address: String)

    fun getAccountName(address: String): String?

    fun saveCryptoType(cryptoType: CryptoType, address: String)

    fun getCryptoType(address: String): CryptoType?

    fun saveConnectionUrl(connectionUrl: String)

    fun getConnectionUrl(): String?

    fun saveNetworkType(networkType: NetworkType)

    fun getNetworkType(): NetworkType?

    fun setMnemonicIsBackedUp(backedUp: Boolean)

    fun getMnemonicIsBackedUp(): Boolean
}