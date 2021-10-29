package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel

import android.os.Parcelable
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadataFlow
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadataFlowData
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class ParachainMetadataParcelModel(
    val iconLink: String,
    val name: String,
    val description: String,
    val rewardRate: BigDecimal?,
    val website: String,
    val customFlow: String?,
    val token: String,
    val flow: ParachainMetadataFlowParcelModel?
) : Parcelable

@Parcelize
class ParachainMetadataFlowParcelModel(
    val name: String,
    val data: ParachainMetadataFlowDataParcelModel
) : Parcelable

@Parcelize
class ParachainMetadataFlowDataParcelModel(
    val devApiUrl: String,
    val devApiKey: String,
    val termsUrl: String,
) : Parcelable

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
        customFlow = customFlow,
        flow = flow?.let { mapParachainMetadataFlowToParcel(it) }
    )
}

fun mapParachainMetadataFlowToParcel(
    flow: ParachainMetadataFlow
) = with(flow) {
    ParachainMetadataFlowParcelModel(
        name = name,
        data = mapParachainMetadataFlowDataToParcel(data)
    )
}

fun mapParachainMetadataFlowDataToParcel(
    flow: ParachainMetadataFlowData
) = with(flow) {
    ParachainMetadataFlowDataParcelModel(
        devApiUrl = devApiUrl,
        devApiKey = devApiKey,
        termsUrl = termsUrl
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
        customFlow = customFlow,
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
        data = mapParachainMetadataFlowDataFromParcel(data)
    )
}

fun mapParachainMetadataFlowDataFromParcel(
    flowDataParcel: ParachainMetadataFlowDataParcelModel
) = with(flowDataParcel) {
    ParachainMetadataFlowData(
        devApiUrl = devApiUrl,
        devApiKey = devApiKey,
        termsUrl = termsUrl
    )
}
