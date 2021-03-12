package jp.co.soramitsu.common.navigation

import android.os.Parcelable

@Retention(AnnotationRetention.SOURCE)
annotation class PinRequired

interface DelayedNavigation : Parcelable

interface SecureRouter {

    fun withPinCodeCheckRequired(
        delayedNavigation: DelayedNavigation,
        createMode: Boolean = false,
        pinCodeTitleRes: Int? = null
    )

    fun openAfterPinCode(delayedNavigation: DelayedNavigation)
}
