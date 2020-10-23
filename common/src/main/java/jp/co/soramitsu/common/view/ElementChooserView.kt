package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import jp.co.soramitsu.common.R
import kotlinx.android.synthetic.main.view_element_chooser.view.elementChooserDropdown
import kotlinx.android.synthetic.main.view_element_chooser.view.elementChooserIcon
import kotlinx.android.synthetic.main.view_element_chooser.view.elementChooserText

class ElementChooserView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_element_chooser, this)

        val padding = resources.getDimensionPixelOffset(R.dimen.element_chooser_padding)
        setPadding(padding, padding, padding, padding)

        attrs?.let(this::applyAttributes)
    }

    private fun applyAttributes(attributeSet: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.ElementChooserView)

        val enabled = typedArray.getBoolean(R.styleable.ElementChooserView_enabled, true)
        isEnabled = enabled

        typedArray.recycle()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        val backgroundRes = if (enabled) R.drawable.bg_input_shape_filled_selector else R.drawable.bg_button_primary_disabled
        setBackgroundResource(backgroundRes)

        elementChooserDropdown.visibility = if (enabled) View.VISIBLE else View.GONE
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