package jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.karura

import java.math.BigDecimal
import jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.custom.karura.KaruraContributeInteractor
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeSubmitter
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.select.parcel.ParachainMetadataParcelModel

class KaruraContributeSubmitter(
    private val interactor: KaruraContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submitOffChain(
        payload: BonusPayload,
        amount: BigDecimal,
        metadata: ParachainMetadataParcelModel?
    ): Result<Unit> {
        require(payload is KaruraBonusPayload)

        return interactor.registerInBonusProgram(payload.referralCode, amount)
    }
}
