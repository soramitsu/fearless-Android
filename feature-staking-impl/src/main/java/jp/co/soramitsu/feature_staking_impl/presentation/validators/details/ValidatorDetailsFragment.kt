package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.model.ValidatorDetailsModel
import kotlinx.android.synthetic.main.fragment_validator_details.validatorInfo

class ValidatorDetailsFragment : BaseFragment<ValidatorDetailsViewModel>() {

    companion object {
        private const val KEY_VALIDATOR = "validator"

        fun getBundle(validator: ValidatorDetailsModel): Bundle {
            return Bundle().apply {
                putParcelable(KEY_VALIDATOR, validator)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_validator_details, container, false)
    }

    override fun initViews() {
    }

    override fun inject() {
        val validator = argument<ValidatorDetailsModel>(KEY_VALIDATOR)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .validatorDetailsComponentFactory()
            .create(this, validator)
            .inject(this)
    }

    override fun subscribe(viewModel: ValidatorDetailsViewModel) {
        viewModel.validatorDetails.observe {
            validatorInfo.setNominatorsCount(it.nominators.size.toString())
            validatorInfo.setEstimatedRewardApy(it.apy)
        }
    }
}