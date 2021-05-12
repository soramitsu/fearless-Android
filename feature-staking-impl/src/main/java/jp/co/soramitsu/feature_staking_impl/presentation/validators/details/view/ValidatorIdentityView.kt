package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.IdentityModel
import kotlinx.android.synthetic.main.view_validator_identity.view.validatorIdentityDisplayNameView
import kotlinx.android.synthetic.main.view_validator_identity.view.validatorIdentityEmailView
import kotlinx.android.synthetic.main.view_validator_identity.view.validatorIdentityLegalNameView
import kotlinx.android.synthetic.main.view_validator_identity.view.validatorIdentityRiotNameView
import kotlinx.android.synthetic.main.view_validator_identity.view.validatorIdentityTwitterView
import kotlinx.android.synthetic.main.view_validator_identity.view.validatorIdentityWebView

class ValidatorIdentityView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_validator_identity, this)

        orientation = VERTICAL
    }

    fun populateIdentity(identity: IdentityModel) {
        validatorIdentityDisplayNameView.setBodyOrHide(identity.display)
        validatorIdentityLegalNameView.setBodyOrHide(identity.legal)
        validatorIdentityEmailView.setBodyOrHide(identity.email)
        validatorIdentityWebView.setBodyOrHide(identity.web)
        validatorIdentityTwitterView.setBodyOrHide(identity.twitter)
        validatorIdentityRiotNameView.setBodyOrHide(identity.riot)
    }

    fun setWebClickListener(clickListener: () -> Unit) {
        validatorIdentityWebView.setOnClickListener { clickListener() }
    }

    fun setEmailClickListener(clickListener: () -> Unit) {
        validatorIdentityEmailView.setOnClickListener { clickListener() }
    }

    fun setTwitterClickListener(clickListener: () -> Unit) {
        validatorIdentityTwitterView.setOnClickListener { clickListener() }
    }
}
