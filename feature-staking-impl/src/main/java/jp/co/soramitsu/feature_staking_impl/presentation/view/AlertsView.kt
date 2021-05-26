package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.AlertsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model.AlertStatus
import kotlinx.android.synthetic.main.view_alert.view.*

class AlertsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle), AlertsAdapter.ItemHandler {

    private val alertsAdapter = AlertsAdapter(this)

    init {
        View.inflate(context, R.layout.view_alert, this)

        orientation = VERTICAL

        with(context) {
            background = getCutCornerDrawable(R.color.blurColor)
        }

        alertsRecycler.adapter = alertsAdapter
        alertsRecycler.setHasFixedSize(true)
    }

    fun setStatus(status: AlertStatus) {
        when (status) {
            is AlertStatus.Alerts -> {
                alertsRecycler.makeVisible()
                alertNoAlertsInfoTextView.makeGone()

                alertsAdapter.submitList(status.alerts)
            }
            AlertStatus.NoAlerts -> {
                alertsRecycler.makeGone()
                alertNoAlertsInfoTextView.makeVisible()
            }
        }
    }

    override fun alertClicked(index: Int) {
    }
}
