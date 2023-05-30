package jp.co.soramitsu.onboarding.impl.welcome

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_onboarding_impl.BuildConfig
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeFragment.Companion.KEY_PAYLOAD
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

private const val SUBSTRATE_BLOCKCHAIN_TYPE = 0

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val router: OnboardingRouter,
    private val appLinksProvider: AppLinksProvider,
    private val savedStateHandle: SavedStateHandle,
    private val backupService: BackupService
) : BaseViewModel(), Browserable {

    private val payload = savedStateHandle.get<WelcomeFragmentPayload>(KEY_PAYLOAD)!!

    val shouldShowBackLiveData: LiveData<Boolean> = MutableLiveData(payload.displayBack)

    private val _events = MutableSharedFlow<WelcomeEvent>()
    val events = _events.asSharedFlow()

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
        router.openCreateAccountFromOnboarding()
    }

    fun importAccountClicked() {
        if (BuildConfig.DEBUG) {
            router.openSelectImportModeForResult()
                .onEach(::handleSelectedImportMode)
                .launchIn(viewModelScope)
        } else {
            router.openImportAccountScreen(
                blockChainType = SUBSTRATE_BLOCKCHAIN_TYPE,
                importMode = ImportMode.MnemonicPhrase
            )
        }
    }

    private fun handleSelectedImportMode(importMode: ImportMode) {
        if (importMode == ImportMode.Google) {
            _events.tryEmit(WelcomeEvent.AuthorizeGoogle)
        } else {
            router.openImportAccountScreen(
                blockChainType = SUBSTRATE_BLOCKCHAIN_TYPE,
                importMode = importMode
            )
        }
    }

    fun authorizeGoogle(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        backupService.authorize(
            activity = activity,
            launcher = launcher
        )
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
