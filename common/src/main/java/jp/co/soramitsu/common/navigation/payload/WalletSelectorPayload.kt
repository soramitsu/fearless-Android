package jp.co.soramitsu.common.navigation.payload

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WalletSelectorPayload(val tag: String, val selectedWalletId: Long) : Parcelable
