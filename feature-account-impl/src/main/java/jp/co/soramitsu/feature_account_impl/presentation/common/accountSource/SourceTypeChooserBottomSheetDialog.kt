package jp.co.soramitsu.feature_account_impl.presentation.common.accountSource

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.common.accountSource.SourceTypeListAdapter.SourceItemHandler
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.ImportSource
import kotlinx.android.synthetic.main.bottom_sheet_source_chooser.sourceRv
import kotlinx.android.synthetic.main.bottom_sheet_source_chooser.titleTv

class SourceSelectorPayload<T: AccountSource>(val allSources: List<T>, val selected: T)

class SourceTypeChooserBottomSheetDialog<T: AccountSource>(
    context: Activity,
    payload: SourceSelectorPayload<T>,
    private val itemTypeClickListener: (T) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog), SourceItemHandler<T> {

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheet_source_chooser, null))

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        })

        titleTv.text = context.getString(R.string.recovery_source_type)

        val adapter = SourceTypeListAdapter(payload.selected, this)

        adapter.submitList(payload.allSources)
        sourceRv.adapter = adapter
    }

    override fun onSourceSelected(source: T) {
        itemTypeClickListener.invoke(source)

        dismiss()
    }
}