package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam

import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.signWithAccount
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.MoonbeamApi
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import java.security.MessageDigest

class MoonbeamContributeInteractor(
    private val moonbeamApi: MoonbeamApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    val fearlessReferralCode: String,
    private val feeEstimator: FeeEstimator,
    private val accountRepository: AccountRepository,
) {
    private val digest = MessageDigest.getInstance("SHA-256")

    private var termsHash: String? = null
    private var termsSigned: String? = null

    suspend fun getHealth() = try {
        moonbeamApi.getHealth()
    } catch (e: Throwable) {

    }

    suspend fun getTerms(): String {
        val account = accountRepository.getSelectedAccount()
        return httpExceptionHandler.wrap { moonbeamApi.getTerms() }.also {
            calcHashes(digest.digest(it.encodeToByteArray()), account)
        }
    }

    private suspend fun calcHashes(termsBytes: ByteArray, ss: Account) {
        termsHash = termsBytes.toHexString(false)
        termsSigned = accountRepository.signWithAccount(ss, termsBytes).toHexString(true)
    }
}
