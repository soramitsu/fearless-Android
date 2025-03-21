package jp.co.soramitsu.app.di.app

import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.app.root.navigation.Navigator
import jp.co.soramitsu.crowdloan.impl.presentation.CrowdloanRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsRouter
import jp.co.soramitsu.nft.navigation.NFTRouter
import jp.co.soramitsu.onboarding.impl.OnboardingRouter
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.soracard.api.presentation.SoraCardRouter
import jp.co.soramitsu.splash.SplashRouter
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.success.presentation.SuccessRouter
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRouter
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter

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
    fun provideSuccessRouter(navigator: Navigator): SuccessRouter = navigator

    @Singleton
    @Provides
    fun provideStakingRouter(navigator: Navigator): StakingRouter = navigator

    @Singleton
    @Provides
    fun provideCrowdloanRouter(navigator: Navigator): CrowdloanRouter = navigator

    @Singleton
    @Provides
    fun provideSoraCardRouter(navigator: Navigator): SoraCardRouter = navigator

    @Singleton
    @Provides
    fun provideWalletConnectRouter(navigator: Navigator): WalletConnectRouter = navigator

    @Singleton
    @Provides
    fun provideTonConnectRouter(navigator: Navigator): TonConnectRouter = navigator

    @Singleton
    @Provides
    fun provideNFTRouter(navigator: Navigator): NFTRouter = navigator

    @Singleton
    @Provides
    fun provideLiquidityPoolsRouter(navigator: Navigator): LiquidityPoolsRouter = navigator
}
