package jp.co.soramitsu.nft.impl.presentation.collection.models

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class NFTsScreenViewModelsTest {
    @Test
    fun ownedCollectionShowsHeaderAndSendActionForEachToken() {
        val token = token(true)
        val selected = mutableListOf<NFT>()
        val sent = mutableListOf<NFT>()
        val model = ScreenModel.ReadyToRender(collection(token), { selected += it }, { sent += it })
        val views = model.views.toList()
        assertEquals(3, views.size)
        assertTrue(views.first() is NFTsScreenView.ScreenHeader)
        val section = views[1] as NFTsScreenView.SectionHeader
        assertEquals(TextModel.ResId(R.string.nft_collection_my_nfts), (section.title as Loadable.ReadyToRender<*>).data)
        val item = views[2] as NFTsScreenView.ItemModel.WithButtonDecorator
        assertEquals(BigInteger.ONE, item.key)
        assertEquals(TextModel.SimpleString("Bird"), (item.title as Loadable.ReadyToRender<*>).data)
        assertEquals(TextModel.SimpleString("Token description"), (item.description as Loadable.ReadyToRender<*>).data)
        assertEquals(TextModel.ResId(R.string.common_action_send), item.buttonText)
        assertEquals(R.drawable.ic_send_outlined, item.buttonImage.id)
        item.onItemClick()
        item.onButtonClick()
        assertSame(token, selected.single())
        assertSame(token, sent.single())
    }

    @Test
    fun unownedCollectionShowsAvailableSectionAndShareAction() {
        val token = token(false)
        var shared: NFT? = null
        val views = ScreenModel.ReadyToRender(collection(token), {}, { shared = it }).views.toList()
        assertEquals(2, views.size)
        val section = views.first() as NFTsScreenView.SectionHeader
        val title = (section.title as Loadable.ReadyToRender<*>).data as TextModel.ResIdWithArgs
        assertEquals(R.string.nft_collection_available_nfts, title.id)
        assertEquals("Birds", title.args.single())
        val item = views.last() as NFTsScreenView.ItemModel.WithButtonDecorator
        assertEquals(TextModel.ResId(R.string.common_share), item.buttonText)
        assertEquals(R.drawable.ic_share_arrow_white_24, item.buttonImage.id)
        item.onButtonClick()
        assertSame(token, shared)
    }

    @Test
    fun missingTokensAndEmptyResultsBecomeEmptyPlaceholder() {
        val emptyCollection = mockk<NFTCollection.Loaded.Result.Collection.WithTokens> {
            every { tokens } returns emptySequence()
        }
        for (result in listOf(emptyCollection, NFTCollection.Loaded.Result.Empty("chain", "Ethereum"))) {
            assertEquals(listOf(NFTsScreenView.EmptyPlaceHolder), ScreenModel.ReadyToRender(result, {}, {}).views)
        }
    }

    @Test
    fun reloadContainsHeaderSectionAndSixLoadingItems() {
        val views = ScreenModel.Reloading.views.toList()
        assertEquals(8, views.size)
        assertTrue((views[0] as NFTsScreenView.ScreenHeader).thumbnail is Loadable.InProgress)
        assertTrue((views[1] as NFTsScreenView.SectionHeader).title is Loadable.InProgress)
        val items = views.filterIsInstance<NFTsScreenView.ItemModel>()
        assertEquals(6, items.size)
        assertEquals(6, items.map { it.key }.distinct().size)
        assertTrue(items.all { it.title is Loadable.InProgress && it.description is Loadable.InProgress })
    }

    private fun token(owned: Boolean) = mockk<NFT> {
        every { isUserOwnedToken } returns owned
        every { tokenId } returns BigInteger.ONE
        every { title } returns "Bird"
        every { thumbnail } returns ""
        every { description } returns "Token description"
        every { collectionName } returns "Birds"
    }

    private fun collection(token: NFT) = mockk<NFTCollection.Loaded.Result.Collection.WithTokens> {
        every { tokens } returns sequenceOf(token)
        every { imageUrl } returns ""
        every { description } returns "Collection description"
    }
}
