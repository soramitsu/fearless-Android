package jp.co.soramitsu.splash.presentation

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.splash.FragmentHiltShim
import jp.co.soramitsu.splash.ShimApplication
import jp.co.soramitsu.splash.ShimHostActivity
import jp.co.soramitsu.splash.SplashRouter
import jp.co.soramitsu.splash.idleMainUntil
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ShimApplication::class)
class SplashFragmentTest {
    private val router = mockk<SplashRouter>(relaxed = true)
    private val scenario = mockk<PendulumPreInstalledAccountsScenario> { coEvery { fetchFeatureToggle() } returns Unit }

    @After fun tearDown() = FragmentHiltShim.reset()

    @Test fun showingTheSplashResolvesTheInitialDestination() {
        FragmentHiltShim.viewModel(SplashViewModel::class) { SplashViewModel(router, scenario) }
        val host = Robolectric.buildActivity(ShimHostActivity::class.java).setup().get()
        val fragment = SplashFragment()

        host.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()

        assertNotNull(fragment.view)
        idleMainUntil { runCatching { verify(exactly = 1) { router.openOnboarding() } }.isSuccess }
    }

    @Test fun subscribingStartsTheInitialNavigation() {
        val viewModel = mockk<SplashViewModel>(relaxed = true)

        SplashFragment().subscribe(viewModel)

        verify(exactly = 1) { viewModel.openInitialDestination() }
    }
}
