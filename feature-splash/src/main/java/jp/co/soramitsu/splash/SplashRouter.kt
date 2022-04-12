package jp.co.soramitsu.splash

import jp.co.soramitsu.common.navigation.SecureRouter
import jp.co.soramitsu.common.presentation.StoryGroupModel
import kotlinx.coroutines.flow.Flow

interface SplashRouter : SecureRouter {

    val educationalStoriesCompleted: Flow<Boolean>

    fun openAddFirstAccount()

    fun openCreatePincode()

    fun openInitialCheckPincode()

    fun openEducationalStories(stories: StoryGroupModel)
}
