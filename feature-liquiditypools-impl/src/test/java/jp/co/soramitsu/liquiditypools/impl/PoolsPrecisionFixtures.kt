package jp.co.soramitsu.liquiditypools.impl

import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.Token
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

/**
 * SORA assets for the pool amount tests: XOR and VAL have 18 decimals, USDX has 6 (like a bridged
 * stablecoin), so a value scaled with the wrong asset's precision is off by 10^12.
 */
internal object PoolsPrecisionFixtures {
    const val XOR_ID = "0x0200000000000000000000000000000000000000000000000000000000000000"
    const val VAL_ID = "0x0200040000000000000000000000000000000000000000000000000000000000"
    const val USDX_ID = "0x00ab000000000000000000000000000000000000000000000000000000000006"

    val xor = coreAsset("xor", "XOR", XOR_ID, precision = 18, isUtility = true)
    val valToken = coreAsset("val", "VAL", VAL_ID, precision = 18)
    val usdx = coreAsset("usdx", "USDX", USDX_ID, precision = 6)

    private const val META_ID = 7L
    private val ACCOUNT_ID = ByteArray(32) { 7 }
    private val fiat = FiatValuationContext("usd", FiatCurrency("usd", "$", "Dollar", ""))

    fun coreAsset(
        id: String,
        symbol: String,
        currencyId: String,
        precision: Int,
        isUtility: Boolean = false
    ) = CoreAsset(
        id = id,
        name = symbol,
        symbol = symbol,
        iconUrl = "https://icons/$id.svg",
        chainId = soraMainChainId,
        chainName = "SORA",
        chainIcon = null,
        isTestNet = false,
        priceId = id,
        precision = precision,
        staking = CoreAsset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = isUtility,
        type = null,
        currencyId = currencyId,
        existentialDeposit = null,
        color = null,
        isNative = null
    )

    /** A token priced at [fiatRate] US dollars, with a verified quote so `fiatRate` is not null. */
    fun token(configuration: CoreAsset, fiatRate: BigDecimal?) = Token(
        configuration = configuration,
        fiatRateInput = fiatRate?.let { AssetAmountState.Known(it) } ?: AssetAmountState.Missing,
        recentRateChangeInput = AssetAmountState.Known(BigDecimal.ZERO),
        fiatContext = fiat,
        quoteCurrencyId = "usd",
        quotePriceId = configuration.priceId
    )

    fun walletAsset(
        configuration: CoreAsset,
        fiatRate: BigDecimal?,
        free: BigDecimal = BigDecimal("100")
    ): Asset = Asset.createEmpty(configuration, META_ID, ACCOUNT_ID, minSupportedVersion = null).copy(
        token = token(configuration, fiatRate),
        freeInPlanksState = AssetAmountState.Known(free.movePointRight(configuration.precision).toBigInteger()),
        reservedInPlanksState = AssetAmountState.Known(BigInteger.ZERO),
        miscFrozenInPlanksState = AssetAmountState.Known(BigInteger.ZERO),
        feeFrozenInPlanksState = AssetAmountState.Known(BigInteger.ZERO)
    )
}
