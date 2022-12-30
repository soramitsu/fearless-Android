package jp.co.soramitsu.polkaswap.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.impl.domain.PolkaswapInteractorImpl

@InstallIn(SingletonComponent::class)
@Module
interface PolkaswapFeatureBindModule {
    @Binds
    fun bindsPolkaswapInteractor(polkaswapInteractor: PolkaswapInteractorImpl): PolkaswapInteractor
}
