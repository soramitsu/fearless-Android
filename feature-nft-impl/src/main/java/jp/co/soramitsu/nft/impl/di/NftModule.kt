package jp.co.soramitsu.nft.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.nft.impl.data.NftRepository
import jp.co.soramitsu.nft.impl.data.remote.AlchemyNftApi
import jp.co.soramitsu.nft.impl.domain.NftInteractor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry

@InstallIn(SingletonComponent::class)
@Module
class NftModule {
    @Provides
    fun provideNftInteractor(
        nftRepository: NftRepository,
        accountRepository: AccountRepository,
        chainRegistry: ChainRegistry
    ) = NftInteractor(nftRepository, accountRepository, chainRegistry)


    @Provides
    @Singleton
    fun provideNftRepository(alchemyNftApi: AlchemyNftApi) = NftRepository(alchemyNftApi)

    @Provides
    @Singleton
    fun provideAlchemyNftApi(apiCreator: NetworkApiCreator) =
        apiCreator.create(AlchemyNftApi::class.java)

}