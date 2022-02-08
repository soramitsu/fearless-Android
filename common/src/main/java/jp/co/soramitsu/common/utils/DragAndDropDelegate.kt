package jp.co.soramitsu.common.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class DragAndDropDelegate<T>(
    private val initialItemsLiveData: LiveData<List<T>>,
    private val updateItems: (List<T>) -> Unit = {}
) : DragAndDropTouchHelperCallback.Listener {

    private val _unsyncedSwapLiveData = MutableLiveData<List<T>>()
    val unsyncedSwapLiveData: LiveData<List<T>> = _unsyncedSwapLiveData

    override fun onItemDrag(from: Int, to: Int) {
        val currentState = _unsyncedSwapLiveData.value ?: initialItemsLiveData.value ?: return

        val newUnsyncedState = currentState.toMutableList()

        val removedItem = newUnsyncedState.removeAt(from)
        newUnsyncedState.add(to, removedItem)

        _unsyncedSwapLiveData.value = newUnsyncedState
        updateItems(newUnsyncedState)
    }
}
