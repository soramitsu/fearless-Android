package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.ViewToolbarBinding
import jp.co.soramitsu.common.utils.dp
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible

class Toolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: ViewToolbarBinding

    val rightActionText: TextView
        get() = binding.rightText

    init {
        inflate(context, R.layout.view_toolbar, this)
        binding = ViewToolbarBinding.bind(this)

        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.Toolbar)

            val title = typedArray.getString(R.styleable.Toolbar_titleText)
            title?.let { setTitle(it) }

            val rightIcon = typedArray.getDrawable(R.styleable.Toolbar_iconRight)
            rightIcon?.let { setRightIconDrawable(it) }

            val action = typedArray.getString(R.styleable.Toolbar_textRight)
            action?.let { setTextRight(it) }

            val homeButtonIcon = typedArray.getDrawable(R.styleable.Toolbar_homeButtonIcon)
            homeButtonIcon?.let { setHomeButtonIcon(it) }

            val homeButtonVisible = typedArray.getBoolean(R.styleable.Toolbar_homeButtonVisible, true)
            setHomeButtonVisibility(homeButtonVisible)

            val dividerVisible = typedArray.getBoolean(R.styleable.Toolbar_dividerVisible, true)
            binding.toolbarDivider.setVisible(dividerVisible)

            val backgroundAttrDrawable = typedArray.getDrawable(R.styleable.Toolbar_contentBackground) ?: ColorDrawable(context.getColor(R.color.black))
            binding.background.background = backgroundAttrDrawable

            typedArray.recycle()
        }
    }

    fun setHomeButtonIcon(icon: Drawable) {
        binding.backImg.setImageDrawable(icon)
    }

    fun setTextRight(action: String) {
        binding.rightImg.makeGone()

        binding.rightText.makeVisible()
        binding.rightText.text = action
    }

    fun setTitle(title: String) {
        binding.titleTv.text = title
    }

    fun setTitle(@StringRes titleRes: Int) {
        binding.titleTv.setText(titleRes)
    }

    fun showHomeButton() {
        binding.backImg.makeVisible()
    }

    fun hideHomeButton() {
        binding.backImg.makeGone()
    }

    fun setHomeButtonListener(listener: (View) -> Unit) {
        binding.backImg.setOnClickListener(listener)
    }

    fun setRightIconDrawable(assetIconDrawable: Drawable) {
        binding.rightText.makeGone()

        binding.rightImg.makeVisible()
        binding.rightImg.setImageDrawable(assetIconDrawable)
    }

    fun setRightActionClickListener(listener: (View) -> Unit) {
        binding.rightImg.setOnClickListener(listener)
        binding.rightText.setOnClickListener(listener)
    }

    fun setHomeButtonVisibility(visible: Boolean) {
        binding.backImg.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun addCustomAction(@DrawableRes icon: Int, onClick: OnClickListener) {
        val actionView = ImageView(context).apply {
            setImageResource(icon)
            imageTintList = context.getColorStateList(R.color.actions_color)

            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                val verticalMargin = 16.dp(context)

                val endMarginDp = if (this@Toolbar.binding.toolbarCustomActions.childCount == 0) 16 else 10
                val endMargin = endMarginDp.dp(context)

                val startMargin = 10.dp(context)

                setMargins(startMargin, verticalMargin, endMargin, verticalMargin)
            }

            setOnClickListener(onClick)
        }

        binding.toolbarCustomActions.makeVisible()
        binding.toolbarCustomActions.addView(actionView, 0)
    }
}
