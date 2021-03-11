package jp.co.soramitsu.feature_account_impl.presentation.view.advanced

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.EditText
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.common.view.LabeledTextView
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.android.synthetic.main.view_advanced_block.view.advancedTv
import kotlinx.android.synthetic.main.view_advanced_block.view.advancedView
import kotlinx.android.synthetic.main.view_advanced_block.view.derivationPathInput
import kotlinx.android.synthetic.main.view_advanced_block.view.encryptionTypeInput

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

    private val showClickListener = OnClickListener {
        toggle()
    }

    init {
        View.inflate(context, R.layout.view_advanced_block, this)
        orientation = VERTICAL

        advancedTv.setOnClickListener(showClickListener)
    }

    val derivationPathEditText: EditText
        get() = derivationPathInput.content

    val derivationPathField: InputField
        get() = derivationPathInput

    val encryptionTypeField: LabeledTextView
        get() = encryptionTypeInput

    fun toggle() {
        if (advancedView.visibility == View.VISIBLE) {
            collapse()
        } else {
            expand()
        }
    }

    fun expand() {
        advancedView.makeVisible()
        advancedTv.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_minus_24, 0)
    }

    fun collapse() {
        advancedView.makeGone()
        advancedTv.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_plus_white_24, 0)
    }

    fun setOnEncryptionTypeClickListener(clickListener: () -> Unit) {
        encryptionTypeInput.setWholeClickListener {
            maybeCallSelectorListener(encryptionTypeInput, clickListener)
        }
    }

    fun getDerivationPath(): String {
        return derivationPathEditText.text?.toString() ?: ""
    }

    fun setDerivationPath(path: String?) {
        derivationPathEditText.setText(path)
    }

    fun setEncryption(encryption: String) {
        encryptionTypeInput.setMessage(encryption)
    }

    fun configure(field: View, fieldState: FieldState) {
        fieldState.applyTo(field)
    }

    fun configure(fieldState: FieldState) {
        configure(encryptionTypeField, fieldState)
        configure(derivationPathField, fieldState)
    }

    fun setEnabled(field: View, enabled: Boolean) {
        val state = if (enabled) FieldState.NORMAL else FieldState.DISABLED

        configure(field, state)
    }

    private fun maybeCallSelectorListener(view: View, clickListener: () -> Unit) {
        if (view.isEnabled) {
            clickListener.invoke()
        }
    }
}