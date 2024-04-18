package jp.co.soramitsu.splash.presentation

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.domain.GetEducationalStoriesUseCase
import jp.co.soramitsu.common.domain.ShouldShowEducationalStoriesUseCase
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.presentation.StoryElement
import jp.co.soramitsu.common.presentation.StoryGroupModel
import jp.co.soramitsu.splash.SplashRouter
import kotlinx.coroutines.launch

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val router: SplashRouter,
    private val repository: AccountRepository,
    shouldShowEducationalStoriesUseCase: ShouldShowEducationalStoriesUseCase,
    private val getEducationalStories: GetEducationalStoriesUseCase,
    private val pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario
) : BaseViewModel() {

    private var shouldShowEducationalStories by shouldShowEducationalStoriesUseCase

    fun checkStories() {
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

    fun openInitialDestination() {
        viewModelScope.launch {
            pendulumPreInstalledAccountsScenario.fetchFeatureToggle()
            if (repository.isAccountSelected()) {
                if (repository.isCodeSet()) {
                    router.openInitialCheckPincode()
                } else {
                    router.openCreatePincode()
                }
            } else {
                router.openAddFirstAccount()
            }
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
