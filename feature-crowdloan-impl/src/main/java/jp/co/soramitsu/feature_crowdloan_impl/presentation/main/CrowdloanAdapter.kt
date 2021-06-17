package jp.co.soramitsu.feature_crowdloan_impl.presentation.main

import android.view.View
import android.view.ViewGroup
import coil.ImageLoader
import coil.clear
import coil.load
import jp.co.soramitsu.common.list.BaseGroupedDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.list.PayloadGenerator
import jp.co.soramitsu.common.list.resolvePayload
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.presentation.main.model.CrowdloanModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.main.model.CrowdloanStatusModel
import kotlinx.android.synthetic.main.item_crowdloan.view.itemCrowdloanArrow
import kotlinx.android.synthetic.main.item_crowdloan.view.itemCrowdloanIcon
import kotlinx.android.synthetic.main.item_crowdloan.view.itemCrowdloanMyContribution
import kotlinx.android.synthetic.main.item_crowdloan.view.itemCrowdloanParaDescription
import kotlinx.android.synthetic.main.item_crowdloan.view.itemCrowdloanParaName
import kotlinx.android.synthetic.main.item_crowdloan.view.itemCrowdloanParaRaised
import kotlinx.android.synthetic.main.item_crowdloan.view.itemCrowdloanTimeRemaining
import kotlinx.android.synthetic.main.item_crowdloan_group.view.itemCrowdloanGroupStatus

class CrowdloanAdapter(
    private val imageLoader: ImageLoader,
    private val handler: Handler,
) : GroupedListAdapter<CrowdloanStatusModel, CrowdloanModel>(CrowdloanDiffCallback) {

    interface Handler {

        fun crowdloanClicked(paraId: ParaId)
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return CrowdloanGroupHolder(parent.inflateChild(R.layout.item_crowdloan_group))
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return CrowdloanChildHolder(imageLoader, parent.inflateChild(R.layout.item_crowdloan))
    }

    override fun bindGroup(holder: GroupedListHolder, group: CrowdloanStatusModel) {
        (holder as CrowdloanGroupHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: CrowdloanModel) {
        (holder as CrowdloanChildHolder).bind(child, handler)
    }

    override fun bindChild(holder: GroupedListHolder, position: Int, child: CrowdloanModel, payloads: List<Any>) {
        resolvePayload(holder, position, payloads) {
            when (it) {
                CrowdloanModel::state -> (holder as CrowdloanChildHolder).bindState(child, handler)
                CrowdloanModel::raised -> (holder as CrowdloanChildHolder).bindRaised(child)
                CrowdloanModel::myContribution -> (holder as CrowdloanChildHolder).bindMyContribution(child)
            }
        }
    }

    override fun onViewRecycled(holder: GroupedListHolder) {
        if (holder is CrowdloanChildHolder) {
            holder.unbind()
        }
    }
}

private object CrowdloanDiffCallback : BaseGroupedDiffCallback<CrowdloanStatusModel, CrowdloanModel>(CrowdloanStatusModel::class.java) {

    override fun getChildChangePayload(oldItem: CrowdloanModel, newItem: CrowdloanModel): Any {
        return CrowdloanPayloadGenerator.diff(oldItem, newItem)
    }

    override fun areGroupItemsTheSame(oldItem: CrowdloanStatusModel, newItem: CrowdloanStatusModel): Boolean {
        return oldItem == newItem
    }

    override fun areGroupContentsTheSame(oldItem: CrowdloanStatusModel, newItem: CrowdloanStatusModel): Boolean {
        return true
    }

    override fun areChildItemsTheSame(oldItem: CrowdloanModel, newItem: CrowdloanModel): Boolean {
        return oldItem.parachainId == newItem.parachainId
    }

    override fun areChildContentsTheSame(oldItem: CrowdloanModel, newItem: CrowdloanModel): Boolean {
        return oldItem == newItem
    }
}

private object CrowdloanPayloadGenerator : PayloadGenerator<CrowdloanModel>(
    CrowdloanModel::state, CrowdloanModel::raised, CrowdloanModel::myContribution
)

private class CrowdloanGroupHolder(containerView: View) : GroupedListHolder(containerView) {

    fun bind(item: CrowdloanStatusModel) = with(containerView) {
        itemCrowdloanGroupStatus.text = item.text
        itemCrowdloanGroupStatus.setTextColorRes(item.textColorRes)
    }
}

private class CrowdloanChildHolder(
    private val imageLoader: ImageLoader,
    containerView: View,
) : GroupedListHolder(containerView) {

    fun bind(
        item: CrowdloanModel,
        handler: CrowdloanAdapter.Handler,
    ) = with(containerView) {
        itemCrowdloanParaDescription.text = item.description
        itemCrowdloanParaName.text = item.title

        bindRaised(item)
        bindMyContribution(item)

        when (val icon = item.icon) {
            is CrowdloanModel.Icon.FromDrawable -> {
                itemCrowdloanIcon.setImageDrawable(icon.data)
            }
            is CrowdloanModel.Icon.FromLink -> {
                itemCrowdloanIcon.load(icon.data, imageLoader)
            }
        }

        bindState(item, handler)
    }

    fun bindState(item: CrowdloanModel, handler: CrowdloanAdapter.Handler) = with(containerView) {
        if (item.state is CrowdloanModel.State.Active) {
            itemCrowdloanTimeRemaining.makeVisible()
            itemCrowdloanTimeRemaining.text = item.state.timeRemaining

            itemCrowdloanParaName.setTextColorRes(R.color.white)
            itemCrowdloanParaDescription.setTextColorRes(R.color.black1)
            itemCrowdloanParaRaised.setTextColorRes(R.color.white)

            itemCrowdloanArrow.makeVisible()

            setOnClickListener { handler.crowdloanClicked(item.parachainId) }
        } else {
            itemCrowdloanTimeRemaining.makeGone()
            itemCrowdloanArrow.makeGone()

            itemCrowdloanParaName.setTextColorRes(R.color.black2)
            itemCrowdloanParaDescription.setTextColorRes(R.color.black2)
            itemCrowdloanParaRaised.setTextColorRes(R.color.black2)

            setOnClickListener(null)
        }
    }

    fun unbind() {
        with(containerView) {
            itemCrowdloanIcon.clear()
        }
    }

    fun bindRaised(item: CrowdloanModel) {
        containerView.itemCrowdloanParaRaised.text = item.raised
    }

    fun bindMyContribution(item: CrowdloanModel) {
        containerView.itemCrowdloanMyContribution.setVisible(item.myContribution != null)
        containerView.itemCrowdloanMyContribution.text = item.myContribution
    }
}
