package jp.co.soramitsu.featurecrowdloanimpl.presentation.main

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.ImageLoader
import coil.dispose
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
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.featurecrowdloanapi.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.featurecrowdloanimpl.presentation.main.model.CrowdloanModel
import jp.co.soramitsu.featurecrowdloanimpl.presentation.main.model.CrowdloanStatusModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class CrowdloanAdapter(
    private val imageLoader: ImageLoader,
    private val handler: Handler
) : GroupedListAdapter<CrowdloanStatusModel, CrowdloanModel>(CrowdloanDiffCallback) {

    interface Handler {

        fun crowdloanClicked(chainId: ChainId, paraId: ParaId)
        fun copyReferralClicked(code: String)
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
        return oldItem.parachainId == newItem.parachainId && oldItem.relaychainId == newItem.relaychainId
    }

    override fun areChildContentsTheSame(oldItem: CrowdloanModel, newItem: CrowdloanModel): Boolean {
        return oldItem == newItem
    }
}

private object CrowdloanPayloadGenerator : PayloadGenerator<CrowdloanModel>(
    CrowdloanModel::state,
    CrowdloanModel::raised,
    CrowdloanModel::myContribution
)

private class CrowdloanGroupHolder(containerView: View) : GroupedListHolder(containerView) {

    fun bind(item: CrowdloanStatusModel) = with(containerView) {
        findViewById<TextView>(R.id.itemCrowdloanGroupStatus).text = item.text
        findViewById<TextView>(R.id.itemCrowdloanGroupStatus).setTextColorRes(item.textColorRes)
    }
}

private class CrowdloanChildHolder(
    private val imageLoader: ImageLoader,
    containerView: View
) : GroupedListHolder(containerView) {

    fun bind(
        item: CrowdloanModel,
        handler: CrowdloanAdapter.Handler
    ) = with(containerView) {
        findViewById<TextView>(R.id.itemCrowdloanParaDescription).text = item.description
        findViewById<TextView>(R.id.itemCrowdloanParaName).text = item.title

        bindRaised(item)
        bindMyContribution(item)

        when (val icon = item.icon) {
            is CrowdloanModel.Icon.FromDrawable -> {
                findViewById<ImageView>(R.id.itemCrowdloanIcon).setImageDrawable(icon.data)
            }
            is CrowdloanModel.Icon.FromLink -> {
                findViewById<ImageView>(R.id.itemCrowdloanIcon).load(icon.data, imageLoader)
            }
        }

        bindState(item, handler)
    }

    fun bindState(item: CrowdloanModel, handler: CrowdloanAdapter.Handler) = with(containerView) {
        if (item.state is CrowdloanModel.State.Active) {
            findViewById<TextView>(R.id.itemCrowdloanTimeRemaining).makeVisible()
            findViewById<TextView>(R.id.itemCrowdloanTimeRemaining).text = item.state.timeRemaining

            findViewById<TextView>(R.id.itemCrowdloanParaName).setTextColorRes(R.color.white)
            findViewById<TextView>(R.id.itemCrowdloanParaDescription).setTextColorRes(R.color.black1)
            findViewById<TextView>(R.id.itemCrowdloanParaRaised).setTextColorRes(R.color.white)

            findViewById<ImageView>(R.id.itemCrowdloanArrow).makeVisible()

            setOnClickListener { handler.crowdloanClicked(item.relaychainId, item.parachainId) }

            findViewById<LinearLayout>(R.id.itemReferralCode).setOnClickListener { item.referral?.let(handler::copyReferralClicked) }
        } else {
            findViewById<TextView>(R.id.itemCrowdloanTimeRemaining).makeGone()
            findViewById<ImageView>(R.id.itemCrowdloanArrow).makeGone()

            findViewById<TextView>(R.id.itemCrowdloanParaName).setTextColorRes(R.color.black2)
            findViewById<TextView>(R.id.itemCrowdloanParaDescription).setTextColorRes(R.color.black2)
            findViewById<TextView>(R.id.itemCrowdloanParaRaised).setTextColorRes(R.color.black2)

            setOnClickListener(null)
        }
    }

    fun unbind() {
        with(containerView) {
            findViewById<ImageView>(R.id.itemCrowdloanIcon).dispose()
        }
    }

    fun bindRaised(item: CrowdloanModel) {
        containerView.findViewById<TextView>(R.id.itemCrowdloanParaRaised).text = item.raised
    }

    fun bindMyContribution(item: CrowdloanModel) {
        containerView.findViewById<TextView>(R.id.itemCrowdloanMyContribution).setVisible(item.myContribution != null)
        containerView.findViewById<TextView>(R.id.itemCrowdloanMyContribution).setTextColorRes(R.color.colorAccent)
        containerView.findViewById<TextView>(R.id.itemCrowdloanMyContribution).text = item.myContribution

        containerView.findViewById<LinearLayout>(R.id.itemReferralCode).setVisible(item.myContribution != null && item.referral != null)
    }
}
