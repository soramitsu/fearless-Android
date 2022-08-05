package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.switchmaterial.SwitchMaterial
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.common.utils.createSpannable
import jp.co.soramitsu.common.utils.observe
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.utils.showBrowser
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.common.view.TableCellView
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import java.math.BigDecimal

open class ReferralContributeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    @LayoutRes layoutId: Int = R.layout.view_referral_flow
) : CustomContributeView(context, attrs, defStyle) {

    init {
        View.inflate(context, layoutId, this)

        findViewById<TextView>(R.id.referralPrivacyText)?.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun bind(
        viewState: CustomContributeViewState,
        scope: LifecycleCoroutineScope
    ) {
        require(viewState is ReferralContributeViewState)

        val referralReferralCodeInput: InputField = findViewById(R.id.referralReferralCodeInput)
        val referralPrivacySwitch: SwitchMaterial = findViewById(R.id.referralPrivacySwitch)
        val referralFearlessBonusApply: TextView = findViewById(R.id.referralFearlessBonusApply)
        val referralBonus: TableCellView = findViewById(R.id.referralBonus)
        val referralPrivacyText: TextView = findViewById(R.id.referralPrivacyText)

        referralReferralCodeInput.content.bindTo(viewState.enteredReferralCodeFlow, scope)
        referralPrivacySwitch.bindTo(viewState.privacyAcceptedFlow, scope)

        viewState.applyFearlessCodeEnabledFlow.observe(scope) { enabled ->
            referralFearlessBonusApply.isEnabled = enabled
            val applyBonusButtonText = when {
                viewState.isAstar || viewState.isAcala -> R.string.apply_fearless_referal_wallet
                else -> when {
                    enabled -> R.string.apply_fearless_wallet_bonus
                    else -> R.string.applied_fearless_wallet_bonus
                }
            }
            referralFearlessBonusApply.setText(applyBonusButtonText)
        }

        viewState.bonusFlow.observe(scope) { bonus ->
            referralBonus.setVisible(bonus != null)

            bonus?.let { referralBonus.showValue(bonus) }
        }

        viewState.bonusNumberFlow.observe(scope) { bonus ->
            referralBonus.setValueColorRes(getColor(bonus))
        }

        referralFearlessBonusApply.setOnClickListener { viewState.applyFearlessCode() }

        referralPrivacyText.text = createSpannable(context.getString(R.string.onboarding_terms_and_conditions_1)) {
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
