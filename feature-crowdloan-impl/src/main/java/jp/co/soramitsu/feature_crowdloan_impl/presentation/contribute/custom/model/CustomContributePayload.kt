package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model

import android.os.Parcelable
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ParachainMetadataParcelModel
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class CustomContributePayload(
    val paraId: ParaId,
    val parachainMetadata: ParachainMetadataParcelModel,
    val amount: BigDecimal,
    val previousBonusPayload: BonusPayload?,
    val step: Int = 0, // used for moonbeam custom flow
    val isPrivacyAccepted: Boolean? = null
) : Parcelable {

    val isMoonbeam: Boolean
        get() = paraId == 2002.toBigInteger()
}
