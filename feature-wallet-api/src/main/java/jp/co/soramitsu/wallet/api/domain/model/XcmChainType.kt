package jp.co.soramitsu.wallet.api.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class XcmChainType : Parcelable {
    Original, Destination
}
