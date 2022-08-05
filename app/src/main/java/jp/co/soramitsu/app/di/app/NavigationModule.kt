package jp.co.soramitsu.app.di.app

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.feature_account_impl.presentation.AccountRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_onboarding_impl.OnboardingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
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
    fun provideStakingRouter(navigator: Navigator): StakingRouter = navigator

    @Singleton
    @Provides
    fun provideCrowdloanRouter(navigator: Navigator): CrowdloanRouter = navigator
}
