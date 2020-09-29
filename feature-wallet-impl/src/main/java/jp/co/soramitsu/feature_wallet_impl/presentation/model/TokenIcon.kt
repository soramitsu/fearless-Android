package jp.co.soramitsu.feature_wallet_impl.presentation.model

import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.R

val Asset.Token.icon: Int
    get() = when (this) {
        Asset.Token.KSM -> R.drawable.ic_token_ksm
        Asset.Token.WND -> R.drawable.ic_token_wnd
        Asset.Token.DOT -> R.drawable.ic_token_dot
    }
