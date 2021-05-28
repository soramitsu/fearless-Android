package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.karura

import jp.co.soramitsu.common.base.BaseException
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.REAL_ROCOCO_ADDRESS_BYTE
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.currentNetworkType
import jp.co.soramitsu.feature_account_api.domain.interfaces.signWithAccount
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.karura.KaruraApi
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.karura.VerifyKaruraParticipationRequest
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import java.math.BigDecimal

class KaruraContributeInteractor(
    private val karuraApi: KaruraApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val accountRepository: AccountRepository,
    private val referralCode: String,
) {

    suspend fun registerInBonusProgram(referralCode: String, amount: BigDecimal): Result<Unit> = runCatching {
        httpExceptionHandler.wrap {
            val selectedAccount = accountRepository.getSelectedAccount()

            val networkType = selectedAccount.network.type

            val accountAddress = when (networkType) {
                // we use fake address byte on rococo, so fix address with real one
                Node.NetworkType.ROCOCO -> selectedAccount.address.toAccountId().toAddress(REAL_ROCOCO_ADDRESS_BYTE)
                else -> selectedAccount.address
            }

            val token = Token.Type.fromNetworkType(networkType)

            val amountInPlanks = token.planksFromAmount(amount)

            val baseUrl = KaruraApi.getBaseUrl(networkType)

            val statement = karuraApi.getStatement(baseUrl).statement

            val signature = accountRepository.signWithAccount(selectedAccount, statement.toByteArray())

            val request = VerifyKaruraParticipationRequest(
                address = accountAddress,
                amount = amountInPlanks,
                referral = referralCode,
                signature = signature.toHexString(withPrefix = true)
            )

            karuraApi.applyForBonus(baseUrl, request)
        }
    }

    suspend fun isReferralValid(referralCode: String) = try {
        val networkType = accountRepository.currentNetworkType()

        httpExceptionHandler.wrap {
            karuraApi.isReferralValid(KaruraApi.getBaseUrl(networkType), referralCode).result
        }
    } catch (e: BaseException) {
        if (e.kind == BaseException.Kind.HTTP) {
            false // karura api return an error http code for some invalid codes, so catch it here
        } else {
            throw e
        }
    }

    fun fearlessReferralCode(): String {
        return referralCode
    }
}
