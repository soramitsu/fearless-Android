package jp.co.soramitsu.nft.impl.domain.usecase.collections

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.data.pagination.PageBackStack
import jp.co.soramitsu.nft.data.pagination.PagedResponse
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsMappingAdapterTest {
    private val chain = mockk<Chain> {
        every { id } returns "1"
        every { name } returns "Ethereum"
    }
    private val chains = mockk<ChainsRepository> { coEvery { getChainsById() } returns mapOf("1" to chain) }
    private val adapter = CollectionsMappingAdapter(chains)

    @Test
    fun contractsRetainCollectionMetadataAndReadOneCatalogSnapshotPerBatch(): Unit = runBlocking {
        val contract = ContractInfo("contract", 2, 2, false, null, null, "Birds", "NFT", 20, "ERC721", null, null, null, null)
        val response = page(Result.success(NFTResponse.UserOwnedContracts(sequenceOf(contract), "next")))
        val result = withTimeout(10_000) { adapter { flowOf(listOf(response), listOf(response)) }.map { it.toList() }.toList() }
        assertEquals(2, result.size)
        val collection = result[0].single() as NFTCollection.Loaded.Result.Collection
        assertEquals("Birds", collection.collectionName)
        assertEquals("contract", collection.contractAddress)
        assertEquals(2, collection.balance)
        assertEquals(20, collection.collectionSize)
        assertEquals("1", collection.chainId)
        assertEquals("Ethereum", collection.chainName)
        coVerify(exactly = 2) { chains.getChainsById() }
        withTimeout(10_000) { adapter { flowOf(emptyList()) }.first() }
        coVerify(exactly = 2) { chains.getChainsById() }
    }

    @Test
    fun emptyContractPageMapsToChainSpecificEmptyResult(): Unit = runBlocking {
        val response = page(Result.success(NFTResponse.UserOwnedContracts(emptySequence(), null)))
        val result = withTimeout(10_000) { adapter { flowOf(listOf(response)) }.first().single() }
        assertTrue(result is NFTCollection.Loaded.Result.Empty)
        assertEquals("1", result.chainId)
        assertEquals("Ethereum", result.chainName)
    }

    @Test
    fun failedChainResponseProducesFailureResultWhileNoChainsProduceNoCollections(): Unit = runBlocking {
        val failure = IllegalStateException("offline")
        val result = withTimeout(10_000) { adapter { flowOf(listOf(page(Result.failure(failure)))) }.first().single() }
        assertSame(failure, (result as NFTCollection.Loaded.WithFailure).throwable)
        assertEquals("1", result.chainId)
        assertTrue(withTimeout(10_000) { adapter { flowOf(emptyList()) }.first().none() })
    }

    @Test
    fun aPageForAChainNoLongerInTheCatalogIsDroppedWithoutTheStrictLookup(): Unit = runBlocking {
        coEvery { chains.getChainsById() } returns emptyMap()
        val contract = ContractInfo("contract", 2, 2, false, null, null, "Birds", "NFT", 20, "ERC721", null, null, null, null)
        val response = page(Result.success(NFTResponse.UserOwnedContracts(sequenceOf(contract), null)))
        val failedResponse = page(Result.failure(IllegalStateException("offline")))

        val result = withTimeout(10_000) { adapter { flowOf(listOf(response, failedResponse)) }.first().toList() }

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { chains.getChain(any()) }
    }

    @Test
    fun aChainRemovedBetweenBatchesIsNotShownFromAnEarlierSnapshot(): Unit = runBlocking {
        coEvery { chains.getChainsById() } returnsMany listOf(mapOf("1" to chain), emptyMap())
        val response = page(Result.success(NFTResponse.UserOwnedContracts(emptySequence(), null)))

        val result = withTimeout(10_000) { adapter { flowOf(listOf(response), listOf(response)) }.map { it.toList() }.toList() }

        assertEquals(1, result[0].size)
        assertTrue(result[1].isEmpty())
    }

    private fun page(result: Result<PageBackStack.PageResult<ContractInfo>>) =
        PagedResponse("1", PageBackStack.Request.Next(null, 10), result)
}
