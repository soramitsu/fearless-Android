package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.data.OnboardingStoriesDataSource

class GetEducationalStoriesUseCase(private val storiesDataSource: OnboardingStoriesDataSource) {
    operator fun invoke() = storiesDataSource.stories
}
