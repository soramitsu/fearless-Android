package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.karura

import jp.co.soramitsu.common.base.BaseException
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.karura.KaruraApi
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import java.math.BigDecimal

class KaruraContributeInteractor(
    private val karuraApi: KaruraApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val accountRepository: AccountRepository,
) {

    // TODO crowdloan
    suspend fun registerInBonusProgram(referralCode: String, amount: BigDecimal): Result<Unit> = runCatching {
        httpExceptionHandler.wrap {
//            val selectedAccount = accountRepository.getSelectedMetaAccount()
//
//            val accountAddress = when (networkType) {
//                // karura backend requires kusama address even in rococo
//                Node.NetworkType.ROCOCO -> selectedAccount.address.toAccountId().toAddress(Node.NetworkType.KUSAMA)
//                else -> selectedAccount.address
//            }
//
//            val (chain, chainAsset) = crowdloanSharedState.chainAndAsset()
//
//            val amountInPlanks = chainAsset.planksFromAmount(amount)
//
//            val baseUrl = KaruraApi.getBaseUrl(networkType)
//
//            val statement = karuraApi.getStatement(baseUrl).statement
//
//            val signature = accountRepository.signWithAccount(selectedAccount, statement.toByteArray())
//
//            val request = VerifyKaruraParticipationRequest(
//                address = accountAddress,
//                amount = amountInPlanks,
//                referral = referralCode,
//                signature = signature.toHexString(withPrefix = true)
//            )
//
//            karuraApi.applyForBonus(baseUrl, request)
        }
    }

    suspend fun isReferralValid(referralCode: String, chainId: ChainId) = try {
        httpExceptionHandler.wrap {
            karuraApi.isReferralValid(KaruraApi.getBaseUrl(chainId), referralCode).result
        }
    } catch (e: BaseException) {
        if (e.kind == BaseException.Kind.HTTP) {
            false // karura api return an error http code for some invalid codes, so catch it here
        } else {
            throw e
        }
    }
}
