package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.IdentityModel
import kotlinx.android.synthetic.main.view_validator_identity.view.validatorIdentityAddressView
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
        setTextOrHideIdentityItem(validatorIdentityDisplayNameView, identity.display)
        setTextOrHideIdentityItem(validatorIdentityLegalNameView, identity.legal)
        setTextOrHideIdentityItem(validatorIdentityEmailView, identity.email)
        setTextOrHideIdentityItem(validatorIdentityWebView, identity.web)
        setTextOrHideIdentityItem(validatorIdentityTwitterView, identity.twitter)
        setTextOrHideIdentityItem(validatorIdentityRiotNameView, identity.riot)
    }

    fun setAddress(address: String?) {
        setTextOrHideIdentityItem(validatorIdentityAddressView, address)
    }

    private fun setTextOrHideIdentityItem(item: ValidatorIdentityItemView, text: String?) {
        if (text == null) {
            item.makeGone()
            item.setBody("")
        } else {
            item.makeVisible()
            item.setBody(text)
        }
    }
}