package jp.co.soramitsu.feature_account_impl.presentation.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.android.synthetic.main.view_advanced_block.view.advancedTv
import kotlinx.android.synthetic.main.view_advanced_block.view.advancedView

class AdvancedBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val showClickListener = OnClickListener {
        if (advancedView.visibility == View.VISIBLE) {
            hideAdvanced()
        } else {
            showAdvanced()
        }
    }

    init {
        View.inflate(context, R.layout.view_advanced_block, this)
        orientation = VERTICAL

        applyAttributes(attrs)

        advancedTv.setOnClickListener(showClickListener)
    }

    private fun applyAttributes(attrs: AttributeSet?) {
        attrs?.let {

        }
    }

    private fun showAdvanced() {
        advancedView.makeVisible()
    }

    private fun hideAdvanced() {
        advancedView.makeGone()
    }
}