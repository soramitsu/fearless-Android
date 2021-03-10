package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.android.synthetic.main.view_validator_info_item.view.validatorIdentityBody
import kotlinx.android.synthetic.main.view_validator_info_item.view.validatorIdentityTitle

class ValidatorInfoItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_validator_info_item, this)

        orientation = VERTICAL

        attrs?.let { applyAttributes(it) }
    }

    private fun applyAttributes(attributeSet: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.ValidatorInfoItemView)

        val titleText = typedArray.getString(R.styleable.ValidatorInfoItemView_validatorInfoItemTitle)
        titleText?.let { setTitle(it) }

        val icon = typedArray.getResourceId(R.styleable.ValidatorInfoItemView_validatorInfoItemIcon, 0)
        setIconResource(icon)

        typedArray.recycle()
    }

    fun setTitle(title: String) {
        validatorIdentityTitle.text = title
    }

    fun setBody(body: String) {
        validatorIdentityBody.text = body
    }

    fun setIconResource(resource: Int) {
        validatorIdentityBody.setCompoundDrawablesWithIntrinsicBounds(0, 0, resource, 0)
    }
}