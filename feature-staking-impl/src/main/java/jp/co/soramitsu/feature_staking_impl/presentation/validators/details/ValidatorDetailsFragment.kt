package jp.co.soramitsu.feature_staking_impl.presentation.validators.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.utils.makeGone
import jp.co.soramitsu.common.utils.makeVisible
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.parcel.ValidatorDetailsParcelModel
import kotlinx.android.synthetic.main.fragment_validator_details.validatorDetailsToolbar
import kotlinx.android.synthetic.main.fragment_validator_details.validatorIdentity
import kotlinx.android.synthetic.main.fragment_validator_details.validatorInfo

class ValidatorDetailsFragment : BaseFragment<ValidatorDetailsViewModel>() {

    companion object {
        private const val KEY_VALIDATOR = "validator"

        fun getBundle(validator: ValidatorDetailsParcelModel): Bundle {
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
        validatorDetailsToolbar.setHomeButtonListener { viewModel.backClicked() }
    }

    override fun inject() {
        val validator = argument<ValidatorDetailsParcelModel>(KEY_VALIDATOR)

        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .validatorDetailsComponentFactory()
            .create(this, validator)
            .inject(this)
    }

    override fun subscribe(viewModel: ValidatorDetailsViewModel) {
        viewModel.validatorDetails.observe { validator ->
            validatorInfo.setNominatorsCount(validator.nominatorsCount)
            validatorInfo.setEstimatedRewardApy(validator.apy)
            validatorInfo.setTotalStakeValue(validator.totalStake)
            if (validator.totalStakeFiat == null) {
                validatorInfo.hideTotalStakeFiat()
                validatorInfo.setTotalStakeValueFiat("")
            } else {
                validatorInfo.showTotalStakeFiat()
                validatorInfo.setTotalStakeValueFiat(validator.totalStakeFiat)
            }
            if (validator.identity == null) {
                validatorIdentity.makeGone()
            } else {
                validatorIdentity.makeVisible()
                validatorIdentity.populateIdentity(validator.identity)
                validatorIdentity.setAddress(validator.address)
            }
            validator.identity?.display?.let { validatorDetailsToolbar.setTitle(it) }
        }
    }
}