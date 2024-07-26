package jp.co.soramitsu.nft.impl.di

import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.models.Contract
import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.models.TokenId
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.impl.data.NFTRepositoryImpl
import jp.co.soramitsu.nft.impl.data.domain.PageCachingDecorator
import jp.co.soramitsu.nft.impl.data.domain.PagingRequestMediator
import jp.co.soramitsu.nft.impl.data.model.utils.deserializer
import jp.co.soramitsu.nft.impl.data.remote.AlchemyNftApi
import jp.co.soramitsu.nft.impl.domain.NFTInteractorImpl
import jp.co.soramitsu.nft.impl.domain.NFTTransferInteractorImpl
import jp.co.soramitsu.nft.impl.domain.usecase.collections.CollectionsFetchingUseCase
import jp.co.soramitsu.nft.impl.domain.usecase.collections.CollectionsMappingAdapter
import jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract.RequestSwitchingMediator
import jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract.TokensFetchingUseCase
import jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract.TokensMappingAdapter
import jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract.TokensTrimmingMediator
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouterImpl
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

@Module
@InstallIn(SingletonComponent::class)
class NFTModule {

    @Provides
    @Singleton
    fun provideAlchemyNftApi(okHttpClient: OkHttpClient): AlchemyNftApi {
        val gson = GsonBuilder()
            .registerTypeAdapter(
                NFTResponse.UserOwnedContracts::class.java,
                NFTResponse.UserOwnedContracts.deserializer
            ).registerTypeAdapter(
                NFTResponse.TokensCollection::class.java,
                NFTResponse.TokensCollection.deserializer
            ).registerTypeAdapter(
                NFTResponse.TokenOwners::class.java,
                NFTResponse.TokenOwners.deserializer
            ).registerTypeAdapter(
                ContractInfo::class.java,
                ContractInfo.deserializer
            ).registerTypeAdapter(
                Contract::class.java,
                Contract.deserializer
            ).registerTypeAdapter(
                TokenInfo::class.java,
                TokenInfo.deserializer
            ).registerTypeAdapter(
                TokenInfo.Media::class.java,
                TokenInfo.Media.deserializer
            ).registerTypeAdapter(
                TokenInfo.TokenMetadata::class.java,
                TokenInfo.TokenMetadata.deserializer
            ).registerTypeAdapter(
                TokenInfo.ContractMetadata::class.java,
                TokenInfo.ContractMetadata.deserializer
            ).registerTypeAdapter(
                TokenInfo.ContractMetadata.OpenSea::class.java,
                TokenInfo.ContractMetadata.OpenSea.deserializer
            ).registerTypeAdapter(
                TokenId::class.java,
                TokenId.deserializer
            ).registerTypeAdapter(
                TokenId.TokenMetadata::class.java,
                TokenId.TokenMetadata.deserializer
            ).create()

        val retrofit = Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://placeholder.com")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(AlchemyNftApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRemoteNFTRepository(
        alchemyNftApi: AlchemyNftApi,
        preferences: Preferences,
        pagingRequestMediator: PagingRequestMediator,
        pageCachingDecorator: PageCachingDecorator
    ): NFTRepository {
        return NFTRepositoryImpl(
            alchemyNftApi = alchemyNftApi,
            preferences = preferences,
            pagingRequestMediator = pagingRequestMediator,
            pageCachingDecorator = pageCachingDecorator
        )
    }

    @Provides
    @Singleton
    fun provideNFTInteractor(
        accountRepository: AccountRepository,
        chainsRepository: ChainsRepository,
        nftRepository: NFTRepository,
        collectionsFetchingUseCase: CollectionsFetchingUseCase,
        tokensFetchingUseCase: TokensFetchingUseCase
    ): NFTInteractor = NFTInteractorImpl(
        accountRepository = accountRepository,
        chainsRepository = chainsRepository,
        nftRepository = nftRepository,
        collectionsFetchingUseCase = collectionsFetchingUseCase,
        tokensFetchingUseCase = tokensFetchingUseCase
    )

    @Provides
    @Singleton
    fun provideCollectionsFetchingUseCase(
        accountRepository: AccountRepository,
        chainsRepository: ChainsRepository,
        nftRepository: NFTRepository,
        collectionsMappingAdapter: CollectionsMappingAdapter
    ) = CollectionsFetchingUseCase(
        accountRepository = accountRepository,
        chainsRepository = chainsRepository,
        nftRepository = nftRepository,
        collectionsMappingAdapter = collectionsMappingAdapter,
    )

    @Provides
    @Singleton
    fun provideTokensFetchingUseCase(
        accountRepository: AccountRepository,
        chainsRepository: ChainsRepository,
        nftRepository: NFTRepository,
        requestSwitchingMediator: RequestSwitchingMediator,
        tokensMappingAdapter: TokensMappingAdapter,
        tokensTrimmingMediator: TokensTrimmingMediator
    ) = TokensFetchingUseCase(
        accountRepository = accountRepository,
        chainsRepository = chainsRepository,
        nftRepository = nftRepository,
        requestSwitchingMediator = requestSwitchingMediator,
        tokensMappingAdapter = tokensMappingAdapter,
        tokensTrimmingMediator = tokensTrimmingMediator
    )

    @Provides
    @Singleton
    fun provideNFTTransferInteractor(
        accountRepository: AccountRepository,
        chainsRepository: ChainsRepository,
        ethereumConnectionPool: EthereumConnectionPool
    ): NFTTransferInteractor = NFTTransferInteractorImpl(
        accountRepository = accountRepository,
        chainsRepository = chainsRepository,
        ethereumConnectionPool = ethereumConnectionPool
    )

    @Provides
    @Singleton
    fun provideNFTRouter(walletRouter: WalletRouter): InternalNFTRouter = InternalNFTRouterImpl(
        walletRouter = walletRouter
    )
}
