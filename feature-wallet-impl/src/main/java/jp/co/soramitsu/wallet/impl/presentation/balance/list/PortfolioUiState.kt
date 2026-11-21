package jp.co.soramitsu.wallet.impl.presentation.balance.list

/** Bounded, non-secret presentation choices. Expansion is isolated by wallet; the home is always one token list. */
data class PortfolioUiState(
    val lens: PortfolioLens = PortfolioLens.ByToken,
    val expandedGroups: Set<String> = emptySet(),
    val expandedZeroGroups: Set<String> = emptySet(),
    val zeroSummaryExpanded: Boolean = false,
    val valueNoticeAcknowledged: Boolean = false,
    /** The wallet had kept By network: the home says once that everything is now one list. */
    val showOneListNotice: Boolean = false
)
