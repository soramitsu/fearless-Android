package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleCoroutineScope
import jp.co.soramitsu.common.utils.bindTo
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.databinding.ViewMoonbeamStep1Binding
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.CustomContributeViewState
import kotlinx.coroutines.launch

class MoonbeamStep1Terms @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : CustomContributeView(context, attrs, defStyle) {

    private val binding: ViewMoonbeamStep1Binding

    init {
        inflate(context, R.layout.view_moonbeam_step1, this)
        binding = ViewMoonbeamStep1Binding.bind(this)
    }

    override fun bind(viewState: CustomContributeViewState, scope: LifecycleCoroutineScope) {
        require(viewState is MoonbeamContributeViewState)

        binding.referralPrivacySwitch.bindTo(viewState.privacyAcceptedFlow, scope)

        scope.launch {
            binding.tvMoonbeamTermsDesc.text = viewState.termsText()
        }
    }
}
