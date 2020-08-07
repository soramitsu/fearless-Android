package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_account_api.domain.model.SourceType
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog.model.SourceTypeChooserDialogData
import kotlinx.android.synthetic.main.choosed_bottom_dialog.list
import kotlinx.android.synthetic.main.choosed_bottom_dialog.titleTv

class SourceTypeChooserBottomSheetDialog(
    context: Activity,
    sourceTypeChooserDialogData: SourceTypeChooserDialogData,
    itemClickListener: (SourceType) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {
    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.choosed_bottom_dialog, null))
        titleTv.text = "Source type"

        val adapter = SourceTypeListAdapter(
            sourceTypeChooserDialogData.selectedSourceType
        ) {
            itemClickListener(it)
            dismiss()
        }
        adapter.submitList(sourceTypeChooserDialogData.sourceTypes)
        list.adapter = adapter
    }
}