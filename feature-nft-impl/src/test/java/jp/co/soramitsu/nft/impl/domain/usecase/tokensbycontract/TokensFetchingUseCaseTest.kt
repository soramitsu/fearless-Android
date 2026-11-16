package jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.models.Contract
import jp.co.soramitsu.nft.data.models.TokenId
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.data.pagination.PageBackStack
import jp.co.soramitsu.nft.data.pagination.PagedResponse
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class TokensFetchingUseCaseTest {
    private val chain = mockk<Chain> {
        every { id } returns "1"
        every { name } returns "Ethereum"
        every { supportNft } returns true
        every { isEthereumChain } returns true
    }
    private val account = mockk<MetaAccount> { every { ethereumPublicKey } returns byteArrayOf(1) }
    private val accounts = mockk<AccountRepository> {
        every { selectedMetaAccountFlow() } returns flowOf(account)
        coEvery { getSelectedMetaAccount() } returns account
    }
    private val chains = mockk<ChainsRepository> {
        every { chainsFlow() } returns flowOf(listOf(chain))
        coEvery { getChain("1") } returns chain
    }
    private val repository = mockk<NFTRepository> {
        every { nftFiltersFlow } returns flowOf(setOf("AirDrops"))
        every { paginatedNFTCollectionByContractAddressFlow(any(), any(), any()) } returns emptyFlow()
        every { paginatedUserOwnedNFTsByContractAddressFlow(any(), any(), any(), any(), any()) } answers {
            val selectedChains = secondArg<Flow<Chain>>()
            val contracts = thirdArg<Flow<String>>()
            val selectedAccounts = arg<Flow<MetaAccount>>(3)
            val filters = arg<Flow<List<String>>>(4)
            combine(selectedChains, contracts, selectedAccounts, filters) { selectedChain, contract, wallet, exclusions ->
                assertEquals(chain, selectedChain)
                assertEquals("contract", contract)
                assertEquals(account, wallet)
                assertEquals(listOf("AIRDROPS"), exclusions)
                page(NFTResponse.TokensCollection(sequenceOf(token()), null))
            }
        }
    }
    private val useCase = TokensFetchingUseCase(accounts, chains, repository, RequestSwitchingMediator(), TokensMappingAdapter(chains), TokensTrimmingMediator())

    @Test
    fun selectedInputsEmitReloadingAndMappedTokens(): Unit = runBlocking {
        val states = mutableListOf<NFTCollection>()
        val result = withTimeout(10_000) {
            useCase(flowOf(PaginationRequest.Start(10)), flowOf("1"), flowOf("contract"))
                .onEach { states += it }.filterIsInstance<NFTCollection.Loaded.Result.Collection.WithTokens>().first()
        }
        assertEquals(listOf(BigInteger.ONE), result.tokens.map { it.tokenId }.toList())
        assertEquals(4, states.count { it is NFTCollection.Reloading })
    }

    @Test
    fun unsupportedSelectionAndMissingEthereumKeyFailClearly(): Unit = runBlocking {
        for (selection in listOf("unknown", "1")) {
            every { account.ethereumPublicKey } returns null
            val failure = runCatching {
                withTimeout(10_000) { useCase(emptyFlow(), flowOf(selection), flowOf("contract")).toList() }
            }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertTrue(failure!!.message!!.contains("does not support NFT operations"))
        }
    }

    @Test
    fun ownedEndSwitchesToAvailableAndAvailableBeginningSwitchesBack(): Unit = runBlocking {
        val requests = MutableSharedFlow<PaginationRequest>(extraBufferCapacity = 1)
        val observed = CopyOnWriteArrayList<Pair<String, PaginationRequest>>()
        every { repository.paginatedUserOwnedNFTsByContractAddressFlow(any(), any(), any(), any(), any()) } answers {
            firstArg<Flow<PaginationRequest>>().map { request ->
                observed += "owned" to request
                if (request is PaginationRequest.Start) {
                    // Give the available-tokens node time to drop the same request before the switch.
                    delay(200)
                    page(PageBackStack.PageResult.NoNextPages())
                } else {
                    page(NFTResponse.TokensCollection(sequenceOf(token()), null))
                }
            }
        }
        every { repository.paginatedNFTCollectionByContractAddressFlow(any(), any(), any()) } answers {
            firstArg<Flow<PaginationRequest>>().map { request ->
                observed += "available" to request
                page(PageBackStack.PageResult.NoPrevPages())
            }
        }
        val result = withTimeout(10_000) {
            coroutineScope {
                val collection = async {
                    useCase(requests, flowOf("1"), flowOf("contract"))
                        .filterIsInstance<NFTCollection.Loaded.Result.Collection.WithTokens>()
                        .first()
                }
                // Both request nodes subscribe on Dispatchers.Default; emit only once they listen.
                requests.subscriptionCount.first { it >= 2 }
                requests.emit(PaginationRequest.Start(10))
                collection.await()
            }
        }
        assertEquals(listOf("owned", "available", "owned"), observed.map { it.first })
        assertEquals(PaginationRequest.Start(100), observed[1].second)
        assertEquals(PaginationRequest.ProceedFromLastPage, observed[2].second)
        assertEquals(listOf(BigInteger.ONE), result.tokens.map { it.tokenId }.toList())
    }

    private fun page(result: PageBackStack.PageResult<TokenInfo>) =
        PagedResponse("1", PageBackStack.Request.Next(null, 10), Result.success(result))

    private fun token() =
        TokenInfo(Contract("contract"), TokenId("0x1", null), "Bird", null, null, null, null, null, null)
}
