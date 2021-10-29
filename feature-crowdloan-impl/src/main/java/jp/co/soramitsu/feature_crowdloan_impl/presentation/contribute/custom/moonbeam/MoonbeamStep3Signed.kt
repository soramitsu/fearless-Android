package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState

class MoonbeamStep3Signed @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : CustomContributeView(context, attrs, defStyle) {

    init {
        View.inflate(context, R.layout.view_moonbeam_step3, this)

        FeatureUtils.getFeature<CrowdloanFeatureComponent>(
            context,
            CrowdloanFeatureApi::class.java
        ).inject(this)
    }

    override fun bind(viewState: CustomContributeViewState, scope: LifecycleCoroutineScope) {
    }
}
