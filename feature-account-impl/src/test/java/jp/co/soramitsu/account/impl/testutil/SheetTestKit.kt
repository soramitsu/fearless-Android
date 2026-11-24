package jp.co.soramitsu.account.impl.testutil

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.feature_account_impl.R

/** The rows of a shown fixed-list sheet, in order. */
fun FixedListBottomSheet.rows(): List<View> {
    val container = checkNotNull(findViewById<LinearLayout>(R.id.fixedListSheetItemContainer))
    return (0 until container.childCount).map(container::getChildAt)
        .filter { it.findViewById<TextView>(R.id.itemExternalActionContent) != null }
}

/** The labels of a shown fixed-list sheet's rows, in order. */
fun FixedListBottomSheet.rowLabels(): List<String> =
    rows().map { it.findViewById<TextView>(R.id.itemExternalActionContent).text.toString() }

/** Clicks the row labelled [label]. */
fun FixedListBottomSheet.clickRow(label: String) {
    rows().first { it.findViewById<TextView>(R.id.itemExternalActionContent).text.toString() == label }.performClick()
}

/** The sheet's title text. */
fun FixedListBottomSheet.titleText(): String? = findViewById<TextView>(R.id.fixedListSheetTitle)?.text?.toString()
