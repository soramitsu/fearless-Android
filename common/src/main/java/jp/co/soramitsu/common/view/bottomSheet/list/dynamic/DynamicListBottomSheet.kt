package jp.co.soramitsu.common.view.bottomSheet.list.dynamic

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.R

typealias ClickHandler<T> = (T) -> Unit

class ReferentialEqualityDiffCallBack<T> : DiffUtil.ItemCallback<T>() {

    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
        return oldItem === newItem
    }

    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
        return true
    }
}

abstract class DynamicListBottomSheet<T : Any>(
    context: Context,
    private val payload: Payload<T>,
    private val diffCallback: DiffUtil.ItemCallback<T>,
    private val onClicked: ClickHandler<T>
) : BottomSheetDialog(context, R.style.BottomSheetDialog), DynamicListSheetAdapter.Handler<T> {

    class Payload<T>(val data: Collection<T>, val selected: T? = null)

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.bottom_sheet_dynamic_list)
        super.onCreate(savedInstanceState)

        val listContent = findViewById<RecyclerView>(R.id.dynamicListSheetContent)
        listContent?.setHasFixedSize(true)

        val adapter = DynamicListSheetAdapter(payload.selected, this, diffCallback, holderCreator())
        listContent?.adapter = adapter

        adapter.submitList(payload.data.toList())
    }

    abstract fun holderCreator(): HolderCreator<T>

    override fun setTitle(title: CharSequence?) {
        val listTitle = findViewById<TextView>(R.id.dynamicListSheetTitle)
        listTitle?.text = title
    }

    override fun setTitle(titleId: Int) {
        val listTitle = findViewById<TextView>(R.id.dynamicListSheetTitle)
        listTitle?.setText(titleId)
    }

    override fun itemClicked(item: T) {
        onClicked(item)

        dismiss()
    }
}
