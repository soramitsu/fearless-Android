package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.lifecycle.LifecycleCoroutineScope
import coil.ImageLoader
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.createSpannable
import jp.co.soramitsu.common.utils.observe
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import kotlinx.android.synthetic.main.view_referral_flow.view.referralBonus
import kotlinx.android.synthetic.main.view_referral_flow.view.referralFearlessBonusApply
import kotlinx.android.synthetic.main.view_referral_flow.view.referralPrivacySwitch
import kotlinx.android.synthetic.main.view_referral_flow.view.referralPrivacyText
import kotlinx.android.synthetic.main.view_referral_flow.view.referralReferralCodeInput
import java.math.BigDecimal
import javax.inject.Inject

open class ReferralContributeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    @LayoutRes layoutId: Int = R.layout.view_referral_flow
) : CustomContributeView(context, attrs, defStyle) {

    @Inject
    lateinit var imageLoader: ImageLoader

    init {
        View.inflate(context, layoutId, this)

        FeatureUtils.getFeature<CrowdloanFeatureComponent>(
            context,
            CrowdloanFeatureApi::class.java
        ).inject(this)

        findViewById<TextView>(R.id.referralPrivacyText)?.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun bind(
        viewState: CustomContributeViewState,
        scope: LifecycleCoroutineScope
    ) {
        require(viewState is ReferralContributeViewState)

        referralReferralCodeInput?.content?.bindTo(viewState.enteredReferralCodeFlow, scope)
        referralPrivacySwitch?.bindTo(viewState.privacyAcceptedFlow, scope)

        viewState.applyFearlessCodeEnabledFlow.observe(scope) { enabled ->
            referralFearlessBonusApply?.isEnabled = enabled
            val applyBonusButtonText = when {
                viewState.isAstar || viewState.isAcala -> R.string.apply_fearless_referal_wallet
                else -> when {
                    enabled -> R.string.apply_fearless_wallet_bonus
                    else -> R.string.applied_fearless_wallet_bonus
                }
            }
            referralFearlessBonusApply?.setText(applyBonusButtonText)
        }

        viewState.bonusFlow.observe(scope) { bonus ->
            referralBonus?.setVisible(bonus != null)

            bonus?.let { referralBonus?.showValue(bonus) }
        }

        viewState.bonusNumberFlow.observe(scope) { bonus ->
            referralBonus?.setValueColorRes(getColor(bonus))
        }

        referralFearlessBonusApply?.setOnClickListener { viewState.applyFearlessCode() }

        referralPrivacyText?.text = createSpannable(context.getString(R.string.onboarding_terms_and_conditions_1)) {
            clickable(context.getString(R.string.onboarding_terms_and_conditions_2)) {
                viewState.termsClicked()
            }
        }

        viewState.openBrowserFlow.observe(scope) {
            context.showBrowser(it)
        }
    }

    protected fun getColor(bonus: BigDecimal?) = when {
        bonus == null || bonus <= BigDecimal.ZERO -> R.color.white
        else -> R.color.colorAccent
    }
}
