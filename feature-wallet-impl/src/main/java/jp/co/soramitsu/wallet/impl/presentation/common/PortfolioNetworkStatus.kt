package jp.co.soramitsu.wallet.impl.presentation.common

internal enum class PortfolioNetworkStatus {
    Failed,
    Outdated,
    NotLoaded
}

/** Only exceptional balance states need an explanation in the network heading. */
internal fun portfolioNetworkStatus(
    lastSuccessMillis: Long?,
    isStale: Boolean,
    errorMessage: String?
): PortfolioNetworkStatus? = when {
    !errorMessage.isNullOrBlank() || (isStale && lastSuccessMillis == null) -> PortfolioNetworkStatus.Failed
    isStale -> PortfolioNetworkStatus.Outdated
    lastSuccessMillis == null -> PortfolioNetworkStatus.NotLoaded
    else -> null
}
