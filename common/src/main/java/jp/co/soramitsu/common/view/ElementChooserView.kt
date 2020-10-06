package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import jp.co.soramitsu.common.R
import kotlinx.android.synthetic.main.view_element_chooser.view.elementChooserIcon
import kotlinx.android.synthetic.main.view_element_chooser.view.elementChooserText


class ElementChooserView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_element_chooser, this)
        setBackgroundResource(R.drawable.bg_input_shape_selector)

        val padding = resources.getDimensionPixelOffset(R.dimen.element_chooser_padding)
        setPadding(padding, padding, padding, padding)
    }

    fun setIcon(@DrawableRes icon: Int) {
        elementChooserIcon.setImageResource(icon)
    }

    fun setIcon(icon: Drawable) {
        elementChooserIcon.setImageDrawable(icon)
    }

    fun setText(text: String) {
        elementChooserText.text = text
    }
}