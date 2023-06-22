package jp.co.soramitsu.onboarding.impl.welcome

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.feature_onboarding_impl.BuildConfig
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeFragment.Companion.KEY_PAYLOAD
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val SUBSTRATE_BLOCKCHAIN_TYPE = 0

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val router: OnboardingRouter,
    private val appLinksProvider: AppLinksProvider,
    savedStateHandle: SavedStateHandle,
    private val backupService: BackupService
) : BaseViewModel(), Browserable {

    private val payload = savedStateHandle.get<WelcomeFragmentPayload>(KEY_PAYLOAD)!!

    val shouldShowBackLiveData: LiveData<Boolean> = MutableLiveData(payload.displayBack)

    private val _events = Channel<WelcomeEvent>(
        capacity = Int.MAX_VALUE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.receiveAsFlow()

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
            _events.trySend(WelcomeEvent.AuthorizeGoogle)
        } else {
            router.openImportAccountScreen(
                blockChainType = SUBSTRATE_BLOCKCHAIN_TYPE,
                importMode = importMode
            )
        }
    }

    fun authorizeGoogle(launcher: ActivityResultLauncher<Intent>) {
        viewModelScope.launch {
            val isAuthorized = backupService.authorize(launcher)
            if (isAuthorized) {
                openAddWalletThroughGoogleScreen()
            }
        }
    }

    fun termsClicked() {
        openBrowserEvent.value = Event(appLinksProvider.termsUrl)
    }

    fun privacyClicked() {
        openBrowserEvent.value = Event(appLinksProvider.privacyUrl)
    }

    private suspend fun openAddWalletThroughGoogleScreen() {
        if (backupService.getBackupAccounts().isEmpty()) {
            router.openCreateWalletDialog()
        } else {
            router.openImportRemoteWalletDialog()
        }
    }

    fun onGoogleLoginError() {
        // TODO: Login error
    }

    fun backClicked() {
        router.back()
    }
}
