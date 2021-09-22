package jp.co.soramitsu.feature_wallet_api.presentation.model

val Token.Type.icon: Int
    get() = when (this) {
        Token.Type.KSM -> R.drawable.ic_token_ksm
        Token.Type.WND -> R.drawable.ic_token_wnd
        Token.Type.DOT -> R.drawable.ic_token_dot
        Token.Type.ROC -> R.drawable.ic_token_dot
    }
