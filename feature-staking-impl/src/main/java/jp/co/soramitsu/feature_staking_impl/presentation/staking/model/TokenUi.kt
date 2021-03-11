package jp.co.soramitsu.feature_staking_impl.presentation.staking.model

import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_wallet_api.domain.model.Token

val Token.Type.icon: Int
    get() = when (this) {
        Token.Type.KSM -> R.drawable.ic_token_ksm
        Token.Type.WND -> R.drawable.ic_token_wnd
        Token.Type.DOT -> R.drawable.ic_token_dot
    }
