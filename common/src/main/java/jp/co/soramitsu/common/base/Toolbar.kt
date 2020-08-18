package jp.co.soramitsu.common.base

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import kotlinx.android.synthetic.main.tool_bar.view.backImg
import kotlinx.android.synthetic.main.tool_bar.view.rightImg
import kotlinx.android.synthetic.main.tool_bar.view.titleTv

class Toolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, R.layout.tool_bar, this)
        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.Toolbar)

            val title = typedArray.getString(R.styleable.Toolbar_titleText)
            title?.let { setTitle(it) }

            val rightIcon = typedArray.getDrawable(R.styleable.Toolbar_iconRight)
            rightIcon?.let { setRightIconDrawable(it) }

            val homeButtonVisible = typedArray.getBoolean(R.styleable.Toolbar_homeButtonVisible, true)
            setHomeButtonVisibility(homeButtonVisible)

            typedArray.recycle()
        }
    }

    fun setTitle(title: String) {
        titleTv.text = title
    }

    fun showHomeButton() {
        backImg.makeVisible()
    }

    fun hideHomeButton() {
        backImg.makeGone()
    }

    fun setHomeButtonListener(listener: (View) -> Unit) {
        backImg.setOnClickListener(listener)
    }

    fun setRightIconDrawable(assetIconDrawable: Drawable) {
        rightImg.makeVisible()
        rightImg.setImageDrawable(assetIconDrawable)
    }

    fun setRightIconClickListener(listener: (View) -> Unit) {
        rightImg.setOnClickListener(listener)
    }

    fun setHomeButtonVisibility(visible: Boolean) {
        backImg.visibility = if (visible) View.VISIBLE else View.GONE
    }
}