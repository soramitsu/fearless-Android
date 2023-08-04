package jp.co.soramitsu.account.impl.presentation.view.advanced

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import jp.co.soramitsu.account.api.presentation.importing.ImportAccountType
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.common.view.LabeledTextView
import jp.co.soramitsu.feature_account_impl.R
import jp.co.soramitsu.feature_account_impl.databinding.ViewAdvancedBlockBinding

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

    private val binding: ViewAdvancedBlockBinding

    init {
        inflate(context, R.layout.view_advanced_block, this)
        binding = ViewAdvancedBlockBinding.bind(this)

        orientation = VERTICAL

        binding.advancedTv.setOnClickListener(showClickListener)
    }

    val substrateDerivationPathEditText: EditText
        get() = binding.substrateDerivationPathInput.content

    val substrateDerivationPathField: InputField
        get() = binding.substrateDerivationPathInput

    val substrateEncryptionTypeField: LabeledTextView
        get() = binding.substrateEncryptionTypeInput

    val ethereumDerivationPathEditText: EditText
        get() = binding.ethereumDerivationPathInput.content

    val ethereumDerivationPathField: InputField
        get() = binding.ethereumDerivationPathInput

    val ethereumEncryptionTypeField: LabeledTextView
        get() = binding.ethereumEncryptionTypeInput

    val substrateDerivationPathHintView: TextView
        get() = binding.substrateDerivationPathHint

    val ethereumDerivationPathHintView: TextView
        get() = binding.ethereumDerivationPathHint

    fun toggle() {
        if (binding.advancedView.visibility == View.VISIBLE) {
            collapse()
        } else {
            expand()
        }
    }

    fun expand() {
        binding.advancedView.makeVisible()
        binding.advancedTv.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_minus_24, 0)
    }

    fun collapse() {
        binding.advancedView.makeGone()
        binding.advancedTv.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_plus_white_24, 0)
    }

    fun setOnSubstrateEncryptionTypeClickListener(clickListener: () -> Unit) {
        binding.substrateEncryptionTypeInput.setWholeClickListener {
            maybeCallSelectorListener(binding.substrateEncryptionTypeInput, clickListener)
        }
    }

    fun getSubstrateDerivationPath(): String {
        return substrateDerivationPathEditText.text?.toString().orEmpty()
    }

    fun setSubstrateDerivationPath(path: String?) {
        substrateDerivationPathEditText.setText(path)
    }

    fun setSubstrateEncryption(encryption: String) {
        binding.substrateEncryptionTypeInput.setMessage(encryption)
    }

    fun getEthereumDerivationPath(): String {
        return ethereumDerivationPathEditText.text?.toString().orEmpty()
    }

    fun setEthereumDerivationPath(path: String?) {
        ethereumDerivationPathEditText.setText(path)
    }

    fun configure(field: View, fieldState: FieldState) {
        fieldState.applyTo(field)
    }

    fun configureHint(hint: View, fieldState: FieldState) {
        when (fieldState) {
            FieldState.NORMAL -> hint.makeVisible()
            FieldState.DISABLED -> hint.makeGone()
            FieldState.HIDDEN -> hint.makeGone()
        }
    }

    fun configure(fieldState: FieldState) {
        configureSubstrate(fieldState)
        configureEthereum(fieldState)
    }

    fun configureForSeed(blockchainType: ImportAccountType) {
        when (blockchainType) {
            ImportAccountType.Substrate -> {
                configureSubstrate(FieldState.NORMAL)
                configureEthereum(FieldState.HIDDEN)
            }
            ImportAccountType.Ethereum -> {
                configure(FieldState.HIDDEN)
                configure(ethereumEncryptionTypeField, FieldState.DISABLED)
            }
        }
    }

    fun configureForMnemonic(blockchainType: ImportAccountType) {
        when (blockchainType) {
            ImportAccountType.Substrate -> {
                configureSubstrate(FieldState.NORMAL)
                configureEthereum(FieldState.HIDDEN)
            }
            ImportAccountType.Ethereum -> {
                configure(FieldState.HIDDEN)
                configureEthereum(FieldState.NORMAL)
                configure(ethereumEncryptionTypeField, FieldState.DISABLED)
            }
        }
    }

    fun configureSubstrate(fieldState: FieldState) {
        configure(substrateEncryptionTypeField, fieldState)
        configure(substrateDerivationPathField, fieldState)
        configureHint(substrateDerivationPathHintView, fieldState)
    }

    fun configureEthereum(fieldState: FieldState) {
        configure(ethereumEncryptionTypeField, fieldState)
        configure(ethereumDerivationPathField, fieldState)
        configureHint(ethereumDerivationPathHintView, fieldState)
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
