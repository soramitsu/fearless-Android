package jp.co.soramitsu.common.view.bottomSheet.list.dynamic

import android.content.Context
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.R
import kotlinx.android.synthetic.main.bottom_sheet_dynamic_list.dynamicListSheetContent
import kotlinx.android.synthetic.main.bottom_sheet_dynamic_list.dynamicListSheetTitle

typealias ClickHandler<T> = (T) -> Unit

abstract class DynamicListBottomSheet<T>(
    context: Context,
    private val payload: Payload<T>,
    private val diffCallback: DiffUtil.ItemCallback<T>,
    private val onClicked: ClickHandler<T>
) : BottomSheetDialog(context, R.style.BottomSheetDialog), DynamicListSheetAdapter.Handler<T> {

    class Payload<T>(val data: List<T>, val selected: T? = null)

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.bottom_sheet_dynamic_list)
        super.onCreate(savedInstanceState)

        dynamicListSheetContent.setHasFixedSize(true)

        val adapter = DynamicListSheetAdapter(payload.selected, this, diffCallback, holderCreator())
        dynamicListSheetContent.adapter = adapter

        adapter.submitList(payload.data)
    }

    abstract fun holderCreator(): HolderCreator<T>

    override fun setTitle(title: CharSequence?) {
        dynamicListSheetTitle.text = title
    }

    override fun setTitle(titleId: Int) {
        dynamicListSheetTitle.setText(titleId)
    }

    override fun itemClicked(item: T) {
        onClicked(item)

        dismiss()
    }
}