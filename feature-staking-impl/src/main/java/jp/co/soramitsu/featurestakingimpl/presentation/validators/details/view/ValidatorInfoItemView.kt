package jp.co.soramitsu.featurestakingimpl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import jp.co.soramitsu.common.utils.dp
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setCompoundDrawableTint
import jp.co.soramitsu.common.utils.setTextOrHide
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewValidatorInfoItemBinding

class ValidatorInfoItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle) {

    private val binding: ViewValidatorInfoItemBinding

    init {
        inflate(context, R.layout.view_validator_info_item, this)
        binding = ViewValidatorInfoItemBinding.bind(this)

        updatePadding(start = 16.dp(context), end = 16.dp(context))

        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attributeSet: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.ValidatorInfoItemView)

        val titleText = typedArray.getString(R.styleable.ValidatorInfoItemView_validatorInfoItemTitle)
        titleText?.let { setTitle(it) }

        val titleIcon = typedArray.getResourceId(R.styleable.ValidatorInfoItemView_validatorInfoItemTitleIcon, 0)
        setTitleIconResource(titleIcon)

        val bodyIcon = typedArray.getResourceId(R.styleable.ValidatorInfoItemView_validatorInfoItemIcon, 0)
        setBodyIconResource(bodyIcon)

        typedArray.recycle()
    }

    fun setDescription(description: String, @DrawableRes icon: Int) {
        binding.validatorsIdentityDescription.makeVisible()
        binding.validatorsIdentityDescription.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)
        binding.validatorsIdentityDescription.text = description
    }

    fun setTitle(title: String) {
        binding.validatorIdentityTitle.text = title
    }

    fun setBody(body: String) {
        binding.validatorIdentityBody.text = body
    }

    fun setBodyIconResource(resource: Int, @ColorRes tintRes: Int? = null) {
        binding.validatorIdentityBody.setCompoundDrawablesWithIntrinsicBounds(0, 0, resource, 0)

        tintRes?.let {
            binding.validatorIdentityBody.setCompoundDrawableTint(tintRes)
        }
    }

    fun setTitleIconResource(resource: Int) {
        binding.validatorIdentityTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, resource, 0)
    }

    fun showExtra() {
        binding.validatorIdentityExtra.makeVisible()
    }

    fun hideExtra() {
        binding.validatorIdentityExtra.makeGone()
    }

    fun setExtraOrHide(extra: String?) {
        binding.validatorIdentityExtra.setTextOrHide(extra)
    }

    fun showDescription() {
        binding.validatorsIdentityDescription.makeVisible()
    }

    fun hideDescription() {
        binding.validatorsIdentityDescription.makeGone()
    }
}

fun ValidatorInfoItemView.setBodyOrHide(text: String?) {
    if (text == null) {
        makeGone()
    } else {
        setBody(text)
        makeVisible()
    }
}
