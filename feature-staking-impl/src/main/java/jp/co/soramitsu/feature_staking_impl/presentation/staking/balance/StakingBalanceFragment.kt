package jp.co.soramitsu.feature_staking_impl.presentation.staking.balance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnNextLayout
import dev.chrisbanes.insetter.applyInsetter
import jp.co.soramitsu.common.base.BaseFragment
import jp.co.soramitsu.common.di.FeatureUtils
import jp.co.soramitsu.common.mixin.impl.observeValidations
import jp.co.soramitsu.common.utils.updatePadding
import jp.co.soramitsu.feature_staking_api.di.StakingFeatureApi
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.di.StakingFeatureComponent
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.rebond.ChooseRebondKindBottomSheet
import kotlinx.android.synthetic.main.fragment_staking_balance.stakingBalanceActions
import kotlinx.android.synthetic.main.fragment_staking_balance.stakingBalanceInfo
import kotlinx.android.synthetic.main.fragment_staking_balance.stakingBalanceScrollingArea
import kotlinx.android.synthetic.main.fragment_staking_balance.stakingBalanceToolbar
import kotlinx.android.synthetic.main.fragment_staking_balance.stakingBalanceUnbondings

class StakingBalanceFragment : BaseFragment<StakingBalanceViewModel>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_staking_balance, container, false)
    }

    override fun initViews() {
        stakingBalanceToolbar.applyInsetter {
            type(statusBars = true) {
                padding()
            }
        }

        stakingBalanceToolbar.setHomeButtonListener { viewModel.backClicked() }

        stakingBalanceActions.bondMore.setOnClickListener { viewModel.bondMoreClicked() }
        stakingBalanceActions.unbond.setOnClickListener { viewModel.unbondClicked() }
        stakingBalanceActions.redeem.setOnClickListener { viewModel.redeemClicked() }

        // set padding dynamically so initially scrolling area in under toolbar
        stakingBalanceToolbar.doOnNextLayout {
            stakingBalanceScrollingArea.updatePadding(top = it.height + 8.dp)
        }

        stakingBalanceUnbondings.setMoreActionClickListener {
            viewModel.unbondingsMoreClicked()
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
        observeValidations(viewModel)

        viewModel.stakingBalanceModelLiveData.observe {
            with(stakingBalanceInfo) {
                bonded.setTokenAmount(it.bonded.token)
                bonded.setFiatAmount(it.bonded.fiat)

                unbonding.setTokenAmount(it.unbonding.token)
                unbonding.setFiatAmount(it.unbonding.fiat)

                redeemable.setTokenAmount(it.redeemable.token)
                redeemable.setFiatAmount(it.redeemable.fiat)
            }
        }

        viewModel.unbondingsLiveData.observe(stakingBalanceUnbondings::submitList)

        viewModel.redeemEnabledLiveData.observe {
            stakingBalanceActions.redeem.isEnabled = it
        }

        viewModel.showRebondActionsEvent.observeEvent {
            ChooseRebondKindBottomSheet(requireContext(), viewModel::rebondKindChosen)
                .show()
        }
    }
}
