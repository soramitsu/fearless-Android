package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.view

import android.content.Context
import android.util.AttributeSet
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewNetworkInfoBinding

class RelayChainNetworkInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : NetworkInfoView(context, attrs) {

    private val binding = ViewNetworkInfoBinding.bind(inflate(context, R.layout.view_network_info, this))
    override val storiesList by lazy { binding.stakingStoriesList }
    override val infoTitle by lazy { binding.stakingNetworkInfoTitle }
    override val collapsibleView by lazy { binding.stakingNetworkCollapsibleView }

    init {
        setup()
    }

    override fun showLoading() {
        binding.totalStakeView.showLoading()
        binding.minimumStakeView.showLoading()
        binding.activeNominatorsView.showLoading()
        binding.lockUpPeriodView.showLoading()
    }

    override fun hideLoading() {
        binding.totalStakeView.hideLoading()
        binding.minimumStakeView.hideLoading()
        binding.activeNominatorsView.hideLoading()
        binding.lockUpPeriodView.hideLoading()
    }

    fun setTotalStake(totalStake: String) {
        binding.totalStakeView.setBody(totalStake)
    }

    fun setNominatorsCount(nominatorsCount: String) {
        binding.activeNominatorsView.setBody(nominatorsCount)
    }

    fun setTotalStakeFiat(totalStake: String) {
        binding.totalStakeView.setExtraBlockValueText(totalStake)
    }

    fun showTotalStakeFiat() {
        binding.totalStakeView.showWholeExtraBlock()
    }

    fun hideTotalStakeFiat() {
        binding.totalStakeView.makeExtraBlockInvisible()
    }

    fun setMinimumStake(minimumStake: String) {
        binding.minimumStakeView.setBody(minimumStake)
    }

    fun setLockupPeriod(period: String) {
        binding.lockUpPeriodView.setBody(period)
    }

    fun setMinimumStakeFiat(minimumStake: String) {
        binding.minimumStakeView.setExtraBlockValueText(minimumStake)
    }

    fun showMinimumStakeFiat() {
        binding.minimumStakeView.showWholeExtraBlock()
    }

    fun hideMinimumStakeFiat() {
        binding.minimumStakeView.makeExtraBlockInvisible()
    }
}
