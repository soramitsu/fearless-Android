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

/** Shared SORA assets and wallet balances for liquidity pool unit tests. */
internal object LiquidityFixtures {
    const val XOR_ID = "0x0200000000000000000000000000000000000000000000000000000000000000"
    const val VAL_ID = "0x0200040000000000000000000000000000000000000000000000000000000000"
    const val PSWAP_ID = "0x0200050000000000000000000000000000000000000000000000000000000000"
    const val UNKNOWN_ID = "0x00ff000000000000000000000000000000000000000000000000000000000000"
    const val META_ID = 7L
    val ACCOUNT_ID = ByteArray(32) { 7 }

    val xor = coreAsset("xor", "XOR", XOR_ID, isUtility = true)
    val valToken = coreAsset("val", "VAL", VAL_ID)
    val pswap = coreAsset("pswap", "PSWAP", PSWAP_ID)

    val fiat = FiatValuationContext("usd", FiatCurrency("usd", "$", "Dollar", ""))

    fun coreAsset(
        id: String,
        symbol: String,
        currencyId: String?,
        precision: Int = 18,
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

    fun token(configuration: CoreAsset, fiatRate: BigDecimal? = BigDecimal("2")) = Token(
        configuration = configuration,
        fiatRateInput = fiatRate?.let { AssetAmountState.Known(it) } ?: AssetAmountState.Missing,
        recentRateChangeInput = AssetAmountState.Known(BigDecimal.ZERO),
        fiatContext = fiat,
        quoteCurrencyId = "usd",
        quotePriceId = configuration.priceId
    )

    fun walletAsset(
        configuration: CoreAsset,
        free: BigDecimal = BigDecimal("100"),
        fiatRate: BigDecimal? = BigDecimal("2")
    ): Asset = Asset.createEmpty(configuration, META_ID, ACCOUNT_ID, minSupportedVersion = null).copy(
        token = token(configuration, fiatRate),
        freeInPlanksState = AssetAmountState.Known(free.movePointRight(configuration.precision).toBigInteger()),
        reservedInPlanksState = AssetAmountState.Known(BigInteger.ZERO),
        miscFrozenInPlanksState = AssetAmountState.Known(BigInteger.ZERO),
        feeFrozenInPlanksState = AssetAmountState.Known(BigInteger.ZERO)
    )
}
