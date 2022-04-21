package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.shape.addRipple
import jp.co.soramitsu.common.view.shape.getIdleDrawable
import kotlinx.android.synthetic.main.view_profile_option_item.view.optionAction
import kotlinx.android.synthetic.main.view_profile_option_item.view.optionIcon
import kotlinx.android.synthetic.main.view_profile_option_item.view.optionSubtitle
import kotlinx.android.synthetic.main.view_profile_option_item.view.optionTitle


class ProfileOptionItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, R.layout.view_profile_option_item, this)

        background = with(context) { addRipple(ContextCompat.getDrawable(context, R.color.black)) }

        isFocusable = true
        isClickable = true

        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ProfileOptionItemView)

            val icon = typedArray.getDrawable(R.styleable.ProfileOptionItemView_optionIcon)
            icon?.let(::setIcon)

            val title = typedArray.getString(R.styleable.ProfileOptionItemView_optionTitle)
            title?.let(::setTitle)

            val subtitle = typedArray.getString(R.styleable.ProfileOptionItemView_optionSubtitle)
            subtitle?.let(::setSubtitle)

            val textVisible = typedArray.getBoolean(R.styleable.ProfileOptionItemView_optionSubtitleVisible, true)
            optionSubtitle.visibility = if (textVisible) View.VISIBLE else View.GONE

            val enabled = typedArray.getBoolean(R.styleable.ProfileOptionItemView_enabled, true)
            isEnabled = enabled

            typedArray.recycle()
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)

        optionAction.setVisible(enabled)
    }

    fun setActionIcon(icon: Drawable) {
        optionAction.setImageDrawable(icon)
    }

    fun setIcon(icon: Drawable) {
        optionIcon.setImageDrawable(icon)
    }

    fun setActionListener(clickListener: (View) -> Unit) {
        optionAction.setOnClickListener(clickListener)
    }

    fun setWholeClickListener(listener: (View) -> Unit) {
        setOnClickListener(listener)

        setActionListener(listener)
    }

    fun setTitle(accountName: String) {
        optionTitle.text = accountName
    }

    fun setSubtitle(address: String) {
        optionSubtitle.text = address
    }

    fun setAccountIcon(icon: Drawable) {
        optionIcon.setImageDrawable(icon)
    }

    fun hideBody() {
        optionSubtitle.makeGone()
    }

    fun showBody() {
        optionSubtitle.makeVisible()
    }
}

