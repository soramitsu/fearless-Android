package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam

import java.math.BigDecimal
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.crowdloan.impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.CustomContributeSubmitter

class MoonbeamContributeSubmitter(
    private val interactor: MoonbeamContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submitOnChain(payload: BonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {
        require(payload is MoonbeamBonusPayload)
        interactor.submitMemo(payload.parachainId, payload.referralCode, extrinsicBuilder)
    }
}
