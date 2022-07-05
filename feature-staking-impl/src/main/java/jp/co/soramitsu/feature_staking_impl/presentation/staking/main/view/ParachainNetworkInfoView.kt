package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_staking_impl.R

class ParachainNetworkInfoView(
    context: Context,
    attrs: AttributeSet? = null,
) : NetworkInfoView(context, attrs) {

    private val view: View = View.inflate(context, R.layout.view_parachain_network_info, this)

    override val storiesList: RecyclerView by lazy { view.findViewById(R.id.parachainStakingStoriesList) }
    override val infoTitle: TextView by lazy { view.findViewById(R.id.parachainStakingNetworkInfoTitle) }
    override val collapsibleView: ConstraintLayout by lazy { view.findViewById(R.id.parachainStakingNetworkCollapsibleView) }
    private val parachainMinimumStakeView: StakingInfoView by lazy { view.findViewById(R.id.parachainMinimumStakeView) }
    private val parachainLockUpPeriodView: StakingInfoView by lazy { view.findViewById(R.id.parachainLockUpPeriodView) }

    init {
        setup()
    }

    override fun showLoading() {
        parachainMinimumStakeView.showLoading()
        parachainLockUpPeriodView.showLoading()
    }

    override fun hideLoading() {
        parachainMinimumStakeView.hideLoading()
        parachainLockUpPeriodView.hideLoading()
    }

    fun setMinimumStake(minimumStake: String) {
        parachainMinimumStakeView.setBody(minimumStake)
    }

    fun setLockupPeriod(period: String) {
        parachainLockUpPeriodView.setBody(period)
    }

    fun setMinimumStakeFiat(minimumStake: String) {
        parachainMinimumStakeView.setExtraBlockValueText(minimumStake)
    }

    fun showMinimumStakeFiat() {
        parachainMinimumStakeView.showWholeExtraBlock()
    }

    fun hideMinimumStakeFiat() {
        parachainMinimumStakeView.makeExtraBlockInvisible()
    }
}
