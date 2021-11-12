package jp.co.soramitsu.feature_crowdloan_impl.di

import dagger.Module
import dagger.Provides
import javax.inject.Named
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.acala.AcalaApi
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam.MoonbeamApi
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.ParachainMetadataApi
import jp.co.soramitsu.feature_crowdloan_impl.data.repository.CrowdloanRepositoryImpl
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeModule
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.CrowdloanInteractor
import jp.co.soramitsu.feature_crowdloan_impl.storage.CrowdloanStorage
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.TransferValidityChecks
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.TransferValidityChecksProvider
import jp.co.soramitsu.runtime.di.REMOTE_STORAGE_SOURCE
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import jp.co.soramitsu.runtime.repository.ChainStateRepository
import jp.co.soramitsu.runtime.storage.source.StorageDataSource

@Module(
    includes = [
        CustomContributeModule::class
    ]
)
class CrowdloanFeatureModule {

    @Provides
    @FeatureScope
    fun provideCrowdloanStorage(preferences: Preferences) = CrowdloanStorage(preferences)

    @Provides
    @FeatureScope
    fun crowdloanRepository(
        @Named(REMOTE_STORAGE_SOURCE) remoteStorageSource: StorageDataSource,
        crowdloanMetadataApi: ParachainMetadataApi,
        runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
        accountRepository: AccountRepository,
        moonbeamApi: MoonbeamApi,
        crowdloanStorage: CrowdloanStorage
    ): CrowdloanRepository = CrowdloanRepositoryImpl(
        remoteStorageSource,
        accountRepository,
        runtimeProperty,
        crowdloanMetadataApi,
        moonbeamApi,
        crowdloanStorage
    )

    @Provides
    @FeatureScope
    fun provideCrowdloanInteractor(
        accountRepository: AccountRepository,
        crowdloanRepository: CrowdloanRepository,
        chainStateRepository: ChainStateRepository
    ) = CrowdloanInteractor(accountRepository, crowdloanRepository, chainStateRepository)

    @Provides
    @FeatureScope
    fun provideCrowdloanMetadataApi(networkApiCreator: NetworkApiCreator): ParachainMetadataApi {
        return networkApiCreator.create(ParachainMetadataApi::class.java)
    }

    @Provides
    @FeatureScope
    fun provideTransferChecks(): TransferValidityChecks.Presentation = TransferValidityChecksProvider()

    @Provides
    @FeatureScope
    fun provideCrowdloanContributeInteractor(
        extrinsicService: ExtrinsicService,
        feeEstimator: FeeEstimator,
        accountRepository: AccountRepository,
        chainStateRepository: ChainStateRepository,
        crowdloanRepository: CrowdloanRepository,
        walletRepository: WalletRepository,
        moonbeamApi: MoonbeamApi,
        acalaApi: AcalaApi,
        resourceManager: ResourceManager,
    ) = CrowdloanContributeInteractor(
        extrinsicService,
        feeEstimator,
        accountRepository,
        chainStateRepository,
        crowdloanRepository,
        walletRepository,
        moonbeamApi,
        acalaApi,
        resourceManager
    )
}
