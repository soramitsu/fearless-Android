package jp.co.soramitsu.common.navigation

import android.os.Parcelable

@Retention(AnnotationRetention.SOURCE)
annotation class PinRequired

interface DelayedNavigation : Parcelable

interface SecureRouter {

    fun withPincodeCheckRequired(
        delayedNavigation: DelayedNavigation,
        createMode: Boolean = false,
        pincodeTitleRes: Int? = null
    )

    fun openAfterPincode(delayedNavigation: DelayedNavigation)
}