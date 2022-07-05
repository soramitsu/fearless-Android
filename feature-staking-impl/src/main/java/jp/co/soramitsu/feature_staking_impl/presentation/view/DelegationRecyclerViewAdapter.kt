package jp.co.soramitsu.feature_staking_impl.presentation.view

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.DelegatorViewState

class DelegationRecyclerViewAdapter(
    private val itemHandler: DelegationHandler
) : ListAdapter<DelegatorViewState.CollatorDelegationModel, DelegationViewHolder>(DelegationDiffCallback) {

    interface DelegationHandler {
        fun moreClicked(model: DelegatorViewState.CollatorDelegationModel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DelegationViewHolder {
        val view = parent.inflateChild(R.layout.item_collator_delegation)
        return DelegationViewHolder(view)
    }

    override fun onBindViewHolder(holder: DelegationViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, itemHandler)
    }
}

class DelegationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(item: DelegatorViewState.CollatorDelegationModel, itemHandler: DelegationRecyclerViewAdapter.DelegationHandler) {
        itemView.findViewById<StakeSummaryView>(R.id.summaryView).apply {
            hideLoading()
            setTitle(item.collatorName)
            setTotalRewards(item.rewarded)
            setTotalStaked(item.staked)
            item.rewardedFiat?.let(::setTotalRewardsFiat) ?: hideTotalRewardsFiat()
            item.stakedFiat?.let(::setTotalStakedFiat) ?: hideTotalStakeFiat()
            val status = when (item.status) {
                is DelegatorViewState.CollatorDelegationModel.Status.Active -> StakeSummaryView.Status.ActiveCollator(item.status.nextRoundTimeLeft)
                is DelegatorViewState.CollatorDelegationModel.Status.Leaving -> StakeSummaryView.Status.LeavingCollator(item.status.collatorLeaveTimeLeft ?: 0L)
                DelegatorViewState.CollatorDelegationModel.Status.Inactive,
                DelegatorViewState.CollatorDelegationModel.Status.Idle -> StakeSummaryView.Status.IdleCollator()
            }

            setElectionStatus(status)
            moreActions.setOnClickListener {
                itemHandler.moreClicked(item)
            }
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
