package jp.co.soramitsu.feature_wallet_impl.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_impl.data.repository.WalletRepositoryImpl
import jp.co.soramitsu.feature_wallet_impl.domain.WalletInteractorImpl

@Module
class WalletFeatureModule {
    @Provides
    @FeatureScope
    fun provideJsonMapper() = Gson()

    @Provides
    @FeatureScope
    fun provideWalletRepository(): WalletRepository = WalletRepositoryImpl()

    @Provides
    @FeatureScope
    fun provideWalletInteractor(repository: WalletRepository): WalletInteractor = WalletInteractorImpl(repository)
}