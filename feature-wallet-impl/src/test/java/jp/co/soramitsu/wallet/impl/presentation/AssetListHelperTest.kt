package jp.co.soramitsu.wallet.impl.presentation

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.model.AssetMetadataSource
import jp.co.soramitsu.common.model.AssetMetadataTrust
import jp.co.soramitsu.common.model.AssetPreference
import jp.co.soramitsu.common.model.CanonicalAssetIdentity
import jp.co.soramitsu.common.model.PriceTrust
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.ext.assetKey
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.toAssetState
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.presentation.manageassets.canonicalManageAssetKey
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioExclusions
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.BalanceListItemModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

class AssetListHelperTest {

    @Test
    fun `shadow mode keeps discovered data but suppresses automatic review rows`() {
        val detected = item(
            assetId = "detected",
            chainId = "solana",
            fiat = null,
            preference = AssetPreference.Auto,
            trust = AssetMetadataTrust.Unverified
        )
        val persistedDiscovery = listOf(detected)

        val presented = AssetListHelper.filterForPortfolioPresentation(
            assets = persistedDiscovery,
            assetDiscoveryShadowEnabled = true
        )

        assertTrue(presented.isEmpty())
        assertEquals(listOf(detected), persistedDiscovery)
    }

    @Test
    fun `visible discovery mode surfaces automatic review rows`() {
        val detected = item(
            assetId = "detected",
            chainId = "solana",
            fiat = null,
            preference = AssetPreference.Auto,
            trust = AssetMetadataTrust.Unverified
        )

        assertEquals(
            listOf(detected),
            AssetListHelper.filterForPortfolioPresentation(
                assets = listOf(detected),
                assetDiscoveryShadowEnabled = false
            )
        )
    }

    @Test
    fun `explicitly shown detected asset remains visible during shadow rollout`() {
        val shown = item(
            assetId = "shown",
            chainId = "ton",
            fiat = null,
            preference = AssetPreference.Shown,
            trust = AssetMetadataTrust.Unverified
        )

        assertEquals(
            listOf(shown),
            AssetListHelper.filterForPortfolioPresentation(
                assets = listOf(shown),
                assetDiscoveryShadowEnabled = true
            )
        )
    }

    @Test
    fun `network subtotal sums only the counted items it is given`() {
        val visible = item("visible", "chain", fiat = "3")
        val unverified = item("unverified", "chain", fiat = "12", trust = AssetMetadataTrust.Unverified)
        val unpriced = item("unpriced", "chain", fiat = null, total = "100")

        assertEquals(BigDecimal("3"), AssetListHelper.trustedFiatSubtotal(listOf(visible, unverified, unpriced)))
    }

    @Test
    fun `network order follows the valuation items, so a hidden holding left out cannot promote its network`() {
        val displayedLow = item("low", "low-chain", fiat = "10")
        val displayedHigh = item("high", "high-chain", fiat = null)

        val sorted = AssetListHelper.sortByNetwork(
            assets = listOf(displayedHigh, displayedLow),
            valuationAssets = listOf(displayedLow, displayedHigh)
        )

        assertEquals(listOf("low-chain", "high-chain"), sorted.map { it.canonicalIdentity.chainId })
    }

    @Test
    fun `zero balance priced native does not promote an otherwise unpriced network`() {
        val zUnpricedHolding = item("unknown-z", "z-chain", fiat = null, total = "5")
        val zZeroNative = item("native-z", "z-chain", fiat = "0", total = "0")
        val aUnpricedHolding = item("unknown-a", "a-chain", fiat = null, total = "8")

        val sorted = AssetListHelper.sortByNetwork(
            assets = listOf(zUnpricedHolding, zZeroNative, aUnpricedHolding)
        )

        assertEquals(
            listOf("a-chain", "z-chain", "z-chain"),
            sorted.map { it.canonicalIdentity.chainId }
        )
    }

