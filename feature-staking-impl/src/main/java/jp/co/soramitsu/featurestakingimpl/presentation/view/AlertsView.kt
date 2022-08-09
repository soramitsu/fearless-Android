package jp.co.soramitsu.featurestakingimpl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewAlertsBinding
import jp.co.soramitsu.featurestakingimpl.presentation.staking.alerts.AlertsAdapter
import jp.co.soramitsu.featurestakingimpl.presentation.staking.alerts.model.AlertModel

class AlertsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val alertsAdapter = AlertsAdapter()

    private val binding: ViewAlertsBinding

    init {
        inflate(context, R.layout.view_alerts, this)
        binding = ViewAlertsBinding.bind(this)

        orientation = VERTICAL

        with(context) {
            background = getCutCornerDrawable(R.color.blurColor)
        }

        binding.alertsRecycler.adapter = alertsAdapter
    }

    fun setStatus(alerts: List<AlertModel>) {
        if (alerts.isEmpty()) {
            binding.alertsRecycler.makeGone()
            binding.alertNoAlertsInfoTextView.makeVisible()
        } else {
            binding.alertsRecycler.makeVisible()
            binding.alertNoAlertsInfoTextView.makeGone()

            alertsAdapter.submitList(alerts)
        }
    }

    fun showLoading() {
        binding.alertShimmer.makeVisible()
        binding.alertNoAlertsInfoTextView.makeGone()
        binding.alertsRecycler.makeGone()
    }

    fun hideLoading() {
        binding.alertShimmer.makeGone()
        binding.alertNoAlertsInfoTextView.makeVisible()
        binding.alertsRecycler.makeVisible()
    }
}
