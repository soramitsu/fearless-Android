package jp.co.soramitsu.common.data

import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.R

class OnboardingStoriesDataSource {
    val stories: StoryGroup.Onboarding
        get() = StoryGroup.Onboarding(
            listOf(
                StoryGroup.Story.Onboarding(
                    R.string.stories_version2_slide1_title,
                    R.string.stories_version2_slide1_subtitle,
                    R.drawable.background_story_networks,
                    null
                ),
                StoryGroup.Story.Onboarding(
                    R.string.stories_version2_slide2_title,
                    R.string.stories_version2_slide2_subtitle,
                    R.drawable.background_story_wallet,
                    null
                ),
                StoryGroup.Story.Onboarding(
                    R.string.stories_version2_slide3_title,
                    R.string.stories_version2_slide3_subtitle,
                    R.drawable.background_story_networks_2,
                    null
                ),
                StoryGroup.Story.Onboarding(
                    R.string.stories_version2_slide4_title,
                    R.string.stories_version2_slide4_subtitle,
                    R.drawable.background_story_chain_accounts,
                    null
                ),
                StoryGroup.Story.Onboarding(
                    R.string.stories_version2_slide5_title,
                    R.string.stories_version2_slide5_subtitle,
                    R.drawable.background_story_ecosystem,
                    R.string.stories_bottom_close_button
                )
            )
        )
}
