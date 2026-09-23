package jp.co.soramitsu.polkamarkt.impl.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.network.config.MutationAuthorizationStore
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.polkamarkt.api.PolkamarktInteractor
import jp.co.soramitsu.polkamarkt.impl.data.PolkamarktIndexer
import jp.co.soramitsu.polkamarkt.impl.data.PolkamarktInteractorImpl
import jp.co.soramitsu.polkamarkt.impl.data.PolkamarktRuntime
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PolkamarktModule {
    @Provides
    @Singleton
    fun providePolkamarktIndexer(client: OkHttpClient, gson: Gson) = PolkamarktIndexer(client, gson)

    @Provides
    @Singleton
    fun providePolkamarktRuntime(chainRegistry: ChainRegistry, bulkRetriever: BulkRetriever) =
        PolkamarktRuntime(chainRegistry, bulkRetriever)

    @Provides
    @Singleton
    fun providePolkamarktInteractor(
        chainRegistry: ChainRegistry,
        indexer: PolkamarktIndexer,
        runtime: PolkamarktRuntime,
        accountRepository: AccountRepository,
        walletRepository: WalletRepository,
        extrinsicService: ExtrinsicService,
        polkaswapInteractor: PolkaswapInteractor,
        toggles: ProductFeatureToggleStore,
        mutationAuthorization: MutationAuthorizationStore
    ): PolkamarktInteractor = PolkamarktInteractorImpl(
        chainRegistry,
        indexer,
        runtime,
        accountRepository,
        walletRepository,
        extrinsicService,
        polkaswapInteractor,
        toggles,
        mutationAuthorization
    )
}
