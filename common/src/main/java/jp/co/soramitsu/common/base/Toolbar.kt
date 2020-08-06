package jp.co.soramitsu.common.base

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import kotlinx.android.synthetic.main.tool_bar.view.backImg
import kotlinx.android.synthetic.main.tool_bar.view.rightIconImg
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
            val rightImageId = typedArray.getResourceId(R.styleable.Toolbar_rightIconImage, 0)

            title?.let {
                setTitle(it)
            }

            setRightIconImage(rightImageId)

            typedArray.recycle()
        }
    }

    private fun setTitle(title: String) {
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

    private fun setRightIconImage(imageResource: Int) {
        rightIconImg.setImageResource(imageResource)
    }

    fun showRightButton() {
        rightIconImg.makeVisible()
    }

    fun hideRightButton() {
        rightIconImg.makeGone()
    }

    fun setRightIconButtonListener(listener: (View) -> Unit) {
        rightIconImg.setOnClickListener(listener)
    }
}