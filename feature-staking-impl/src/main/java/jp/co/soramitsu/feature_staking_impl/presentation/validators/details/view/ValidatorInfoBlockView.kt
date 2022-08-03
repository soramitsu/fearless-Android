package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewValidatorInfoBlockBinding

class ValidatorInfoBlockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val binding: ViewValidatorInfoBlockBinding

    init {
        inflate(context, R.layout.view_validator_info_block, this)
        binding = ViewValidatorInfoBlockBinding.bind(this)

        orientation = VERTICAL

        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attributeSet: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.ValidatorInfoBlockView)

        val showExtra = typedArray.getBoolean(R.styleable.ValidatorInfoBlockView_showExtra, false)
        changeExtraVisibility(showExtra)

        val title = typedArray.getString(R.styleable.ValidatorInfoBlockView_validatorInfoTitle)
        title?.let { setTitle(it) }

        val showInfoIcon = typedArray.getBoolean(R.styleable.ValidatorInfoBlockView_showInfoIcon, false)
        changeInfoIconVisibility(showInfoIcon)

        typedArray.recycle()
    }

    fun setTitle(title: String) {
        binding.validatorInfoBlockTitle.text = title
    }

    fun changeExtraVisibility(visible: Boolean) {
        if (visible) {
            binding.validatorInfoBlockExtra.makeVisible()
        } else {
            binding.validatorInfoBlockExtra.makeGone()
        }
    }

    fun setBody(text: String) {
        binding.validatorInfoBlockBody.text = text
    }

    fun setExtra(text: String) {
        binding.validatorInfoBlockExtra.text = text
    }

    fun changeInfoIconVisibility(visible: Boolean) {
        if (visible) {
            binding.validatorInfoBlockTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_info_16, 0)
        } else {
            binding.validatorInfoBlockTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }
    }
}
