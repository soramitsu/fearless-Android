package jp.co.soramitsu.wallet.impl.presentation.balance.list

import java.math.BigDecimal

/** Stable-keyed rows shared by the pure portfolio builder and its lazy list. */
sealed interface PortfolioListItem {
    val key: String

    data class AssetRow(val asset: PortfolioAsset, val nested: Boolean = false) : PortfolioListItem {
        override val key: String get() = "asset:${asset.key}"
    }
    data class TokenGroup(
        val id: String,
        val symbol: String,
        val name: String,
        val members: List<PortfolioAsset>,
        val total: BigDecimal?,
        val fiat: BigDecimal?,
        val priceIncomplete: Boolean,
        val hiddenCount: Int,
        val expanded: Boolean
    ) : PortfolioListItem {
        override val key: String get() = "group:$id"
    }
    data class NetworkHeader(val first: PortfolioAsset, val collapsed: Boolean) : PortfolioListItem {
        override val key: String get() = "network:${first.state.chainId}"
    }
    data class ZeroSummary(val networkCount: Int, val expanded: Boolean, val groupId: String? = null) : PortfolioListItem {
        override val key: String get() = "zero:${groupId ?: "all"}"
    }
    data object EmptyWallet : PortfolioListItem { override val key = "empty-wallet" }
}
