package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeBrowserEvents
import jp.co.soramitsu.common.utils.setVisible
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.validators.ValidatorsAdapter
import jp.co.soramitsu.feature_staking_impl.presentation.validators.recommended.model.ValidatorModel
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorLearnMore
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsAccounts
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsList
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsNext
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsProgress
import kotlinx.android.synthetic.main.fragment_recommended_validators.recommendedValidatorsToolbar
import kotlinx.android.synthetic.main.fragment_staking_balance.stakingBalanceToolbar

class StakingBalanceFragment : BaseFragment<StakingBalanceViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_staking_balance, container, false)
    }

    override fun initViews() {
        stakingBalanceToolbar.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }
    }

    override fun inject() {
        FeatureUtils.getFeature<StakingFeatureComponent>(
            requireContext(),
            StakingFeatureApi::class.java
        )
            .stakingBalanceFactory()
            .create(this)
            .inject(this)
    }

    override fun subscribe(viewModel: StakingBalanceViewModel) {

    }
}