    @Test
    fun `empty networks follow registry rank and come after a network holding an unpriced token`() {
        val analog = coreAsset("anlog", "ANLOG", "analog", chainId = "analog", isUtility = true)
        val ethereum = coreAsset("eth", "ETH", "ethereum", chainId = "ethereum", isUtility = true)
        val unranked = coreAsset("unranked", "UNR", "unranked", chainId = "aaa-unranked", isUtility = true)
        val unpricedHolding = coreAsset("unpriced", "UNP", null, chainId = "zzz-holding")
        val chains = listOf(
            chain("analog", Ecosystem.Substrate, listOf(analog), rank = 14),
            chain("ethereum", Ecosystem.Ethereum, listOf(ethereum), rank = 1),
            chain("aaa-unranked", Ecosystem.Substrate, listOf(unranked)),
            chain("zzz-holding", Ecosystem.Substrate, listOf(unpricedHolding))
        )
        val items = AssetListHelper.processAssets(
            assets = listOf(
                walletAsset(analog, free = 0),
                walletAsset(unranked, free = 0),
                walletAsset(ethereum, free = 0),
                walletAsset(unpricedHolding)
            ),
            filteredChains = chains
        )

        val sorted = AssetListHelper.sortByNetwork(items)

        assertEquals(
            listOf("zzz-holding", "ethereum", "analog", "aaa-unranked"),
            sorted.map { it.canonicalIdentity.chainId }
        )
    }

    @Test
    fun `row shows the total and adds a transferable line only when part of it cannot be sent`() {
        val locked = item("locked", "chain", fiat = "20", total = "20").copy(transferable = BigDecimal("5"))
        val free = item("free", "chain", fiat = "7", total = "7").copy(transferable = BigDecimal("7"))

        val lockedState = locked.toAssetState()
        assertEquals(BigDecimal("20").formatCrypto(), lockedState.assetTotalBalance)
        assertEquals(BigDecimal("5").formatCrypto(), lockedState.assetTransferableBalance)
        assertTrue(lockedState.showTransferableBalance)
        assertEquals(BigDecimal("20").formatFiat("$"), lockedState.assetTotalBalanceFiat)

        val freeState = free.toAssetState()
        assertEquals(BigDecimal("7").formatCrypto(), freeState.assetTotalBalance)
        assertFalse(freeState.showTransferableBalance)
    }

    @Test
    fun `unpriced row has no total fiat`() {
        val state = item("unpriced", "chain", fiat = null, total = "3").toAssetState()

        assertEquals(BigDecimal("3").formatCrypto(), state.assetTotalBalance)
        assertNull(state.assetTotalBalanceFiat)
    }

    @Test
    fun `processed rows carry the shared testnet rule, not only the registry option`() {
        val asset = coreAsset("caga", "CAGA", "caga-price", chainId = "72778")
        val chain = chain("72778", Ecosystem.Ethereum, listOf(asset))

        val defaultRule = AssetListHelper.processAssets(listOf(walletAsset(asset)), listOf(chain)).single()
        val sharedRule = AssetListHelper.processAssets(
            listOf(walletAsset(asset)),
            listOf(chain),
            isTestnet = { it.id == "72778" }
        ).single()

        assertFalse(defaultRule.isTestnet)
        assertTrue(sharedRule.isTestnet)
        assertTrue(sharedRule.toAssetState().isTestnet)
    }

    @Test
    fun `a testnet row shows no price even when the registry prices its token`() {
        val asset = coreAsset("cenj", "cENJ", "enjincoin", chainId = "canary")
        val chain = chain("canary", Ecosystem.Substrate, listOf(asset))

        val mainnetRule = AssetListHelper.processAssets(listOf(walletAsset(asset)), listOf(chain)).single()
        val testnetRow = AssetListHelper.processAssets(
            listOf(walletAsset(asset)),
            listOf(chain),
            isTestnet = { true }
        ).single()

        assertEquals(PriceTrust.Canonical, mainnetRule.priceTrust)
        assertEquals(PriceTrust.Untrusted, testnetRow.priceTrust)
        assertNull(testnetRow.fiatAmount)
        assertNull(testnetRow.token.fiatRate)
        val state = testnetRow.toAssetState()
        assertNull(state.assetTotalBalanceFiat)
        assertNull(state.assetTokenFiat)
        assertTrue(state.isTestnet)
    }

