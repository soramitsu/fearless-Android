package jp.co.soramitsu.feature_staking_impl.presentation.staking.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class StakingStoryModel(
    val title: String,
    val iconSymbol: String,
    val elements: List<Element>
) : Parcelable {

    @Parcelize
    class Element(
        val title: String,
        val body: String,
        val url: String
    ) : Parcelable
}
