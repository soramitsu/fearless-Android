package jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.bifrost

import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.custom.bifrost.BifrostContributeInteractor
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeSubmitter
import java.math.BigDecimal

class BifrostContributeSubmitter(
    private val interactor: BifrostContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submitOnChain(payload: BonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {
        require(payload is BifrostBonusPayload)

        interactor.submitOnChain(payload.parachainId, payload.referralCode, extrinsicBuilder)
    }
}
