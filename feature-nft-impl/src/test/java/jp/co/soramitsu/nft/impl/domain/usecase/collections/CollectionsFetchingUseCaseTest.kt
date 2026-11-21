package jp.co.soramitsu.nft.impl.domain.usecase.collections

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsFetchingUseCaseTest {
    private val ethereum = chain("1", true)
    private val polygon = chain("137", true)
    private val unsupported = chain("unknown", true)
    private val disabled = chain("11155111", false)
    private val account = mockk<MetaAccount> { every { ethereumPublicKey } returns byteArrayOf(1) }
    private val accounts = mockk<AccountRepository> {
        every { selectedMetaAccountFlow() } returns flowOf(account)
        coEvery { getSelectedMetaAccount() } returns account
    }
    private val chains = mockk<ChainsRepository> {
        every { chainsFlow() } returns flowOf(listOf(ethereum, polygon, unsupported, disabled))
    }
    private val seenChains = mutableListOf<List<String>>()
    private val seenFilters = mutableListOf<List<String>>()
    private val seenAccounts = mutableListOf<MetaAccount>()
    private val repository = mockk<NFTRepository> {
        every { nftFiltersFlow } returns flowOf(setOf("AirDrops"))
        every { paginatedUserOwnedContractsFlow(any(), any(), any(), any()) } answers {
            val chainFlow = secondArg<Flow<List<Chain>>>()
            val accountFlow = thirdArg<Flow<MetaAccount>>()
            val filterFlow = arg<Flow<List<String>>>(3)
            combine(chainFlow, accountFlow, filterFlow) { selectedChains, selectedAccount, filters ->
                seenChains += selectedChains.map { it.id }
                seenAccounts += selectedAccount
                seenFilters += filters
                emptyList()
            }
        }
    }
    private val useCase = CollectionsFetchingUseCase(accounts, chains, repository, CollectionsMappingAdapter(chains))

    @Test
    fun allChainsExcludeUnsupportedNetworksAndNormalizeExclusions(): Unit = runBlocking {
        val states = load(null)
        assertEquals(listOf(listOf("1", "137")), seenChains)
        assertEquals(listOf(listOf("AIRDROPS")), seenFilters)
        assertEquals(listOf(account), seenAccounts)
        assertEquals(3, states.count { it == listOf(NFTCollection.Reloading) })
        assertTrue(states.last().isEmpty())
    }

    @Test
    fun selectedChainRestrictsRequestsAndUnknownSelectionIsEmpty(): Unit = runBlocking {
        load("137")
        load("unknown")
        assertEquals(listOf(listOf("137"), emptyList<String>()), seenChains)
    }

    @Test
    fun walletWithoutEthereumKeyExcludesEthereumNetworks(): Unit = runBlocking {
        every { account.ethereumPublicKey } returns null
        load("")
        assertEquals(listOf(emptyList<String>()), seenChains)
    }

    private suspend fun load(selection: String?): List<List<NFTCollection>> {
        val states = mutableListOf<List<NFTCollection>>()
        withTimeout(10_000) {
            useCase(flowOf(PaginationRequest.Start(10)), flowOf(selection))
                .onEach { states += it.toList() }.first { it.none() }
        }
        return states
    }

    private fun chain(id: String, supportsNft: Boolean): Chain = mockk {
        every { this@mockk.id } returns id
        every { supportNft } returns supportsNft
        every { isEthereumChain } returns true
    }
}
