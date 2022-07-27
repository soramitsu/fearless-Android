package jp.co.soramitsu.common.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

sealed class StoryGroup(
    open val elements: List<Story>
) {

    data class Staking(
        @StringRes val titleRes: Int,
        val iconSymbol: String,
        override val elements: List<Story.Staking>
    ) : StoryGroup(elements)

    data class Onboarding(override val elements: List<Story.Onboarding>) : StoryGroup(elements)

    sealed class Story(
        open val titleRes: Int,
        open val bodyRes: Int
    ) {
        data class Staking(
            @StringRes override val titleRes: Int,
            @StringRes override val bodyRes: Int,
            val url: String? = null
        ) : Story(titleRes, bodyRes)

        data class Onboarding(
            @StringRes override val titleRes: Int,
            @StringRes override val bodyRes: Int,
            @DrawableRes val image: Int,
            @StringRes val buttonCaptionRes: Int?
        ) : Story(titleRes, bodyRes)
    }
}
