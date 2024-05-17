package jp.co.soramitsu.onboarding.impl.welcome

import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.domain.AppVersion
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.onboarding.api.data.OnboardingConfig
import jp.co.soramitsu.onboarding.api.domain.OnboardingInteractor
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeFragment.Companion.KEY_PAYLOAD
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SUBSTRATE_BLOCKCHAIN_TYPE = 0

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val router: OnboardingRouter,
    private val appLinksProvider: AppLinksProvider,
    savedStateHandle: SavedStateHandle,
    private val backupService: BackupService,
    private val pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario,
    private val onboardingInteractor: OnboardingInteractor,
    private val accountRepository: AccountRepository
) : BaseViewModel(), Browserable, WelcomeScreenInterface, OnboardingScreenCallback,
    OnboardingSplashScreenClickListener {

    private val payload = savedStateHandle.get<WelcomeFragmentPayload>(KEY_PAYLOAD)!!

    private val _onboardingBackgroundState = MutableStateFlow<String?>(null)
    val onboardingBackground = _onboardingBackgroundState
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _onboardingFlowState = MutableStateFlow<Result<OnboardingFlow>?>(null)
    val onboardingFlowState = _onboardingFlowState.map { it?.getOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state = MutableStateFlow(
        WelcomeState(
            isBackVisible = payload.displayBack,
            preinstalledFeatureEnabled = pendulumPreInstalledAccountsScenario.isFeatureEnabled()
        )
    )

    private val _events = Channel<WelcomeEvent>(
        capacity = Int.MAX_VALUE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.receiveAsFlow()

    override val openBrowserEvent = MutableLiveData<Event<String>>()
    private var currentOnboardingConfigVersion: String? = null

    init {
        payload.createChainAccount?.run {
            when (isImport) {
                true -> router.openImportAccountSkipWelcome(this)
                else -> router.openCreateAccountSkipWelcome(this)
            }
        }

        viewModelScope.launch {
            val remoteOnboardingConfig: OnboardingConfig? = onboardingInteractor.getConfig()
                .onFailure {
                    Log.e("OnboardingScreen", "onboardingInteractor.getConfig() failed: $it")
                    showError(it)
                }.getOrNull()

            val useConfig = getAppVersionSupportedConfig(remoteOnboardingConfig)
            val isAccountSelected = accountRepository.isAccountSelected()
            val shouldShowSlides = useConfig != null
                    && (onboardingInteractor.shouldShowWelcomeSlides(useConfig.minVersion) || isAccountSelected.not())

            currentOnboardingConfigVersion = useConfig?.minVersion

            when {
                isAccountSelected && shouldShowSlides -> {
                    _onboardingFlowState.value = Result.success(OnboardingFlow(useConfig!!.enEn.regular))
                    _onboardingBackgroundState.value = useConfig.background
                    _events.trySend(WelcomeEvent.Onboarding.PagerScreen)
                }
                isAccountSelected -> {
                    moveNextToPincode()
                }
                shouldShowSlides -> {
                    _onboardingFlowState.value = Result.success(OnboardingFlow(useConfig!!.enEn.new))
                    _onboardingBackgroundState.value = useConfig.background
                }
                else -> {
                    _onboardingFlowState.value = Result.failure(IllegalStateException("Onboarding config is empty"))
                }
            }
        }
    }

    private fun getAppVersionSupportedConfig(config: OnboardingConfig?): OnboardingConfig.OnboardingConfigItem? {
        val current = AppVersion.current()
        return config?.configs?.filter {
            val configAppVersion = AppVersion.fromString(it.minVersion)
            configAppVersion.major == current.major && configAppVersion.minor == current.minor
        }?.maxByOrNull {
            AppVersion.fromString(it.minVersion)
        }
    }

    override fun createAccountClicked() {
        router.openCreateAccountFromOnboarding()
    }

    override fun googleSigninClicked() {
        _events.trySend(WelcomeEvent.AuthorizeGoogle)
    }

    override fun getPreInstalledWalletClicked() {
        _events.trySend(WelcomeEvent.ScanQR)
    }

    override fun importAccountClicked() {
        router.openSelectImportModeForResult()
            .onEach(::handleSelectedImportMode)
            .launchIn(viewModelScope)
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
            try {
                backupService.logout()
                if (backupService.authorize(launcher)) {
                    openAddWalletThroughGoogleScreen()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError(e)
            }
        }
    }

    override fun termsClicked() {
        openBrowserEvent.value = Event(appLinksProvider.termsUrl)
    }

    override fun privacyClicked() {
        openBrowserEvent.value = Event(appLinksProvider.privacyUrl)
    }

    fun openAddWalletThroughGoogleScreen() {
        router.openImportRemoteWalletDialog()
    }

    fun onGoogleLoginError(message: String?) {
        showError("GoogleLoginError\n$message")
    }

    override fun backClicked() {
        router.back()
    }

    fun onQrScanResult(result: String?) {
        if (result == null) {
            showError("Can't scan qr code")
            return
        }

        viewModelScope.launch {
            pendulumPreInstalledAccountsScenario.import(result)
                .onFailure {
                    showError(it)
                }
                .onSuccess {
                    router.openCreatePincode()
                }
        }
    }

    override fun onStart() {
        if (_onboardingFlowState.value?.isFailure == true) {
            _events.trySend(WelcomeEvent.Onboarding.WelcomeScreen)
        } else {
            _events.trySend(WelcomeEvent.Onboarding.PagerScreen)
        }
    }

    override fun onClose() {
        viewModelScope.launch {
            if (accountRepository.isAccountSelected()) {
                moveNextToPincode()
            } else {
                _events.trySend(WelcomeEvent.Onboarding.WelcomeScreen)
            }
        }
        currentOnboardingConfigVersion?.let {
            onboardingInteractor.saveWelcomeSlidesShownVersion(it)
            currentOnboardingConfigVersion = null
        }
    }

    override fun onSkip() {
        onClose()
    }

    private suspend fun moveNextToPincode() {
        if (accountRepository.isCodeSet()) {
            router.openInitialCheckPincode()
        } else {
            router.openCreatePincode()
        }
    }
}
