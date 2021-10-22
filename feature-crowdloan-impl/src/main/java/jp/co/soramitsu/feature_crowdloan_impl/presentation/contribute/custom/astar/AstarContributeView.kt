package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.observe
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeView
import kotlinx.android.synthetic.main.astar_referral_flow.view.referralFriendBonus
import kotlinx.android.synthetic.main.view_referral_flow.view.referralPrivacyText

class AstarContributeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ReferralContributeView(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.astar_referral_flow, this)

        FeatureUtils.getFeature<CrowdloanFeatureComponent>(
            context,
            CrowdloanFeatureApi::class.java
        ).inject(this)

        referralPrivacyText.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun bind(
        viewState: CustomContributeViewState,
        scope: LifecycleCoroutineScope
    ) {
        super.bind(viewState, scope)

        require(viewState is AstarContributeViewState)
        viewState.privacyAcceptedFlow.value = true //no agreement for astar

        viewState.bonusFriendFlow.observe(scope) { bonus ->
            referralFriendBonus.setVisible(bonus != null)

            bonus?.let { referralFriendBonus.showValue(bonus) }
        }

    }
}
