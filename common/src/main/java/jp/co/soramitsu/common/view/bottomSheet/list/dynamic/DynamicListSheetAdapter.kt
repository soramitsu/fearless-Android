package jp.co.soramitsu.common.view.bottomSheet.list.dynamic

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer

typealias HolderCreator<T> = (parentView: ViewGroup) -> DynamicListSheetAdapter.Holder<T>

class DynamicListSheetAdapter<T>(
    private val selected: T?,
    private val handler: DynamicListBottomSheet<T>,
    private val diffCallback: DiffUtil.ItemCallback<T>,
    private val holderCreator: HolderCreator<T>
) : ListAdapter<T, DynamicListSheetAdapter.Holder<T>>(diffCallback) {

    interface Handler<T> {
        fun itemClicked(item: T)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder<T> {
        return holderCreator(parent)
    }

    override fun onBindViewHolder(holder: Holder<T>, position: Int) {
        val item = getItem(position)
        val isSelected = selected?.let { diffCallback.areItemsTheSame(it, item) } ?: false

        holder.bind(item, isSelected, handler)
    }

    abstract class Holder<T>(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {

        open fun bind(item: T, isSelected: Boolean, handler: Handler<T>) {
            itemView.setOnClickListener { handler.itemClicked(item) }
        }
    }
}