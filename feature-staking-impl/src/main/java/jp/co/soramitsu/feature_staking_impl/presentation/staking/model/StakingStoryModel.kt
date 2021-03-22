package jp.co.soramitsu.feature_staking_impl.presentation.staking.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class StakingStoryModel(
    val titleRes: Int,
    val iconSymbol: String,
    val elements: List<Element>
) : Parcelable {

    @Parcelize
    class Element(
        val titleRes: Int,
        val bodyRes: Int,
        val url: String
    ) : Parcelable
}
