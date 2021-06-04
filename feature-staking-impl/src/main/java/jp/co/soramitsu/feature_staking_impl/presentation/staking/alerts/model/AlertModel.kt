package jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model

import android.view.View
import androidx.annotation.DrawableRes

class AlertModel(
    @DrawableRes val icon: Int,
    val title: String,
    val extraMessage: String,
    val type: Type
) {
    sealed class Type {
        object Warning : Type()

        class CallToAction(val action: (View) -> Unit) : Type()
    }
}