    @Test
    fun `unverified auto asset is detected until explicitly shown`() {
        val auto = item(
            assetId = "mint",
            chainId = "solana",
            fiat = null,
            preference = AssetPreference.Auto,
            trust = AssetMetadataTrust.Unverified
        )

        assertTrue(auto.toAssetState().isDetected)
        assertFalse(auto.copy(preference = AssetPreference.Shown).toAssetState().isDetected)
        assertFalse(auto.copy(preference = AssetPreference.Hidden).toAssetState().isDetected)
    }

    @Test
    fun `verified indexer metadata remains trusted even when price is unavailable`() {
        val item = item(
            assetId = "jetton-master",
            chainId = "ton-mainnet",
            fiat = null,
            trust = AssetMetadataTrust.Verified,
            source = AssetMetadataSource.Indexer
        ).toAssetState()

        assertEquals("verified", item.metadataTrust)
        assertEquals("indexer", item.metadataSource)
        assertEquals("untrusted", item.priceTrust)
        assertFalse(item.isDetected)
    }

    @Test
    fun `unverified same-symbol holdings retain distinct canonical identities`() {
        val first = item("master-a", "ton-mainnet", null, trust = AssetMetadataTrust.Unverified)
        val second = item("master-b", "ton-mainnet", null, trust = AssetMetadataTrust.Unverified)

        assertNotEquals(first.toAssetState().canonicalAssetKey, second.toAssetState().canonicalAssetKey)
    }

    @Test
    fun `unverified same-symbol asset cannot inherit legacy price id or fiat`() {
        val verified = coreAsset("verified", "DUP", "verified-price")
        val unverified = coreAsset("unverified", "DUP", "legacy-wrong-price")
        val chain = chain("shared-chain", Ecosystem.Substrate, listOf(verified, unverified))

        val processed = AssetListHelper.processAssets(
            assets = listOf(walletAsset(verified), walletAsset(unverified)),
            filteredChains = listOf(chain),
            metadataDescriptor = { identity ->
                if (identity.assetId == unverified.id) {
                    jp.co.soramitsu.common.model.AssetMetadataDescriptor(
                        AssetMetadataTrust.Unverified,
                        AssetMetadataSource.Indexer
                    )
                } else {
                    null
                }
            }
        ).associateBy { it.asset.id }

        assertEquals(PriceTrust.Canonical, processed.getValue(verified.id).priceTrust)
        assertEquals(BigDecimal("50"), processed.getValue(verified.id).fiatAmount)
        assertEquals(PriceTrust.Untrusted, processed.getValue(unverified.id).priceTrust)
        assertNull(processed.getValue(unverified.id).fiatAmount)
        assertNull(processed.getValue(unverified.id).token.fiatRate)
        assertNull(AssetListHelper.trustedFiatSubtotal(listOf(processed.getValue(unverified.id))))
    }

