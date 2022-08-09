package jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.moonbeam

import android.content.Context
import android.util.AttributeSet
import androidx.lifecycle.LifecycleCoroutineScope
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.databinding.ViewMoonbeamStep3Binding
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeView
import jp.co.soramitsu.featurecrowdloanimpl.presentation.contribute.custom.CustomContributeViewState

class MoonbeamStep3Signed @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : CustomContributeView(context, attrs, defStyle) {

    private val binding: ViewMoonbeamStep3Binding

    init {
        inflate(context, R.layout.view_moonbeam_step3, this)
        binding = ViewMoonbeamStep3Binding.bind(this)
    }

    override fun bind(viewState: CustomContributeViewState, scope: LifecycleCoroutineScope) {
        require(viewState is MoonbeamContributeViewState)
        binding.tvMoonbeamSignedHash.text = viewState.getRemarkTxHash()
        binding.tvMoonbeamSignedHash.setOnClickListener {}
    }
}
