package jp.co.soramitsu.feature_account_impl.presentation.view.advanced

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.EditText
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.android.synthetic.main.view_advanced_block.view.advancedTv
import kotlinx.android.synthetic.main.view_advanced_block.view.advancedView
import kotlinx.android.synthetic.main.view_advanced_block.view.derivationPathEt
import kotlinx.android.synthetic.main.view_advanced_block.view.derivationPathInput
import kotlinx.android.synthetic.main.view_advanced_block.view.encryptionTypeInput
import kotlinx.android.synthetic.main.view_advanced_block.view.encryptionTypeText
import kotlinx.android.synthetic.main.view_advanced_block.view.networkInput
import kotlinx.android.synthetic.main.view_advanced_block.view.networkText

class AdvancedBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var areSelectorsEnabled: Boolean = true

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

    val derivationPathField: EditText
        get() = derivationPathEt

    private fun showAdvanced() {
        advancedView.makeVisible()
        advancedTv.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_minus_24, 0)
    }

    private fun hideAdvanced() {
        advancedView.makeGone()
        advancedTv.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_plus_white_24, 0)
    }

    fun setOnEncryptionTypeClickListener(clickListener: () -> Unit) {
        encryptionTypeInput.setOnClickListener {
            maybeCallSelectorListener(clickListener)
        }
    }

    fun setOnNetworkClickListener(clickListener: () -> Unit) {
        networkInput.setOnClickListener {
            maybeCallSelectorListener(clickListener)
        }
    }

    fun getDerivationPath(): String {
        return derivationPathEt.text?.toString() ?: ""
    }

    fun setEncryption(encryption: String) {
        encryptionTypeText.text = encryption
    }

    fun setNetworkName(network: String) {
        networkText.text = network
    }

    fun setNetworkIconResource(icon: Int) {
        networkText.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
    }

    fun setSelectorsEnabled(enabled: Boolean) {
        areSelectorsEnabled = enabled

        updateSelectorState(encryptionTypeInput, enabled)
        updateSelectorState(networkInput, enabled)

        derivationPathInput.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    fun setNetworkSelectorEnabled(enabled: Boolean) {
        updateSelectorState(networkInput, enabled)

        networkInput.isEnabled = enabled
    }

    private fun updateSelectorState(view: View, enabled: Boolean) {
        val background = if (enabled) R.drawable.bg_input_shape_selector else R.drawable.bg_button_primary_disabled

        view.setBackgroundResource(background)
    }

    private fun maybeCallSelectorListener(clickListener: () -> Unit) {
        if (areSelectorsEnabled) {
            clickListener.invoke()
        }
    }
}