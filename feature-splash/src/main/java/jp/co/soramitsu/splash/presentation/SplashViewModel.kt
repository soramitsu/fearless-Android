package jp.co.soramitsu.splash.presentation

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.domain.GetEducationalStoriesUseCase
import jp.co.soramitsu.common.domain.ShouldShowEducationalStoriesUseCase
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.presentation.StoryElement
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.splash.SplashRouter
import kotlinx.coroutines.launch

class SplashViewModel(
    private val router: SplashRouter,
    private val repository: AccountRepository,
    shouldShowEducationalStoriesUseCase: ShouldShowEducationalStoriesUseCase,
    private val getEducationalStories: GetEducationalStoriesUseCase
) : BaseViewModel() {

    private val _removeSplashBackgroundLiveData = MutableLiveData<Event<Unit>>()
    val removeSplashBackgroundLiveData = _removeSplashBackgroundLiveData

    private var shouldShowEducationalStories by shouldShowEducationalStoriesUseCase

    init {
        checkStories()
    }

    private fun checkStories() {
        launch {
            when {
                repository.isAccountSelected() -> openInitialDestination()
                shouldShowEducationalStories -> {
                    listenForStories()
                    val stories = getEducationalStories().transform()
                    router.openEducationalStories(stories)
                    shouldShowEducationalStories = false
                }
                else -> openInitialDestination()
            }
        }
    }

    private fun StoryGroup.Onboarding.transform() =
        StoryGroupModel(
            this.elements.map {
                StoryElement.Onboarding(it.titleRes, it.bodyRes, it.image, it.buttonCaptionRes)
            }
        )

    private fun openInitialDestination() {
        viewModelScope.launch {
            if (repository.isAccountSelected()) {
                if (repository.isCodeSet()) {
                    router.openInitialCheckPincode()
                } else {
                    router.openCreatePincode()
                }
            } else {
                router.openAddFirstAccount()
            }

            _removeSplashBackgroundLiveData.value = Event(Unit)
        }
    }

    private fun listenForStories() {
        viewModelScope.launch {
            router.educationalStoriesCompleted.collect {
                if (it) {
                    openInitialDestination()
                }
            }
        }
    }
}
