package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.interlay

import jp.co.soramitsu.crowdloan.impl.domain.contribute.custom.interlay.InterlayContributeInteractor
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.CustomContributeSubmitter
import jp.co.soramitsu.shared_utils.runtime.extrinsic.ExtrinsicBuilder
import java.math.BigDecimal

class InterlayContributeSubmitter(
    private val interactor: InterlayContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submitOnChain(payload: BonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {
        require(payload is InterlayBonusPayload)

        interactor.submitMemo(payload.parachainId, payload.referralCode, extrinsicBuilder)
    }
}
