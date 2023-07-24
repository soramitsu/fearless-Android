package jp.co.soramitsu.onboarding.impl.welcome

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import jp.co.soramitsu.onboarding.impl.welcome.WelcomeFragment.Companion.KEY_PAYLOAD
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
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
) : BaseViewModel(), Browserable, WelcomeScreenInterface {

    private val payload = savedStateHandle.get<WelcomeFragmentPayload>(KEY_PAYLOAD)!!

    val state = MutableStateFlow(WelcomeState(isBackVisible = payload.displayBack))

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

    override fun createAccountClicked() {
        router.openCreateAccountFromOnboarding()
    }
    override fun googleSigninClicked() {
        _events.trySend(WelcomeEvent.AuthorizeGoogle)
/*
        viewModelScope.launch {
            if (backupService.authorize(launcher)) {
                _events.trySend(WelcomeEvent.AuthorizeGoogle)
//                onSuccessfulGoogleSignin()
            } else {
//                _tutorialScreenState.value = it.copy(isGoogleSigninLoading = false)
            }
        }
*/
    }

    fun onSuccessfulGoogleSignin(navController: NavController) {
//        _tutorialScreenState.value =
//            _tutorialScreenState.value?.copy(isGoogleSigninLoading = true)

        viewModelScope.launch {
            try {
//                isFromGoogleDrive = true

//                if (navController.currentDestination?.route == OnboardingFeatureRoutes.PASSPHRASE) {
//                    navController.navigate(OnboardingFeatureRoutes.CREATE_BACKUP_PASSWORD)
//                } else {
                val result = getBackupedAccountsFiltered()

//                _tutorialScreenState.value =
//                    _tutorialScreenState.value?.copy(isGoogleSigninLoading = false)

                if (result.isEmpty()) {
                    println("!!! getBackupedAccountsFiltered emptyResults")
//                    navController.navigate(OnboardingFeatureRoutes.CREATE_ACCOUNT)
                } else {
                    println("!!! getBackupedAccountsFiltered results = ${result.size}")

//                    _importAccountListState.value = ImportAccountListScreenState(
//                        accountList = result.map {
//                            BackupAccountMetaWithIcon(
//                                it,
//                                getDrawableFromGoogleBackup(it.address),
//                            )
//                        }
//                    )
//                    navController.navigate(OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST)
                }
//                }
            } catch (e: UnauthorizedException) {
//                _tutorialScreenState.value =
//                    _tutorialScreenState.value?.copy(isGoogleSigninLoading = false)

                showError("GOOGLE_LOGIN_FAILED")
            }
        }
    }

    private suspend fun getBackupedAccountsFiltered(): List<BackupAccountMeta> {
        return backupService.getBackupAccounts()
//            .filter {
//                multiaccountInteractor.isAddressValid(it.address) && !multiaccountInteractor.accountExists(
//                    it.address
//                )
//            }
    }

    override fun importAccountClicked() {
//        if (BuildConfig.DEBUG) {
            router.openSelectImportModeForResult()
                .onEach(::handleSelectedImportMode)
                .launchIn(viewModelScope)
//        } else {
//            router.openImportAccountScreen(
//                blockChainType = SUBSTRATE_BLOCKCHAIN_TYPE,
//                importMode = ImportMode.MnemonicPhrase
//            )
//        }
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
//            val isAuthorized = backupService.authorize(launcher)
//            println("!!! authorizeGoogle isAuthorized = $isAuthorized")
//            if (isAuthorized) {
//                openAddWalletThroughGoogleScreen()
//            }

            try {
                backupService.logout()
                if (backupService.authorize(launcher)) {
                    print("!!! authorizeGoogle isAuthorized = true")
                    openAddWalletThroughGoogleScreen()
                } else {
                    print("!!! authorizeGoogle isAuthorized = false")
                }
            } catch (e: Exception) {
                print("!!! authorizeGoogle error = ${e.message}")
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

    private suspend fun openAddWalletThroughGoogleScreen() {
        if (backupService.getBackupAccounts().isEmpty()) {
            router.openCreateWalletDialog(true)
        } else {
            router.openImportRemoteWalletDialog()
        }
    }

    fun onGoogleLoginError(message: String?) {
        println("!!! onGoogleLoginError")
        showError("GoogleLoginError\n$message")
    }

    override fun backClicked() {
        router.back()
    }
}
