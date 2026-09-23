package jp.co.soramitsu.wallet.impl.presentation

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.model.AssetMetadataSource
import jp.co.soramitsu.common.model.AssetMetadataTrust
import jp.co.soramitsu.common.model.AssetPreference
import jp.co.soramitsu.common.model.CanonicalAssetIdentity
import jp.co.soramitsu.common.model.PriceTrust
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.ext.assetKey
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.presentation.manageassets.canonicalManageAssetKey
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.BalanceListItemModel
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.toAssetState
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
    fun `hidden trusted holdings remain in network subtotal`() {
        val visible = item("visible", "chain", fiat = "3")
        val hidden = item("hidden", "chain", fiat = "12", preference = AssetPreference.Hidden)

        assertEquals(BigDecimal("15"), AssetListHelper.trustedFiatSubtotal(listOf(visible, hidden)))
    }

    @Test
    fun `hidden trusted valuation still controls network order`() {
        val displayedLow = item("low", "low-chain", fiat = "10")
        val displayedHigh = item("high", "high-chain", fiat = null)
        val hiddenHigh = item("hidden", "high-chain", fiat = "100", preference = AssetPreference.Hidden)

        val sorted = AssetListHelper.sortByNetwork(
            assets = listOf(displayedLow, displayedHigh),
            valuationAssets = listOf(displayedLow, displayedHigh, hiddenHigh)
        )

        assertEquals(listOf("high-chain", "low-chain"), sorted.map { it.canonicalIdentity.chainId })
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
    fun `explicitly shown zero-balance non-native is not rendered in Portfolio`() {
        val shownErc20 = coreAsset("contract-a", "DUP", null).copy(isNative = false)

        assertFalse(
            AssetListHelper.shouldDisplayInPortfolio(
                total = BigDecimal.ZERO,
                asset = shownErc20,
                isPinnedNetwork = true,
                isDefaultNetwork = true
            )
        )
    }

    @Test
    fun `zero-balance native remains visible only on pinned or default network`() {
        val native = coreAsset("native", "NATIVE", null).copy(isNative = true, isUtility = true)

        assertTrue(
            AssetListHelper.shouldDisplayInPortfolio(
                total = BigDecimal.ZERO,
                asset = native,
                isPinnedNetwork = true,
                isDefaultNetwork = false
            )
        )
        assertFalse(
            AssetListHelper.shouldDisplayInPortfolio(
                total = BigDecimal.ZERO,
                asset = native,
                isPinnedNetwork = false,
                isDefaultNetwork = false
            )
        )
    }

    @Test
    fun `hidden positive verified priced holding still promotes its network`() {
        val visibleUnpriced = item("visible", "z-chain", fiat = null, total = "2")
        val hiddenPriced = item(
            "hidden",
            "z-chain",
            fiat = "50",
            total = "4",
            preference = AssetPreference.Hidden
        )
        val alphabeticalFirst = item("visible", "a-chain", fiat = null, total = "3")

        val sorted = AssetListHelper.sortByNetwork(
            assets = listOf(visibleUnpriced, alphabeticalFirst),
            valuationAssets = listOf(visibleUnpriced, hiddenPriced, alphabeticalFirst)
        )

        assertEquals(listOf("z-chain", "a-chain"), sorted.map { it.canonicalIdentity.chainId })
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
            token = Token(asset, fiatRate = BigDecimal.ONE, fiatSymbol = "$", recentRateChange = BigDecimal.ZERO),
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

    private fun coreAsset(
        id: String,
        symbol: String,
        priceId: String?,
        chainId: String = "shared-chain"
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
        isUtility = false,
        type = ChainAssetType.Normal,
        currencyId = id,
        existentialDeposit = null,
        color = null,
        isNative = false
    )

    private fun walletAsset(asset: Asset): AssetWithStatus = AssetWithStatus(
        asset = WalletAsset(
            metaId = 1L,
            token = Token(asset, BigDecimal.TEN, "$", BigDecimal.ZERO),
            accountId = byteArrayOf(1),
            freeInPlanks = BigInteger.valueOf(5),
            reservedInPlanks = BigInteger.ZERO,
            miscFrozenInPlanks = BigInteger.ZERO,
            feeFrozenInPlanks = BigInteger.ZERO,
            bondedInPlanks = null,
            redeemableInPlanks = null,
            unbondingInPlanks = null,
            sortIndex = 0,
            enabled = null,
            minSupportedVersion = null,
            chainAccountName = null,
            markedNotNeed = false,
            status = null
        ),
        hasAccount = true,
        hasChainAccount = true
    )

    private fun chain(id: String, ecosystem: Ecosystem, assets: List<Asset>): Chain = mock<Chain>().also { chain ->
        whenever(chain.id).thenReturn(id)
        whenever(chain.name).thenReturn(id)
        whenever(chain.icon).thenReturn("")
        whenever(chain.ecosystem).thenReturn(ecosystem)
        whenever(chain.externalApi).thenReturn(null)
        whenever(chain.assets).thenReturn(assets)
        whenever(chain.assetsById).thenReturn(assets.associateBy(Asset::id))
    }
}
