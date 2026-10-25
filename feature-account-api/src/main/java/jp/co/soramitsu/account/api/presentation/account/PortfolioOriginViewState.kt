package jp.co.soramitsu.account.api.presentation.account

import android.graphics.drawable.PictureDrawable
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.address.AddressModel

/** Name, icon and money are rendered atomically from one captured portfolio. */
data class PortfolioOriginViewState(
    val source: TotalBalance,
    val name: String,
    val icon: PictureDrawable?,
    val balanceText: String
)

/** A missing value is an explicit placeholder, with the supplied localized status beside it. */
fun presentPortfolioOrigin(
    origin: CapturedPortfolioDetail<AddressModel>,
    caption: (PortfolioBalancePresentation) -> String?
): PortfolioOriginViewState {
    val presentation = presentTotalBalance(origin.source, includeHidden = false)
    val ready = origin.source as? TotalBalance.Ready
    return PortfolioOriginViewState(
        source = origin.source,
        name = ready?.meta?.name.orEmpty(),
        icon = if (ready == null) null else origin.detail?.image,
        balanceText = listOfNotNull(presentation.value ?: "—", caption(presentation)).joinToString(" · ")
    )
}
