package jp.co.soramitsu.nft.impl.di

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.nft.impl.data.NFTRepositoryImpl
import jp.co.soramitsu.nft.impl.data.domain.PageCachingDecorator
import jp.co.soramitsu.nft.impl.data.domain.PagingRequestMediator
import jp.co.soramitsu.nft.impl.domain.NFTInteractorImpl
import jp.co.soramitsu.nft.impl.domain.NFTTransferInteractorImpl
import jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract.RequestSwitchingMediator
import jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract.TokensTrimmingMediator
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouterImpl
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NFTModuleTest {
    private val module = NFTModule()

    @Test
    fun dataAndDomainProvidersBuildTheProductionImplementations() {
        val repository = module.provideRemoteNFTRepository(mockk(), mockk(), PagingRequestMediator(), PageCachingDecorator())
        assertTrue(repository is NFTRepositoryImpl)

        val collections = module.provideCollectionsFetchingUseCase(mockk(), mockk(), repository, mockk())
        val tokens = module.provideTokensFetchingUseCase(
            mockk(), mockk(), repository, RequestSwitchingMediator(), mockk(), TokensTrimmingMediator()
        )

        assertTrue(module.provideNFTInteractor(mockk(), mockk(), repository, collections, tokens) is NFTInteractorImpl)
        assertTrue(module.provideNFTTransferInteractor(mockk(), mockk(), mockk()) is NFTTransferInteractorImpl)
    }

    @Test
    fun eachRepositoryIsANewInstance() {
        val first = module.provideRemoteNFTRepository(mockk(), mockk(), PagingRequestMediator(), PageCachingDecorator())
        val second = module.provideRemoteNFTRepository(mockk(), mockk(), PagingRequestMediator(), PageCachingDecorator())
        assertNotSame(first, second)
    }

    @Test
    fun routerDelegatesWalletScreensToTheWalletRouter(): Unit = runBlocking {
        val walletRouter = mockk<WalletRouter>(relaxed = true) {
            every { openContactsWithResult("1") } returns flowOf("0xcontact")
        }
        val router = module.provideNFTRouter(walletRouter)
        assertTrue(router is InternalNFTRouterImpl)

        assertEquals("0xcontact", router.openContacts("1").first())
        router.openSuccessScreen("0xhash", "1")
        verify { walletRouter.openOperationSuccess("0xhash", "1") }
    }
}
