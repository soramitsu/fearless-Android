package jp.co.soramitsu.nft.impl.di

import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.coredb.dao.NFTContractMetadataResponseDao
import jp.co.soramitsu.nft.data.CachedNFTRepository
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.RemoteNFTRepository
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.impl.data.cached.CachingNFTRepositoryDecorator
import jp.co.soramitsu.nft.impl.data.NFTRepositoryImpl
import jp.co.soramitsu.nft.data.models.Contract
import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.models.TokenId
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.impl.data.model.utils.deserializer
import jp.co.soramitsu.nft.impl.data.remote.AlchemyNftApi
import jp.co.soramitsu.nft.impl.domain.NFTInteractorImpl
import jp.co.soramitsu.nft.impl.domain.NFTTransferInteractorImpl
import jp.co.soramitsu.nft.impl.navigation.NFTRouterImpl
import jp.co.soramitsu.nft.impl.navigation.NftRouter
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

@Module
@InstallIn(SingletonComponent::class)
class NftModule {

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
    @RemoteNFTRepository
    fun provideRemoteNFTRepository(
        alchemyNftApi: AlchemyNftApi,
        preferences: Preferences
    ): NFTRepository {
        return NFTRepositoryImpl(
            alchemyNftApi = alchemyNftApi,
            preferences = preferences
        )
    }

    @Provides
    @Singleton
    @CachedNFTRepository
    fun provideCachingNFTRepositoryDecorator(
        @RemoteNFTRepository nftRepository: NFTRepository,
        nftContractMetadataResponseDao: NFTContractMetadataResponseDao
    ): NFTRepository {
        return CachingNFTRepositoryDecorator(
            nftRepository = nftRepository,
            nftContractMetadataResponseDao = nftContractMetadataResponseDao
        )
    }

    @Provides
    @Singleton
    fun provideNFTInteractor(
        @CachedNFTRepository nftRepository: NFTRepository,
        accountRepository: AccountRepository,
        chainsRepository: ChainsRepository
    ): NFTInteractor = NFTInteractorImpl(
        nftRepository = nftRepository,
        accountRepository = accountRepository,
        chainsRepository = chainsRepository
    )

    @Provides
    @Singleton
    fun provideNFTTransferInteractor(
        accountRepository: AccountRepository,
        chainsRepository: ChainsRepository,
        @RemoteNFTRepository nftRepository: NFTRepository,
        ethereumConnectionPool: EthereumConnectionPool
    ): NFTTransferInteractor = NFTTransferInteractorImpl(
        accountRepository = accountRepository,
        chainsRepository = chainsRepository,
        nftRepository = nftRepository,
        ethereumConnectionPool = ethereumConnectionPool
    )

    @Provides
    @Singleton
    fun provideNFTRouter(
        walletRouter: WalletRouter
    ): NftRouter = NFTRouterImpl(
        walletRouter = walletRouter
    )

}