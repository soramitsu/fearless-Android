package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history

import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.list.BaseGroupedDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.formatDaysSinceEpoch
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.feature_wallet_api.domain.model.SubqueryElement
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.DayHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.NewTransactionHistoryElement
import kotlinx.android.synthetic.main.item_day_header.view.itemDayHeader
import kotlinx.android.synthetic.main.item_transaction.view.*

class TransactionHistoryAdapter(
    val handler: Handler
) : GroupedListAdapter<DayHeader, NewTransactionHistoryElement>(TransactionHistoryDiffCallback) {

    interface Handler {
        fun transactionClicked(transactionModel: TransactionModel)
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return DayHolder(parent.inflateChild(R.layout.item_day_header))
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return TransactionHolder(parent.inflateChild(R.layout.item_transaction))
    }

    override fun bindGroup(holder: GroupedListHolder, group: DayHeader) {
        (holder as DayHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: NewTransactionHistoryElement) {
        (holder as TransactionHolder).bind(child, handler)
    }
}

class TransactionHolder(view: View) : GroupedListHolder(view) {
    fun bind(item: NewTransactionHistoryElement, handler: TransactionHistoryAdapter.Handler) {
        with(containerView) {
            with(item.transactionModel) {
                itemTransactionAddress.text = getOperationHeader()

                itemTransactionAmount.setTextColorRes(amountColorRes)
                itemTransactionAmount.text = formattedAmount

                itemTransactionTime.text = time.formatDateTime(context)

                itemTransactionType.text = getElementDescription()

                if (operation.status != SubqueryElement.Status.COMPLETED) {
                    itemTransactionStatus.makeVisible()
                    itemTransactionStatus.setImageResource(statusAppearance.icon)
                } else {
                    itemTransactionStatus.makeGone()
                }
//
//                setOnClickListener { handler.transactionClicked(this) }
            }

            val operationIcon = item.transactionModel.getOperationIcon()?.let { ContextCompat.getDrawable(context, it) }
            itemTransactionIcon.setImageDrawable(operationIcon ?: item.displayAddressModel.image)
        }
    }
}

class DayHolder(view: View) : GroupedListHolder(view) {
    fun bind(item: DayHeader) {
        with(containerView) {
            itemDayHeader.text = item.daysSinceEpoch.formatDaysSinceEpoch(context)
        }
    }
}

object TransactionHistoryDiffCallback : BaseGroupedDiffCallback<DayHeader, NewTransactionHistoryElement>(DayHeader::class.java) {
    override fun areGroupItemsTheSame(oldItem: DayHeader, newItem: DayHeader): Boolean {
        return oldItem.daysSinceEpoch == oldItem.daysSinceEpoch
    }

    override fun areGroupContentsTheSame(oldItem: DayHeader, newItem: DayHeader): Boolean {
        return true
    }

    override fun areChildItemsTheSame(oldItem: NewTransactionHistoryElement, newItem: NewTransactionHistoryElement): Boolean {
        return oldItem.transactionModel.hash == newItem.transactionModel.hash
    }

    override fun areChildContentsTheSame(oldItem: NewTransactionHistoryElement, newItem: NewTransactionHistoryElement): Boolean {
        return oldItem.transactionModel == newItem.transactionModel
    }
}
