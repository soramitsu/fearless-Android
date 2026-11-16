package jp.co.soramitsu.nft.impl.presentation.collection

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.compose.models.LoadableListPage
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.presentation.CoroutinesStore
import jp.co.soramitsu.nft.impl.presentation.collection.models.NFTsScreenView
import jp.co.soramitsu.nft.impl.presentation.collection.models.ScreenModel
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList

class CollectionNFTsPresenterTest {
    private val errors = CopyOnWriteArrayList<Throwable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> errors += error })
    private val requests = CopyOnWriteArrayList<PaginationRequest>()
    private val token = mockk<NFT> {
        every { isUserOwnedToken } returns true
        every { tokenId } returns BigInteger.ONE
        every { title } returns "Bird"
        every { thumbnail } returns ""
        every { description } returns "Description"
        every { collectionName } returns "Birds"
        every { chainId } returns "1"
        every { chainName } returns "Ethereum"
        every { creatorAddress } returns "creator"
        every { tokenType } returns "ERC721"
    }
    private val collection = mockk<NFTCollection.Loaded.Result.Collection.WithTokens> {
        every { tokens } returns sequenceOf(token)
        every { imageUrl } returns ""
        every { description } returns "Collection"
    }
    private val router = mockk<InternalNFTRouter>(relaxed = true) {
        every { createNavGraphRoutesFlow() } returns flowOf(NFTNavGraphRoute.CollectionNFTsScreen("1", "contract"))
    }
    private val nfts = mockk<NFTInteractor> {
        every { tokensFlow(any(), any(), any()) } answers {
            val pagination = firstArg<Flow<PaginationRequest>>()
            val chains = secondArg<Flow<String>>()
            val contracts = thirdArg<Flow<String>>()
            channelFlow {
                assertEquals("1", chains.first())
                assertEquals("contract", contracts.first())
                pagination.collect { request ->
                    requests += request
                    send(NFTCollection.Reloading)
                    send(collection)
                }
            }
        }
    }
    private val accounts = mockk<AccountInteractor> {
        coEvery { selectedMetaAccount() } returns mockk {
            every { chainAccounts } returns emptyMap()
            every { ethereumAddress } returns ByteArray(20) { 1 }
        }
    }
    private val chains = mockk<ChainsRepository> {
        coEvery { getChain("1") } returns mockk<Chain> {
            every { ecosystem } returns Ecosystem.Ethereum
            every { externalApi } returns null
            every { id } returns "1"
        }
    }
    private val resources = mockk<ResourceManager> {
        every { getString(any()) } returns "Label"
        every { getString(jp.co.soramitsu.feature_nft_impl.R.string.wallet_receive_share_message) } returns "%s: %s"
    }
    private val store = mockk<CoroutinesStore> { every { uiScope } returns scope }
    private val presenter by lazy { CollectionNFTsPresenter(accounts, chains, store, nfts, resources, router) }

    @After
    fun closeScopes() {
        scope.cancel()
        assertTrue(errors.toString(), errors.isEmpty())
    }

    @Test
    fun ownedTokenActionsOpenDetailsAndRecipientSelection(): Unit = runBlocking {
        val state = presenter.createCollectionsNFTsFlow(scope)
        scope.launch { state.collect() }
        await { state.value is ScreenModel.ReadyToRender }
        val item = (state.value as LoadableListPage.ReadyToRender<NFTsScreenView>).views.filterIsInstance<NFTsScreenView.ItemModel.WithButtonDecorator>().single()
        item.onItemClick()
        item.onButtonClick()
        verify { router.openDetailsNFTScreen(token) }
        verify { router.openChooseRecipientScreen(token) }
        assertEquals(PaginationRequest.Start(100), requests.single())
    }

    @Test
    fun unownedActionSharesSelectedAddressAndTokenDetails(): Unit = runBlocking {
        every { token.isUserOwnedToken } returns false
        val state = presenter.createCollectionsNFTsFlow(scope)
        scope.launch { state.collect() }
        await { state.value is ScreenModel.ReadyToRender }
        val item = (state.value as LoadableListPage.ReadyToRender<NFTsScreenView>).views.filterIsInstance<NFTsScreenView.ItemModel.WithButtonDecorator>().single()
        item.onButtonClick()
        verify(timeout = 10_000) {
            router.shareText(match { it.contains("Birds") && it.contains("Ethereum") && it.contains("01".repeat(20)) })
        }
        verify(exactly = 0) { router.openChooseRecipientScreen(any()) }
    }

    @Test
    fun pageScrollingEmitsLoadingStatesAndCorrectPaginationRequests(): Unit = runBlocking {
        val state = presenter.createCollectionsNFTsFlow(scope)
        scope.launch { state.collect() }
        await { state.value is ScreenModel.ReadyToRender }
        presenter.pageScrollingCallback.onAllNextPagesScrolled()
        await { state.value is ScreenModel.NextPageLoading }
        await { requests.size == 2 && state.value is ScreenModel.ReadyToRender }
        presenter.pageScrollingCallback.onAllPrevPagesScrolled()
        await { state.value is ScreenModel.PreviousPageLoading }
        await { requests.size == 3 && state.value is ScreenModel.ReadyToRender }
        assertEquals(listOf(PaginationRequest.Start(100), PaginationRequest.Next(100), PaginationRequest.Prev), requests)
        assertTrue((state.value as LoadableListPage.ReadyToRender<NFTsScreenView>).views.isNotEmpty())
    }

    private suspend fun await(condition: () -> Boolean) {
        withTimeout(10_000) { while (!condition()) delay(10) }
    }
}
