package jp.co.soramitsu.common.view.bottomSheet

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.inflateChild
import kotlinx.android.synthetic.main.bottom_sheeet_fixed_list.fixedListSheetClose
import kotlinx.android.synthetic.main.bottom_sheeet_fixed_list.fixedListSheetItemContainer
import kotlinx.android.synthetic.main.bottom_sheeet_fixed_list.fixedListSheetTitle
import kotlinx.android.synthetic.main.item_fixed_list_sheet.view.itemFixedListLabel
import kotlinx.android.synthetic.main.item_fixed_list_sheet.view.itemFixedListValue
import java.math.BigDecimal

abstract class BaseFixedListBottomSheet(context: Context) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheeet_fixed_list, null))

        fixedListSheetClose.setOnClickListener { dismiss() }
    }

    final override fun setContentView(view: View) {
        super.setContentView(view)
    }

    override fun setTitle(@StringRes titleRes: Int) {
        fixedListSheetTitle.setText(titleRes)
    }

    override fun setTitle(title: CharSequence?) {
        fixedListSheetTitle.text = title
    }

    fun addItem(@StringRes labelRes: Int, value: String) {
        val view = fixedListSheetItemContainer.inflateChild(R.layout.item_fixed_list_sheet)

        view.itemFixedListLabel.setText(labelRes)
        view.itemFixedListValue.text = value

        fixedListSheetItemContainer.addView(view)
    }


}