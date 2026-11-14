package jp.co.soramitsu.wallet.impl.presentation.balance.list

import com.google.gson.JsonParser
import java.io.File
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups
import jp.co.soramitsu.wallet.impl.presentation.manageassets.ManageAssetItemState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shipped network list. A row's key must be unique or a LazyColumn crashes and a list merges two tokens, and the
 * runtime identity is not: these pairs of one chain share a currency id.
 */
class CatalogAssetRowKeysTest {
    private data class CatalogAsset(val chainId: String, val id: String, val symbol: String, val canonical: String)

    private val catalog: List<CatalogAsset> = run {
        val file = File("../remote-config/production/chains/v13/chains.json")
        assertTrue("run from the module directory: ${file.absolutePath}", file.isFile)
        JsonParser.parseString(file.readText()).asJsonArray.flatMap { chain ->
            val chainId = chain.asJsonObject["chainId"].asString
            chain.asJsonObject["assets"].asJsonArray.map { element ->
                val asset = element.asJsonObject
                val id = asset["id"].asString
                val currency = asset["currencyId"]?.takeIf { !it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() }
                CatalogAsset(chainId, id, asset["symbol"].asString, "substrate:$chainId:${currency ?: id}")
            }
        }
    }

    private val pairs = listOf(setOf("tdot", "glmr"), setOf("rmrk", "taiksm"), setOf("vksm", "vsksm"))

    private fun state(asset: CatalogAsset) = AssetListItemViewState(
        index = null, assetIconUrl = "", assetName = asset.symbol, assetChainName = asset.chainId, assetSymbol = asset.symbol,
        assetTokenFiat = null, assetTokenRate = null, assetTransferableBalance = "1", assetTransferableBalanceFiat = null,
        assetChainUrls = emptyMap(), chainId = asset.chainId, chainAssetId = asset.id, isSupported = true, isHidden = false,
        isTestnet = false, canonicalAssetKey = asset.canonical
    )

    private fun portfolio(asset: CatalogAsset) = PortfolioAsset(state(asset), BigDecimal.ONE, null, "$")

    private fun manageRow(asset: CatalogAsset) = ManageAssetItemState(
        canonicalAssetKey = asset.canonical, id = asset.id, imageUrl = null, chainName = asset.chainId, assetName = null,
        symbol = asset.symbol, amount = "1", fiatAmount = null, chainId = asset.chainId, isChecked = true, showEdit = false,
        isZeroAmount = false
    )

    private val collisions: List<List<CatalogAsset>> get() = catalog.groupBy { it.canonical }.values.filter { it.size > 1 }

    @Test fun theKnownPairsStillShareTheirRuntimeIdentity() {
        val found = collisions.map { pair -> pair.map { it.symbol.lowercase() }.toSet() }
        pairs.forEach { assertTrue("$it among $found", it in found) }
    }

    @Test fun everyRowKeyInTheShippedListIsUnique() {
        assertEquals(catalog.size, catalog.map { state(it).rowKey }.toSet().size)
        assertEquals(catalog.size, catalog.map { state(it).key }.toSet().size)
        assertEquals(catalog.size, catalog.map { manageRow(it).listKey }.toSet().size)
    }

    @Test fun theHomeKeepsBothTokensOfAPairInEachLens() {
        collisions.forEach { pair ->
            for (lens in PortfolioLens.entries) {
                val rows = PortfolioListBuilder.build(
                    visible = pair.map(::portfolio),
                    hidden = emptyList(),
                    groups = ResolvedTokenGroups(emptyList()),
                    ui = PortfolioUiState(lens = lens),
                    selectedNetwork = null,
                    walletHasPositiveBalance = true
                ).filterIsInstance<PortfolioListItem.AssetRow>()
                assertEquals("$lens ${pair.map { it.symbol }}", pair.map { it.symbol }.toSet(), rows.map { it.asset.state.assetSymbol }.toSet())
                assertEquals(rows.size, rows.map { it.key }.toSet().size)
            }
        }
    }
}
