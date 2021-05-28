package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel

import android.os.Parcelable
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ParachainMetadataParcelModel
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class ConfirmContributePayload(
    val paraId: ParaId,
    val fee: BigDecimal,
    val amount: BigDecimal,
    val bonusPayload: BonusPayload?,
    val metadata: ParachainMetadataParcelModel?,
    val estimatedRewardDisplay: String?
) : Parcelable
