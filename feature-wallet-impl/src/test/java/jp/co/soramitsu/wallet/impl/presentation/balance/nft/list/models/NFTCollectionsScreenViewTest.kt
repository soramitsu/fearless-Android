package jp.co.soramitsu.wallet.impl.presentation.balance.nft.list.models

import jp.co.soramitsu.common.compose.models.ScreenLayout
import jp.co.soramitsu.nft.domain.models.NFTCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NFTCollectionsScreenViewTest {

    @Test
    fun `all-network NFT model creates network sections and network-scoped keys`() {
        val contract = "0x1234"
        val first = collection("chain-a", "Alpha", contract)
        val second = collection("chain-b", "Beta", contract)

        val views = ScreenModel.ReadyToRender(
            result = listOf(second, first),
            screenLayout = ScreenLayout.Grid,
            onItemClick = {}
        ).views.toList()

        assertEquals(listOf("Alpha", "Beta"), views.filterIsInstance<NFTCollectionsScreenView.NetworkHeader>().map { it.networkName })
        val itemKeys = views.filterIsInstance<NFTCollectionsScreenView.ItemModel>().map { it.key }
        assertEquals(2, itemKeys.size)
        assertNotEquals(itemKeys[0], itemKeys[1])
    }

    private fun collection(
        chainId: String,
        chainName: String,
        contractAddress: String
    ): NFTCollection.Loaded.Result.Collection = object : NFTCollection.Loaded.Result.Collection {
        override val chainId = chainId
        override val chainName = chainName
        override val contractAddress = contractAddress
        override val collectionName = "Collection"
        override val description = ""
        override val imageUrl = ""
        override val type = "ERC721"
        override val balance = 1
        override val collectionSize = 1
    }
}
