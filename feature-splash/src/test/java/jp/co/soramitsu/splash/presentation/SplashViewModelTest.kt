package jp.co.soramitsu.splash.presentation

import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.splash.SplashRouter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<SplashRouter>(relaxed = true)
    private val scenario = mockk<PendulumPreInstalledAccountsScenario>()
    private lateinit var vm: SplashViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = SplashViewModel(router, scenario)
    }

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun onboardingOpensOnlyAfterTheFeatureToggleIsFetched() = runTest(dispatcher) {
        val toggle = CompletableDeferred<Unit>()
        coEvery { scenario.fetchFeatureToggle() } coAnswers { toggle.await() }

        vm.openInitialDestination(); runCurrent()
        verify(exactly = 0) { router.openOnboarding() }

        toggle.complete(Unit); runCurrent()
        coVerifyOrder {
            scenario.fetchFeatureToggle()
            router.openOnboarding()
        }
    }
}
