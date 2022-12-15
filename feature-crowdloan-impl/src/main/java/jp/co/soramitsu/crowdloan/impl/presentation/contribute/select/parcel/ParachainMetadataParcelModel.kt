package jp.co.soramitsu.crowdloan.impl.presentation.contribute.select.parcel

import android.os.Parcelable
import jp.co.soramitsu.crowdloan.api.data.repository.ParachainMetadata
import jp.co.soramitsu.crowdloan.api.data.repository.ParachainMetadataFlow
import kotlinx.parcelize.Parcelize
import kotlinx.android.parcel.RawValue
import java.math.BigDecimal
import java.util.Locale

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
    val isInterlay: Boolean
        get() = flow?.name?.toLowerCase(Locale.getDefault()) == "interlay"
}

@Parcelize
class ParachainMetadataFlowParcelModel(
    val name: String?,
    val data: Map<String, @RawValue Any?>?
) : Parcelable

fun Map<String, Any?>.getString(key: String) = get(key) as? String
fun Map<String, Any?>.getAsBigDecimal(key: String) = (get(key) as? Double)?.toBigDecimal()

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
