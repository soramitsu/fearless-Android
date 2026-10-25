package jp.co.soramitsu.account.api.domain.model

import java.math.BigDecimal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

/** Completed holdings only. A subtotal is explicitly separate from a complete valuation. */
data class PortfolioBalance(
    val amount: BigDecimal?,
    val knownSubtotal: BigDecimal?,
    val change: BigDecimal?,
    val rateChange: BigDecimal?,
    val holdingCount: Int,
    val pricedCount: Int,
    val unpricedAssetCount: Int,
    val unknownQuantityCount: Int
)

/**
 * No value is available before a completed read. Ready instances carry one captured account and
 * fiat unit; consumers must retain that instance across asynchronous presentation work. Equality
 * of wallet IDs or currency text cannot admit an old result after selection/catalog replacement.
 * Account API deliberately has no dependency on wallet API's valuation implementation.
 */
sealed interface TotalBalance {
    data object AwaitingSelection : TotalBalance
    data class Loading(val metaId: Long, val currencyId: String) : TotalBalance
    data object Unavailable : TotalBalance

    /** Historical when returned by a one-shot read; none of these fields claims currentness. */
    class Ready(
        val meta: MetaAccount,
        /** The reader snapshot's catalog; address work must not consult a later global catalog. */
        val chainsById: Map<ChainId, Chain>,
        val currencyId: String,
        val fiatSymbol: String,
        /** False while completed crypto quantities are awaiting their quote query. */
        val quotesReady: Boolean,
        val shown: PortfolioBalance,
        val hidden: PortfolioBalance,
        val allHoldings: PortfolioBalance
    ) : TotalBalance
}
