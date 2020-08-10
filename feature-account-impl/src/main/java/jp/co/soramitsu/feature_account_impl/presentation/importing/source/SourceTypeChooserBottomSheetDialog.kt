package jp.co.soramitsu.feature_account_impl.presentation.importing.source

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_account_api.domain.model.SourceType
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.presentation.importing.source.model.SourceTypeModel
import kotlinx.android.synthetic.main.bottom_sheet_source_chooser.sourceRv
import kotlinx.android.synthetic.main.bottom_sheet_source_chooser.titleTv

class SourceTypeChooserBottomSheetDialog(
    context: Activity,
    sourceTypeModels: List<SourceTypeModel>,
    itemClickListener: (SourceType) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

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

        val adapter = SourceTypeListAdapter {
            itemClickListener(it)
            dismiss()
        }

        adapter.submitList(sourceTypeModels)
        sourceRv.adapter = adapter
    }
}