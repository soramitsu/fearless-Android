package jp.co.soramitsu.account.api.domain.model

import java.math.BigDecimal
import jp.co.soramitsu.core.models.Asset as CoreAsset

/** Transferable quantity and its valuation retain one account, asset and currency snapshot. */
sealed interface AssetBalance {
    data object AwaitingSelection : AssetBalance
    data class Loading(val metaId: Long, val currencyId: String, val chainId: String, val assetId: String) : AssetBalance
    data object Unavailable : AssetBalance

    /** A partial subtotal is not a complete or spendable amount. One-shot results are historical. */
    class Ready(
        val meta: MetaAccount,
        val configuration: CoreAsset,
        val currencyId: String,
        val fiatSymbol: String,
        val quotesReady: Boolean,
        val transferable: BigDecimal?,
        val knownTransferableSubtotal: BigDecimal?,
        val unknownQuantityCount: Int,
        val fiat: PortfolioBalance
    ) : AssetBalance
}
