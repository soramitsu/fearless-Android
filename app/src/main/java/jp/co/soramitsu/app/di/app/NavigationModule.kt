package jp.co.soramitsu.app.di.app

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.crowdloan.impl.presentation.CrowdloanRouter
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.splash.SplashRouter
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class NavigationModule {

    @Singleton
    @Provides
    fun provideNavigator(): Navigator = Navigator()

    @Singleton
    @Provides
    fun provideSplashRouter(navigator: Navigator): SplashRouter = navigator

    @Singleton
    @Provides
    fun provideOnboardingRouter(navigator: Navigator): OnboardingRouter = navigator

    @Singleton
    @Provides
    fun provideAccountRouter(navigator: Navigator): AccountRouter = navigator

    @Singleton
    @Provides
    fun provideWalletRouter(navigator: Navigator): WalletRouter = navigator

    @Singleton
    @Provides
    fun providePolkaswapRouter(navigator: Navigator): PolkaswapRouter = navigator

    @Singleton
    @Provides
    fun provideStakingRouter(navigator: Navigator): StakingRouter = navigator

    @Singleton
    @Provides
    fun provideCrowdloanRouter(navigator: Navigator): CrowdloanRouter = navigator
}
