package jp.co.soramitsu.nft.impl.domain.usecase.tokensbycontract

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.domain.models.nft.CollectionWithTokensImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.math.BigInteger

class TokensTrimmingMediatorTest {
    @Test
    fun allTokenPagesExcludeTokensAlreadyShownInOwnedSection() = runTest {
        val owned = token(1, true)
        val duplicate = token(1, false)
        val available = token(2, false)
        val mediator = TokensTrimmingMediator()
        val result = mediator { flowOf(collection(owned), collection(duplicate, available)) }
            .map { (it as CollectionWithTokensImpl).tokens.toList() }.toList()
        assertEquals(listOf(listOf(owned), listOf(available)), result)
        val fresh = mediator { flowOf(collection(duplicate)) }.first() as CollectionWithTokensImpl
        assertEquals(listOf(duplicate), fresh.tokens.toList())
    }

    @Test
    fun emptyAndFailureResultsPassThroughUnchanged() = runTest {
        val empty = NFTCollection.Loaded.Result.Empty("1", "Ethereum")
        val failed = NFTCollection.Loaded.WithFailure("1", "Ethereum", IllegalStateException("offline"))
        val mediator = TokensTrimmingMediator()
        val results = mediator { flowOf(empty, failed) }.toList()
        assertSame(empty, results[0])
        assertSame(failed, results[1])
    }

    private fun collection(vararg tokens: NFT) = CollectionWithTokensImpl(
        NFTResponse.TokensCollection(emptySequence(), null), "1", "Ethereum", tokens.asSequence()
    )

    private fun token(id: Int, owned: Boolean): NFT = mockk {
        every { tokenId } returns BigInteger.valueOf(id.toLong())
        every { isUserOwnedToken } returns owned
    }
}
