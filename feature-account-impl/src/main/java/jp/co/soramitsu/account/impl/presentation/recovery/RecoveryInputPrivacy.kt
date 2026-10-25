package jp.co.soramitsu.account.impl.presentation.recovery

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText

/** FragmentManager saves its root directly, so excluding only the root from its parent is insufficient. */
internal fun View.excludeRecoveryInputFromPersistence() {
    isSaveEnabled = false
    isSaveFromParentEnabled = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (this is EditText) imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    }
    if (this is ViewGroup) {
        for (index in 0 until childCount) getChildAt(index).excludeRecoveryInputFromPersistence()
    }
}
