package jp.co.soramitsu.nft.impl.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.impl.data.domain.PageCachingDecorator
import jp.co.soramitsu.nft.impl.data.domain.PagingRequestMediator
import jp.co.soramitsu.nft.impl.data.remote.AlchemyNftApi
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ethereumChainId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NFTRepositoryImplTest {
    private val api = mockk<AlchemyNftApi>()
    private val preferences = mockk<Preferences>(relaxed = true) {
        every { getStringSet(any(), any()) } returns emptySet()
    }
    private val chain = mockk<Chain> {
        every { id } returns ethereumChainId
        every { ecosystem } returns Ecosystem.Ethereum
        every { externalApi } returns null
    }
    private val wallet = mockk<MetaAccount> {
        every { chainAccounts } returns emptyMap()
        every { ethereumAddress } returns ByteArray(20) { 1 }
    }
    private val repository = NFTRepositoryImpl(api, preferences, PagingRequestMediator(), PageCachingDecorator())

    @Test
    fun userCollectionsCarryChainTagFiltersAndPagingMetadata(): Unit = runBlocking {
        val contract = mockk<ContractInfo>()
        coEvery { api.getUserOwnedContracts(any(), any(), any(), any(), any(), any()) } returns
            NFTResponse.UserOwnedContracts(sequenceOf(contract), "page-2")
        val result = withTimeout(10_000) {
            repository.paginatedUserOwnedContractsFlow(
                flowOf(PaginationRequest.Start(100)), flowOf(listOf(chain)), flowOf(wallet), flowOf(listOf("SPAM"))
            ).first().single()
        }
        assertEquals(ethereumChainId, result.tag)
        assertSame(contract, result.result.getOrThrow().items.single())
        coVerify {
            api.getUserOwnedContracts(any(), "0x" + "01".repeat(20), true, null, 100, listOf("SPAM"))
        }
    }

    @Test
    fun ownedTokensAreRequestedForSelectedContractAndWallet(): Unit = runBlocking {
        val token = mockk<TokenInfo>()
        coEvery { api.getUserOwnedNFTsByContractAddress(any(), any(), any(), any(), any(), any(), any()) } returns
            NFTResponse.TokensCollection(sequenceOf(token), null)
        val result = withTimeout(10_000) {
            repository.paginatedUserOwnedNFTsByContractAddressFlow(
                flowOf(PaginationRequest.Start(100)), flowOf(chain), flowOf("contract"), flowOf(wallet), flowOf(emptyList())
            ).first()
        }
        assertEquals(ethereumChainId, result.tag)
        assertSame(token, result.result.getOrThrow().items.single())
        coVerify {
            api.getUserOwnedNFTsByContractAddress(any(), "0x" + "01".repeat(20), "contract", true, null, 100, emptyList())
        }
    }

    @Test
    fun collectionTokensDoNotRequireAnOwner(): Unit = runBlocking {
        val token = mockk<TokenInfo>()
        coEvery { api.getNFTCollectionByContactAddress(any(), any(), any(), any(), any()) } returns
            NFTResponse.TokensCollection(sequenceOf(token), "next")
        val result = withTimeout(10_000) {
            repository.paginatedNFTCollectionByContractAddressFlow(
                flowOf(PaginationRequest.Start(100)), flowOf(chain), flowOf("contract")
            ).first()
        }
        assertEquals(ethereumChainId, result.tag)
        assertSame(token, result.result.getOrThrow().items.single())
        coVerify { api.getNFTCollectionByContactAddress(any(), "contract", true, null, 100) }
    }

    @Test
    fun pagingFailuresAreReturnedWithTheirChain(): Unit = runBlocking {
        val failure = IllegalStateException("offline")
        coEvery { api.getNFTCollectionByContactAddress(any(), any(), any(), any(), any()) } throws failure
        val result = withTimeout(10_000) {
            repository.paginatedNFTCollectionByContractAddressFlow(
                flowOf(PaginationRequest.Start(100)), flowOf(chain), flowOf("contract")
            ).first()
        }
        assertEquals(ethereumChainId, result.tag)
        assertSame(failure, result.result.exceptionOrNull())
    }

    @Test
    fun unsupportedWalletReturnsFailureWithoutRequestingCollections(): Unit = runBlocking {
        every { wallet.ethereumAddress } returns null
        val result = withTimeout(10_000) {
            repository.paginatedUserOwnedContractsFlow(
                flowOf(PaginationRequest.Start(100)), flowOf(listOf(chain)), flowOf(wallet), flowOf(emptyList())
            ).first().single()
        }
        assertTrue(result.result.isFailure)
        assertTrue(result.result.exceptionOrNull()!!.message!!.contains("Owner is not supported"))
        coVerify(exactly = 0) { api.getUserOwnedContracts(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun metadataAndOwnerQueriesReturnResultsAndLimitOwnerCount(): Unit = runBlocking {
        val token = mockk<TokenInfo>()
        val owners = NFTResponse.TokenOwners(listOf("owner"))
        coEvery { api.getNFTMetadata(any(), "contract", "42") } returns token
        coEvery { api.getNFTOwners(any(), "contract", "42", DEFAULT_OWNERS_PAGE_SIZE) } returns owners
        assertSame(token, repository.tokenMetadata(chain, "contract", "42").getOrThrow())
        assertSame(owners, repository.tokenOwners(chain, "contract", "42").getOrThrow())
        coVerify { api.getNFTOwners(any(), "contract", "42", 10) }
    }

    @Test
    fun metadataAndOwnerFailuresStayInsideResult(): Unit = runBlocking {
        val failure = IllegalArgumentException("not found")
        coEvery { api.getNFTMetadata(any(), any(), any()) } throws failure
        coEvery { api.getNFTOwners(any(), any(), any(), any()) } throws failure
        val failures = listOf(
            repository.tokenMetadata(chain, "contract", "42").exceptionOrNull(),
            repository.tokenOwners(chain, "contract", "42").exceptionOrNull()
        )
        failures.forEach {
            assertTrue(it is IllegalArgumentException)
            assertEquals("not found", it?.message)
        }
    }
}
