package jp.co.soramitsu.feature_staking_impl.presentation.payouts.list

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.model.PendingPayoutModel
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_payout.view.itemPayoutAddress
import kotlinx.android.synthetic.main.item_payout.view.itemPayoutAmount
import kotlinx.android.synthetic.main.item_payout.view.itemPayoutFiat
import kotlinx.android.synthetic.main.item_payout.view.itemPayoutTime

class PayoutAdapter(
    private val itemHandler: ItemHandler,
) : ListAdapter<PendingPayoutModel, PayoutViewHolder>(PayoutModelDiffCallback()) {

    interface ItemHandler {
        fun payoutClicked(index: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PayoutViewHolder {
        val view = parent.inflateChild(R.layout.item_payout)

        return PayoutViewHolder(view)
    }

    override fun onBindViewHolder(holder: PayoutViewHolder, position: Int) {
        val item = getItem(position)

        holder.bind(item, itemHandler)
    }
}

class PayoutViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {

    fun bind(validator: PendingPayoutModel, itemHandler: PayoutAdapter.ItemHandler) = with(containerView) {
        with(validator) {
            itemPayoutAddress.text = validatorTitle
            itemPayoutAmount.text = amount
            itemPayoutFiat.text = amountFiat
            itemPayoutTime.text = daysLeft
            itemPayoutTime.setTextColorRes(daysLeftColor)
        }

        setOnClickListener { itemHandler.payoutClicked(adapterPosition) }
    }
}

class PayoutModelDiffCallback : DiffUtil.ItemCallback<PendingPayoutModel>() {

    override fun areItemsTheSame(oldItem: PendingPayoutModel, newItem: PendingPayoutModel): Boolean {
        return oldItem === newItem
    }

    override fun areContentsTheSame(oldItem: PendingPayoutModel, newItem: PendingPayoutModel): Boolean {
        return true
    }
}
