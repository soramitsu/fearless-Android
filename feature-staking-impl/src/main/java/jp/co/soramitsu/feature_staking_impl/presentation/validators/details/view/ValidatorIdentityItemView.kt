package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.feature_staking_impl.R
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

        typedArray.recycle()
    }

    fun setTitle(title: String) {
        validatorIdentityTitle.text = title
    }
}