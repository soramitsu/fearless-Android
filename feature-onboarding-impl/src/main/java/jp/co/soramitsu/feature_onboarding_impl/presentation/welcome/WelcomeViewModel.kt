package jp.co.soramitsu.feature_onboarding_impl.presentation.welcome

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter

class WelcomeViewModel(
    payload: WelcomeFragmentPayload,
    private val router: OnboardingRouter,
    private val appLinksProvider: AppLinksProvider
) : BaseViewModel(), Browserable {

    companion object {
        private const val SUBSTRATE_BLOCKCHAIN_TYPE = 0
    }

    val shouldShowBackLiveData: LiveData<Boolean> = MutableLiveData(payload.displayBack)

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    init {
        payload.createChainAccount?.run {
            when (isImport) {
                true -> router.openImportAccountSkipWelcome(this)
                else -> router.openCreateAccountSkipWelcome(this)
            }
        }
    }

    fun createAccountClicked() {
        router.openCreateAccount()
    }

    fun importAccountClicked() {
        router.openImportAccountScreen(SUBSTRATE_BLOCKCHAIN_TYPE)
    }

    fun termsClicked() {
        openBrowserEvent.value = Event(appLinksProvider.termsUrl)
    }

    fun privacyClicked() {
        openBrowserEvent.value = Event(appLinksProvider.privacyUrl)
    }

    fun backClicked() {
        router.back()
    }
}
