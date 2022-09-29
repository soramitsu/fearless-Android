package jp.co.soramitsu.crowdloan.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.account.api.extrinsic.ExtrinsicService
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.crowdloan.api.data.repository.CrowdloanRepository
import jp.co.soramitsu.crowdloan.impl.data.CrowdloanSharedState
import jp.co.soramitsu.crowdloan.impl.data.network.api.acala.AcalaApi
import jp.co.soramitsu.crowdloan.impl.data.network.api.moonbeam.MoonbeamApi
import jp.co.soramitsu.crowdloan.impl.data.network.api.parachain.ParachainMetadataApi
import jp.co.soramitsu.crowdloan.impl.data.repository.CrowdloanRepositoryImpl
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.CustomContributeModule
import jp.co.soramitsu.crowdloan.impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.crowdloan.impl.domain.main.CrowdloanInteractor
import jp.co.soramitsu.crowdloan.impl.storage.CrowdloanStorage
import jp.co.soramitsu.wallet.api.domain.AssetUseCase
import jp.co.soramitsu.wallet.impl.domain.TokenUseCase
import jp.co.soramitsu.wallet.impl.domain.implementations.AssetUseCaseImpl
import jp.co.soramitsu.wallet.impl.domain.implementations.TokenUseCaseImpl
import jp.co.soramitsu.wallet.impl.domain.interfaces.TokenRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.api.presentation.mixin.assetSelector.AssetSelectorFactory
import jp.co.soramitsu.wallet.api.presentation.mixin.assetSelector.AssetSelectorMixin
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderProvider
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import jp.co.soramitsu.runtime.storage.source.StorageDataSource

@InstallIn(SingletonComponent::class)
@Module(
    includes = [
        CustomContributeModule::class
    ]
)
class CrowdloanFeatureModule {

    @Provides
    fun provideCrowdloanStorage(preferences: Preferences) = CrowdloanStorage(preferences)

    @Provides
    @Singleton
    @Named("CrowdloanAssetUseCase")
    fun provideAssetUseCase(
        walletRepository: WalletRepository,
        accountRepository: AccountRepository,
        sharedState: CrowdloanSharedState
    ): AssetUseCase = AssetUseCaseImpl(
        walletRepository,
        accountRepository,
        sharedState
    )

    @Provides
    @Named("CrowdloanAssetSelector")
    fun provideAssetSelectorMixinFactory(
        @Named("CrowdloanAssetUseCase") assetUseCase: AssetUseCase,
        singleAssetSharedState: CrowdloanSharedState,
        resourceManager: ResourceManager
    ): AssetSelectorMixin.Presentation.Factory = AssetSelectorFactory(
        assetUseCase,
        singleAssetSharedState,
        resourceManager
    )

    @Provides
    @Singleton
    @Named("CrowdloanTokenUseCase")
    fun provideTokenUseCase(
        tokenRepository: TokenRepository,
        sharedState: CrowdloanSharedState
    ): TokenUseCase = TokenUseCaseImpl(
        tokenRepository,
        sharedState
    )

    @Provides
    @Singleton
    @Named("CrowdloanFeeLoader")
    fun provideFeeLoaderMixin(
        resourceManager: ResourceManager,
        @Named("CrowdloanTokenUseCase") tokenUseCase: TokenUseCase
    ): FeeLoaderMixin.Presentation = FeeLoaderProvider(
        resourceManager,
        tokenUseCase
    )

    @Provides
    fun provideCrowdloanSharedState(
        chainRegistry: ChainRegistry,
        preferences: Preferences
    ) = CrowdloanSharedState(chainRegistry, preferences)

    @Provides
    fun crowdloanRepository(
        @Named(REMOTE_STORAGE_SOURCE) remoteStorageSource: StorageDataSource,
        crowdloanMetadataApi: ParachainMetadataApi,
        chainRegistry: ChainRegistry,
        moonbeamApi: MoonbeamApi,
        crowdloanStorage: CrowdloanStorage
    ): CrowdloanRepository = CrowdloanRepositoryImpl(
        remoteStorageSource,
        chainRegistry,
        crowdloanMetadataApi,
        moonbeamApi,
        crowdloanStorage
    )

    @Provides
    fun provideCrowdloanInteractor(
        accountRepository: AccountRepository,
        crowdloanRepository: CrowdloanRepository,
        chainStateRepository: ChainStateRepository
    ) = CrowdloanInteractor(
        accountRepository,
        crowdloanRepository,
        chainStateRepository
    )

    @Provides
    fun provideCrowdloanMetadataApi(networkApiCreator: NetworkApiCreator): ParachainMetadataApi {
        return networkApiCreator.create(ParachainMetadataApi::class.java)
    }

    @Provides
    fun provideCrowdloanContributeInteractor(
        extrinsicService: ExtrinsicService,
        accountRepository: AccountRepository,
        chainRegistry: ChainRegistry,
        chainStateRepository: ChainStateRepository,
        sharedState: CrowdloanSharedState,
        crowdloanRepository: CrowdloanRepository,
        walletRepository: WalletRepository,
        moonbeamApi: MoonbeamApi,
        acalaApi: AcalaApi,
        resourceManager: ResourceManager
    ) = CrowdloanContributeInteractor(
        extrinsicService,
        accountRepository,
        chainRegistry,
        chainStateRepository,
        sharedState,
        crowdloanRepository,
        walletRepository,
        moonbeamApi,
        acalaApi,
        resourceManager
    )
}
