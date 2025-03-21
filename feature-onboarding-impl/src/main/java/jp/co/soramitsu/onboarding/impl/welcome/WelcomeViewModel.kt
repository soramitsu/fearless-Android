package jp.co.soramitsu.onboarding.impl.welcome

import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.account.api.domain.model.AddAccountPayload
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.onboarding.api.domain.OnboardingInteractor
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeFragment.Companion.KEY_PAYLOAD
import jp.co.soramitsu.shared_utils.encrypt.mnemonic.Mnemonic
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
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val router: OnboardingRouter,
    private val appLinksProvider: AppLinksProvider,
    savedStateHandle: SavedStateHandle,
    private val backupService: BackupService,
    private val pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario,
    private val onboardingInteractor: OnboardingInteractor,
    private val accountRepository: AccountRepository,
    private val interactor: AccountInteractor
) : BaseViewModel(), Browserable, WelcomeScreenInterface, OnboardingScreenCallback,
    OnboardingSplashScreenClickListener, SelectEcosystemScreenCallbacks {

    private val payload = savedStateHandle.get<WelcomeFragmentPayload>(KEY_PAYLOAD)!!

    private val _onboardingBackgroundState = MutableStateFlow<String?>(null)
    val onboardingBackground = _onboardingBackgroundState
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _onboardingFlowState = MutableStateFlow<Result<OnboardingFlow>?>(null)
    val onboardingFlowState = _onboardingFlowState.map { it?.getOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val showLoading = MutableStateFlow(false)
    val state = showLoading.map {
        WelcomeState(
            isLoading = it,
            isBackVisible = payload.displayBack,
            preinstalledFeatureEnabled = pendulumPreInstalledAccountsScenario.isFeatureEnabled()
        )
    }.stateIn(this, SharingStarted.Eagerly, WelcomeState(
        isLoading = false,
        isBackVisible = payload.displayBack,
        preinstalledFeatureEnabled = pendulumPreInstalledAccountsScenario.isFeatureEnabled()
    ))

    private val _events = Channel<WelcomeEvent>(
        capacity = Int.MAX_VALUE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events = _events.receiveAsFlow()

    val startDestination = payload.route ?: WelcomeEvent.Onboarding.SplashScreen.route

    override val openBrowserEvent = MutableLiveData<Event<String>>()
    private var currentOnboardingConfigVersion: String? = null

    init {
        viewModelScope.launch {
            val isAccountSelected = accountRepository.isAccountSelected()

            val useConfig = onboardingInteractor.getAppVersionSupportedConfig()
                .onFailure {
                    Log.e("OnboardingScreen", "onboardingInteractor.getConfig() failed: $it")
                    showError(it)
                }.getOrNull()

            val shouldShowSlides = useConfig != null
                    && (onboardingInteractor.shouldShowWelcomeSlides(useConfig.minVersion) || isAccountSelected.not())

            currentOnboardingConfigVersion = useConfig?.minVersion

            when {
                payload.route != null -> {
                    Unit // skip
                }
                isAccountSelected && shouldShowSlides -> {
                    _onboardingFlowState.value =
                        Result.success(OnboardingFlow(useConfig!!.enEn.regular))
                    _onboardingBackgroundState.value = useConfig.background
                    _events.trySend(WelcomeEvent.Onboarding.PagerScreen)
                }

                isAccountSelected -> {
                    moveNextToPincode()
                }

                shouldShowSlides -> {
                    _onboardingFlowState.value =
                        Result.success(OnboardingFlow(useConfig!!.enEn.new))
                    _onboardingBackgroundState.value = useConfig.background
                    _events.trySend(WelcomeEvent.Onboarding.PagerScreen)
                }

                !isAccountSelected -> {
                    _events.trySend(WelcomeEvent.Onboarding.SelectEcosystemScreen)
                }

                else -> {
                    _onboardingFlowState.value =
                        Result.failure(IllegalStateException("Onboarding config is empty"))
                }
            }
        }
    }

    override fun createAccountClicked(accountType: AccountType) {
        when (accountType) {
            AccountType.SubstrateOrEvm -> router.openCreateAccountFromOnboarding(accountType)
            AccountType.Ton -> silentCreateTonAccount()
        }
    }

    override fun googleSigninClicked() {
        _events.trySend(WelcomeEvent.AuthorizeGoogle)
    }

    override fun getPreInstalledWalletClicked() {
        _events.trySend(WelcomeEvent.ScanQR)
    }


    override fun importAccountClicked(accountType: AccountType) {
        when (accountType) {
            AccountType.SubstrateOrEvm -> {
                router.openSelectImportModeForResult()
                    .onEach(::handleSelectedImportMode)
                    .launchIn(viewModelScope)
            }

            AccountType.Ton -> router.openImportAccountScreen(
                walletEcosystem = WalletEcosystem.Ton,
                importMode = ImportMode.MnemonicPhrase
            )
        }
    }

    private fun handleSelectedImportMode(importMode: ImportMode) {
        if (importMode == ImportMode.Google) {
            _events.trySend(WelcomeEvent.AuthorizeGoogle)
        } else {
            router.openImportAccountScreen(
                walletEcosystem = WalletEcosystem.Substrate,
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

    override fun substrateEvmClick() {
        _events.trySend(WelcomeEvent.Onboarding.WelcomeScreen(AccountType.SubstrateOrEvm))
    }

    override fun tonClick() {
        _events.trySend(WelcomeEvent.Onboarding.WelcomeScreen(AccountType.Ton))
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
        _events.trySend(WelcomeEvent.Back)
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
            _events.trySend(WelcomeEvent.Onboarding.SelectEcosystemScreen)
        } else {
            _events.trySend(WelcomeEvent.Onboarding.PagerScreen)
        }
    }

    override fun onClose() {
        viewModelScope.launch {
            if (accountRepository.isAccountSelected()) {
                moveNextToPincode()
            } else {
                _events.trySend(WelcomeEvent.Onboarding.SelectEcosystemScreen)
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

    private fun silentCreateTonAccount() {
        showLoading.value = true
        launch {
            val mnemonic = interactor.generateMnemonic(Mnemonic.Length.TWENTY_FOUR)
            val mnemonicWords = mnemonic.joinToString(" ")
            val createTonAccountPayload = AddAccountPayload.Ton(
                accountName = "Wallet",
                mnemonic = mnemonicWords,
                isBackedUp = false
            )
            val result = interactor.createAccount(createTonAccountPayload)
            if (result.isSuccess) {
                continueBasedOnCodeStatus()
            } else {
                showError(result.requireException())
                showLoading.value = false
            }
        }
    }

    private suspend fun continueBasedOnCodeStatus() {
        if (interactor.isCodeSet()) {
            router.openMain()
        } else {
            router.openCreatePincode()
        }
    }
}
