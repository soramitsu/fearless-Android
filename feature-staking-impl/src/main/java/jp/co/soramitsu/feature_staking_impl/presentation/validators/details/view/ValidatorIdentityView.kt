package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
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
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_validator_identity, this)

        orientation = VERTICAL
    }

    fun populateIdentity(identity: IdentityModel) {
        if (identity.display == null) {
            validatorIdentityDisplayNameView.makeGone()
            validatorIdentityDisplayNameView.setBody("")
        } else {
            validatorIdentityDisplayNameView.makeVisible()
            validatorIdentityDisplayNameView.setBody(identity.display)
        }

        if (identity.legal == null) {
            validatorIdentityLegalNameView.makeGone()
            validatorIdentityLegalNameView.setBody("")
        } else {
            validatorIdentityLegalNameView.makeVisible()
            validatorIdentityLegalNameView.setBody(identity.legal)
        }

        if (identity.email == null) {
            validatorIdentityEmailView.makeGone()
            validatorIdentityEmailView.setBody("")
        } else {
            validatorIdentityEmailView.makeVisible()
            validatorIdentityEmailView.setBody(identity.email)
        }

        if (identity.web == null) {
            validatorIdentityWebView.makeGone()
            validatorIdentityWebView.setBody("")
        } else {
            validatorIdentityWebView.makeVisible()
            validatorIdentityWebView.setBody(identity.web)
        }

        if (identity.twitter == null) {
            validatorIdentityTwitterView.makeGone()
            validatorIdentityTwitterView.setBody("")
        } else {
            validatorIdentityTwitterView.makeVisible()
            validatorIdentityTwitterView.setBody(identity.twitter)
        }

        if (identity.riot == null) {
            validatorIdentityRiotNameView.makeGone()
            validatorIdentityRiotNameView.setBody("")
        } else {
            validatorIdentityRiotNameView.makeVisible()
            validatorIdentityRiotNameView.setBody(identity.riot)
        }
    }
}