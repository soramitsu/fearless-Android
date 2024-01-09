package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.astar

import jp.co.soramitsu.crowdloan.impl.domain.contribute.custom.astar.AstarContributeInteractor
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.CustomContributeSubmitter
import jp.co.soramitsu.shared_utils.runtime.extrinsic.ExtrinsicBuilder
import java.math.BigDecimal

class AstarContributeSubmitter(
    private val interactor: AstarContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submitOnChain(payload: BonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {
        require(payload is AstarBonusPayload)

        interactor.submitMemo(payload.parachainId, payload.referralCode, extrinsicBuilder)
    }
}
