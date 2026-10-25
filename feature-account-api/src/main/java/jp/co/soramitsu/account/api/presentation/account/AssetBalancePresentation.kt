package jp.co.soramitsu.account.api.presentation.account

import jp.co.soramitsu.account.api.domain.model.AssetBalance
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat

/** Complete crypto and complete/labelled partial fiat are distinct; the captured source stays attached. */
data class AssetBalancePresentation(
    val source: AssetBalance,
    val transferable: String?,
    val fiatValue: String?,
    val status: PortfolioValuationStatus,
    val rateChange: String?
)

fun presentAssetBalance(balance: AssetBalance): AssetBalancePresentation {
    if (balance !is AssetBalance.Ready) {
        return AssetBalancePresentation(balance, null, null,
            if (balance == AssetBalance.Unavailable) PortfolioValuationStatus.Unavailable else PortfolioValuationStatus.Loading, null)
    }
    val status = when {
        balance.fiat.amount != null -> PortfolioValuationStatus.Complete
        !balance.quotesReady -> PortfolioValuationStatus.AwaitingPrices
        balance.fiat.knownSubtotal != null -> PortfolioValuationStatus.Partial
        else -> PortfolioValuationStatus.Unavailable
    }
    return AssetBalancePresentation(
        source = balance,
        transferable = balance.transferable?.formatCrypto(balance.configuration.symbol),
        fiatValue = (balance.fiat.amount ?: balance.fiat.knownSubtotal)?.formatFiat(balance.fiatSymbol),
        status = status,
        rateChange = balance.fiat.rateChange?.takeIf { balance.fiat.amount != null }?.formatAsChange()
    )
}
