package jp.co.soramitsu.feature_onboarding_impl.presentation.welcome

import android.graphics.Color
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.utils.createSpannable
import jp.co.soramitsu.feature_account_api.presentation.account.create.ChainAccountCreatePayload
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import kotlinx.android.synthetic.main.fragment_welcome.back
import kotlinx.android.synthetic.main.fragment_welcome.createAccountBtn
import kotlinx.android.synthetic.main.fragment_welcome.importAccountBtn
import kotlinx.android.synthetic.main.fragment_welcome.termsTv

class WelcomeFragment : BaseFragment<WelcomeViewModel>() {

    companion object {
        private const val KEY_PAYLOAD = "key_payload"

        fun getBundle(
            displayBack: Boolean,
            chainAccountData: ChainAccountCreatePayload? = null,
        ): Bundle {
            return bundleOf(
                KEY_PAYLOAD to WelcomeFragmentPayload(displayBack, chainAccountData)
            )
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_welcome, container, false)
    }

    override fun initViews() {
        configureTermsAndPrivacy(
            getString(R.string.onboarding_terms_and_conditions_1),
            getString(R.string.onboarding_terms_and_conditions_2),
            getString(R.string.onboarding_privacy_policy)
        )
        termsTv.movementMethod = LinkMovementMethod.getInstance()
        termsTv.highlightColor = Color.TRANSPARENT

        createAccountBtn.setOnClickListener { viewModel.createAccountClicked() }

        importAccountBtn.setOnClickListener { viewModel.importAccountClicked() }

        back.setOnClickListener { viewModel.backClicked() }
    }

    private fun configureTermsAndPrivacy(sourceText: String, terms: String, privacy: String) {
        termsTv.text = createSpannable(sourceText) {
            clickable(terms) {
                viewModel.termsClicked()
            }

            clickable(privacy) {
                viewModel.privacyClicked()
            }
        }
    }

    override fun inject() {
        val payload = argument<WelcomeFragmentPayload>(KEY_PAYLOAD)

        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .welcomeComponentFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: WelcomeViewModel) {
        viewModel.shouldShowBackLiveData.observe {
            back.visibility = if (it) View.VISIBLE else View.GONE
        }

        observeBrowserEvents(viewModel)
    }
}
