package jp.co.soramitsu.featurewalletimpl.presentation.transaction.history

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import coil.ImageLoader
import coil.load
import jp.co.soramitsu.common.list.BaseGroupedDiffCallback
import jp.co.soramitsu.common.list.GroupedListAdapter
import jp.co.soramitsu.common.list.GroupedListHolder
import jp.co.soramitsu.common.utils.formatDateTime
import jp.co.soramitsu.common.utils.formatDaysSinceEpoch
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setTextColorRes
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.featurewalletimpl.presentation.model.OperationModel
import jp.co.soramitsu.featurewalletimpl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.featurewalletimpl.presentation.transaction.history.model.DayHeader

class TransactionHistoryAdapter(
    private val handler: Handler,
    private val imageLoader: ImageLoader
) : GroupedListAdapter<DayHeader, OperationModel>(TransactionHistoryDiffCallback) {

    interface Handler {
        fun transactionClicked(transactionModel: OperationModel)
    }

    override fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder {
        return DayHolder(parent.inflateChild(R.layout.item_day_header))
    }

    override fun createChildViewHolder(parent: ViewGroup): GroupedListHolder {
        return TransactionHolder(parent.inflateChild(R.layout.item_transaction), imageLoader)
    }

    override fun bindGroup(holder: GroupedListHolder, group: DayHeader) {
        (holder as DayHolder).bind(group)
    }

    override fun bindChild(holder: GroupedListHolder, child: OperationModel) {
        (holder as TransactionHolder).bind(child, handler)
    }
}

class TransactionHolder(view: View, private val imageLoader: ImageLoader) : GroupedListHolder(view) {
    fun bind(item: OperationModel, handler: TransactionHistoryAdapter.Handler) {
        with(containerView) {
            with(item) {
                findViewById<TextView>(R.id.itemTransactionHeader).text = header

                findViewById<TextView>(R.id.itemTransactionAmount).setTextColorRes(amountColorRes)
                findViewById<TextView>(R.id.itemTransactionAmount).text = amount

                findViewById<TextView>(R.id.itemTransactionTime).text = time.formatDateTime(context)

                findViewById<TextView>(R.id.itemTransactionSubHeader).text = subHeader

                if (statusAppearance != OperationStatusAppearance.COMPLETED) {
                    findViewById<ImageView>(R.id.itemTransactionStatus).makeVisible()
                    findViewById<ImageView>(R.id.itemTransactionStatus).setImageResource(statusAppearance.icon)
                } else {
                    findViewById<ImageView>(R.id.itemTransactionStatus).makeGone()
                }

                setOnClickListener { handler.transactionClicked(this) }
            }

            if (item.assetIconUrl != null) {
                findViewById<ImageView>(R.id.itemTransactionIcon).load(item.assetIconUrl, imageLoader)
            } else {
                findViewById<ImageView>(R.id.itemTransactionIcon).setImageDrawable(item.operationIcon)
            }
        }
    }
}

class DayHolder(view: View) : GroupedListHolder(view) {
    fun bind(item: DayHeader) {
        with(containerView) {
            findViewById<TextView>(R.id.itemDayHeader).text = item.daysSinceEpoch.formatDaysSinceEpoch(context)
        }
    }
}

object TransactionHistoryDiffCallback : BaseGroupedDiffCallback<DayHeader, OperationModel>(DayHeader::class.java) {
    override fun areGroupItemsTheSame(oldItem: DayHeader, newItem: DayHeader): Boolean {
        return oldItem.daysSinceEpoch == oldItem.daysSinceEpoch
    }

    override fun areGroupContentsTheSame(oldItem: DayHeader, newItem: DayHeader): Boolean {
        return true
    }

    override fun areChildItemsTheSame(oldItem: OperationModel, newItem: OperationModel): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areChildContentsTheSame(oldItem: OperationModel, newItem: OperationModel): Boolean {
        return oldItem.statusAppearance == newItem.statusAppearance &&
            oldItem.header == newItem.header &&
            oldItem.subHeader == newItem.subHeader &&
            oldItem.amount == newItem.amount
    }
}