    /** Shared contract §10: a listed asset with a balance and a registry price adds 0 fiat. */
    @Test
    fun `an asset on the bundled untrusted list has no price whatever the registry and the wallet row carry`() {
        val thirdPartyUsdt = coreAsset(KUSAMA_ASSET_HUB_THIRD_PARTY_USDT, "USDT", "tether", chainId = KUSAMA_ASSET_HUB)
        val ksm = coreAsset("ksm", "KSM", "kusama", chainId = KUSAMA_ASSET_HUB)
        val chain = chain(KUSAMA_ASSET_HUB, Ecosystem.Substrate, listOf(thirdPartyUsdt, ksm))
        val rows = listOf(walletAsset(thirdPartyUsdt), walletAsset(ksm))

        val registryOnly = AssetListHelper.processAssets(rows, listOf(chain)).associateBy { it.asset.id }
        assertEquals(
            "without the list the registry would value the token as Tether",
            BigDecimal("50"),
            registryOnly.getValue(thirdPartyUsdt.id).fiatAmount
        )

        val processed = AssetListHelper.processAssets(
            assets = rows,
            filteredChains = listOf(chain),
            hasTrustedPrice = bundledPortfolioVisibility()::hasTrustedPrice
        ).associateBy { it.asset.id }
        val untrusted = processed.getValue(thirdPartyUsdt.id)

        assertEquals(AssetMetadataTrust.Verified, untrusted.metadataTrust)
        assertEquals(PriceTrust.Untrusted, untrusted.priceTrust)
        assertNull(untrusted.fiatAmount)
        assertNull(untrusted.token.fiatRate)
        assertNull("the row says Price unavailable", untrusted.toAssetState().assetTotalBalanceFiat)
        assertEquals(BigDecimal("50"), processed.getValue(ksm.id).fiatAmount)
        assertEquals(BigDecimal("50"), AssetListHelper.trustedFiatSubtotal(processed.values.toList()))
        assertEquals(1, AssetListHelper.portfolioExclusions(processed.values.toList(), emptyList()).unpricedFundedCount)
    }

    @Test
    fun `exclusions report hidden funded assets, holdings without price and pool stakes`() {
        val dot = coreAsset("dot", "DOT", "polkadot", chainId = "polkadot", isUtility = true)
        val unpriced = coreAsset("unpriced", "UNP", null, chainId = "polkadot")
        val hiddenPriced = coreAsset("hidden-priced", "HID", "hidden", chainId = "polkadot")
        val hiddenUnpriced = coreAsset("hidden-unpriced", "HUN", null, chainId = "token-chain")
        val polkadot = chain("polkadot", Ecosystem.Substrate, listOf(dot, unpriced, hiddenPriced), supportStakingPool = true)
        val tokenChain = chain("token-chain", Ecosystem.Substrate, listOf(hiddenUnpriced))
        val chains = listOf(polkadot, tokenChain)

        val exclusions = AssetListHelper.portfolioExclusions(
            countedItems = AssetListHelper.processAssets(listOf(walletAsset(dot), walletAsset(unpriced)), chains),
            hiddenItems = AssetListHelper.processAssets(
                listOf(walletAsset(hiddenPriced, enabled = false), walletAsset(hiddenUnpriced, enabled = false)),
                chains
            )
        )

        assertEquals(setOf("polkadot:hidden-priced", "token-chain:hidden-unpriced"), exclusions.hiddenFundedAssetKeys)
        assertEquals(2, exclusions.hiddenFundedCount)
        assertEquals(1, exclusions.unpricedFundedCount)
        assertTrue(exclusions.poolStakesExcluded)
        assertFalse(exclusions.isEmpty)
    }

    @Test
    fun `exclusions never report empty assets, detected tokens or a token on a pool-staking network`() {
        val token = coreAsset("token", "TKN", "token-price", chainId = "polkadot")
        val emptyDot = coreAsset("dot", "DOT", "polkadot", chainId = "polkadot", isUtility = true)
        val detected = coreAsset("airdrop", "AIR", null, chainId = "polkadot", type = ChainAssetType.Unknown)
        val hiddenEmpty = coreAsset("hidden-empty", "HEM", "empty", chainId = "polkadot")
        val hiddenDetected = coreAsset("hidden-airdrop", "HAI", null, chainId = "polkadot", type = ChainAssetType.Unknown)
        val polkadot = chain(
            "polkadot",
            Ecosystem.Substrate,
            listOf(token, emptyDot, detected, hiddenEmpty, hiddenDetected),
            supportStakingPool = true
        )

        val exclusions = AssetListHelper.portfolioExclusions(
            countedItems = AssetListHelper.processAssets(
                listOf(walletAsset(token), walletAsset(emptyDot, free = 0), walletAsset(detected)),
                listOf(polkadot)
            ),
            hiddenItems = AssetListHelper.processAssets(
                listOf(walletAsset(hiddenEmpty, enabled = false, free = 0), walletAsset(hiddenDetected, enabled = false)),
                listOf(polkadot)
            )
        )

        assertFalse(exclusions.isEmpty)
        assertEquals(PortfolioExclusions(hiddenAssetCount = 2), exclusions)
    }

