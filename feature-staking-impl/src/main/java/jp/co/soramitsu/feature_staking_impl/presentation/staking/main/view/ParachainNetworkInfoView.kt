package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.view

import android.content.Context
import android.util.AttributeSet
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewParachainNetworkInfoBinding

class ParachainNetworkInfoView(
    context: Context,
    attrs: AttributeSet? = null,
) : NetworkInfoView(context, attrs) {

    private val binding = ViewParachainNetworkInfoBinding.bind(inflate(context, R.layout.view_parachain_network_info, this))

    override val storiesList by lazy { binding.parachainStakingStoriesList }
    override val infoTitle by lazy { binding.parachainStakingNetworkInfoTitle }
    override val collapsibleView by lazy { binding.parachainStakingNetworkCollapsibleView }

    init {
        setup()
    }

    override fun showLoading() {
        binding.parachainMinimumStakeView.showLoading()
        binding.parachainLockUpPeriodView.showLoading()
    }

    override fun hideLoading() {
        binding.parachainMinimumStakeView.hideLoading()
        binding.parachainLockUpPeriodView.hideLoading()
    }

    fun setMinimumStake(minimumStake: String) {
        binding.parachainMinimumStakeView.setBody(minimumStake)
    }

    fun setLockupPeriod(period: String) {
        binding.parachainLockUpPeriodView.setBody(period)
    }

    fun setMinimumStakeFiat(minimumStake: String) {
        binding.parachainMinimumStakeView.setExtraBlockValueText(minimumStake)
    }

    fun showMinimumStakeFiat() {
        binding.parachainMinimumStakeView.showWholeExtraBlock()
    }

    fun hideMinimumStakeFiat() {
        binding.parachainMinimumStakeView.makeExtraBlockInvisible()
    }
}
