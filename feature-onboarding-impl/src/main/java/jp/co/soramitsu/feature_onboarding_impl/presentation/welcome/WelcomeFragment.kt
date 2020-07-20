package jp.co.soramitsu.feature_onboarding_impl.presentation.welcome

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
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
        termsTv.text = configureTermsAndPrivacy()
        termsTv.movementMethod = LinkMovementMethod.getInstance()
        termsTv.highlightColor = Color.TRANSPARENT
    }

    private fun configureTermsAndPrivacy(): SpannableString {
        val termsSpan = object : ClickableSpan() {
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
            }

            override fun onClick(widget: View) {
            }
        }

        val termsContent = SpannableString("Terms and Conditions")
        termsContent.setSpan(
            termsSpan,
            0,
            termsContent.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val privacySpan = object : ClickableSpan() {
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
            }

            override fun onClick(widget: View) {
            }
        }

        val privacyContent = SpannableString("Privacy Policy")
        privacyContent.setSpan(
            privacySpan,
            0,
            privacyContent.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val spannableBuilder = SpannableStringBuilder()
        spannableBuilder.append("I have read and agreed with")
        spannableBuilder.append("\n")
        spannableBuilder.append(termsContent)
        spannableBuilder.append(" ")
        spannableBuilder.append("and")
        spannableBuilder.append(" ")
        spannableBuilder.append(privacyContent)
        return SpannableString.valueOf(spannableBuilder)
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