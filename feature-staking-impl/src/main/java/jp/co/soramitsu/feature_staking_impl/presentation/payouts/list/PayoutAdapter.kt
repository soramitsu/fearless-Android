package jp.co.soramitsu.feature_staking_impl.presentation.payouts.list

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.common.view.startTimer
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.model.PendingPayoutModel

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

    override fun onBindViewHolder(holder: PayoutViewHolder, position: Int) {
        val item = getItem(position)

        holder.bind(item, itemHandler)
    }
}

class PayoutViewHolder(private val containerView: View) : RecyclerView.ViewHolder(containerView) {

    fun bind(payout: PendingPayoutModel, itemHandler: PayoutAdapter.ItemHandler) = with(containerView) {
        with(payout) {
            findViewById<TextView>(R.id.itemListElementDescriptionLeft).startTimer(timeLeft, createdAt) {
                it.text = context.getText(R.string.staking_payout_expired)
                it.setTextColor(context.getColor(R.color.red))
            }

            findViewById<TextView>(R.id.itemListElementTitleLeft).text = validatorTitle
            findViewById<TextView>(R.id.itemListElementTitleRight).text = amount
            findViewById<TextView>(R.id.itemListElementDescriptionRight).text = amountFiat
            findViewById<TextView>(R.id.itemListElementDescriptionLeft).setTextColorRes(daysLeftColor)
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
