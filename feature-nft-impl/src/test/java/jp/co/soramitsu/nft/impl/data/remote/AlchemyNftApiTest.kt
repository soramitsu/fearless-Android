package jp.co.soramitsu.nft.impl.data.remote

import jp.co.soramitsu.nft.impl.di.NFTModule
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class AlchemyNftApiTest {
    private var body = "{}"
    private val requests = mutableListOf<Request>()
    private val api = NFTModule().provideAlchemyNftApi(
        OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody())
                .build()
        }.build()
    )

    @Test
    fun metadataPreservesTokenContractAndMediaFields(): Unit = runBlocking {
        body = TOKEN
        val token = api.getNFTMetadata(URL, "0xabc", "42")
        assertEquals("0xabc", token.contract?.address)
        assertEquals("0x2a", token.id?.tokenId)
        assertEquals("ERC721", token.id?.tokenMetadata?.tokenType)
        assertEquals("Bird #42", token.title)
        assertEquals("2", token.balance)
        assertEquals("Token description", token.description)
        val media = token.media!!.single()
        assertEquals("gateway", media.gateway)
        assertEquals("thumbnail", media.thumbnail)
        assertEquals("raw", media.raw)
        assertEquals("png", media.format)
        assertEquals(BigInteger("2048"), media.bytes)
        val metadata = token.metadata!!
        assertEquals("Bird", metadata.name)
        assertEquals("Metadata description", metadata.description)
        assertEquals("ffffff", metadata.backgroundColor)
        assertEquals("poster", metadata.poster)
        assertEquals("image", metadata.image)
        assertEquals("external", metadata.externalUrl)
        val contract = token.contractMetadata!!
        assertEquals("Collection", contract.name)
        assertEquals("BIRD", contract.symbol)
        assertEquals("100", contract.totalSupply)
        assertEquals("ERC721", contract.tokenType)
        assertEquals("0xcreator", contract.contractDeployer)
        assertEquals(BigInteger("12345"), contract.deployedBlockNumber)
        val sea = contract.openSea!!
        assertEquals(1.5f, sea.floorPrice)
        assertEquals("Bird collection", sea.collectionName)
        assertEquals("verified", sea.safelistRequestStatus)
        assertEquals("collection-image", sea.imageUrl)
        assertEquals("Collection description", sea.description)
        assertEquals("collection-external", sea.externalUrl)
        assertEquals("birds", sea.twitterUsername)
        assertEquals("2026-01-01", sea.lastIngestedAt)
        assertFalse(token.spamInfo!!.isSpam!!)
        assertEquals(listOf("none"), token.spamInfo!!.classifications)
        assertEquals("42", requests.single().url.queryParameter("tokenId"))
        assertEquals("0xabc", requests.single().url.queryParameter("contractAddress"))
    }

    @Test
    fun collectionsAcceptBothAlchemyTokenListNames(): Unit = runBlocking {
        for (key in listOf("nfts", "ownedNfts")) {
            body = """{"$key":[$TOKEN],"nextToken":"next-page"}"""
            val page = api.getNFTCollectionByContactAddress(URL, "0xabc", true, "cursor", 7)
            assertEquals("Bird #42", page.items.single().title)
            assertEquals("next-page", page.nextPage)
            assertEquals("cursor", requests.last().url.queryParameter("startToken"))
            assertEquals("7", requests.last().url.queryParameter("limit"))
        }
    }

    @Test
    fun ownedContractsPreserveBalancesAndCollectionMetadata(): Unit = runBlocking {
        body = """
            {"contracts":[{
                "address":"0xabc","totalBalance":3,"numDistinctTokensOwned":2,
                "isSpam":false,"tokenId":"0x2a","name":"Birds","title":"Bird collection",
                "symbol":"BIRD","totalSupply":100,"tokenType":"ERC1155",
                "contractDeployer":"creator","deployedBlockNumber":"500",
                "opensea":{"collectionName":"OpenSea birds","description":"Bird art"},
                "media":[{"thumbnail":"thumb","gateway":"gateway"}]
            }],"pageKey":"page-2"}
        """.trimIndent()
        val page = api.getUserOwnedContracts(URL, "owner", true, "page-1", 10, listOf("SPAM", "AIRDROPS"))
        val contract = page.items.single()
        assertEquals("0xabc", contract.address)
        assertEquals(3, contract.totalBalance)
        assertEquals(2, contract.numDistinctTokensOwned)
        assertEquals(false, contract.isSpam)
        assertEquals("0x2a", contract.tokenId)
        assertEquals("Birds", contract.name)
        assertEquals("Bird collection", contract.title)
        assertEquals("BIRD", contract.symbol)
        assertEquals(100, contract.totalSupply)
        assertEquals("ERC1155", contract.tokenType)
        assertEquals("creator", contract.contractDeployer)
        assertEquals("500", contract.deployedBlockNumber)
        assertEquals("OpenSea birds", contract.openSea?.collectionName)
        assertEquals("thumb", contract.media?.single()?.thumbnail)
        assertEquals("page-2", page.nextPage)
        assertEquals(listOf("SPAM", "AIRDROPS"), requests.single().url.queryParameterValues("excludeFilters[]"))
        assertEquals("owner", requests.single().url.queryParameter("owner"))
    }

    @Test
    fun absentOptionalFieldsYieldEmptyPagesAndNullableMetadata(): Unit = runBlocking {
        assertTrue(api.getUserOwnedContracts(URL, "owner", excludeFilters = emptyList()).items.none())
        assertTrue(api.getNFTCollectionByContactAddress(URL, "contract", true, limit = 10).items.none())
        assertTrue(api.getNFTOwners(URL, "contract", "1", 10).ownersList.isEmpty())
        val token = api.getNFTMetadata(URL, "contract", "1")
        assertNull(token.title)
        assertNull(token.id)
        assertNull(token.contract)
        assertNull(token.metadata)
        assertNull(token.media)
        assertNull(token.contractMetadata)
    }

    @Test
    fun ownersAndOwnedTokensRetainOrderAndPagination(): Unit = runBlocking {
        body = """{"owners":["owner-a","owner-b"]}"""
        assertEquals(listOf("owner-a", "owner-b"), api.getNFTOwners(URL, "contract", "1", 10).ownersList)
        assertEquals("10", requests.last().url.queryParameter("pageSize"))
        body = """{"ownedNfts":[$TOKEN],"nextToken":"more"}"""
        val page = api.getUserOwnedNFTsByContractAddress(URL, "owner", "contract", excludeFilters = emptyList())
        assertEquals("0x2a", page.items.single().id?.tokenId)
        assertEquals("more", page.nextPage)
        assertEquals("contract", requests.last().url.queryParameter("contractAddresses[]"))
    }

    private companion object {
        const val URL = "https://fixture.invalid/nft"
        val TOKEN = """
            {
                "contract":{"address":"0xabc"},
                "id":{"tokenId":"0x2a","tokenMetadata":{"tokenType":"ERC721"}},
                "title":"Bird #42","balance":"2","description":"Token description",
                "media":[{"gateway":"gateway","thumbnail":"thumbnail","raw":"raw","format":"png","bytes":2048}],
                "metadata":{"name":"Bird","description":"Metadata description","backgroundColor":"ffffff",
                    "poster":"poster","image":"image","externalUrl":"external"},
                "contractMetadata":{"name":"Collection","symbol":"BIRD","totalSupply":"100","tokenType":"ERC721",
                    "contractDeployer":"0xcreator","deployedBlockNumber":12345,
                    "openSea":{"floorPrice":1.5,"collectionName":"Bird collection","safelistRequestStatus":"verified",
                        "imageUrl":"collection-image","description":"Collection description",
                        "externalUrl":"collection-external","twitterUsername":"birds","lastIngestedAt":"2026-01-01"}},
                "spamInfo":{"isSpam":false,"classifications":["none"]}
            }
        """.trimIndent()
    }
}
