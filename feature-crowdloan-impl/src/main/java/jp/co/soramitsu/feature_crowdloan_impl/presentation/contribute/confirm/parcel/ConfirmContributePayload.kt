package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel

import android.os.Parcelable
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class ConfirmContributePayload(
    val paraId: ParaId,
    val fee: BigDecimal,
    val amount: BigDecimal,
    val estimatedRewardDisplay: String?
) : Parcelable
