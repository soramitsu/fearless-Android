package jp.co.soramitsu.coredb.dao

import io.mockk.*
import jp.co.soramitsu.account.impl.domain.AssetNotNeedAccountUseCaseImpl
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AssetMetadataWriterTest {
    private fun useCase(dao: RecordingFreshAssetDao, provider: Boolean = false, usd: Boolean = true): AssetNotNeedAccountUseCaseImpl {
        val asset = mockk<Asset> {
            every { id } returns "asset"
            every { priceId } returns "new-price"
            every { priceProvider } returns if (provider) mockk {
                every { type } returns Asset.PriceProviderType.Chainlink
                every { id } returns "chainlink-price"
            } else null
        }
        val chain = mockk<Chain> { every { assets } returns listOf(asset) }
        val registry = mockk<ChainRegistry> { coEvery { getChain("chain") } returns chain }
        val prefs = mockk<Preferences> { every { getString(any(), any()) } returns if (usd) "usd" else "eur" }
        return AssetNotNeedAccountUseCaseImpl(registry, dao, mockk(), SelectedFiat(prefs))
    }
    @Test fun actualUseCaseMarksExistingWithoutReadingCachedAmountsOrPrice() = runBlocking {
        val dao = RecordingFreshAssetDao(); useCase(dao).markChainAssetsNotNeed("chain", 7)
        assertEquals(listOf("mark"), dao.operations.map { it.kind })
        assertEquals(setOf("metaId", "accountId", "chainId", "assetId"), dao.operations.single().params.keys)
        assertEquals(mapOf("blob" to ""), dao.operations.single().params["accountId"])
    }
    @Test fun actualUseCaseCreatesAutoMetadataRowWithCatalogPrice() = runBlocking {
        val dao = RecordingFreshAssetDao(); dao.markResults.clear(); dao.markResults.addAll(listOf(0, 1))
        useCase(dao).markChainAssetsNotNeed("chain", 7)
        assertEquals(listOf("mark", "insert-ignore", "mark"), dao.operations.map { it.kind })
        assertNull(dao.operations[1].params["enabled"]); assertEquals("new-price", dao.operations[1].params["tokenPriceId"])
    }
    @Test fun actualUseCaseRetainsUsdChainlinkAndNonUsdCatalogChoice() = runBlocking {
        for (usd in listOf(true, false)) {
            val dao = RecordingFreshAssetDao(); dao.markResults.clear(); dao.markResults.addAll(listOf(0, 1))
            useCase(dao, true, usd).markChainAssetsNotNeed("chain", 7)
            assertEquals(if (usd) "chainlink-price" else "new-price", dao.operations[1].params["tokenPriceId"])
        }
    }
}
