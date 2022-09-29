package jp.co.soramitsu.wallet.impl.presentation.balance.list.model

import jp.co.soramitsu.common.compose.component.MultiToggleItem

enum class AssetType(override val title: String) : MultiToggleItem {
    Currencies("Currencies"), NFTs("NFTs")
}
