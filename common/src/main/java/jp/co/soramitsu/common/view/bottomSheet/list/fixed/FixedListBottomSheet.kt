package jp.co.soramitsu.common.view.bottomSheet.list.fixed

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.inflateChild
import jp.co.soramitsu.common.utils.setDrawableStart

abstract class FixedListBottomSheet(context: Context) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        setContentView(R.layout.bottom_sheeet_fixed_list)
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
    }

    final override fun setContentView(layoutResId: Int) {
        super.setContentView(layoutResId)
    }

    override fun setTitle(@StringRes titleRes: Int) {
        findViewById<TextView>(R.id.fixedListSheetTitle)?.setText(titleRes)
    }

    override fun setTitle(title: CharSequence?) {
        findViewById<TextView>(R.id.fixedListSheetTitle)?.text = title
    }

    fun item(@LayoutRes layoutRes: Int, builder: (View) -> Unit) {
        val view = findViewById<LinearLayout>(R.id.fixedListSheetItemContainer)?.inflateChild(layoutRes)

        view?.let { builder.invoke(it) }

        findViewById<LinearLayout>(R.id.fixedListSheetItemContainer)?.addView(view)
    }

    fun <T : View> item(view: T, builder: (T) -> Unit) {
        builder.invoke(view)

        findViewById<LinearLayout>(R.id.fixedListSheetItemContainer)?.addView(view)
    }

    inline fun View.setDismissingClickListener(crossinline listener: (View) -> Unit) {
        setOnClickListener {
            listener.invoke(it)

            dismiss()
        }
    }
}

fun FixedListBottomSheet.item(@DrawableRes icon: Int, @StringRes titleRes: Int, onClick: (View) -> Unit) =
    item(icon, context.resources.getString(titleRes), onClick)

fun FixedListBottomSheet.item(@DrawableRes icon: Int, title: String, onClick: (View) -> Unit) {
    item(R.layout.item_sheet_iconic_label) { view ->
        view.findViewById<TextView>(R.id.itemExternalActionContent).text = title
        view.findViewById<TextView>(R.id.itemExternalActionContent).setDrawableStart(icon, widthInDp = 24, tint = R.color.white)

        view.setDismissingClickListener(onClick)
    }
}

fun FixedListBottomSheet.textItem(@StringRes titleRes: Int, onClick: (View) -> Unit) {
    item(R.layout.item_sheet_label) { view ->
        view.findViewById<TextView>(R.id.itemExternalActionContent).text = context.resources.getString(titleRes)

        view.setDismissingClickListener(onClick)
    }
}
