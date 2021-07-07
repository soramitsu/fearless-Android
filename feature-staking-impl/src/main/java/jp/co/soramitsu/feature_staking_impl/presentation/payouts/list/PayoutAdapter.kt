package jp.co.soramitsu.feature_staking_impl.presentation.payouts.list

import android.os.CountDownTimer
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.model.PendingPayoutModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.formatTime
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.getDays
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_list_default.view.itemListElementDescriptionLeft
import kotlinx.android.synthetic.main.item_list_default.view.itemListElementDescriptionRight
import kotlinx.android.synthetic.main.item_list_default.view.itemListElementTitleLeft
import kotlinx.android.synthetic.main.item_list_default.view.itemListElementTitleRight

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

class PayoutViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
    var timer : CountDownTimer? = null

    fun bind(payout: PendingPayoutModel, itemHandler: PayoutAdapter.ItemHandler) = with(containerView) {
        with(payout) {
            if (timer != null) timer?.cancel()

            timer = object : CountDownTimer(timeLeft, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val days = millisUntilFinished.getDays()

                    itemListElementDescriptionLeft.text = if(days > 0)
                        resources.getQuantityString(R.plurals.staking_payouts_days_left, days, days)
                    else
                        millisUntilFinished.formatTime()
                }

                override fun onFinish() {
                    itemListElementDescriptionLeft.text = 0L.formatTime()

                    cancel()
                }
            }

            timer?.start()

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
