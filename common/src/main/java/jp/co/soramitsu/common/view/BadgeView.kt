package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.load
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.databinding.ViewTokenBadgeBinding

class BadgeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewTokenBadgeBinding

    init {
        inflate(context, R.layout.view_token_badge, this)
        binding = ViewTokenBadgeBinding.bind(this)

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
        binding.badgeIcon.setImageDrawable(icon)
    }

    fun setIcon(iconUrl: String?, imageLoader: ImageLoader) {
        binding.badgeIcon.load(iconUrl, imageLoader)
    }

    fun setText(text: CharSequence) {
        binding.badgeName.text = text
    }
}
