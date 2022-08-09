package jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.interlay

import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.custom.interlay.InterlayContributeInteractor
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeSubmitter
import java.math.BigDecimal

class InterlayContributeSubmitter(
    private val interactor: InterlayContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submitOnChain(payload: BonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {
        require(payload is InterlayBonusPayload)

        interactor.submitMemo(payload.parachainId, payload.referralCode, extrinsicBuilder)
    }
}
