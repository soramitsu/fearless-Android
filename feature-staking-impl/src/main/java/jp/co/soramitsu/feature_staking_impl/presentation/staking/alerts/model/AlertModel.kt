package jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.Alert
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter

class AlertModel(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val extraMessage: Int,
    val startFlow: ((View) -> Unit)? = null,
) {
    val isWarning: Boolean = startFlow == null

    companion object {
        fun mapAlertToAlertModel(alert: Alert, router: StakingRouter): AlertModel {
            return when (alert) {
                Alert.Election -> {
                    AlertModel(
                        R.drawable.ic_time_24,
                        R.string.staking_alert_election,
                        R.string.staking_alert_start_election_extra_message
                    )
                }
                Alert.ChangeValidators -> {
                    AlertModel(
                        R.drawable.ic_alert_triangle_yellow_24,
                        R.string.staking_alert_change_validators,
                        R.string.staking_alert_change_validators_extra_message
                    ) { router.openCurrentValidators() }
                }
            }
        }
    }
}
