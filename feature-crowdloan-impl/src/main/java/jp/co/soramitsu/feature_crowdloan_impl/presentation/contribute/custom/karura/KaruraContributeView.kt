package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.karura

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import coil.ImageLoader
import coil.load
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.createSpannable
import jp.co.soramitsu.common.utils.observe
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import kotlinx.android.synthetic.main.view_karura_flow.view.karuraBonus
import kotlinx.android.synthetic.main.view_karura_flow.view.karuraFearlessBonusApply
import kotlinx.android.synthetic.main.view_karura_flow.view.karuraFearlessBonusTitle
import kotlinx.android.synthetic.main.view_karura_flow.view.karuraLearnMore
import kotlinx.android.synthetic.main.view_karura_flow.view.karuraPrivacySwitch
import kotlinx.android.synthetic.main.view_karura_flow.view.karuraPrivacyText
import kotlinx.android.synthetic.main.view_karura_flow.view.karuraReferralCodeInput
import javax.inject.Inject

class KaruraContributeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : CustomContributeView(context, attrs, defStyle) {

    @Inject lateinit var imageLoader: ImageLoader

    init {
        View.inflate(context, R.layout.view_karura_flow, this)

        FeatureUtils.getFeature<CrowdloanFeatureComponent>(
            context,
            CrowdloanFeatureApi::class.java
        ).inject(this)

        karuraPrivacyText.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun bind(
        viewState: CustomContributeViewState,
        scope: LifecycleCoroutineScope
    ) {
        require(viewState is KaruraContributeViewState)

        karuraReferralCodeInput.content.bindTo(viewState.enteredReferralCodeFlow, scope)
        karuraPrivacySwitch.bindTo(viewState.privacyAcceptedFlow, scope)

        karuraFearlessBonusTitle.text = viewState.applyFearlessTitle

        viewState.applyFearlessCodeEnabledFlow.observe(scope) { enabled ->
            karuraFearlessBonusApply.isEnabled = enabled

            val applyBonusButtonText = if (enabled) R.string.crowdloan_apply else R.string.crowdloan_applied
            karuraFearlessBonusApply.setText(applyBonusButtonText)
        }

        viewState.bonusFlow.observe(scope) {
            karuraBonus.showValue(it)
        }

        with(viewState.learnBonusesTitle) {
            karuraLearnMore.icon.load(iconLink, imageLoader)
            karuraLearnMore.title.text = text

            karuraLearnMore.setOnClickListener { viewState.learnMoreClicked() }
        }

        karuraFearlessBonusApply.setOnClickListener { viewState.applyFearlessCode() }

        karuraPrivacyText.text = createSpannable(context.getString(R.string.crowdloan_privacy_policy)) {
            clickable(context.getString(R.string.onboarding_terms_and_conditions_2)) {
                viewState.privacyClicked()
            }
        }

        viewState.openBrowserFlow.observe(scope) {
            context.showBrowser(it)
        }
    }
}
