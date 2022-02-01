package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.load
import jp.co.soramitsu.common.R
import kotlinx.android.synthetic.main.view_token_badge.view.badgeIcon
import kotlinx.android.synthetic.main.view_token_badge.view.badgeName

class BadgeView @JvmOverloads constructor(context: Context,
                                          attrs: AttributeSet? = null,
                                          defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, R.layout.view_token_badge, this)
        orientation = HORIZONTAL
        background = ContextCompat.getDrawable(context, R.drawable.bg_asset_badge)
        applyAttributes(attrs)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.BadgeView)

            val iconResource = typedArray.getDrawable(R.styleable.BadgeView_iconRes)
            iconResource?.let(::setIcon)

            val text = typedArray.getText(R.styleable.BadgeView_text)
            text?.let(::setText)

            typedArray.recycle()
        }
    }

    fun setIcon(icon: Drawable) {
        badgeIcon.setImageDrawable(icon)
    }

    fun setIcon(iconUrl: String?, imageLoader: ImageLoader) {
        badgeIcon.load(iconUrl, imageLoader)
    }

    fun setText(text: CharSequence) {
        badgeName.text = text
    }

    fun setText(text: String?) {
        badgeName.text = text
    }
}
