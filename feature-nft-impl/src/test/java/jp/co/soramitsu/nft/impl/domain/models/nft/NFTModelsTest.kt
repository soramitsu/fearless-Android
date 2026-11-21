package jp.co.soramitsu.nft.impl.domain.models.nft

import jp.co.soramitsu.nft.data.models.Contract
import jp.co.soramitsu.nft.data.models.ContractInfo
import jp.co.soramitsu.nft.data.models.TokenId
import jp.co.soramitsu.nft.data.models.TokenInfo
import jp.co.soramitsu.nft.data.models.wrappers.NFTResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class NFTModelsTest {
    @Test
    fun tokenUsesExplicitMetadataAndConvertsHexIdentifier() {
        val token = NFTImpl(tokenInfo(title = "Rare bird", balance = "2"), "chain", "Ethereum")
        assertEquals("Rare bird", token.title)
        assertEquals(BigInteger("42"), token.tokenId)
        assertEquals("0xabcdef123456", token.contractAddress)
        assertEquals("Collection", token.collectionName)
        assertEquals("Collection", token.description)
        assertEquals("creator", token.creatorAddress)
        assertEquals("ERC721", token.tokenType)
        assertEquals("chain", token.chainId)
        assertEquals("Ethereum", token.chainName)
        assertTrue(token.isUserOwnedToken)
        assertNull(token.date)
        assertEquals("", token.price)
    }

    @Test
    fun tokenNamesFallBackThroughMetadataAndShortenedIdentifiers() {
        val metadata = TokenInfo.TokenMetadata("Metadata bird", null, null, null, null, null)
        assertEquals("Metadata bird", NFTImpl(tokenInfo(metadata = metadata), "chain", "Ethereum").title)
        assertEquals("Collection #42", NFTImpl(tokenInfo(), "chain", "Ethereum").title)
        assertEquals("Collection #100...", NFTImpl(tokenInfo(id = "0x3e8"), "chain", "Ethereum").title)
        val empty = NFTImpl(tokenInfo(id = null, contractMetadata = null), "chain", "Ethereum")
        assertEquals(BigInteger("-1"), empty.tokenId)
        assertEquals("abc...456", empty.collectionName)
        assertEquals("abc...456 #-1", empty.title)
        assertEquals("", empty.tokenType)
        assertNull(empty.creatorAddress)
    }

    @Test
    fun tokenThumbnailPrefersMediaThenOpenSeaAndOwnershipRequiresNonzeroBalance() {
        val sea = TokenInfo.ContractMetadata.OpenSea(null, "Sea birds", null, "sea-image", "Sea description", null, null, null)
        val contract = contractMetadata(sea)
        val blank = TokenInfo.Media("", "", null, null, null)
        val gateway = TokenInfo.Media("gateway", "", null, null, null)
        val thumbnail = TokenInfo.Media("gateway", "thumbnail", null, null, null)
        val expected = listOf(
            listOf(blank, thumbnail) to "thumbnail",
            listOf(blank, gateway) to "gateway",
            emptyList<TokenInfo.Media>() to "sea-image"
        )
        expected.forEach { (media, image) ->
            val token = NFTImpl(tokenInfo(media = media, contractMetadata = contract), "chain", "Ethereum")
            assertEquals(image, token.thumbnail)
            assertEquals("Sea birds", token.collectionName)
            assertEquals("Sea description", token.description)
        }
        assertEquals("", NFTImpl(tokenInfo(), "chain", "Ethereum").thumbnail)
        listOf(null, "", "0").forEach { balance ->
            assertFalse(NFTImpl(tokenInfo(balance = balance), "chain", "Ethereum").isUserOwnedToken)
        }
    }

    @Test
    fun collectionUsesTitleThumbnailAndExplicitCounts() {
        val media = TokenInfo.Media("gateway", "thumbnail", null, null, null)
        val response = contractInfo(title = "Title", media = listOf(media), totalBalance = 4, totalSupply = 20)
        val collection = CollectionImpl("chain", "Ethereum", response)
        assertEquals("Title", collection.collectionName)
        assertEquals("Title", collection.description)
        assertEquals("thumbnail", collection.imageUrl)
        assertEquals(4, collection.balance)
        assertEquals(20, collection.collectionSize)
        assertEquals("ERC1155", collection.type)
        assertEquals("0xabcdef123456", collection.contractAddress)
        assertEquals("chain", collection.chainId)
        assertEquals("Ethereum", collection.chainName)
    }

    @Test
    fun collectionFallsBackToOpenSeaNameGatewayAndDistinctBalance() {
        val sea = TokenInfo.ContractMetadata.OpenSea(null, "Sea", null, "sea-image", "Description", null, null, null)
        val withSea = CollectionImpl("chain", "Ethereum", contractInfo(sea = sea))
        assertEquals("Sea", withSea.collectionName)
        assertEquals("Description", withSea.description)
        assertEquals("sea-image", withSea.imageUrl)
        assertEquals(2, withSea.balance)
        assertEquals(0, withSea.collectionSize)
        val fallback = CollectionImpl(
            "chain", "Ethereum",
            contractInfo(
                name = "Name",
                media = listOf(
                    TokenInfo.Media("gateway", null, null, null, null)
                )
            )
        )
        assertEquals("Name", fallback.collectionName)
        assertEquals("gateway", fallback.imageUrl)
        val unnamed = CollectionImpl("chain", "Ethereum", contractInfo())
        assertEquals("abc...456", unnamed.collectionName)
        assertEquals("", unnamed.imageUrl)
    }

    @Test
    fun tokenCollectionSkipsIncompleteMetadataAndCopyReplacesOnlyTokens() {
        val info = tokenInfo(media = listOf(TokenInfo.Media("gateway", null, null, null, null)))
        val token = NFTImpl(info, "chain", "Ethereum")
        val response = NFTResponse.TokensCollection(sequenceOf(tokenInfo(contractMetadata = null), info), "next")
        val collection = CollectionWithTokensImpl(response, "chain", "Ethereum", sequenceOf(token))
        assertEquals("0xabcdef123456", collection.contractAddress)
        assertEquals("Collection", collection.collectionName)
        assertEquals("Collection", collection.description)
        assertEquals("gateway", collection.imageUrl)
        assertEquals("ERC721", collection.type)
        assertEquals(2, collection.balance)
        assertEquals(10, collection.collectionSize)
        assertSame(token, collection.tokens.single())
        val copy = collection.copy(emptySequence())
        assertTrue(copy.tokens.none())
        assertEquals(collection.collectionName, copy.collectionName)
        assertEquals(collection.balance, copy.balance)
    }

    @Test
    fun emptyTokenCollectionHasNoMediaAndZeroCounts() {
        val collection = CollectionWithTokensImpl(
            NFTResponse.TokensCollection(emptySequence(), null), "chain", "Ethereum", emptySequence()
        )
        assertEquals("", collection.contractAddress)
        assertEquals("", collection.collectionName)
        assertEquals("", collection.description)
        assertEquals("", collection.imageUrl)
        assertEquals("", collection.type)
        assertEquals(0, collection.balance)
        assertEquals(0, collection.collectionSize)
    }

    private fun tokenInfo(
        title: String? = null,
        balance: String? = null,
        id: String? = "0x2a",
        media: List<TokenInfo.Media>? = null,
        metadata: TokenInfo.TokenMetadata? = null,
        contractMetadata: TokenInfo.ContractMetadata? = contractMetadata()
    ) = TokenInfo(
        Contract("0xabcdef123456"), id?.let { TokenId(it, null) }, title, balance, null, media,
        metadata, contractMetadata, null
    )

    private fun contractMetadata(sea: TokenInfo.ContractMetadata.OpenSea? = null) =
        TokenInfo.ContractMetadata("Collection", "BIRD", "10", "ERC721", "creator", null, sea)

    private fun contractInfo(
        title: String? = null,
        name: String? = null,
        media: List<TokenInfo.Media>? = null,
        sea: TokenInfo.ContractMetadata.OpenSea? = null,
        totalBalance: Int? = null,
        totalSupply: Int? = null
    ) = ContractInfo(
        "0xabcdef123456", totalBalance, 2, false, null, name, title, "BIRD",
        totalSupply, "ERC1155", null, null, sea, media
    )
}
