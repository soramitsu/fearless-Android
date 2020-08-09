package jp.co.soramitsu.feature_account_impl.presentation.view.advanced

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
import kotlinx.android.synthetic.main.view_advanced_block.view.derivationPathEt
import kotlinx.android.synthetic.main.view_advanced_block.view.encryptionTypeInput
import kotlinx.android.synthetic.main.view_advanced_block.view.networkInput

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

    fun setOnEncryptionTypeClickListener(clickListener: () -> Unit) {
        encryptionTypeInput.setOnClickListener { clickListener() }
    }

    fun setOnNetworkClickListener(clickListener: () -> Unit) {
        networkInput.setOnClickListener { clickListener() }
    }

    fun getDerivationPath(): String {
        return derivationPathEt.text?.toString() ?: ""
    }
}