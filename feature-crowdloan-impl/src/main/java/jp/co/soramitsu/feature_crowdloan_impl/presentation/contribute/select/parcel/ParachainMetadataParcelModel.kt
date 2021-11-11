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
    val data: ParachainMetadataFlowDataParcelModel?
) : Parcelable

@Parcelize
class ParachainMetadataFlowDataParcelModel(
    val apiUrl: String?,
    val apiKey: String?,
    val bonusUrl: String?,
    val termsUrl: String?,
    val crowdloanInfoUrl: String?,
    val fearlessReferral: String?,
    val totalReward: String?,
) : Parcelable {
    val baseUrl = apiUrl?.removePrefix("https://")
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
        flow = flow?.let { mapParachainMetadataFlowToParcel(it) }
    )
}

fun mapParachainMetadataFlowToParcel(
    flow: ParachainMetadataFlow
) = with(flow) {
    ParachainMetadataFlowParcelModel(
        name = name,
        data = data?.let { mapParachainMetadataFlowDataToParcel(it) }
    )
}

fun mapParachainMetadataFlowDataToParcel(
    flow: ParachainMetadataFlowData
) = with(flow) {
    ParachainMetadataFlowDataParcelModel(
        apiUrl = apiUrl,
        apiKey = apiKey,
        bonusUrl = bonusUrl,
        termsUrl = termsUrl,
        crowdloanInfoUrl = crowdloanInfoUrl,
        fearlessReferral = fearlessReferral,
        totalReward = totalReward
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
        data = data?.let { mapParachainMetadataFlowDataFromParcel(it) }
    )
}

fun mapParachainMetadataFlowDataFromParcel(
    flowDataParcel: ParachainMetadataFlowDataParcelModel
) = with(flowDataParcel) {
    ParachainMetadataFlowData(
        apiUrl = apiUrl,
        apiKey = apiKey,
        bonusUrl = bonusUrl,
        termsUrl = termsUrl,
        crowdloanInfoUrl = crowdloanInfoUrl,
        fearlessReferral = fearlessReferral,
        totalReward = totalReward
    )
}
