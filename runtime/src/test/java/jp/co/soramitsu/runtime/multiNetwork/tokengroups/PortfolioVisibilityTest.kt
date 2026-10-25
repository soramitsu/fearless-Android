package jp.co.soramitsu.runtime.multiNetwork.tokengroups

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonMainnetChainId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PortfolioVisibilityTest {

    private val visibility = PortfolioVisibility(bundledConfigSource())
    private val reviewedChains = reviewedRegistryChains()

    private val soraMainnet = reviewedChains.chain(SORA_MAINNET_CHAIN_ID)
    private val xLayer = reviewedChains.chain(X_LAYER_CHAIN_ID)
    private val westend = reviewedChains.chain(WESTEND_CHAIN_ID)
    private val cagaAnkara = reviewedChains.chain(CAGA_ANKARA_CHAIN_ID)
    private val polkadot = reviewedChains.chain(POLKADOT_CHAIN_ID)
    private val assetHub = reviewedChains.chain(POLKADOT_ASSET_HUB_CHAIN_ID)

    @Test
    fun `unknown balance remains visible without authorizing spend or bypassing preferences`() {
        val asset = soraMainnet.assets.single { it.symbol.equals("val", ignoreCase = true) }
        assertTrue(isVisible(null, null, soraMainnet, asset))
        assertFalse(isVisible(false, null, soraMainnet, asset))
        assertEquals(AssetSearchSection.Portfolio, searchSection(null, null, soraMainnet, asset))
        assertFalse(isVisible(null, null, westend, westend.utilityAsset!!))
        assertTrue(isVisible(null, null, westend, westend.utilityAsset!!, showTestnets = true))
        assertFalse(visibility.isPickableToSpend(null, soraMainnet, true, true))
    }

    @Test
    fun `zero defaults on the reviewed registry are exactly the ranked mainnet primary utilities plus TON`() {
        val expected = reviewedChains
            .filter { it.rank != null && !visibility.isTestnet(it) }
            .mapNotNull { chain -> chain.utilityAsset?.let { chain.id to it.id } }
            .toSet() + (tonMainnetChainId to TON_ASSET_ID)

        assertEquals(15 + 1, expected.size)
        assertEquals(expected, zeroDefaults(reviewedChains))
    }

    @Test
    fun `the bundled offline registry never makes a testnet or a secondary token a zero default`() {
        val offlineChains = bundledRegistryChains()
        val zeroDefaults = zeroDefaults(offlineChains)
        val chainsById = offlineChains.associateBy(Chain::id)

        assertTrue(zeroDefaults.isNotEmpty())
        zeroDefaults.forEach { (chainId, assetId) ->
            val chain = chainsById.getValue(chainId)
            assertFalse("${chain.name} is a testnet", visibility.isTestnet(chain))
            assertEquals("${chain.name} $assetId is not its primary utility", chain.utilityAsset?.id, assetId)
        }
    }

    @Test
    fun `secondary isNative token (SORA VAL) is not a zero default`() {
        val valToken = soraMainnet.assets.single { it.symbol.equals("val", ignoreCase = true) }

        assertTrue(valToken.isNative == true)
        assertFalse(isZeroDefault(soraMainnet, valToken, isFavourite = true, isOnlySelectedNetwork = true))
        assertTrue(isZeroDefault(soraMainnet, soraMainnet.utilityAsset!!))
    }

    @Test
    fun `second isUtility asset (X Layer WOKB) is not a zero default`() {
        val okb = xLayer.assets.single { it.symbol.equals("okb", ignoreCase = true) }
        val wokb = xLayer.assets.single { it.symbol.equals("wokb", ignoreCase = true) }

        assertTrue(wokb.isUtility)
        assertTrue(isZeroDefault(xLayer, okb))
        assertFalse(isZeroDefault(xLayer, wokb, isFavourite = true, isOnlySelectedNetwork = true))
    }

    @Test
    fun `testnet utility with rank (Westend rank 108) is not a zero default`() {
        assertEquals(108, westend.rank)
        assertFalse(isZeroDefault(westend, westend.utilityAsset!!))
    }

    @Test
    fun `pinned or selected testnet is not a zero default`() {
        assertFalse(isZeroDefault(westend, westend.utilityAsset!!, isFavourite = true))
        assertFalse(isZeroDefault(cagaAnkara, cagaAnkara.utilityAsset!!, isOnlySelectedNetwork = true))
    }

    @Test
    fun `pinned unranked mainnet shows its utility at zero`() {
        val dot = assetHub.utilityAsset!!

        assertEquals(null, assetHub.rank)
        assertFalse(isZeroDefault(assetHub, dot))
        assertTrue(isZeroDefault(assetHub, dot, isFavourite = true))
    }

    @Test
    fun `selected unranked network shows its utility at zero`() {
        assertTrue(isZeroDefault(assetHub, assetHub.utilityAsset!!, isOnlySelectedNetwork = true))
    }

    @Test
    fun `a vetoed send network has no zero default`() {
        val bitcoin = appOwnedChain(UniversalWalletRegistry.bitcoinMainnet.chainId, "BTC")

        assertTrue(isZeroDefault(bitcoin, bitcoin.utilityAsset!!))
        assertFalse(isZeroDefault(bitcoin, bitcoin.utilityAsset!!, isFavourite = true, isSendVetoed = true))
    }

    @Test
    fun `app-owned Bitcoin and Solana are zero defaults under either of their ids`() {
        listOf(
            UniversalWalletRegistry.bitcoinMainnet.chainId to "BTC",
            UniversalWalletRegistry.bitcoinMainnet.id to "BTC",
            UniversalWalletRegistry.solanaMainnet.chainId to "SOL",
            UniversalWalletRegistry.solanaMainnet.id to "SOL"
        ).forEach { (chainId, assetId) ->
            val chain = appOwnedChain(chainId, assetId)
            assertTrue(chainId, isZeroDefault(chain, chain.utilityAsset!!))
        }
        listOf(
            UniversalWalletRegistry.bitcoinTestnet.id to "BTC",
            UniversalWalletRegistry.solanaDevnet.chainId to "SOL"
        ).forEach { (chainId, assetId) ->
            val chain = appOwnedChain(chainId, assetId)
            assertTrue(visibility.isTestnet(chain))
            assertFalse(chainId, isZeroDefault(chain, chain.utilityAsset!!, isFavourite = true))
        }
    }

    @Test
    fun `explicit shown wins at zero, explicit hidden never shows, auto needs a balance or the zero rule`() {
        val secondary = soraMainnet.assets.single { it.symbol.equals("pswap", ignoreCase = true) }

        assertTrue(isVisible(enabled = true, total = BigDecimal.ZERO, chain = soraMainnet, asset = secondary))
        assertFalse(isVisible(enabled = false, total = BigDecimal.TEN, chain = polkadot, asset = polkadot.utilityAsset!!))
        assertFalse(isVisible(enabled = null, total = BigDecimal.ZERO, chain = soraMainnet, asset = secondary))
        assertTrue(isVisible(enabled = null, total = BigDecimal.ONE, chain = soraMainnet, asset = secondary))
        assertTrue(isVisible(enabled = null, total = BigDecimal.ZERO, chain = polkadot, asset = polkadot.utilityAsset!!))
    }

    @Test
    fun `testnet rows appear only behind the Testnets toggle or as the selected network`() {
        val wnd = westend.utilityAsset!!

        assertFalse(isVisible(enabled = null, total = BigDecimal.TEN, chain = westend, asset = wnd))
        assertFalse(isVisible(enabled = true, total = BigDecimal.TEN, chain = westend, asset = wnd))
        assertTrue(isVisible(enabled = null, total = BigDecimal.TEN, chain = westend, asset = wnd, showTestnets = true))
        assertTrue(isVisible(enabled = true, total = BigDecimal.ZERO, chain = westend, asset = wnd, showTestnets = true))
        assertFalse(
            "a testnet is never a zero default, even with the toggle on",
            isVisible(enabled = null, total = BigDecimal.ZERO, chain = westend, asset = wnd, showTestnets = true)
        )
        assertTrue(isVisible(enabled = null, total = BigDecimal.TEN, chain = westend, asset = wnd, isOnlySelectedNetwork = true))
        assertFalse(isVisible(enabled = false, total = BigDecimal.TEN, chain = westend, asset = wnd, showTestnets = true))
    }

    @Test
    fun `isCountedInTotals is false for hidden assets and for every testnet`() {
        assertTrue(visibility.isCountedInTotals(enabled = null, chain = polkadot))
        assertTrue(visibility.isCountedInTotals(enabled = true, chain = polkadot))
        assertFalse(visibility.isCountedInTotals(enabled = false, chain = polkadot))
        assertFalse(visibility.isCountedInTotals(enabled = true, chain = westend))
        assertFalse("listed only by the shared testnet file", visibility.isCountedInTotals(enabled = null, chain = cagaAnkara))
        assertFalse(visibility.isCountedInTotals(enabled = null, chain = reviewedChains.chain(CANARY_MATRIXCHAIN_CHAIN_ID)))
    }

    @Test
    fun `search lists portfolio rows, then explicit hides, then other reviewed assets`() {
        val dot = polkadot.utilityAsset!!
        val pswap = soraMainnet.assets.single { it.symbol.equals("pswap", ignoreCase = true) }
        val detected = pswap.copy(id = "detected", type = ChainAssetType.Unknown, priceId = null)

        assertEquals(AssetSearchSection.Portfolio, searchSection(enabled = null, total = BigDecimal.ZERO, chain = polkadot, asset = dot))
        assertEquals(AssetSearchSection.Portfolio, searchSection(enabled = null, total = BigDecimal.ONE, chain = soraMainnet, asset = pswap))
        assertEquals(AssetSearchSection.Portfolio, searchSection(enabled = true, total = BigDecimal.ZERO, chain = soraMainnet, asset = pswap))
        assertEquals(AssetSearchSection.Hidden, searchSection(enabled = false, total = BigDecimal.TEN, chain = polkadot, asset = dot))
        assertEquals(AssetSearchSection.Hidden, searchSection(enabled = false, total = BigDecimal.ZERO, chain = soraMainnet, asset = detected))
        assertEquals(
            "a reviewed token the wallet does not hold yet can be found",
            AssetSearchSection.Other,
            searchSection(enabled = null, total = BigDecimal.ZERO, chain = soraMainnet, asset = pswap)
        )
        assertEquals(
            "a detected token outside the portfolio is never offered by Search",
            null,
            searchSection(enabled = null, total = BigDecimal.ZERO, chain = soraMainnet, asset = detected)
        )
    }

    @Test
    fun `search keeps testnets behind the Testnets toggle, as the list does`() {
        val wnd = westend.utilityAsset!!

        assertEquals(null, searchSection(enabled = null, total = BigDecimal.TEN, chain = westend, asset = wnd))
        assertEquals(null, searchSection(enabled = false, total = BigDecimal.TEN, chain = westend, asset = wnd))
        assertEquals(null, searchSection(enabled = null, total = BigDecimal.ZERO, chain = cagaAnkara, asset = cagaAnkara.utilityAsset!!))
        assertEquals(
            AssetSearchSection.Portfolio,
            searchSection(enabled = null, total = BigDecimal.TEN, chain = westend, asset = wnd, showTestnets = true)
        )
        assertEquals(
            AssetSearchSection.Other,
            searchSection(enabled = null, total = BigDecimal.ZERO, chain = westend, asset = wnd, showTestnets = true)
        )
        assertEquals(
            AssetSearchSection.Portfolio,
            searchSection(enabled = null, total = BigDecimal.TEN, chain = westend, asset = wnd, isOnlySelectedNetwork = true)
        )
    }

    @Test
    fun `spend pickers list any sendable balance, hidden or not, and testnets only behind the toggle or as the picker network`() {
        assertTrue(visibility.isPickableToSpend(BigDecimal.ONE, polkadot, isPickerNetwork = false, showTestnets = false))
        assertFalse(visibility.isPickableToSpend(BigDecimal.ZERO, polkadot, isPickerNetwork = true, showTestnets = true))
        assertFalse(visibility.isPickableToSpend(BigDecimal("-1"), polkadot, isPickerNetwork = true, showTestnets = true))
        assertFalse(visibility.isPickableToSpend(BigDecimal.ONE, westend, isPickerNetwork = false, showTestnets = false))
        assertTrue(visibility.isPickableToSpend(BigDecimal.ONE, westend, isPickerNetwork = true, showTestnets = false))
        assertTrue(visibility.isPickableToSpend(BigDecimal.ONE, westend, isPickerNetwork = false, showTestnets = true))
    }

    @Test
    fun `receive pickers list every reviewed asset of the network, held or not, and never a detected token`() {
        val usdt = assetHub.assets.first { it.symbol.equals("usdt", ignoreCase = true) }
        val detected = usdt.copy(id = "detected", type = ChainAssetType.Unknown, priceId = null)

        assetHub.assets.filterNot { it.isDetectedAsset() }.forEach { asset ->
            assertTrue(asset.symbol, visibility.isPickableToReceive(assetHub, asset, isPickerNetwork = true, showTestnets = false))
        }
        assertFalse(visibility.isPickableToReceive(assetHub, detected, isPickerNetwork = true, showTestnets = true))
        assertFalse(visibility.isPickableToReceive(westend, westend.utilityAsset!!, isPickerNetwork = false, showTestnets = false))
        assertTrue(visibility.isPickableToReceive(westend, westend.utilityAsset!!, isPickerNetwork = true, showTestnets = false))
    }

    @Test
    fun `a testnet token never carries a price, by the registry option or the shared list`() {
        val canary = reviewedChains.chain(CANARY_MATRIXCHAIN_CHAIN_ID)
        val pricedTestnetTokens = (canary.assets + cagaAnkara.assets + westend.assets).filter { it.priceId != null }
        assertTrue("the reviewed registry prices Canary Matrixchain cENJ", canary.assets.any { it.priceId != null })

        pricedTestnetTokens.forEach { asset ->
            assertTrue("${asset.symbol} passes the chain-id-only check", visibility.hasTrustedPrice(asset.chainId, asset.id))
            assertFalse("${asset.symbol} on ${asset.chainName} has no value", visibility.hasTrustedPrice(asset))
        }
        val cagaListedOnly = cagaAnkara.assets.first().copy(isTestNet = false)
        assertFalse("listed only by the shared testnet file", visibility.hasTrustedPrice(cagaListedOnly))
        assertTrue(visibility.hasTrustedPrice(polkadot.assets.single { it.id == DOT_ASSET_ID }))
    }

    @Test
    fun `every bundled untrusted asset is a priced registry asset that gets no trusted price`() {
        val untrusted = requireNotNull(bundledConfig().untrustedAssets)

        assertTrue(untrusted.entries.isNotEmpty())
        untrusted.entries.forEach { entry ->
            val chain = reviewedChains.chain(entry.chainId)
            val asset = chain.assets.single { it.id == entry.assetId }

            assertEquals("${entry.symbol}: the registry still binds the distrusted price", entry.registryPriceId, asset.priceId)
            assertFalse(visibility.isTestnet(chain))
            assertFalse(asset.isDetectedAsset())
            assertFalse(visibility.hasTrustedPrice(chain.id, asset.id))
            assertFalse(visibility.hasTrustedPrice(asset))
            chain.assets.filter { it.id != asset.id }.forEach { other ->
                assertTrue("${other.symbol} on ${chain.name} keeps its price", visibility.hasTrustedPrice(chain.id, other.id))
            }
        }
        assertTrue(visibility.hasTrustedPrice(POLKADOT_CHAIN_ID, DOT_ASSET_ID))
    }

    @Test
    fun `an untrusted asset is never part of a resolved token group`() {
        val config = bundledConfig()
        val resolved = resolveTokenGroups(config.tokenGroups, reviewedChains, config::isTestnet)

        requireNotNull(config.untrustedAssets).entries.forEach { entry ->
            assertEquals(null, resolved.groupOf(entry.chainId, entry.assetId))
        }
    }

    @Test
    fun `app-owned networks are matched for price trust under either of their ids`() {
        val distrustBitcoin = PortfolioVisibility(
            SharedPortfolioConfigSource(
                readAsset = { name ->
                    if (name == SharedPortfolioConfigSource.TOKEN_GROUPS_ASSET) {
                        bundledCatalogJson().apply {
                            getAsJsonArray("untrustedAssets").add(
                                getAsJsonArray("untrustedAssets")[0].deepCopy().asJsonObject.apply {
                                    addProperty("chainId", UniversalWalletRegistry.bitcoinMainnet.chainId)
                                    addProperty("assetId", "BTC")
                                }
                            )
                        }.toBytes()
                    } else {
                        bundledAsset(name)
                    }
                }
            )
        )

        assertFalse(distrustBitcoin.hasTrustedPrice(UniversalWalletRegistry.bitcoinMainnet.chainId, "BTC"))
        assertFalse(distrustBitcoin.hasTrustedPrice(UniversalWalletRegistry.bitcoinMainnet.id, "BTC"))
        assertTrue(visibility.hasTrustedPrice(UniversalWalletRegistry.bitcoinMainnet.id, "BTC"))
    }

    @Test
    fun `an unusable zero list falls back to the primary utility of ranked mainnets`() {
        val fallback = PortfolioVisibility(
            SharedPortfolioConfigSource(
                readAsset = { name ->
                    if (name == SharedPortfolioConfigSource.DEFAULT_VISIBLE_AT_ZERO_ASSET) error("unreadable") else bundledAsset(name)
                }
            )
        )
        val expected = reviewedChains
            .filter { it.rank != null && !fallback.isTestnet(it) }
            .mapNotNull { chain -> chain.utilityAsset?.let { chain.id to it.id } }
            .toSet()

        assertEquals(expected, zeroDefaults(reviewedChains, fallback))
        assertTrue(fallback.isZeroDefault(assetHub, assetHub.utilityAsset!!, isFavourite = true, isOnlySelectedNetwork = false, isSendVetoed = false))
    }

    @Test
    fun `shared zero-default golden cases hold for every wallet type`() {
        val bundled = testResource(PARITY_CASES_RESOURCE)
        assertEquals(
            "parity-cases.v1.json differs from the workspace root copy it was taken from",
            PARITY_CASES_SHA256,
            sha256Hex(bundled)
        )
        val cases = JsonParser.parseString(bundled.decodeToString()).asJsonObject.getAsJsonArray("zeroDefaults")
        val chains = reviewedChains + listOf(
            appOwnedChain(UniversalWalletRegistry.bitcoinMainnet.chainId, "BTC", name = "Bitcoin"),
            appOwnedChain(UniversalWalletRegistry.solanaMainnet.chainId, "SOL", name = "Solana"),
            appOwnedChain(UniversalWalletRegistry.bitcoinTestnet.chainId, "BTC", name = "Bitcoin Testnet"),
            appOwnedChain(UniversalWalletRegistry.solanaDevnet.chainId, "SOL", name = "Solana Devnet")
        )

        assertEquals(6, cases.size())
        cases.map(JsonElement::getAsJsonObject).forEach { case ->
            val accounts = case.strings("accounts").toSet()
            val paused = case.strings("sendPausedChainIds").toSet()
            val actual = chains
                .filter { accountFor(it) in accounts }
                .flatMap { chain ->
                    chain.assets.filter { asset ->
                        visibility.isZeroDefault(
                            chain = chain,
                            asset = asset,
                            isFavourite = false,
                            isOnlySelectedNetwork = false,
                            isSendVetoed = chain.id in paused
                        )
                    }.map { "${it.symbol}@${chain.name}" }
                }

            assertEquals(case["id"].asString, case.strings("expected").sorted(), actual.sorted())
        }
    }

    private fun zeroDefaults(chains: List<Chain>, rule: PortfolioVisibility = visibility): Set<Pair<String, String>> {
        return chains.flatMap { chain ->
            chain.assets
                .filter { rule.isZeroDefault(chain, it, isFavourite = false, isOnlySelectedNetwork = false, isSendVetoed = false) }
                .map { chain.id to it.id }
        }.toSet()
    }

    private fun isZeroDefault(
        chain: Chain,
        asset: Asset,
        isFavourite: Boolean = false,
        isOnlySelectedNetwork: Boolean = false,
        isSendVetoed: Boolean = false
    ): Boolean = visibility.isZeroDefault(chain, asset, isFavourite, isOnlySelectedNetwork, isSendVetoed)

    private fun isVisible(
        enabled: Boolean?,
        total: BigDecimal?,
        chain: Chain,
        asset: Asset,
        isOnlySelectedNetwork: Boolean = false,
        showTestnets: Boolean = false
    ): Boolean = visibility.isVisible(
        enabled = enabled,
        total = total,
        chain = chain,
        asset = asset,
        isFavourite = false,
        isOnlySelectedNetwork = isOnlySelectedNetwork,
        isSendVetoed = false,
        showTestnets = showTestnets
    )

    private fun searchSection(
        enabled: Boolean?,
        total: BigDecimal?,
        chain: Chain,
        asset: Asset,
        isOnlySelectedNetwork: Boolean = false,
        showTestnets: Boolean = false
    ): AssetSearchSection? = visibility.searchSection(
        enabled = enabled,
        total = total,
        chain = chain,
        asset = asset,
        isFavourite = false,
        isOnlySelectedNetwork = isOnlySelectedNetwork,
        isSendVetoed = false,
        showTestnets = showTestnets
    )

    // The wallet account each network needs (root `scripts/portfolio-config.mjs` ACCOUNT_FOR_ECOSYSTEM).
    private fun accountFor(chain: Chain): String = when {
        chain.isUniversalWalletBitcoin() -> "bitcoin"
        chain.isUniversalWalletSolana() -> "solana"
        chain.ecosystem == Ecosystem.Ton -> "ton"
        chain.isEthereumBased || chain.ecosystem == Ecosystem.Ethereum || chain.ecosystem == Ecosystem.EthereumBased -> "evm"
        else -> "substrate"
    }

    private fun JsonObject.strings(name: String): List<String> =
        getAsJsonArray(name)?.map { it.asString }.orEmpty()

    private fun appOwnedChain(chainId: String, assetId: String, name: String = chainId): Chain {
        val asset = Asset(
            id = assetId,
            name = assetId,
            symbol = assetId,
            iconUrl = "",
            chainId = chainId,
            chainName = name,
            chainIcon = null,
            isTestNet = false,
            priceId = null,
            precision = 8,
            staking = Asset.StakingType.UNSUPPORTED,
            purchaseProviders = null,
            supportStakingPool = false,
            isUtility = true,
            type = ChainAssetType.Normal,
            currencyId = null,
            existentialDeposit = null,
            color = null,
            isNative = true
        )
        return polkadot.copy(
            id = chainId,
            rank = null,
            name = name,
            assets = listOf(asset),
            isEthereumBased = false,
            isTestNet = false,
            ecosystem = Ecosystem.Substrate,
            externalApi = null
        )
    }

    private companion object {
        const val PARITY_CASES_RESOURCE = "portfolio/parity-cases.v1.json"

        /** sha256 of root `config/portfolio/parity-cases.v1.json`; copy the file again when it changes. */
        const val PARITY_CASES_SHA256 = "aab5746a3bf2aa75d661266c75ebea37138cde8aacd426e08508c06d2e5daa81"

        const val SORA_MAINNET_CHAIN_ID = "7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
        const val X_LAYER_CHAIN_ID = "196"
        const val CANARY_MATRIXCHAIN_CHAIN_ID = "a37725fd8943d2a524cb7ecc65da438f9fa644db78ba24dcd0003e2f95645e8f"
        const val TON_ASSET_ID = "2ba4723a-74b4-4a6f-a888-e51937773807-239"
    }
}
