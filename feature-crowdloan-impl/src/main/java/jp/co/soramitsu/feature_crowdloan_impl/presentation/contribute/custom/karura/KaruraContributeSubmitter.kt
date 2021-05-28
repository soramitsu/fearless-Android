package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.karura

import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.karura.KaruraContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeSubmitter
import java.math.BigDecimal

class KaruraContributeSubmitter(
    private val interactor: KaruraContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submit(
        payload: BonusPayload,
        amount: BigDecimal
    ): Result<Unit> {
        require(payload is KaruraBonusPayload)

        return interactor.registerInBonusProgram(payload.referralCode, amount)
    }
}
