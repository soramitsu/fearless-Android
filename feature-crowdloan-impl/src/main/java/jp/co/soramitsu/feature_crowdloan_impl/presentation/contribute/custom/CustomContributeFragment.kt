package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_crowdloan_api.di.CrowdloanFeatureApi
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.CrowdloanFeatureComponent
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import kotlinx.android.synthetic.main.fragment_custom_contribute.customContributeApply
import kotlinx.android.synthetic.main.fragment_custom_contribute.customContributeContainer
import kotlinx.android.synthetic.main.fragment_custom_contribute.customContributeToolbar
import kotlinx.android.synthetic.main.fragment_custom_contribute.customFlowContainer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

private const val KEY_PAYLOAD = "KEY_PAYLOAD"

class CustomContributeFragment : BaseFragment<CustomContributeViewModel>() {

    @Inject
    protected lateinit var contributionManager: CustomContributeManager

    companion object {

        fun getBundle(payload: CustomContributePayload) = Bundle().apply {
            putParcelable(KEY_PAYLOAD, payload)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_custom_contribute, container, false)
    }

    override fun initViews() {
        customContributeContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }

            consume(true)
        }

        customContributeToolbar.setHomeButtonListener { viewModel.backClicked() }

        customContributeApply.prepareForProgress(viewLifecycleOwner)
        customContributeApply.setOnClickListener { viewModel.applyClicked() }
    }

    override fun inject() {
        val payload = argument<CustomContributePayload>(KEY_PAYLOAD)

        FeatureUtils.getFeature<CrowdloanFeatureComponent>(
            requireContext(),
            CrowdloanFeatureApi::class.java
        )
            .customContributeFactory()
            .create(this, payload)
            .inject(this)
    }

    override fun subscribe(viewModel: CustomContributeViewModel) {
        lifecycleScope.launchWhenResumed {
            viewModel.applyButtonState.combine(viewModel.applyingInProgress) { state, inProgress ->
                when {
                    inProgress -> customContributeApply.setState(ButtonState.PROGRESS)
                    state is ApplyActionState.Unavailable -> {
                        customContributeApply.setState(ButtonState.DISABLED)
                        customContributeApply.text = state.reason
                    }
                    state is ApplyActionState.Available -> {
                        customContributeApply.setState(ButtonState.NORMAL)
                        customContributeApply.setText(R.string.crowdloan_apply)
                    }
                }
            }.collect()
        }

        viewModel.viewStateFlow.observe { viewState ->
            customFlowContainer.removeAllViews()

            val newView = contributionManager.createView(viewModel.customFlowType, requireContext())

            customFlowContainer.addView(newView)

            newView.bind(viewState, lifecycleScope)
        }
    }
}
