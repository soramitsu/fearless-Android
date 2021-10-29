package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam

import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.signWithAccount
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.MoonbeamApi
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.RemarkStoreRequest
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import java.math.BigInteger
import java.security.MessageDigest

class MoonbeamContributeInteractor(
    private val moonbeamApi: MoonbeamApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val resourceManager: ResourceManager,
    val fearlessReferralCode: String,
    private val feeEstimator: FeeEstimator,
    private val accountRepository: AccountRepository,
    private val extrinsicService: ExtrinsicService,
    private val snapshot: SuspendableProperty<RuntimeSnapshot>
) {
    private val digest = MessageDigest.getInstance("SHA-256")

    private var termsHash: String? = null
    private var termsSigned: String? = null
    private var moonbeamRemark: String? = null

    fun nextStep(payload: CustomContributePayload) {
    }

    suspend fun doSystemRemark(): Boolean {
        val remark = requireNotNull(moonbeamRemark)
        val result = extrinsicService.submitAndWatchExtrinsic(
            accountAddress = accountRepository.getSelectedAccount().address,
            formExtrinsic = {
                call(
                    moduleName = "System",
                    callName = "remark",
                    arguments = mapOf(
                        "remark" to remark.toByteArray()
                    )
                )
            },
            snapshot = snapshot.get()
        )
        return result != null
    }

    suspend fun getSystemRemarkFee(apiUrl: String, apiKey: String): BigInteger {
        val sign = requireNotNull(termsSigned)
        val remarkResponse = moonbeamApi.agreeRemark(
            apiUrl,
            apiKey,
            RemarkStoreRequest(
                accountRepository.getSelectedAccount().address,
                sign
            )
        )
        val remark = remarkResponse.remark
        moonbeamRemark = remark
        return feeEstimator.estimateFee(
            accountAddress = accountRepository.getSelectedAccount().address,
            formExtrinsic = {
                call(
                    moduleName = "System",
                    callName = "remark",
                    arguments = mapOf(
                        "remark" to remark.toByteArray()
                    )
                )
            }
        )
    }

    suspend fun getTerms(url: String): String {
        return httpExceptionHandler.wrap { moonbeamApi.getTerms(url) }.also {
            calcHashes(digest.digest(it.encodeToByteArray()))
        }
    }

    private suspend fun calcHashes(termsBytes: ByteArray) {
        val account = accountRepository.getSelectedAccount()
        termsHash = termsBytes.toHexString(false)
        termsSigned = accountRepository.signWithAccount(account, termsHash?.encodeToByteArray()!!).toHexString(true)
    }
}
