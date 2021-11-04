package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleCoroutineScope
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeView
import kotlinx.android.synthetic.main.view_referral_flow.view.referralReferralCodeInput

class AstarContributeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ReferralContributeView(context, attrs, defStyle, R.layout.astar_referral_flow) {

    init {
        FeatureUtils.getFeature<CrowdloanFeatureComponent>(
            context,
            CrowdloanFeatureApi::class.java
        ).inject(this)
    }

    override fun bind(
        viewState: CustomContributeViewState,
        scope: LifecycleCoroutineScope
    ) {
        super.bind(viewState, scope)

        require(viewState is AstarContributeViewState)
        viewState.privacyAcceptedFlow.value = true // no agreement for astar
        referralReferralCodeInput.hint = context.getString(R.string.crowdloan_astar_referral_code_hint)
    }
}
