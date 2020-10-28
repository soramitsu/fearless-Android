package jp.co.soramitsu.feature_account_impl.presentation.common.accountSource

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.makeInvisible
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.common.accountSource.SourceTypeListAdapter.SourceItemHandler
import kotlinx.android.synthetic.main.item_source.view.rightIcon
import kotlinx.android.synthetic.main.item_source.view.sourceTv

class SourceTypeListAdapter<T : AccountSource>(
    private val selectedSource: T?,
    private val itemClickListener: SourceItemHandler<T>
) : ListAdapter<T, SourceTypeViewHolder<T>>(DiffCallback()) {

    interface SourceItemHandler<T : AccountSource> {
        fun onSourceSelected(source: T)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): SourceTypeViewHolder<T> {
        return SourceTypeViewHolder(viewGroup.inflateChild(R.layout.item_source))
    }

    override fun onBindViewHolder(sourceTypeViewHolder: SourceTypeViewHolder<T>, position: Int) {
        val currentSourceType = getItem(position)

        val isSelected = selectedSource?.nameRes == currentSourceType.nameRes

        sourceTypeViewHolder.bind(currentSourceType, itemClickListener, isSelected)
    }
}

class SourceTypeViewHolder<T : AccountSource>(itemView: View) : RecyclerView.ViewHolder(itemView) {

    fun bind(sourceType: T, typeClickListener: SourceItemHandler<T>, isSelected: Boolean) {
        with(itemView) {
            if (isSelected) {
                rightIcon.makeVisible()
            } else {
                rightIcon.makeInvisible()
            }

            sourceTv.setText(sourceType.nameRes)

            setOnClickListener {
                typeClickListener.onSourceSelected(sourceType)
            }
        }
    }
}

private class DiffCallback<T : AccountSource> : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
        return oldItem.nameRes == newItem.nameRes
    }

    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
        return true
    }
}