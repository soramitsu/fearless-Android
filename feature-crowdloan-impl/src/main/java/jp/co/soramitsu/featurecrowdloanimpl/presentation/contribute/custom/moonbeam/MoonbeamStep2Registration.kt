package jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.moonbeam

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleCoroutineScope
import jp.co.soramitsu.common.utils.createSpannable
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.databinding.ViewMoonbeamStep2Binding
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeViewState

class MoonbeamStep2Registration @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : CustomContributeView(context, attrs, defStyle) {

    private val binding: ViewMoonbeamStep2Binding

    init {
        inflate(context, R.layout.view_moonbeam_step2, this)
        binding = ViewMoonbeamStep2Binding.bind(this)
    }

    override fun bind(viewState: CustomContributeViewState, scope: LifecycleCoroutineScope) {
        require(viewState is MoonbeamContributeViewState)
        binding.tvMoonbeamRegistrationDesc.text = createSpannable(context.getString(R.string.moonbeam_registration_description)) {
            clickable(context.getString(R.string.moonbeam_registration_description_system_remark)) {}
        }
    }
}
