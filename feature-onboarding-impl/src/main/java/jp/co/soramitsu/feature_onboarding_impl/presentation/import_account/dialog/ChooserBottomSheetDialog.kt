package jp.co.soramitsu.feature_onboarding_impl.presentation.import_account.dialog

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_onboarding_impl.R

class ChooserBottomSheetDialog(
    context: Activity
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {
    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.choosed_bottom_dialog, null))
    }
}