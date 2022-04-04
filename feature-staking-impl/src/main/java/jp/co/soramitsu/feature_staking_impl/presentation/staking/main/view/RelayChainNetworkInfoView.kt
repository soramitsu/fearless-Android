package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_network_info.view.activeNominatorsView
import kotlinx.android.synthetic.main.view_network_info.view.lockUpPeriodView
import kotlinx.android.synthetic.main.view_network_info.view.minimumStakeView
import kotlinx.android.synthetic.main.view_network_info.view.totalStakeView

class RelayChainNetworkInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : NetworkInfoView(context, attrs) {

    private val view = View.inflate(context, R.layout.view_network_info, this)
    override val storiesList: RecyclerView by lazy { view.findViewById(R.id.stakingStoriesList) }
    override val infoTitle: TextView by lazy { view.findViewById(R.id.stakingNetworkInfoTitle) }
    override val collapsibleView: ConstraintLayout by lazy { view.findViewById(R.id.stakingNetworkCollapsibleView) }

    init {
        setup()
    }

    override fun showLoading() {
        totalStakeView.showLoading()
        minimumStakeView.showLoading()
        activeNominatorsView.showLoading()
        lockUpPeriodView.showLoading()
    }

    override fun hideLoading() {
        totalStakeView.hideLoading()
        minimumStakeView.hideLoading()
        activeNominatorsView.hideLoading()
        lockUpPeriodView.hideLoading()
    }

    fun setTotalStake(totalStake: String) {
        totalStakeView.setBody(totalStake)
    }

    fun setNominatorsCount(nominatorsCount: String) {
        activeNominatorsView.setBody(nominatorsCount)
    }

    fun setTotalStakeFiat(totalStake: String) {
        totalStakeView.setExtraBlockValueText(totalStake)
    }

    fun showTotalStakeFiat() {
        totalStakeView.showWholeExtraBlock()
    }

    fun hideTotalStakeFiat() {
        totalStakeView.makeExtraBlockInvisible()
    }

    fun setMinimumStake(minimumStake: String) {
        minimumStakeView.setBody(minimumStake)
    }

    fun setLockupPeriod(period: String) {
        lockUpPeriodView.setBody(period)
    }

    fun setMinimumStakeFiat(minimumStake: String) {
        minimumStakeView.setExtraBlockValueText(minimumStake)
    }

    fun showMinimumStakeFiat() {
        minimumStakeView.showWholeExtraBlock()
    }

    fun hideMinimumStakeFiat() {
        minimumStakeView.makeExtraBlockInvisible()
    }
}
