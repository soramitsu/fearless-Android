package jp.co.soramitsu.feature_onboarding_impl.presentation.welcome

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_onboarding_api.di.OnboardingFeatureApi
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.feature_onboarding_impl.di.OnboardingFeatureComponent
import kotlinx.android.synthetic.main.fragment_welcome.back
import kotlinx.android.synthetic.main.fragment_welcome.createAccountBtn
import kotlinx.android.synthetic.main.fragment_welcome.importAccountBtn
import kotlinx.android.synthetic.main.fragment_welcome.termsTv

class WelcomeFragment : BaseFragment<WelcomeViewModel>() {

    companion object {
        private const val KEY_DISPLAY_BACK = "display_back"

        fun getBundle(displayBack: Boolean): Bundle {

            return Bundle().apply {
                putBoolean(KEY_DISPLAY_BACK, displayBack)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_welcome, container, false)
    }

    override fun initViews() {
        configureTermsAndPrivacy1(
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

    private fun configureTermsAndPrivacy1(sourceText: String, terms: String, privacy: String) {
        val termsClickableSpan = object : ClickableSpan() {
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
            }

            override fun onClick(widget: View) {
                viewModel.termsClicked()
            }
        }

        val termsIndex = sourceText.indexOf(terms)

        if (termsIndex == -1) {
            return
        }

        val lastTermsIndex = termsIndex + terms.length

        if (lastTermsIndex > sourceText.length) {
            return
        }

        val spannable = SpannableString(sourceText)
        spannable.setSpan(
            termsClickableSpan,
            termsIndex,
            lastTermsIndex,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val privacyClickableSpan = object : ClickableSpan() {
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
            }

            override fun onClick(widget: View) {
                viewModel.privacyClicked()
            }
        }

        val privacyIndex = sourceText.indexOf(privacy)

        if (privacyIndex == -1) {
            return
        }

        val lastPrivacyIndex = privacyIndex + privacy.length

        if (lastPrivacyIndex > sourceText.length) {
            return
        }

        spannable.setSpan(
            privacyClickableSpan,
            privacyIndex,
            lastPrivacyIndex,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        termsTv.text = spannable
    }

    override fun inject() {
        val shouldShowBack = arguments!![KEY_DISPLAY_BACK] as Boolean

        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .welcomeComponentFactory()
            .create(this, shouldShowBack)
            .inject(this)
    }

    override fun subscribe(viewModel: WelcomeViewModel) {
        viewModel.shouldShowBackLiveData.observe {
            back.visibility = if (it) View.VISIBLE else View.GONE
        }
    }
}