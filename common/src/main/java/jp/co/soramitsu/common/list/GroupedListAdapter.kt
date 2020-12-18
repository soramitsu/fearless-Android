package jp.co.soramitsu.common.list

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer

private const val TYPE_GROUP = 1
private const val TYPE_CHILD = 2

abstract class GroupedListAdapter<GROUP, CHILD>(private val diffCallback: BaseGroupedDiffCallback<GROUP, CHILD>) :
    ListAdapter<Any, GroupedListHolder>(diffCallback) {
    abstract fun createGroupViewHolder(parent: ViewGroup): GroupedListHolder
    abstract fun createChildViewHolder(parent: ViewGroup): GroupedListHolder

    abstract fun bindGroup(holder: GroupedListHolder, group: GROUP)
    abstract fun bindChild(holder: GroupedListHolder, child: CHILD)

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)

        return if (diffCallback.isGroup(item)) TYPE_GROUP else TYPE_CHILD
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): GroupedListHolder {
        return if (viewType == TYPE_GROUP) {
            createGroupViewHolder(parent)
        } else {
            createChildViewHolder(parent)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(holder: GroupedListHolder, position: Int) {
        val item = getItem(position)

        if (getItemViewType(position) == TYPE_GROUP) {
            bindGroup(holder, item as GROUP)
        } else {
            bindChild(holder, item as CHILD)
        }
    }

    protected inline fun <reified T> findIndexOfElement(crossinline condition: (T) -> Boolean): Int {
        return currentList.indexOfFirst { it is T && condition(it) }
    }
}

@Suppress("UNCHECKED_CAST")
abstract class BaseGroupedDiffCallback<GROUP, CHILD>(private val groupClass: Class<GROUP>) :
    DiffUtil.ItemCallback<Any>() {
    abstract fun areGroupItemsTheSame(oldItem: GROUP, newItem: GROUP): Boolean
    abstract fun areGroupContentsTheSame(oldItem: GROUP, newItem: GROUP): Boolean

    abstract fun areChildItemsTheSame(oldItem: CHILD, newItem: CHILD): Boolean
    abstract fun areChildContentsTheSame(oldItem: CHILD, newItem: CHILD): Boolean

    override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem::class != newItem::class) return false

        return if (isGroup(oldItem)) {
            areGroupItemsTheSame(oldItem as GROUP, newItem as GROUP)
        } else {
            areChildItemsTheSame(oldItem as CHILD, newItem as CHILD)
        }
    }

    override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
        if (oldItem::class != newItem::class) return false

        return if (isGroup(oldItem)) {
            areGroupContentsTheSame(oldItem as GROUP, newItem as GROUP)
        } else {
            areChildContentsTheSame(oldItem as CHILD, newItem as CHILD)
        }
    }

    internal fun isGroup(item: Any) = item::class.java == groupClass
}

abstract class GroupedListHolder(override val containerView: View) :
    RecyclerView.ViewHolder(containerView), LayoutContainer