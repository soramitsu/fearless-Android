package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala

import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.acala.AcalaContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeSubmitter
import java.math.BigDecimal

class AcalaContributeSubmitter(
    private val interactor: AcalaContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submitOnChain(payload: BonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {
        require(payload is AcalaBonusPayload)

        interactor.submitOnChain(payload, amount, extrinsicBuilder)
    }

    override suspend fun submitOffChain(payload: BonusPayload, amount: BigDecimal): Result<Unit> {
        require(payload is AcalaBonusPayload)

        return interactor.submitOffChain(payload, amount)
    }
}
