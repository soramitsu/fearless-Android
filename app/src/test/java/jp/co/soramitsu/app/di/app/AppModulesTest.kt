package jp.co.soramitsu.app.di.app

import android.content.Context
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.app.root.di.RootFeatureModule
import jp.co.soramitsu.app.root.domain.AppInitializer
import jp.co.soramitsu.app.root.domain.ProductionAssetDiscoverySweep
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.app.root.presentation.di.RootActivityModule
import jp.co.soramitsu.app.root.presentation.stories.di.StoryModule
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app graph hands every feature the one Navigator, and builds its root services from their collaborators. */
class AppModulesTest {
    private val navigation = NavigationModule()

    @Test
    fun everyFeatureRouterIsTheSingleNavigator() {
        val navigator = navigation.provideNavigator(mockk(relaxed = true))
        val routers = listOf<Any>(
            navigation.provideSplashRouter(navigator),
            navigation.provideOnboardingRouter(navigator),
            navigation.provideAccountRouter(navigator),
            navigation.provideWalletRouter(navigator),
            navigation.providePolkaswapRouter(navigator),
            navigation.providePolkamarktRouter(navigator),
            navigation.provideSuccessRouter(navigator),
            navigation.provideStakingRouter(navigator),
            navigation.provideWalletConnectRouter(navigator),
            navigation.provideTonConnectRouter(navigator),
            navigation.provideNFTRouter(navigator),
            navigation.provideLiquidityPoolsRouter(navigator),
            RootActivityModule().provideRootRouter(navigator)
        )
        routers.forEach { assertSame(navigator, it) }
    }

    @Test
    fun storiesFinishThroughTheNavigator() {
        val navigator = mockk<Navigator>(relaxed = true)
        StoryModule().provideBackTransition(navigator).invoke()
        verify(exactly = 1) { navigator.educationalStoriesCompleted() }
    }

    @Test
    fun theApplicationContextIsProvidedAsIs() {
        val context = mockk<Context>()
        assertSame(context, AppModule().provideContext(context))
    }

    @Test
    fun rootServicesAreBuiltFromTheirCollaborators() {
        val module = RootFeatureModule()
        assertNotSame(module.provideRootInteractor(), module.provideRootInteractor())
        val sweep = mockk<ProductionAssetDiscoverySweep>()
        assertSame(sweep, module.provideAssetDiscoveryService(sweep))
        val initializer = module.provideAppInitializer(
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true)
        )
        assertTrue(initializer is AppInitializer)
    }
}
