package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getCutCornerDrawable
import kotlinx.android.synthetic.main.button_glassy.view.buttonGlassyContent

class GlassyButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.button_glassy, this)

        with(context) {
            background = addRipple(getCutCornerDrawable(fillColorRes = R.color.blurColorDark))
        }

        attrs?.let(::applyAttributes)
    }

    private fun applyAttributes(attributeSet: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.GlassyButton)

        val title = typedArray.getString(R.styleable.GlassyButton_text)
        buttonGlassyContent.text = title

        val icon = typedArray.getDrawable(R.styleable.GlassyButton_drawable)
        icon?.let(this::setIcon)

        typedArray.recycle()
    }

    private fun setIcon(icon: Drawable) {
        buttonGlassyContent.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
    }
}