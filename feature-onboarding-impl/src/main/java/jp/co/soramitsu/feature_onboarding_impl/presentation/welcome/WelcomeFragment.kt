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
import kotlinx.android.synthetic.main.fragment_welcome.termsTv

class WelcomeFragment : BaseFragment<WelcomeViewModel>() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_welcome, container, false)
    }

    override fun initViews() {
        configureTermsAndPrivacy1(getString(R.string.onboarding_terms_and_conditions_1), getString(R.string.onboarding_terms_and_conditions_2),
            getString(R.string.onboarding_privacy_policy))
        termsTv.movementMethod = LinkMovementMethod.getInstance()
        termsTv.highlightColor = Color.TRANSPARENT
    }

    private fun configureTermsAndPrivacy1(sourceText: String, terms: String, privacy: String) {
        val termsClickableSpan = object : ClickableSpan() {
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
            }

            override fun onClick(widget: View) {
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
        FeatureUtils.getFeature<OnboardingFeatureComponent>(context!!, OnboardingFeatureApi::class.java)
            .welcomeComponentFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: WelcomeViewModel) {
    }
}