package jp.co.soramitsu.staking.impl

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.expectedFiatPriceId
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Real (not mocked) chain, chain asset and balance values for the staking lane's tests. */
object StakingLaneFixtures {

    const val CHAIN_ID = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
    val accountId: ByteArray = ByteArray(32) { 7 }

    fun chainAsset(
        chainId: String = CHAIN_ID,
        symbol: String = "DOT",
        precision: Int = 10,
        staking: ChainAsset.StakingType = ChainAsset.StakingType.RELAYCHAIN,
        supportStakingPool: Boolean = true
    ) = ChainAsset(
        id = "${symbol.lowercase()}-id",
        name = symbol,
        symbol = symbol,
        iconUrl = "https://example.org/$symbol.svg",
        chainId = chainId,
        chainName = "Polkadot",
        chainIcon = null,
        isTestNet = false,
        priceId = "${symbol.lowercase()}-price",
        precision = precision,
        staking = staking,
        purchaseProviders = null,
        supportStakingPool = supportStakingPool,
        isUtility = true,
        type = null,
        currencyId = null,
        existentialDeposit = "10000000000",
        color = null,
        isNative = true
    )

    fun chain(
        asset: ChainAsset = chainAsset(),
        externalApi: Chain.ExternalApi? = null,
        addressPrefix: Int = 0,
        ethereum: Boolean = false
    ) = Chain(
        id = asset.chainId,
        paraId = null,
        rank = null,
        name = "Polkadot",
        minSupportedVersion = null,
        assets = listOf(asset),
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = externalApi,
        icon = "https://example.org/chain.svg",
        addressPrefix = addressPrefix,
        isEthereumBased = ethereum,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = true,
        isEthereumChain = false,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = Ecosystem.Substrate,
        remoteAssetsSource = null
    )

    fun token(asset: ChainAsset = chainAsset(), fiatRate: BigDecimal? = null) =
        if (fiatRate == null) {
            Token(configuration = asset, fiatRate = null, fiatSymbol = null, recentRateChange = null)
        } else {
            Token(
                configuration = asset,
                fiatRateInput = AssetAmountState.Known(fiatRate),
                recentRateChangeInput = AssetAmountState.Unavailable("no-test-price-change"),
                fiatContext = FiatValuationContext("usd"),
                quoteCurrencyId = "usd",
                quotePriceId = expectedFiatPriceId(asset, "usd")
            )
        }

    /** Free 100, bonded 50, redeemable 5, unbonding 10 (in whole tokens). */
    fun asset(chainAsset: ChainAsset = chainAsset(), metaId: Long = 1L): Asset {
        val unit = BigInteger.TEN.pow(chainAsset.precision)
        return Asset(
            metaId = metaId,
            token = token(chainAsset),
            accountId = accountId,
            freeInPlanks = unit * BigInteger.valueOf(100),
            reservedInPlanks = BigInteger.ZERO,
            miscFrozenInPlanks = BigInteger.ZERO,
            feeFrozenInPlanks = BigInteger.ZERO,
            bondedInPlanks = unit * BigInteger.valueOf(50),
            redeemableInPlanks = unit * BigInteger.valueOf(5),
            unbondingInPlanks = unit * BigInteger.valueOf(10),
            sortIndex = 0,
            enabled = true,
            minSupportedVersion = null,
            chainAccountName = null,
            markedNotNeed = false,
            status = null
        )
    }

    fun planks(tokens: Long, precision: Int = 10): BigInteger = BigInteger.TEN.pow(precision) * BigInteger.valueOf(tokens)
}
