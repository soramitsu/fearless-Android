package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.AlertsAdapter
import kotlinx.android.synthetic.main.view_alert.view.*

class AlertsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle), AlertsAdapter.ItemHandler {

    sealed class Status {
        class Alerts(val alerts: List<AlertModel>) : Status()

        object NoAlerts : Status()
    }

    private val alertsAdapter = AlertsAdapter(this)

    init {
        View.inflate(context, R.layout.view_alert, this)

        orientation = VERTICAL

        with(context) {
            background = getCutCornerDrawable(R.color.blurColor)
        }

        alertsRecycler.adapter = alertsAdapter
        alertsRecycler.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
    }

    fun setStatus(status: Status) {
        when (status) {
            is Status.Alerts -> {
                alertsRecycler.makeVisible()
                alertNoAlertsInfoTextView.makeGone()
                alertStakingUnavailableTextView.makeGone()

                alertsAdapter.submitList(status.alerts)
            }
            Status.NoAlerts -> {
                alertsRecycler.makeGone()
                alertNoAlertsInfoTextView.makeVisible()
                alertStakingUnavailableTextView.makeGone()
                alertMessage.setText(R.string.staking_alert_no_alerts_now)
            }
        }
    }

    override fun alertClicked(index: Int) {
    }
}
