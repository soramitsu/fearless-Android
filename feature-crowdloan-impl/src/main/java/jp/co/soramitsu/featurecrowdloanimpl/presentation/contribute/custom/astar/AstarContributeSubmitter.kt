package jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.astar

import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.custom.astar.AstarContributeInteractor
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeSubmitter
import java.math.BigDecimal

class AstarContributeSubmitter(
    private val interactor: AstarContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submitOnChain(payload: BonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {
        require(payload is AstarBonusPayload)

        interactor.submitMemo(payload.parachainId, payload.referralCode, extrinsicBuilder)
    }
}
