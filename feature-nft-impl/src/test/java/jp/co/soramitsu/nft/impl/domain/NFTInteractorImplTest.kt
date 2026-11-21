package jp.co.soramitsu.nft.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.nft.data.NFTRepository
import jp.co.soramitsu.nft.data.models.Contract
import jp.co.soramitsu.nft.data.models.TokenId
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.data.pagination.PaginationRequest
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.domain.models.NFTFilter
import jp.co.soramitsu.nft.impl.domain.usecase.collections.CollectionsFetchingUseCase
import jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract.TokensFetchingUseCase
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class NFTInteractorImplTest {
    private val own = "0x" + "01".repeat(20)
    private val chain = mockk<Chain> {
        every { id } returns "1"
        every { name } returns "Ethereum"
        every { ecosystem } returns Ecosystem.Ethereum
        every { externalApi } returns null
    }
    private val account = mockk<MetaAccount> {
        every { chainAccounts } returns emptyMap()
        every { ethereumAddress } returns ByteArray(20) { 1 }
    }
    private val accounts = mockk<AccountRepository> { coEvery { getSelectedMetaAccount() } returns account }
    private val chains = mockk<ChainsRepository> { coEvery { getChain("1") } returns chain }
    private val repository = mockk<NFTRepository>(relaxed = true)
    private val collections = mockk<CollectionsFetchingUseCase>()
    private val tokens = mockk<TokensFetchingUseCase>()
    private val interactor = NFTInteractorImpl(accounts, repository, chains, collections, tokens)

    @Test
    fun enabledFiltersInvertRepositoryExclusionFlags() = runTest {
        every { repository.nftFiltersFlow } returns flowOf(setOf(NFTFilter.AirDrops.name))
        assertEquals(mapOf(NFTFilter.AirDrops to false), interactor.nftFiltersFlow().first())
        every { repository.nftFiltersFlow } returns flowOf(emptySet())
        assertEquals(mapOf(NFTFilter.AirDrops to true), interactor.nftFiltersFlow().first())
        interactor.setNFTFilter(NFTFilter.AirDrops, true)
        interactor.setNFTFilter(NFTFilter.AirDrops, false)
        verify { repository.setNFTFilter("AirDrops", false) }
        verify { repository.setNFTFilter("AirDrops", true) }
    }

    @Test
    fun metadataIsMappedWithSelectedChainIdentity() = runTest {
        val info = TokenInfo(Contract("contract"), TokenId("0x2a", null), "Bird", null, null, null, null, null, null)
        coEvery { repository.tokenMetadata(chain, "contract", "42") } returns Result.success(info)
        val nft = interactor.getNFTDetails("1", "contract", "42").getOrThrow()
        assertEquals("Bird", nft.title)
        assertEquals(BigInteger.valueOf(42), nft.tokenId)
        assertEquals("1", nft.chainId)
        assertEquals("Ethereum", nft.chainName)
    }

    @Test
    fun metadataFailureIsPreserved() = runTest {
        val failure = IllegalStateException("missing token")
        coEvery { repository.tokenMetadata(chain, any(), any()) } returns Result.failure(failure)
        assertSame(failure, interactor.getNFTDetails("1", "contract", "42").exceptionOrNull())
    }

    @Test
    fun ownedTokenMovesSelectedAccountFirstWithoutDuplicatingIt() = runTest {
        coEvery { repository.tokenOwners(chain, "contract", "42") } returns
            Result.success(NFTResponse.TokenOwners(listOf("abcd", own, "ef01")))
        assertEquals(listOf(own, "0xabcd", "0xef01"), interactor.getOwnersForNFT(token(true)).getOrThrow())
    }

    @Test
    fun emptyOwnerResponseFallsBackOnlyForOwnedToken() = runTest {
        coEvery { repository.tokenOwners(chain, "contract", "42") } returns Result.success(NFTResponse.TokenOwners(emptyList()))
        assertEquals(listOf(own), interactor.getOwnersForNFT(token(true)).getOrThrow())
        assertEquals(emptyList<String>(), interactor.getOwnersForNFT(token(false)).getOrThrow())
    }

    @Test
    fun unownedTokenPreservesOwnersOrderAndAddsHexPrefixes() = runTest {
        coEvery {
            repository.tokenOwners(chain, "contract", "42")
        } returns Result.success(NFTResponse.TokenOwners(listOf("abcd", "0xef01")))
        assertEquals(listOf("0xabcd", "0xef01"), interactor.getOwnersForNFT(token(false)).getOrThrow())
    }

    @Test
    fun invalidTokenDoesNotRequestOwners() = runTest {
        assertTrue(interactor.getOwnersForNFT(token(true, contract = " ")).isFailure)
        assertTrue(interactor.getOwnersForNFT(token(true, id = BigInteger.valueOf(-1))).isFailure)
        coVerify(exactly = 0) { repository.tokenOwners(any(), any(), any()) }
    }

    @Test
    fun ownersFailureRemainsFailure() = runTest {
        val failure = IllegalStateException("offline")
        coEvery { repository.tokenOwners(chain, "contract", "42") } returns Result.failure(failure)
        assertSame(failure, interactor.getOwnersForNFT(token(false)).exceptionOrNull())
    }

    @Test
    fun collectionAndTokenRequestsDelegateTheSameInputFlows() = runTest {
        val requests = flowOf<PaginationRequest>(PaginationRequest.Start(10))
        val optionalChain = flowOf<String?>("1")
        val chainIds = flowOf("1")
        val contracts = flowOf("contract")
        val result = NFTCollection.Loaded.Result.Empty("1", "Ethereum")
        every { collections(requests, optionalChain) } returns flowOf(sequenceOf(result))
        every { tokens(requests, chainIds, contracts) } returns flowOf(result)
        assertSame(result, interactor.collectionsFlow(requests, optionalChain).first().single())
        assertSame(result, interactor.tokensFlow(requests, chainIds, contracts).first())
    }

    private fun token(
        owned: Boolean,
        contract: String = "contract",
        id: BigInteger = BigInteger.valueOf(42)
    ): NFT = mockk {
        every { chainId } returns "1"
        every { contractAddress } returns contract
        every { tokenId } returns id
        every { isUserOwnedToken } returns owned
    }
}
