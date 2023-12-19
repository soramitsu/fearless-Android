package jp.co.soramitsu.nft.impl.di

import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.nft.impl.data.NftRepository
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftCollection
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftContractInfo
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftId
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftMediaInfo
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftMetadata
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftOpenseaInfo
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftSpamInfo
import jp.co.soramitsu.nft.impl.data.model.AlchemyNftTokenMetadata
import jp.co.soramitsu.nft.impl.data.remote.AlchemyNftApi
import jp.co.soramitsu.nft.impl.data.serializtion.AlchemyNftCollectionDeserializer
import jp.co.soramitsu.nft.impl.data.serializtion.AlchemyNftContractInfoDeserializer
import jp.co.soramitsu.nft.impl.data.serializtion.AlchemyNftIdDeserializer
import jp.co.soramitsu.nft.impl.data.serializtion.AlchemyNftMediaInfoDeserializer
import jp.co.soramitsu.nft.impl.data.serializtion.AlchemyNftMetadataDeserializer
import jp.co.soramitsu.nft.impl.data.serializtion.AlchemyNftSpamInfoDeserializer
import jp.co.soramitsu.nft.impl.data.serializtion.AlchemyNftTokenMetadataDeserializer
import jp.co.soramitsu.nft.impl.data.serializtion.OpenSeaDeserializer
import jp.co.soramitsu.nft.impl.domain.NftInteractor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

@InstallIn(SingletonComponent::class)
@Module
class NftModule {
    @Provides
    fun provideNftInteractor(
        nftRepository: NftRepository,
        accountRepository: AccountRepository,
        chainRepository: ChainsRepository
    ) = NftInteractor(nftRepository, accountRepository, chainRepository)


    @Provides
    @Singleton
    fun provideNftRepository(alchemyNftApi: AlchemyNftApi) = NftRepository(alchemyNftApi)

    @Provides
    @Singleton
    fun provideAlchemyNftApi(okHttpClient: OkHttpClient): AlchemyNftApi {
        val gson = GsonBuilder()
            .registerTypeAdapter(
                AlchemyNftContractInfo::class.java,
                AlchemyNftContractInfoDeserializer()
            ).registerTypeAdapter(
                AlchemyNftMetadata::class.java,
                AlchemyNftMetadataDeserializer()
            ).registerTypeAdapter(
                AlchemyNftMediaInfo::class.java,
                AlchemyNftMediaInfoDeserializer()
            ).registerTypeAdapter(
                AlchemyNftCollection::class.java,
                AlchemyNftCollectionDeserializer()
            ).registerTypeAdapter(
                AlchemyNftOpenseaInfo::class.java,
                OpenSeaDeserializer()
            ).registerTypeAdapter(
                AlchemyNftTokenMetadata::class.java,
                AlchemyNftTokenMetadataDeserializer()
            ).registerTypeAdapter(
                AlchemyNftId::class.java,
                AlchemyNftIdDeserializer()
            ).registerTypeAdapter(
                AlchemyNftSpamInfo::class.java,
                AlchemyNftSpamInfoDeserializer()
            ).create()

        val retrofit = Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://placeholder.com")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(AlchemyNftApi::class.java)
    }

}