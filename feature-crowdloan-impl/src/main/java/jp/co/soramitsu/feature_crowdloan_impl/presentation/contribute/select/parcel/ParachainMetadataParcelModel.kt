package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel

import android.os.Parcelable
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class ParachainMetadataParcelModel(
    val iconLink: String,
    val name: String,
    val description: String,
    val rewardRate: BigDecimal,
    val website: String,
    val customFlow: String?,
    val token: String
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
        customFlow = customFlow
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
        token = token
    )
}
