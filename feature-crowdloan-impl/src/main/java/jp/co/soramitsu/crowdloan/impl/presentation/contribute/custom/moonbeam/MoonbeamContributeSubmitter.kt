package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam

import jp.co.soramitsu.crowdloan.impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.CustomContributeSubmitter
import jp.co.soramitsu.shared_utils.runtime.extrinsic.ExtrinsicBuilder
import java.math.BigDecimal

class MoonbeamContributeSubmitter(
    private val interactor: MoonbeamContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submitOnChain(payload: BonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {
        require(payload is MoonbeamBonusPayload)
        interactor.submitMemo(payload.parachainId, payload.referralCode, extrinsicBuilder)
    }
}
