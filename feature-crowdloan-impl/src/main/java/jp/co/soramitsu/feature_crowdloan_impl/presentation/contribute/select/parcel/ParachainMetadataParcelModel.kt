package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel

import android.os.Parcelable
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadataFlow
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadataFlowData
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal
import java.util.*

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
) : Parcelable {
    val isMoonbeam: Boolean
        get() = name.toLowerCase(Locale.getDefault()) == "moonbeam"
}

@Parcelize
class ParachainMetadataFlowParcelModel(
    val name: String,
    val data: ParachainMetadataFlowDataParcelModel
) : Parcelable

@Parcelize
class ParachainMetadataFlowDataParcelModel(
    val apiUrl: String,
    val apiKey: String,
    val termsUrl: String,
) : Parcelable {
    val baseUrl = apiUrl.replace("https://", "")
}

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
        apiUrl = apiUrl,
        apiKey = apiKey,
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
        apiUrl = apiUrl,
        apiKey = apiKey,
        termsUrl = termsUrl
    )
}
