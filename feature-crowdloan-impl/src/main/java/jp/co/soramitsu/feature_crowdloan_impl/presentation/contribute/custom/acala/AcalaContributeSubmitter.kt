package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala

import java.math.BigDecimal
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.acala.AcalaContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeSubmitter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ParachainMetadataParcelModel

class AcalaContributeSubmitter(
    private val interactor: AcalaContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submitOnChain(payload: BonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {
        require(payload is AcalaBonusPayload)

        interactor.submitRemark(payload, extrinsicBuilder)
    }

    override suspend fun submitOffChain(payload: BonusPayload, amount: BigDecimal, metadata: ParachainMetadataParcelModel?): Result<Unit> {
        require(payload is AcalaBonusPayload)

        return when {
            metadata?.flow?.data?.baseUrl == null || metadata.flow.data.apiKey == null -> Result.failure(Exception("Empty required parameters"))
            else -> interactor.submitOffChain(payload, amount, metadata.flow.data.baseUrl, metadata.flow.data.apiKey)
        }
    }
}
