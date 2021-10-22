package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar

import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.astar.AstarContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeSubmitter
import java.math.BigDecimal

class AstarContributeSubmitter(
    private val interactor: AstarContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submitOnChain(payload: BonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {
        require(payload is AstarBonusPayload)

        interactor.submitOnChain(payload, amount, extrinsicBuilder)
    }

    override suspend fun submitOffChain(payload: BonusPayload, amount: BigDecimal): Result<Unit> {
        require(payload is AstarBonusPayload)

        return interactor.submitOffChain(payload, amount)
    }
}
