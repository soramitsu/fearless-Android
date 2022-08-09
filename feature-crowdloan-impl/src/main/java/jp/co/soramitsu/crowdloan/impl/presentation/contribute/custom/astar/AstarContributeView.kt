package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.astar

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleCoroutineScope
import jp.co.soramitsu.common.view.InputField
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.referral.ReferralContributeView

class AstarContributeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ReferralContributeView(context, attrs, defStyle, R.layout.astar_referral_flow) {

    override fun bind(
        viewState: CustomContributeViewState,
        scope: LifecycleCoroutineScope
    ) {
        super.bind(viewState, scope)

        require(viewState is AstarContributeViewState)
        viewState.privacyAcceptedFlow.value = true // no agreement for astar
        findViewById<InputField>(R.id.referralReferralCodeInput).hint = context.getString(R.string.crowdloan_astar_referral_code_hint)
    }
}
