package jp.co.soramitsu.common.presentation

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kotlinx.parcelize.Parcelize

@Parcelize
data class StakingStoryModel(
    @StringRes val titleRes: Int,
    val iconSymbol: String,
    val elements: List<StoryElement.Staking>
) : Parcelable

@Parcelize
data class StoryGroupModel(val stories: List<StoryElement>) : Parcelable

sealed class StoryElement(
    open val titleRes: Int,
    open val bodyRes: Int
) : Parcelable {

    @Parcelize
    data class Staking(
        @StringRes override val titleRes: Int,
        @StringRes override val bodyRes: Int,
        val url: String
    ) : StoryElement(titleRes, bodyRes)

    @Parcelize
    data class Onboarding(
        @StringRes override val titleRes: Int,
        @StringRes override val bodyRes: Int,
        @DrawableRes val imageRes: Int,
        @StringRes val buttonCaptionRes: Int?
    ) : StoryElement(titleRes, bodyRes)
}
