package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history

import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.list.BaseGroupedDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.DayHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history.model.TransactionHistoryElement
import jp.co.soramitsu.feature_wallet_impl.util.formatDateTime
import jp.co.soramitsu.feature_wallet_impl.util.formatDaysSinceEpoch
import kotlinx.android.synthetic.main.item_day_header.view.itemDayHeader
import kotlinx.android.synthetic.main.item_transaction.view.itemTransactionAddress
import kotlinx.android.synthetic.main.item_transaction.view.itemTransactionAmount
import kotlinx.android.synthetic.main.item_transaction.view.itemTransactionIcon
import kotlinx.android.synthetic.main.item_transaction.view.itemTransactionStatus
import kotlinx.android.synthetic.main.item_transaction.view.itemTransactionTime

class TransactionHistoryAdapter(
    val handler: Handler
) : GroupedListAdapter<DayHeader, TransactionHistoryElement>(TransactionHistoryDiffCallback) {

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

    override fun bindChild(holder: GroupedListHolder, child: TransactionHistoryElement) {
        (holder as TransactionHolder).bind(child, handler)
    }
}

class TransactionHolder(view: View) : GroupedListHolder(view) {
    fun bind(item: TransactionHistoryElement, handler: TransactionHistoryAdapter.Handler) {
        with(containerView) {
            with(item.transactionModel) {
                itemTransactionAddress.text = displayAddress

                itemTransactionAmount.setTextColorRes(amountColorRes)
                itemTransactionAmount.text = formattedAmount

                itemTransactionTime.text = date.formatDateTime(context)

                if (status != Transaction.Status.COMPLETED) {
                    itemTransactionStatus.makeVisible()
                    itemTransactionStatus.setImageResource(statusAppearance.icon)
                } else {
                    itemTransactionStatus.makeGone()
                }

                setOnClickListener { handler.transactionClicked(this) }
            }

            itemTransactionIcon.setImageDrawable(item.displayAddressModel.image)
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

object TransactionHistoryDiffCallback : BaseGroupedDiffCallback<DayHeader, TransactionHistoryElement>(DayHeader::class.java) {
    override fun areGroupItemsTheSame(oldItem: DayHeader, newItem: DayHeader): Boolean {
        return oldItem.daysSinceEpoch == oldItem.daysSinceEpoch
    }

    override fun areGroupContentsTheSame(oldItem: DayHeader, newItem: DayHeader): Boolean {
        return true
    }

    override fun areChildItemsTheSame(oldItem: TransactionHistoryElement, newItem: TransactionHistoryElement): Boolean {
        return oldItem.transactionModel.hash == newItem.transactionModel.hash
    }

    override fun areChildContentsTheSame(oldItem: TransactionHistoryElement, newItem: TransactionHistoryElement): Boolean {
        return oldItem.transactionModel == newItem.transactionModel
    }
}