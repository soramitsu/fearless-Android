package jp.co.soramitsu.feature_onboarding_impl.presentation.welcome

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_onboarding_api.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter

class WelcomeViewModel(
    private val interactor: OnboardingInteractor,
    private val shouldShowBack: Boolean,
    private val router: OnboardingRouter,
    private val selectedNetworkType: Node.NetworkType?
) : BaseViewModel() {
    val shouldShowBackLiveData: LiveData<Boolean> = MutableLiveData(shouldShowBack)

    fun createAccountClicked() {
        router.openCreateAccount(selectedNetworkType)
    }

    fun importAccountClicked() {
        router.openImportAccountScreen(selectedNetworkType)
    }

    fun termsClicked() {
        router.openTermsScreen()
    }

    fun privacyClicked() {
        router.openPrivacyScreen()
    }

    fun backClicked() {
        router.back()
    }
}