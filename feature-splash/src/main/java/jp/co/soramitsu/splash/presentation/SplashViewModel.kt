package jp.co.soramitsu.splash.presentation

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.splash.SplashRouter
import kotlinx.coroutines.launch

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val router: SplashRouter,
    private val repository: AccountRepository,
    private val pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario
) : BaseViewModel() {

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
}
