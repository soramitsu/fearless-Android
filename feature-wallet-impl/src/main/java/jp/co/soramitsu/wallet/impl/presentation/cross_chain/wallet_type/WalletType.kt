package jp.co.soramitsu.wallet.impl.presentation.cross_chain.wallet_type

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class WalletType : Parcelable {
    My, External
}
