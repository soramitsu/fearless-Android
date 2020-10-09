package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.history

import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.groupedList.BaseGroupedDiffCallback
import jp.co.soramitsu.common.groupedList.GroupedListAdapter
import jp.co.soramitsu.common.groupedList.GroupedListHolder
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TransactionModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.icon
import jp.co.soramitsu.feature_wallet_impl.util.formatDateTime
import jp.co.soramitsu.feature_wallet_impl.util.formatDaysSinceEpoch
import kotlinx.android.synthetic.main.item_day_header.view.itemDayHeader
import kotlinx.android.synthetic.main.item_transaction.view.itemTransactionAddress
import kotlinx.android.synthetic.main.item_transaction.view.itemTransactionAmount
import kotlinx.android.synthetic.main.item_transaction.view.itemTransactionIcon
import kotlinx.android.synthetic.main.item_transaction.view.itemTransactionStatus
import kotlinx.android.synthetic.main.item_transaction.view.itemTransactionTime

class TransactionHistoryAdapter : GroupedListAdapter<DayHeader, TransactionModel>(TransactionHistoryDiffCallback) {
    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return DayHolder(parent.inflateChild(R.layout.item_day_header))
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return TransactionHolder(parent.inflateChild(R.layout.item_transaction))
    }

    override fun bindGroup(holder: GroupedListHolder, group: DayHeader) {
        (holder as DayHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: TransactionModel) {
        (holder as TransactionHolder).bind(child)
    }
}

class TransactionHolder(view: View) : GroupedListHolder(view) {
    fun bind(item: TransactionModel) {
        with(containerView) {
            itemTransactionAddress.text = item.displayAddress

            itemTransactionAmount.setTextColorRes(item.amountColorRes)
            itemTransactionAmount.text = item.formattedAmount

            itemTransactionIcon.setImageResource(item.token.icon)
            itemTransactionTime.text = item.date.formatDateTime(context)

            if (item.status != Transaction.Status.COMPLETED) {
                itemTransactionStatus.makeVisible()
                itemTransactionStatus.setImageResource(item.statusIcon)
            } else {
                itemTransactionStatus.makeGone()
            }
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

object TransactionHistoryDiffCallback : BaseGroupedDiffCallback<DayHeader, TransactionModel>(DayHeader::class.java) {
    override fun areGroupItemsTheSame(oldItem: DayHeader, newItem: DayHeader): Boolean {
        return oldItem.daysSinceEpoch == oldItem.daysSinceEpoch
    }

    override fun areGroupContentsTheSame(oldItem: DayHeader, newItem: DayHeader): Boolean {
        return true
    }

    override fun areChildItemsTheSame(oldItem: TransactionModel, newItem: TransactionModel): Boolean {
        return oldItem.hash == newItem.hash
    }

    override fun areChildContentsTheSame(oldItem: TransactionModel, newItem: TransactionModel): Boolean {
        return oldItem == newItem
    }
}