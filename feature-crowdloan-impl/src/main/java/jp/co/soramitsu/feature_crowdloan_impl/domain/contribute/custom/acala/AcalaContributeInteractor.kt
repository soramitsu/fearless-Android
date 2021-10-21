package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.acala

import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.acala.AcalaApi
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaBonusPayload
import java.math.BigDecimal

class AcalaContributeInteractor(
    private val acalaApi: AcalaApi,
    private val httpExceptionHandler: HttpExceptionHandler,
    val fearlessReferralCode: String,
) {

    suspend fun isReferralValid(referralCode: String) =
        httpExceptionHandler.wrap {
            acalaApi.isReferralValid(referralCode).result
        }

    fun submitOffChain(payload: AcalaBonusPayload, amount: BigDecimal): Result<Unit> {
        return Result.success(Unit)
    }

    fun submitOnChain(payload: AcalaBonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {

    }

}
