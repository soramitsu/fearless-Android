package jp.co.soramitsu.wallet.impl.presentation.balance.list.model

import androidx.annotation.StringRes
import jp.co.soramitsu.common.compose.component.MultiToggleItem
import jp.co.soramitsu.feature_wallet_impl.R

enum class AssetType(@StringRes override val titleResId: Int) : MultiToggleItem {
    Currencies(R.string.сurrencies_stub_text),
    NFTs(R.string.nfts_stub);
}
