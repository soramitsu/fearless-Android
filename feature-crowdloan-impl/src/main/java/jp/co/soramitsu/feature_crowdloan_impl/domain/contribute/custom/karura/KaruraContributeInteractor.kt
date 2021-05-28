package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.karura

import jp.co.soramitsu.common.base.BaseException
import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.karura.KaruraApi

class KaruraContributeInteractor(
    private val karuraApi: KaruraApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    private val referralCode: String,
) {

    suspend fun isReferralValid(referralCode: String) = try {
        httpExceptionHandler.wrap {
            karuraApi.isReferralValid(referralCode).result
        }
    } catch (e: BaseException) {
        if (e.kind == BaseException.Kind.HTTP) {
            false
        } else {
            throw e
        }
    }

    fun fearlessReferralCode(): String {
        return referralCode
    }
}
