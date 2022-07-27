package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.interlay

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleCoroutineScope
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.observe
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.common.view.TableCellView
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralContributeView

class InterlayContributeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ReferralContributeView(context, attrs, defStyle, R.layout.interlay_referral_flow) {

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

        require(viewState is InterlayContributeViewState)
        viewState.privacyAcceptedFlow.value = true // no agreement for interlay
        viewState.bonusFlow.observe(scope) { bonus ->
            findViewById<TableCellView>(R.id.referralFriendBonus).setVisible(bonus != null)

            bonus?.let {
                findViewById<TableCellView>(R.id.referralFriendBonus).showValue(bonus)
            }
        }
        viewState.bonusNumberFlow.observe(scope) { bonus ->
            findViewById<TableCellView>(R.id.referralFriendBonus).setValueColorRes(getColor(bonus))
        }
    }
}
