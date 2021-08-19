package jp.co.soramitsu.feature_staking_impl.presentation.payouts.list

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.view.startTimer
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.model.PendingPayoutModel
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_list_default.view.itemListElementDescriptionLeft
import kotlinx.android.synthetic.main.item_list_default.view.itemListElementDescriptionRight
import kotlinx.android.synthetic.main.item_list_default.view.itemListElementTitleLeft
import kotlinx.android.synthetic.main.item_list_default.view.itemListElementTitleRight
import kotlin.time.ExperimentalTime

class PayoutAdapter(
    private val itemHandler: ItemHandler,
) : ListAdapter<PendingPayoutModel, PayoutViewHolder>(PayoutModelDiffCallback()) {

    interface ItemHandler {
        fun payoutClicked(index: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PayoutViewHolder {
        val view = parent.inflateChild(R.layout.item_list_default)

        return PayoutViewHolder(view)
    }

    @ExperimentalTime
    override fun onBindViewHolder(holder: PayoutViewHolder, position: Int) {
        val item = getItem(position)

        holder.bind(item, itemHandler)
    }
}

class PayoutViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {

    @ExperimentalTime
    fun bind(payout: PendingPayoutModel, itemHandler: PayoutAdapter.ItemHandler) = with(containerView) {
        with(payout) {
            itemListElementDescriptionLeft.startTimer(timeLeft, createdAt) {
                it.text = context.getText(R.string.staking_payout_expired)
                it.setTextColor(context.getColor(R.color.red))
            }

            itemListElementTitleLeft.text = validatorTitle
            itemListElementTitleRight.text = amount
            itemListElementDescriptionRight.text = amountFiat
            itemListElementDescriptionLeft.setTextColorRes(daysLeftColor)
        }

        setOnClickListener { itemHandler.payoutClicked(adapterPosition) }
    }
}

private class PayoutModelDiffCallback : DiffUtil.ItemCallback<PendingPayoutModel>() {

    override fun areItemsTheSame(oldItem: PendingPayoutModel, newItem: PendingPayoutModel): Boolean {
        return oldItem === newItem
    }

    override fun areContentsTheSame(oldItem: PendingPayoutModel, newItem: PendingPayoutModel): Boolean {
        return true
    }
}
