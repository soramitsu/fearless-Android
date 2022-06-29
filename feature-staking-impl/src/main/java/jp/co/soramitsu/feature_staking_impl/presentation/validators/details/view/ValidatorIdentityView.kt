package jp.co.soramitsu.feature_staking_impl.presentation.validators.details.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewValidatorIdentityBinding
import jp.co.soramitsu.feature_staking_impl.presentation.validators.details.model.IdentityModel


class ValidatorIdentityView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    private val binding: ViewValidatorIdentityBinding

    init {
        inflate(context, R.layout.view_validator_identity, this)
        binding = ViewValidatorIdentityBinding.bind(this)

        orientation = VERTICAL
    }

    fun populateIdentity(identity: IdentityModel) {
        binding.validatorIdentityLegalNameView.setBodyOrHide(identity.legal)
        binding.validatorIdentityEmailView.setBodyOrHide(identity.email)
        binding.validatorIdentityWebView.setBodyOrHide(identity.web)
        binding.validatorIdentityTwitterView.setBodyOrHide(identity.twitter)
        binding.validatorIdentityRiotNameView.setBodyOrHide(identity.riot)
    }

    fun setWebClickListener(clickListener: () -> Unit) {
        binding.validatorIdentityWebView.setOnClickListener { clickListener() }
    }

    fun setEmailClickListener(clickListener: () -> Unit) {
        binding.validatorIdentityEmailView.setOnClickListener { clickListener() }
    }

    fun setTwitterClickListener(clickListener: () -> Unit) {
        binding.validatorIdentityTwitterView.setOnClickListener { clickListener() }
    }
}
