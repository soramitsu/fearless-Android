package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam

import android.content.Context
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.util.AttributeSet
import androidx.lifecycle.LifecycleCoroutineScope
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.databinding.ViewMoonbeamStep4Binding
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.CustomContributeViewState

class MoonbeamStep4Contribute @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : CustomContributeView(context, attrs, defStyle) {

    private val binding: ViewMoonbeamStep4Binding

    init {
        inflate(context, R.layout.view_moonbeam_step4, this)
        binding = ViewMoonbeamStep4Binding.bind(this)

        FeatureUtils.getFeature<CrowdloanFeatureComponent>(
            context,
            CrowdloanFeatureApi::class.java
        ).inject(this)
    }

    override fun bind(viewState: CustomContributeViewState, scope: LifecycleCoroutineScope) {
        require(viewState is MoonbeamContributeViewState)

        binding.moonbeamEtheriumAddressInput.content.bindTo(viewState.enteredEtheriumAddressFlow, scope)
        binding.moonbeamEtheriumAddressInput.content.filters = arrayOf<InputFilter>(LengthFilter(42))

        binding.moonbeamContributeAmount.amountInput.bindTo(viewState.enteredAmountFlow, scope)
    }
}
