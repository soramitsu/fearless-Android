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
import jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model.AlertModel
import kotlinx.android.synthetic.main.view_alerts.view.*

class AlertsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    private val alertsAdapter = AlertsAdapter()

    init {
        View.inflate(context, R.layout.view_alerts, this)

        orientation = VERTICAL

        with(context) {
            background = getCutCornerDrawable(R.color.blurColor)
        }

        alertsRecycler.adapter = alertsAdapter
    }

    fun setStatus(alerts: List<AlertModel>) {
        if (alerts.isEmpty()) {
            alertsRecycler.makeGone()
            alertNoAlertsInfoTextView.makeVisible()
        } else {
            alertsRecycler.makeVisible()
            alertNoAlertsInfoTextView.makeGone()

            alertsAdapter.submitList(alerts)
        }
    }

    fun showLoading() {
        alertShimmer.makeVisible()
        alertNoAlertsInfoTextView.makeGone()
        alertsRecycler.makeGone()
    }

    fun hideLoading() {
        alertShimmer.makeGone()
        alertNoAlertsInfoTextView.makeVisible()
        alertsRecycler.makeVisible()
    }
}
