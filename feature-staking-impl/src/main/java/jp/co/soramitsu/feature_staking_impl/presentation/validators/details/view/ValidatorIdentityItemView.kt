package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_validator_identity_item.view.validatorIdentityBody
import kotlinx.android.synthetic.main.view_validator_identity_item.view.validatorIdentityTitle

class ValidatorIdentityItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_validator_identity_item, this)

        orientation = HORIZONTAL

        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attributeSet: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.ValidatorIdentityItemView)

        val titleText = typedArray.getString(R.styleable.ValidatorIdentityItemView_validatorIdentityTitle)
        titleText?.let { setTitle(it) }

        val showArrowIcon = typedArray.getBoolean(R.styleable.ValidatorIdentityItemView_showArrowIcon, false)
        changeArrowVisibility(showArrowIcon)

        typedArray.recycle()
    }

    fun setTitle(title: String) {
        validatorIdentityTitle.text = title
    }

    fun setBody(body: String) {
        validatorIdentityBody.text = body
    }

    fun changeArrowVisibility(visible: Boolean) {
        if (visible) {
            validatorIdentityBody.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_top_right_white_16, 0)
        } else {
            validatorIdentityBody.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }
    }
}
