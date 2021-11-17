package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel

import android.os.Parcelable
import java.math.BigDecimal
import java.util.Locale
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadataFlow
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.RawValue

@Parcelize
class ParachainMetadataParcelModel(
    val iconLink: String,
    val name: String,
    val description: String,
    val rewardRate: BigDecimal?,
    val website: String,
    val token: String,
    val flow: ParachainMetadataFlowParcelModel?
) : Parcelable {
    val isMoonbeam: Boolean
        get() = name.toLowerCase(Locale.getDefault()) == "moonbeam"
    val isAstar: Boolean
        get() = name.toLowerCase(Locale.getDefault()) == "astar"
    val isAcala: Boolean
        get() = name.toLowerCase(Locale.getDefault()) == "acala"
}

@Parcelize
class ParachainMetadataFlowParcelModel(
    val name: String?,
    val data: Map<String, @RawValue Any?>?
) : Parcelable

fun Map<String, Any?>.getString(key: String) = get(key) as? String

fun mapParachainMetadataToParcel(
    parachainMetadata: ParachainMetadata
) = with(parachainMetadata) {
    ParachainMetadataParcelModel(
        iconLink = iconLink,
        name = name,
        description = description,
        rewardRate = rewardRate,
        website = website,
        token = token,
        flow = flow?.let { mapParachainMetadataFlowToParcel(it) }
    )
}

fun mapParachainMetadataFlowToParcel(
    flow: ParachainMetadataFlow
) = with(flow) {
    ParachainMetadataFlowParcelModel(
        name = name,
        data = data
    )
}

fun mapParachainMetadataFromParcel(
    parcelModel: ParachainMetadataParcelModel
) = with(parcelModel) {
    ParachainMetadata(
        iconLink = iconLink,
        name = name,
        description = description,
        rewardRate = rewardRate,
        website = website,
        token = token,
        flow = flow?.let { mapParachainMetadataFlowFromParcel(it) }
    )
}

fun mapParachainMetadataFlowFromParcel(
    flowParcel: ParachainMetadataFlowParcelModel
) = with(flowParcel) {
    ParachainMetadataFlow(
        name = name,
        data = data
    )
}
