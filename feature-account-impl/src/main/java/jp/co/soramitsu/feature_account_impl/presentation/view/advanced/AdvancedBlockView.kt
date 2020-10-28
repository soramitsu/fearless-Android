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
import kotlinx.android.synthetic.main.view_advanced_block.view.networkInput

class AdvancedBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class FieldState {
        NORMAL {
            override fun applyTo(field: View) {
                field.isEnabled = true
                field.makeVisible()
            }
        },

        DISABLED {
            override fun applyTo(field: View) {
                field.isEnabled = false
                field.makeVisible()
            }
        },

        HIDDEN {
            override fun applyTo(field: View) {
                field.makeGone()
            }
        };

        abstract fun applyTo(field: View)
    }

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

        advancedTv.setOnClickListener(showClickListener)
    }

    val derivationPathEditText: EditText
        get() = derivationPathEt

    val derivationPathField: View
        get() = derivationPathInput

    val encryptionTypeField: View
        get() = encryptionTypeInput

    val networkTypeField: View
        get() = networkInput

    private fun showAdvanced() {
        advancedView.makeVisible()
        advancedTv.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_minus_24, 0)
    }

    private fun hideAdvanced() {
        advancedView.makeGone()
        advancedTv.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_plus_white_24, 0)
    }

    fun setOnEncryptionTypeClickListener(clickListener: () -> Unit) {
        encryptionTypeInput.setWholeClickListener {
            maybeCallSelectorListener(encryptionTypeInput, clickListener)
        }
    }

    fun setOnNetworkClickListener(clickListener: () -> Unit) {
        networkInput.setWholeClickListener {
            maybeCallSelectorListener(networkInput, clickListener)
        }
    }

    fun getDerivationPath(): String {
        return derivationPathEt.text?.toString() ?: ""
    }

    fun setEncryption(encryption: String) {
        encryptionTypeInput.setMessage(encryption)
    }

    fun setNetworkName(network: String) {
        networkInput.setMessage(network)
    }

    fun setNetworkIconResource(iconRes: Int) {
        networkInput.setTextIcon(iconRes)
    }

    fun configure(field: View, fieldState: FieldState) {
        fieldState.applyTo(field)
    }

    fun configure(fieldState: FieldState) {
        configure(encryptionTypeField, fieldState)
        configure(networkTypeField, fieldState)
        configure(derivationPathField, fieldState)
    }

    fun setNetworkSelectorEnabled(enabled: Boolean) {
        updateSelectorState(networkInput, enabled)

        networkInput.isEnabled = enabled
    }

    private fun updateSelectorState(view: View, enabled: Boolean) {
        val background = if (enabled) R.drawable.bg_input_shape_selector else R.drawable.bg_button_primary_disabled

        view.setBackgroundResource(background)
    }

    private fun maybeCallSelectorListener(view: View, clickListener: () -> Unit) {
        if (view.isEnabled) {
            clickListener.invoke()
        }
    }
}