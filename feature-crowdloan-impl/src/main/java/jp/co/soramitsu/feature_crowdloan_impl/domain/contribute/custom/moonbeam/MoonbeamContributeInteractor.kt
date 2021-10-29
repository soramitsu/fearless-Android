package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseException
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.signWithAccount
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import java.security.MessageDigest
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.MoonbeamApi
import retrofit2.HttpException
import java.io.IOException

class MoonbeamContributeInteractor(
    private val moonbeamApi: MoonbeamApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val resourceManager: ResourceManager,
    val fearlessReferralCode: String,
    private val feeEstimator: FeeEstimator,
    private val accountRepository: AccountRepository,
) {
    private val digest = MessageDigest.getInstance("SHA-256")

    private var termsHash: String? = null
    private var termsSigned: String? = null

    suspend fun getHealth(apiKey: String) = try {
        moonbeamApi.getHealth(apiKey)
        true
    } catch (e: Throwable) {
        val errorCode = (e as? HttpException)?.response()?.code()
        if (errorCode == 403) {
            false
        } else {
            throw transformException(e)
        }
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

    private fun transformException(exception: Throwable): BaseException {
        return when (exception) {
            is HttpException -> {
                val response = exception.response()!!

                val errorCode = response.code()
                response.errorBody()?.close()

                BaseException.httpError(errorCode, resourceManager.getString(R.string.common_undefined_error_message))
            }
            is IOException -> BaseException.networkError(resourceManager.getString(R.string.connection_error_message), exception)
            else -> BaseException.unexpectedError(exception)
        }
    }

}
