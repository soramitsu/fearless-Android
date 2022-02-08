package jp.co.soramitsu.common.utils

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class DragAndDropTouchHelperCallback(private val listener: Listener) : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
    private var dragFrom: Int? = null
    private var dragTo: Int? = null

    override fun isLongPressDragEnabled() = false

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        val from = viewHolder.adapterPosition
        val to = target.adapterPosition

        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false

        if (dragFrom == null) {
            dragFrom = from
        }

        dragTo = to

        listener.onItemDrag(from, to)

        return false
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)

        dragFrom = null
        dragTo = null
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    interface Listener {
        fun onItemDrag(from: Int, to: Int)
    }
}

fun dragAndDropItemTouchHelper(listener: DragAndDropTouchHelperCallback.Listener): ItemTouchHelper {
    val callback = DragAndDropTouchHelperCallback(listener)
    return ItemTouchHelper(callback)
}
