package jp.co.soramitsu.soracard.impl.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.co.soramitsu.oauth.base.sdk.contract.IbanInfo
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.oauth.clients.ClientsFacade
import jp.co.soramitsu.soracard.api.util.createSoraCardBasicContract
import jp.co.soramitsu.soracard.api.util.soraCardBackendUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoraCardClientProxy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clientsFacade: ClientsFacade,
) {

    suspend fun init() = clientsFacade.init(
        createSoraCardBasicContract(),
        context,
        soraCardBackendUrl,
    )

    suspend fun getKycStatus(): Result<SoraCardCommonVerification> {
        return clientsFacade.getKycStatus()
    }

    suspend fun getApplicationFee(): String {
        return clientsFacade.getApplicationFee()
    }

    suspend fun getVersion(): Result<String> {
        return clientsFacade.getFearlessSupportVersion()
    }

    suspend fun getIBAN(): Result<IbanInfo?> {
        return clientsFacade.getIBAN()
    }

    suspend fun getPhone() = clientsFacade.getPhoneNumber()

    suspend fun logout() {
        clientsFacade.logout()
    }
}
