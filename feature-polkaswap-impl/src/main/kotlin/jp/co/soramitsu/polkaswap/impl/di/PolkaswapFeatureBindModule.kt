package jp.co.soramitsu.polkaswap.impl.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.OptionsProvider
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.polkaswap.api.data.PolkaswapRepository
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.impl.data.PolkaswapRepositoryImpl
import jp.co.soramitsu.polkaswap.impl.domain.PolkaswapInteractorImpl
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.xnetworking.basic.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.SoraWalletBlockExplorerInfo
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraRemoteConfigBuilder
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraRemoteConfigProvider
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class PolkaswapFeatureModule {

    @Provides
    fun providePolkaswapRepositoryImpl(
        remoteConfigFetcher: RemoteConfigFetcher,
        @Named(REMOTE_STORAGE_SOURCE) remoteSource: StorageDataSource,
        extrinsicService: ExtrinsicService,
        chainRegistry: ChainRegistry,
        accountRepository: AccountRepository,
    ): PolkaswapRepository {
        return PolkaswapRepositoryImpl(
            remoteConfigFetcher,
            remoteSource,
            extrinsicService,
            chainRegistry,
            accountRepository,
        )
    }

    @Provides
    @Singleton
    fun providePolkaswapInteractor(
        chainRegistry: ChainRegistry,
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        polkaswapRepository: PolkaswapRepository,
        sharedPreferences: Preferences,
        chainsRepository: ChainsRepository,
    ): PolkaswapInteractor {
        return PolkaswapInteractorImpl(
            chainRegistry,
            walletRepository,
            accountRepository,
            polkaswapRepository,
            sharedPreferences,
            chainsRepository
        )
    }

    @Singleton
    @Provides
    fun provideSoraWalletBlockExplorerInfo(
        client: SoramitsuNetworkClient,
        soraRemoteConfigBuilder: SoraRemoteConfigBuilder,
    ): SoraWalletBlockExplorerInfo {
        return SoraWalletBlockExplorerInfo(
            networkClient = client,
            soraRemoteConfigBuilder = soraRemoteConfigBuilder,
        )
    }

    @Singleton
    @Provides
    fun provideSoraRemoteConfigBuilder(
        client: SoramitsuNetworkClient,
        @ApplicationContext context: Context,
    ): SoraRemoteConfigBuilder {
        return SoraRemoteConfigProvider(
            context = context,
            client = client,
            commonUrl = OptionsProvider.soraConfigCommon,
            mobileUrl = OptionsProvider.soraConfigMobile,
        ).provide()
    }

}