    @Test
    fun `portfolio and manage assets use the identical canonical key`() {
        val solanaChainId = jp.co.soramitsu.common.model.UniversalWalletRegistry.solanaMainnet.id
        val asset = coreAsset("MintCaseSensitive", "SPL", null, chainId = solanaChainId)
        val chain = chain(
            solanaChainId,
            Ecosystem.Substrate,
            listOf(asset)
        )

        val portfolioKey = AssetListHelper.processAssets(
            listOf(walletAsset(asset)),
            listOf(chain)
        ).single().canonicalIdentity.serialized

        assertEquals(chain.assetKey(asset).serialized, portfolioKey)
        assertEquals(portfolioKey, canonicalManageAssetKey(chain, asset))
    }

    private fun item(
        assetId: String,
        chainId: String,
        fiat: String?,
        total: String = "1",
        preference: AssetPreference = AssetPreference.Auto,
        trust: AssetMetadataTrust = AssetMetadataTrust.Verified,
        source: AssetMetadataSource = if (trust == AssetMetadataTrust.Verified) {
            AssetMetadataSource.Registry
        } else {
            AssetMetadataSource.Indexer
        }
    ): BalanceListItemModel {
        val asset = Asset(
            id = assetId,
            name = assetId,
            symbol = "DUP",
            iconUrl = "",
            chainId = chainId,
            chainName = chainId,
            chainIcon = null,
            isTestNet = false,
            priceId = fiat?.let { "price-$assetId" },
            precision = 0,
            staking = Asset.StakingType.UNSUPPORTED,
            purchaseProviders = null,
            supportStakingPool = false,
            isUtility = false,
            type = if (trust == AssetMetadataTrust.Verified) ChainAssetType.Normal else ChainAssetType.Unknown,
            currencyId = assetId,
            existentialDeposit = null,
            color = null,
            isNative = false
        )
        return BalanceListItemModel(
            asset = asset,
            chain = null,
            token = Token(asset, AssetAmountState.Known(BigDecimal.ONE), AssetAmountState.Known(BigDecimal.ZERO), FiatValuationContext("usd", jp.co.soramitsu.common.data.network.coingecko.FiatCurrency("usd", "$", "US Dollar", "")), "usd", asset.priceId),
            total = BigDecimal(total),
            fiatAmount = fiat?.let(::BigDecimal),
            transferable = BigDecimal.ONE,
            chainUrls = emptyMap(),
            chainAccountName = null,
            isHidden = preference == AssetPreference.Hidden,
            preference = preference,
            canonicalIdentity = CanonicalAssetIdentity("substrate", chainId, assetId),
            metadataTrust = trust,
            metadataSource = source,
            priceTrust = if (fiat == null) PriceTrust.Untrusted else PriceTrust.Canonical
        )
    }

    @Test
    fun `unknown list amount stays unavailable while known zero remains numeric`() {
        val configuration = coreAsset("unit", "UNIT", "unit-price")
        val chain = chain("shared-chain", Ecosystem.Substrate, listOf(configuration))
        val known = walletAsset(configuration, free = 0)
        val unknown = known.copy(asset = known.asset.copy(
            freeInPlanksState = AssetAmountState.Unavailable("unreadable-free")))
        val row = AssetListHelper.processAssets(listOf(unknown), listOf(chain)).single()
        assertNull(row.total)
        assertNull(row.transferable)
        assertNull(row.fiatAmount)
        val state = row.toAssetState(unavailableText = "Balance unavailable")
        assertEquals("Balance unavailable", state.assetTotalBalance)
        assertEquals("Balance unavailable", state.assetTransferableBalance)
        assertNull(state.assetTotalBalanceFiat)
        assertEquals(BigDecimal.ZERO, AssetListHelper.processAssets(listOf(known), listOf(chain)).single().total)
    }

