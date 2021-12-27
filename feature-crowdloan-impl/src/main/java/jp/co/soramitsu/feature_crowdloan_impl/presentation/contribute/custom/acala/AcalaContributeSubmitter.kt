package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala

import java.math.BigDecimal
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_API_KEY
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_API_URL
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.custom.acala.AcalaContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeSubmitter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ParachainMetadataParcelModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.getString

class AcalaContributeSubmitter(
    private val interactor: AcalaContributeInteractor
) : CustomContributeSubmitter {

    override suspend fun submitOnChain(payload: BonusPayload, amount: BigDecimal, extrinsicBuilder: ExtrinsicBuilder) {
        require(payload is AcalaBonusPayload)

        interactor.submitRemark(payload, extrinsicBuilder)
    }

    override suspend fun submitOffChain(payload: BonusPayload, amount: BigDecimal, metadata: ParachainMetadataParcelModel?): Result<Unit> {
        require(payload is AcalaBonusPayload)

        val apiUrl = metadata?.flow?.data?.getString(FLOW_API_URL)
        val apiKey = metadata?.flow?.data?.getString(FLOW_API_KEY)
        return when {
            apiUrl == null || apiKey == null -> Result.failure(Exception("Empty required parameters"))
            else -> interactor.submitOffChain(payload, amount, apiUrl, apiKey)
        }
    }
}
