package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import jp.co.soramitsu.feature_account_api.domain.model.AuthType

interface AccountDatasource {

    fun saveAuthType(authType: AuthType)

    fun getAuthType(): AuthType

    fun saveSelectedLanguage(language: String)

    fun getSelectedLanguage(): String?

    fun savePinCode(pinCode: String)

    fun getPinCode(): String?
}