package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.DelegatorViewState
import kotlinx.android.synthetic.main.item_collator_delegation.view.summaryView

class DelegationRecyclerViewAdapter : ListAdapter<DelegatorViewState.CollatorDelegationModel, DelegationViewHolder>(DelegationDiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DelegationViewHolder {
        val view = parent.inflateChild(R.layout.item_collator_delegation)
        return DelegationViewHolder(view)
    }

    override fun onBindViewHolder(holder: DelegationViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }
}

class DelegationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(item: DelegatorViewState.CollatorDelegationModel) {
        itemView.summaryView.apply {
            hideLoading()
            setTitle(item.collatorName)
            setTotalRewards(item.rewarded)
            setTotalStaked(item.staked)
            item.rewardedFiat?.let(::setTotalRewardsFiat) ?: hideTotalRewardsFiat()
            item.stakedFiat?.let(::setTotalStakedFiat) ?: hideTotalStakeFiat()
            val status = when (item.status) {
                DelegatorViewState.CollatorDelegationModel.Status.ACTIVE -> StakeSummaryView.Status.ActiveCollator(item.nextRewardTimeLeft)
                DelegatorViewState.CollatorDelegationModel.Status.INACTIVE -> StakeSummaryView.Status.InactiveCollator()
            }

            setElectionStatus(status)
        }
    }
}

object DelegationDiffCallback : DiffUtil.ItemCallback<DelegatorViewState.CollatorDelegationModel>() {

    override fun areItemsTheSame(oldItem: DelegatorViewState.CollatorDelegationModel, newItem: DelegatorViewState.CollatorDelegationModel): Boolean {
        return oldItem.collatorAddress == newItem.collatorAddress
    }

    override fun areContentsTheSame(oldItem: DelegatorViewState.CollatorDelegationModel, newItem: DelegatorViewState.CollatorDelegationModel): Boolean {
        return oldItem == newItem
    }
}
