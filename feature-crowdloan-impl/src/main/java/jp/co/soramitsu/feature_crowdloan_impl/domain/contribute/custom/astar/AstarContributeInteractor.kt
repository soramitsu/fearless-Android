package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.astar

import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar.AstarBonusPayload
import java.math.BigDecimal

class AstarContributeInteractor(
    private val httpExceptionHandler: HttpExceptionHandler,
    val fearlessReferralCode: String,
) {

    suspend fun isReferralValid(address: String) = true // todo check address is in polkadot

    fun submitOffChain(payload: AstarBonusPayload, amount: BigDecimal): Result<Unit> {
        return Result.success(Unit)
    }

    fun submitOnChain(payload: AstarBonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {
    }
}