    @Test
    fun `catalog replacement cannot relabel a previously captured amount`() {
        val old = coreAsset("unit", "UNIT", "unit-price")
        val captured = walletAsset(old, enabled = true)
        for (current in listOf(old.copy(precision = 4), old.copy(priceId = "replacement-price"))) {
            val row = AssetListHelper.processAssets(listOf(captured),
                listOf(chain("shared-chain", Ecosystem.Substrate, listOf(current)))).single()
            assertEquals(current, row.asset)
            assertNull(row.total)
            assertNull(row.transferable)
            assertNull(row.token.fiatRate)
            assertNull(row.fiatAmount)
            assertEquals(AssetPreference.Shown, row.preference)
        }
    }

    @Test
    fun `failed balance remains an unavailable holding rather than a known zero`() {
        val configuration = coreAsset("unit", "UNIT", "unit-price")
        val row = AssetListHelper.processAssets(listOf(walletAsset(configuration, free = -1)),
            listOf(chain("shared-chain", Ecosystem.Substrate, listOf(configuration)))).single()
        assertNull(row.total)
        assertNull(row.fiatAmount)
        assertEquals("Balance unavailable", row.toAssetState(unavailableText = "Balance unavailable").assetTotalBalance)
    }

    private fun coreAsset(
        id: String,
        symbol: String,
        priceId: String?,
        chainId: String = "shared-chain",
        isUtility: Boolean = false,
        type: ChainAssetType = ChainAssetType.Normal
    ): Asset = Asset(
        id = id,
        name = id,
        symbol = symbol,
        iconUrl = "",
        chainId = chainId,
        chainName = "Shared",
        chainIcon = null,
        isTestNet = false,
        priceId = priceId,
        precision = 0,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = isUtility,
        type = type,
        currencyId = id,
        existentialDeposit = null,
        color = null,
        isNative = false
    )

    private fun walletAsset(asset: Asset, enabled: Boolean? = null, free: Long = 5): AssetWithStatus = AssetWithStatus(
        asset = WalletAsset(
            metaId = 1L,
            token = Token(asset, AssetAmountState.Known(BigDecimal.TEN), AssetAmountState.Known(BigDecimal.ZERO), FiatValuationContext("usd", jp.co.soramitsu.common.data.network.coingecko.FiatCurrency("usd", "$", "US Dollar", "")), "usd", asset.priceId),
            accountId = byteArrayOf(1),
            freeInPlanks = BigInteger.valueOf(free),
            reservedInPlanks = BigInteger.ZERO,
            miscFrozenInPlanks = BigInteger.ZERO,
            feeFrozenInPlanks = BigInteger.ZERO,
            bondedInPlanks = null,
            redeemableInPlanks = null,
            unbondingInPlanks = null,
            sortIndex = 0,
            enabled = enabled,
            minSupportedVersion = null,
            chainAccountName = null,
            markedNotNeed = false,
            status = null
        ),
        hasAccount = true,
        hasChainAccount = true
    )

    private fun chain(
        id: String,
        ecosystem: Ecosystem,
        assets: List<Asset>,
        supportStakingPool: Boolean = false,
        rank: Int? = null
    ): Chain = mock<Chain>().also { chain ->
        whenever(chain.supportStakingPool).thenReturn(supportStakingPool)
        whenever(chain.rank).thenReturn(rank)
        whenever(chain.id).thenReturn(id)
        whenever(chain.name).thenReturn(id)
        whenever(chain.icon).thenReturn("")
        whenever(chain.ecosystem).thenReturn(ecosystem)
        whenever(chain.externalApi).thenReturn(null)
        whenever(chain.assets).thenReturn(assets)
        whenever(chain.assetsById).thenReturn(assets.associateBy(Asset::id))
    }

    private companion object {
        const val KUSAMA_ASSET_HUB = "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a"
        const val KUSAMA_ASSET_HUB_THIRD_PARTY_USDT = "27768227-8a9a-4443-a57d-6013c24f85e9"
    }
}
