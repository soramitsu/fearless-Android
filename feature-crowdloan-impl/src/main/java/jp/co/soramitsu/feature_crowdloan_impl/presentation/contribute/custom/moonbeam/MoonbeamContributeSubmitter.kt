package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam

import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.moonbeam.MoonbeamContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeSubmitter
import java.math.BigDecimal

class MoonbeamContributeSubmitter(
    private val interactor: MoonbeamContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submitOffChain(payload: BonusPayload, amount: BigDecimal): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun submitOnChain(payload: BonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {
        require(payload is MoonbeamBonusPayload)
        interactor.submitMemo(payload.parachainId, payload.referralCode, extrinsicBuilder)
    }
}
