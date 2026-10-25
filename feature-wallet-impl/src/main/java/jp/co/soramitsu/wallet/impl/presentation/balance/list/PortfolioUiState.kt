package jp.co.soramitsu.wallet.impl.presentation.balance.list

/** Bounded, non-secret presentation choices. Expansion is isolated by wallet. */
data class PortfolioUiState(
    val lens: PortfolioLens = PortfolioLens.ByToken,
    val expandedGroups: Set<String> = emptySet(),
    val collapsedNetworks: Set<String> = emptySet(),
    val expandedZeroGroups: Set<String> = emptySet(),
    val zeroSummaryExpanded: Boolean = false,
    val valueNoticeAcknowledged: Boolean = false
)
