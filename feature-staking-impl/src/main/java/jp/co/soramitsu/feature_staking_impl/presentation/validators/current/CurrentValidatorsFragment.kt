package jp.co.soramitsu.feature_staking_impl.presentation.validators.current

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.current.model.NominatedValidatorModel
import kotlinx.android.synthetic.main.fragment_current_validators.*

class CurrentValidatorsFragment : BaseFragment<CurrentValidatorsViewModel>(), CurrentValidatorsAdapter.Handler {

    lateinit var adapter: CurrentValidatorsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_current_validators, container, false)
    }

    override fun initViews() {
        currentValidatorsContainer.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        adapter = CurrentValidatorsAdapter(this)
        currentValidatorsList.adapter = adapter

        currentValidatorsList.setHasFixedSize(true)

        currentValidatorsToolbar.setHomeButtonListener { viewModel.backClicked() }

        currentValidatorsToolbar.setRightActionClickListener { viewModel.changeClicked() }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .currentValidatorsFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: CurrentValidatorsViewModel) {
        viewModel.currentValidatorModelsLiveData.observe { loadingState ->
            when (loadingState) {
                is LoadingState.Loading -> {
                    currentValidatorsList.makeGone()
                    currentValidatorsProgress.makeVisible()
                }

                is LoadingState.Loaded -> {
                    currentValidatorsList.makeVisible()
                    currentValidatorsProgress.makeGone()

                    adapter.submitList(loadingState.data)
                }
            }
        }

        viewModel.oversubscribedValidatorsFlow.observe {
            currentValidatorsOversubscribedMessage.setVisible(it)
            payoutDivider.setVisible(it)
        }
    }

    override fun infoClicked(validatorModel: NominatedValidatorModel) {
        viewModel.validatorInfoClicked(validatorModel.addressModel.address)
    }
}
