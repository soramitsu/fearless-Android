package jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.nft.data.models.Contract
import jp.co.soramitsu.nft.data.models.TokenId
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.data.pagination.PageBackStack
import jp.co.soramitsu.nft.data.pagination.PagedResponse
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class TokensMappingAdapterTest {
    private val chains = mockk<ChainsRepository> {
        coEvery { getChain(any()) } answers {
            val chainId = firstArg<String>()
            mockk<Chain> {
                every { id } returns chainId
                every { name } returns "Chain $chainId"
            }
        }
    }
    private val adapter = TokensMappingAdapter(chains)

    @Test
    fun validPageSkipsTokensWithoutIdentifiers(): Unit = runBlocking {
        val response = NFTResponse.TokensCollection(sequenceOf(info(null), info("0x2a")), "next")
        val mapped = withTimeout(10_000) { adapter { flowOf(page(Result.success(response))) }.first() }
        val collection = mapped as NFTCollection.Loaded.Result.Collection.WithTokens
        assertEquals("1", collection.chainId)
        assertEquals("Chain 1", collection.chainName)
        assertEquals(listOf(BigInteger.valueOf(42)), collection.tokens.map { it.tokenId }.toList())
    }

    @Test
    fun boundaryPagesMapToEmptyResults(): Unit = runBlocking {
        val inputs = listOf(
            page(Result.success(PageBackStack.PageResult.NoNextPages())),
            page(Result.success(PageBackStack.PageResult.NoPrevPages()))
        )
        val mapped = withTimeout(10_000) { adapter { flowOf(*inputs.toTypedArray()) }.toList() }
        assertEquals(2, mapped.size)
        assertTrue(mapped.all { it is NFTCollection.Loaded.Result.Empty })
        coVerify(exactly = 1) { chains.getChain("1") }
    }

    @Test
    fun failedPageRetainsChainAndCause(): Unit = runBlocking {
        val failure = IllegalStateException("offline")
        val mapped = withTimeout(10_000) { adapter { flowOf(page(Result.failure(failure))) }.first() }
        assertSame(failure, (mapped as NFTCollection.Loaded.WithFailure).throwable)
        assertEquals("1", mapped.chainId)
    }

    @Test
    fun chainCacheChangesWithTagAndClearsAfterCollection(): Unit = runBlocking {
        val result = Result.success<PageBackStack.PageResult<TokenInfo>>(PageBackStack.PageResult.NoNextPages())
        val input = flowOf(page(result), page(result), page(result, "2"))
        val first = withTimeout(10_000) { adapter { input }.toList() }
        assertEquals(listOf("1", "1", "2"), first.map { it.chainId })
        coVerify(exactly = 1) { chains.getChain("1") }
        coVerify(exactly = 1) { chains.getChain("2") }
        withTimeout(10_000) { adapter { flowOf(page(result, "2")) }.first() }
        coVerify(exactly = 2) { chains.getChain("2") }
    }

    private fun page(result: Result<PageBackStack.PageResult<TokenInfo>>, chainId: String = "1") =
        PagedResponse(chainId, PageBackStack.Request.Next(null, 10), result)

    private fun info(id: String?) = TokenInfo(
        Contract("contract"), id?.let { TokenId(it, null) }, "Bird", null, null, null, null, null, null
    )
}
