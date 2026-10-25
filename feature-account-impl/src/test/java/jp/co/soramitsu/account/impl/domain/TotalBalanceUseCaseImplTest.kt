package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.coredb.model.cache.CachedAssetRead
import jp.co.soramitsu.coredb.model.cache.CachedAssetWithPriceRead
import jp.co.soramitsu.coredb.model.cache.CachedTokenPriceRead
import jp.co.soramitsu.coredb.model.cache.CachedNumericState
import jp.co.soramitsu.coredb.model.cache.CachedFiatCurrencyIdentity
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetContext
import jp.co.soramitsu.wallet.impl.data.repository.CurrentAccountAssetSnapshot
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import org.junit.Assert.assertNull
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.SharedPortfolioConfigSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TotalBalanceUseCaseImplTest {

    @Test
    fun `missing catalog row skips only that balance`() {
        val verified = chainAsset("verified", priceId = "verified-price")
        val chain = chain(listOf(verified))
        val balances = listOf(
            walletAsset("verified", "verified-price", amount = 2, rate = "3"),
            walletAsset("missing", "missing-price", amount = 999, rate = "100")
        )

        val total = calculateTrustedTotalBalance(balances, mapOf(CHAIN_ID to chain), Chain::isTestNet, REGISTRY_PRICES_TRUSTED)

        assertEquals(BigDecimal("6"), total.shown.knownSubtotal)
    }

    @Test
    fun `same symbol different contract never inherits a curated price`() {
        val pricedContract = chainAsset(PRICED_CONTRACT, symbol = "DUP", priceId = "priced-contract")
        val unpricedContract = chainAsset(UNPRICED_CONTRACT, symbol = "DUP", priceId = null)
        val chain = chain(listOf(pricedContract, unpricedContract))
        val balances = listOf(
            walletAsset(PRICED_CONTRACT, "priced-contract", amount = 4, rate = "2"),
            // Simulates a symbol-keyed price accidentally attached to a different contract.
            walletAsset(UNPRICED_CONTRACT, "priced-contract", amount = 100, rate = "2")
        )

        val total = calculateTrustedTotalBalance(balances, mapOf(CHAIN_ID to chain), Chain::isTestNet, REGISTRY_PRICES_TRUSTED)

        assertEquals(BigDecimal("8"), total.shown.knownSubtotal)
        assertNull(total.shown.amount)
        assertEquals(1, total.shown.unpricedAssetCount)
    }

    @Test
    fun `hidden verified asset is reported in hiddenBalance not balance`() {
        val asset = chainAsset("hidden", priceId = "hidden-price")
        val chain = chain(listOf(asset))

        val total = calculateTrustedTotalBalance(
            assets = listOf(walletAsset("hidden", "hidden-price", amount = 5, rate = "7", enabled = false)),
            chainsById = mapOf(CHAIN_ID to chain),
            isTestnet = Chain::isTestNet,
            hasTrustedPrice = REGISTRY_PRICES_TRUSTED
        )

        assertEquals(BigDecimal.ZERO, total.shown.knownSubtotal)
        assertEquals(BigDecimal("35"), total.hidden.knownSubtotal)
        assertEquals(1, total.hidden.holdingCount)
    }

    @Test
    fun `allHoldingsBalance includes hidden`() {
        val shown = chainAsset("shown", priceId = "shown-price")
        val hidden = chainAsset("hidden", priceId = "hidden-price")
        val hiddenUnpriced = chainAsset("hidden-unpriced", priceId = null)
        val hiddenEmpty = chainAsset("hidden-empty", priceId = "empty-price")
        val chain = chain(listOf(shown, hidden, hiddenUnpriced, hiddenEmpty))

        val total = calculateTrustedTotalBalance(
            assets = listOf(
                walletAsset("shown", "shown-price", amount = 2, rate = "3.00", enabled = true, rateChange = "10.00"),
                walletAsset("hidden", "hidden-price", amount = 5, rate = "7.00", enabled = false, rateChange = "-20.00"),
                walletAsset("hidden-unpriced", "anything", amount = 9, rate = "1", enabled = false),
                walletAsset("hidden-empty", "empty-price", amount = 0, rate = "1", enabled = false)
            ),
            chainsById = mapOf(CHAIN_ID to chain),
            isTestnet = Chain::isTestNet,
            hasTrustedPrice = REGISTRY_PRICES_TRUSTED
        )

        assertSameAmount("6", total.shown.knownSubtotal)
        assertSameAmount("35", total.hidden.knownSubtotal)
        assertSameAmount("41", total.allHoldings.knownSubtotal)
        assertEquals("only funded hidden assets are reported", 2, total.hidden.holdingCount)
        assertSameAmount("0.6", total.shown.change)
        assertNull(total.allHoldings.amount)
        assertNull(total.allHoldings.change)
        assertSameAmount("10", total.shown.rateChange)
        assertNull(total.allHoldings.rateChange)
    }

    @Test
    fun `priced testnet asset is never counted, hidden or not`() {
        val cEnj = chainAsset("cenj", priceId = "enjincoin")
        val canary = chain(listOf(cEnj), id = CANARY_CHAIN_ID, isTestNet = true)
        val cagaAsset = chainAsset("caga", priceId = "caga-price")
        val cagaAnkara = chain(listOf(cagaAsset), id = CAGA_CHAIN_ID)
        val sharedTestnetIds = setOf(CANARY_CHAIN_ID, CAGA_CHAIN_ID)

        val total = calculateTrustedTotalBalance(
            assets = listOf(
                walletAsset("cenj", "enjincoin", amount = 100, rate = "0.1", chainId = CANARY_CHAIN_ID, enabled = true),
                walletAsset("caga", "caga-price", amount = 10, rate = "1", chainId = CAGA_CHAIN_ID),
                walletAsset("caga", "caga-price", amount = 10, rate = "1", chainId = CAGA_CHAIN_ID, enabled = false)
            ),
            chainsById = mapOf(CANARY_CHAIN_ID to canary, CAGA_CHAIN_ID to cagaAnkara),
            isTestnet = { it.isTestNet || it.id in sharedTestnetIds },
            hasTrustedPrice = REGISTRY_PRICES_TRUSTED
        )

        assertEquals(BigDecimal.ZERO, total.shown.knownSubtotal)
        assertEquals(BigDecimal.ZERO, total.allHoldings.knownSubtotal)
        assertEquals(0, total.hidden.holdingCount)
        assertEquals(0, total.shown.unpricedAssetCount)
    }

    @Test
    fun `unpriced funded asset increments unpricedAssetCount`() {
        val priced = chainAsset("priced", priceId = "priced-price")
        val unpriced = chainAsset("unpriced", priceId = null)
        val emptyUnpriced = chainAsset("empty-unpriced", priceId = null)
        val chain = chain(listOf(priced, unpriced, emptyUnpriced))

        val total = calculateTrustedTotalBalance(
            assets = listOf(
                walletAsset("priced", "priced-price", amount = 1, rate = "4"),
                walletAsset("unpriced", "unknown", amount = 50, rate = "1"),
                walletAsset("empty-unpriced", "unknown", amount = 0, rate = "1")
            ),
            chainsById = mapOf(CHAIN_ID to chain),
            isTestnet = Chain::isTestNet,
            hasTrustedPrice = REGISTRY_PRICES_TRUSTED
        )

        assertEquals(BigDecimal("4"), total.shown.knownSubtotal)
        assertEquals(1, total.shown.unpricedAssetCount)
        assertNull(total.shown.amount)
    }

    @Test
    fun `detected tokens are never reported as value the total leaves out`() {
        val priced = chainAsset("priced", priceId = "priced-price")
        val airdrop = chainAsset("airdrop", priceId = null, type = ChainAssetType.Unknown)
        val hiddenAirdrop = chainAsset("hidden-airdrop", priceId = null, type = ChainAssetType.Unknown)
        val chain = chain(listOf(priced, airdrop, hiddenAirdrop))

        val total = calculateTrustedTotalBalance(
            assets = listOf(
                walletAsset("priced", "priced-price", amount = 1, rate = "4"),
                walletAsset("airdrop", "unknown", amount = 1_000_000, rate = "1"),
                walletAsset("hidden-airdrop", "unknown", amount = 1_000_000, rate = "1", enabled = false)
            ),
            chainsById = mapOf(CHAIN_ID to chain),
            isTestnet = Chain::isTestNet,
            hasTrustedPrice = REGISTRY_PRICES_TRUSTED
        )

        assertEquals(BigDecimal("4"), total.shown.knownSubtotal)
        assertEquals(0, total.shown.unpricedAssetCount)
        assertEquals(0, total.hidden.holdingCount)
        assertSameAmount("4", total.allHoldings.knownSubtotal)
    }

    /**
     * Shared portfolio contract §10: every bundled `untrustedAssets` entry, given a balance and
     * its registry price, adds 0 fiat to the header and is counted as an asset without price.
     * The chains are the registry snapshot the shared files were reviewed against.
     */
    @Test
    fun `every bundled untrusted asset adds nothing to the header and counts as without price`() {
        val visibility = bundledPortfolioVisibility()
        val untrusted = requireNotNull(bundledConfigSource().config.untrustedAssets).entries
        val chainsById = reviewedRegistryChains().associateBy(Chain::id)
        assertTrue(untrusted.isNotEmpty())

        untrusted.forEach { entry ->
            val chain = chainsById.getValue(entry.chainId)
            val listed = chain.assetsById.getValue(entry.assetId)
            val utility = requireNotNull(chain.assets.firstOrNull { it.isUtility && it.priceId != null }) {
                "${chain.name} needs a priced utility asset for the comparison"
            }
            val holdings = { enabled: Boolean? ->
                listOf(
                    walletAsset(utility.id, utility.priceId!!, amount = 3, rate = "2", chainId = chain.id),
                    walletAsset(listed.id, entry.registryPriceId, amount = 500_000, rate = "1", chainId = chain.id, enabled = enabled)
                )
            }
            val utilityValue = BigInteger.valueOf(3).toBigDecimal(scale = utility.precision) * BigDecimal("2")

            val registryOnly = calculateTrustedTotalBalance(holdings(null), chainsById, visibility::isTestnet, REGISTRY_PRICES_TRUSTED)
            assertTrue(
                "without the list the registry price would value ${entry.symbol}",
                requireNotNull(registryOnly.shown.amount) > utilityValue
            )

            val total = calculateTrustedTotalBalance(holdings(null), chainsById, visibility::isTestnet, visibility::hasTrustedPrice)
            assertSameAmount(utilityValue.toPlainString(), total.shown.knownSubtotal)
            assertNull(total.shown.amount)
            assertEquals(1, total.shown.unpricedAssetCount)

            val hidden = calculateTrustedTotalBalance(holdings(false), chainsById, visibility::isTestnet, visibility::hasTrustedPrice)
            assertSameAmount(utilityValue.toPlainString(), hidden.shown.knownSubtotal)
            assertNull(hidden.hidden.amount)
            assertNull(hidden.hidden.knownSubtotal)
            assertSameAmount(utilityValue.toPlainString(), hidden.allHoldings.knownSubtotal)
            assertEquals(1, hidden.hidden.holdingCount)
            assertEquals(0, hidden.shown.unpricedAssetCount)
        }
    }

    // Keep the original registry/trust scenarios, now entering the real captured-snapshot API.
    private fun calculateTrustedTotalBalance(
        assets: List<AssetWithToken>,
        chainsById: Map<String, Chain>,
        isTestnet: (Chain) -> Boolean,
        hasTrustedPrice: (String, String) -> Boolean
    ): TotalBalance.Ready {
        fun <T : Any> state(value: T?): CachedNumericState<T> =
            value?.let { CachedNumericState.Known(it) } ?: CachedNumericState.Missing
        val rows = assets.map { row ->
            val a = row.asset
            CachedAssetWithPriceRead(
                CachedAssetRead(a.id, a.chainId, a.accountId, a.metaId, a.tokenPriceId, a.sortIndex,
                    a.enabled, a.markedNotNeed, a.chainAccountName, a.status,
                    state(a.freeInPlanks), state(a.reservedInPlanks), state(a.miscFrozenInPlanks),
                    state(a.feeFrozenInPlanks), state(a.bondedInPlanks), state(a.redeemableInPlanks), state(a.unbondingInPlanks)),
                row.token?.let { price -> CachedTokenPriceRead(price.priceId, price.fiatSymbol,
                    state(price.fiatRate), state(price.recentRateChange),
                    price.fiatCurrencyId?.let { CachedFiatCurrencyIdentity.Known(it) } ?: CachedFiatCurrencyIdentity.Unverified) }
            )
        }
        val snapshot = CurrentAccountAssetSnapshot(
            CurrentAccountAssetContext(AssetBalanceFixtures.meta(id = 1), FiatValuationContext("usd")),
            chainsById, rows, quotesReady = true
        )
        return calculateTrustedTotalBalance(snapshot, isTestnet, hasTrustedPrice) as TotalBalance.Ready
    }

    private fun bundledConfigSource() = SharedPortfolioConfigSource(
        readAsset = { name -> runtimeFile("src/main/assets/$name").readBytes() },
        onLoadFailure = { name, error -> throw AssertionError("Bundled $name failed to load", error) }
    )

    private fun bundledPortfolioVisibility() = PortfolioVisibility(bundledConfigSource())

    // Enabled chains of the reviewed registry snapshot, mapped exactly as the app maps the registry.
    private fun reviewedRegistryChains(): List<Chain> {
        val type = object : TypeToken<List<ChainRemote>>() {}.type
        val snapshot = runtimeFile("src/test/resources/portfolio/chains-v13-20261009.json").readText()
        return Gson().fromJson<List<ChainRemote>>(snapshot, type)
            .filterNot(ChainRemote::disabled)
            .map(ChainRemote::toChain)
    }

    private fun runtimeFile(path: String): File {
        return listOf(File("../runtime/$path"), File("runtime/$path")).first(File::isFile)
    }

    private fun assertSameAmount(expected: String, actual: BigDecimal?) {
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))
    }

    private fun walletAsset(
        assetId: String,
        priceId: String,
        amount: Long,
        rate: String,
        enabled: Boolean? = null,
        chainId: String = CHAIN_ID,
        rateChange: String = "0"
    ): AssetWithToken {
        return AssetWithToken(
            asset = AssetLocal(
                id = assetId,
                chainId = chainId,
                accountId = byteArrayOf(1),
                metaId = 1,
                tokenPriceId = priceId,
                freeInPlanks = BigInteger.valueOf(amount),
                enabled = enabled
            ),
            token = TokenPriceLocal(
                priceId = priceId,
                fiatSymbol = "$",
                fiatRate = BigDecimal(rate),
                recentRateChange = BigDecimal(rateChange),
                fiatCurrencyId = "usd"
            )
        )
    }

    private fun chainAsset(
        id: String,
        symbol: String = id,
        priceId: String?,
        type: ChainAssetType = ChainAssetType.ERC20
    ): Asset {
        return Asset(
            id = id,
            name = symbol,
            symbol = symbol,
            iconUrl = "",
            chainId = CHAIN_ID,
            chainName = "Ethereum",
            chainIcon = null,
            isTestNet = false,
            priceId = priceId,
            precision = 0,
            staking = Asset.StakingType.UNSUPPORTED,
            purchaseProviders = null,
            supportStakingPool = false,
            isUtility = false,
            type = type,
            currencyId = id,
            existentialDeposit = null,
            color = null,
            isNative = false
        )
    }

    private fun chain(assets: List<Asset>, id: String = CHAIN_ID, isTestNet: Boolean = false): Chain {
        return Chain(
            id = id,
            paraId = null,
            rank = null,
            name = "Ethereum",
            minSupportedVersion = null,
            assets = assets.map { it.copy(chainId = id) },
            nodes = emptyList(),
            explorers = emptyList(),
            externalApi = null,
            icon = "",
            addressPrefix = 0,
            isEthereumBased = true,
            isTestNet = isTestNet,
            hasCrowdloans = false,
            parentId = null,
            supportStakingPool = false,
            isEthereumChain = true,
            chainlinkProvider = false,
            supportNft = false,
            isUsesAppId = false,
            identityChain = null,
            ecosystem = Ecosystem.Ethereum,
            remoteAssetsSource = null
        )
    }

    private companion object {
        val REGISTRY_PRICES_TRUSTED: (String, String) -> Boolean = { _, _ -> true }
        const val CHAIN_ID = "1"
        const val CANARY_CHAIN_ID = "a37725fd8943d2a524cb7ecc65da438f9fa644db78ba24dcd0003e2f95645e8f"
        const val CAGA_CHAIN_ID = "72778"
        const val PRICED_CONTRACT = "0xA0b86991c6218b36C1d19D4a2e9Eb0cE3606eB48"
        const val UNPRICED_CONTRACT = "0x1111111111111111111111111111111111111111"
    }
}
