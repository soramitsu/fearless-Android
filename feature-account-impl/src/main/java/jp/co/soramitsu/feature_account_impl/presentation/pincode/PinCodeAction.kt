package jp.co.soramitsu.feature_account_impl.presentation.pincode

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

sealed class PinCodeAction : Parcelable {
    @Parcelize class Create(val destination: Int) : PinCodeAction()
    @Parcelize class Check(val destination: Int) : PinCodeAction()
    @Parcelize object Change : PinCodeAction()
}