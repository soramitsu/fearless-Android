package jp.co.soramitsu.common.view

import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.util.StateSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import coil.ImageLoader
import coil.load
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.shape.getIdleDrawable
import jp.co.soramitsu.common.view.shape.getSelectedDrawable
import kotlinx.android.synthetic.main.view_accounts_with_one_key.view.chainIcon
import kotlinx.android.synthetic.main.view_accounts_with_one_key.view.chainName
import kotlinx.android.synthetic.main.view_accounts_with_one_key.view.checkMark
import kotlinx.android.synthetic.main.view_accounts_with_one_key.view.moreChainsBadge

class AmountsWithOneKeyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : ConstraintLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_accounts_with_one_key, this)

        setBackground()

        applyAttributes(attrs)
    }

    override fun childDrawableStateChanged(child: View?) {
        refreshDrawableState()
    }

    private fun setBackground() {
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), context.getSelectedDrawable())
            addState(StateSet.WILD_CARD, context.getIdleDrawable())
        }
    }

    private fun applyAttributes(attributeSet: AttributeSet?) {
        attributeSet?.let {
            val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.AmountsWithOneKeyView)

            val checked = typedArray.getBoolean(R.styleable.AmountsWithOneKeyView_android_checked, false)
            isSelected = checked
            typedArray.recycle()
        }
    }

    override fun setSelected(selected: Boolean) {
        checkMark.isVisible = selected
        super.setSelected(selected)
    }

    fun setBadgeText(text: String?) {
        moreChainsBadge.isVisible = !text.isNullOrEmpty()
        text?.let {
            moreChainsBadge.text = it
        }
    }

    fun setChainName(name: String?) {
        name?.let { chainName.text = it }
    }

    fun loadChainIcon(iconUrl: String?, imageLoader: ImageLoader) {
        chainIcon.load(iconUrl, imageLoader)
    }
}
