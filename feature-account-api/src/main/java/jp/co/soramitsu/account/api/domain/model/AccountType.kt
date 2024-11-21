package jp.co.soramitsu.account.api.domain.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
enum class AccountType: Parcelable {
    SubstrateOrEvm, Ton
}